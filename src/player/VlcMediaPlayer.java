package player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import utils.HTTPUtilities;
import utils.Utils;

public class VlcMediaPlayer extends Application implements Player {
	private final String VLC = "C:/Program Files (x86)/VideoLAN/VLC/VLC.exe";

	private static final String URL_TEMPLATE = "http://localhost:%d/index.m3u8";
	private static final int RECENT_SEGMENTS_SIZE = 20;

	private Process vlc;
	private Stage stage;
	private HttpServer server;
	volatile private byte[] indexData;
	private static SynchronousQueue<Player> instance;
	private BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(2);
	private Label stats1 = new Label(), stats2 = new Label();

	private double q_Sums = 0, q_Samples = 0;
	private Deque<Integer> recentSegments = new ArrayDeque<>();

	@Override
	public void decode(byte[] data) {
		if (indexData == null)
			indexData = data;
		else {
			Utils.putInto(queue, data);

			Platform.runLater(() -> {
				int quality = ByteBuffer.wrap(data, data.length - Integer.BYTES, Integer.BYTES).getInt();
				recentSegments.addFirst(quality);
				while (recentSegments.size() > RECENT_SEGMENTS_SIZE)
					recentSegments.removeLast();

				q_Sums += quality;
				q_Samples += 1;
				double mean = q_Sums / q_Samples;
				stats1.setText(String.format("avg: %d kbps", (int) mean));
				stats2.setText(String.format("\n\n%s", recentSegments));
			});
		}
	}

	@Override
	public Player setSize(int width, int height) {
		stage.setWidth(width);
		stage.setHeight(height);
		System.err.println("set size not [yet] supported on vlc...");
		return this;
	}

	@Override
	public Player mute(boolean val) {
		System.err.println("mute not [yet] supported on vlc...");
		return this;
	}

	public static Player getInstance() {
		if (instance == null) {
			instance = new SynchronousQueue<>();
			Utils.newThread(true, () -> {
				launch(new String[] {});
			}).start();
		}
		return Utils.takeFrom(instance);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		stage = primaryStage;

		server = new HttpServer();

		primaryStage.setOnCloseRequest(h -> {
			System.err.println("Window closed by user...");
			if (vlc != null)
				vlc.destroyForcibly();
			System.exit(0);
		});

		server.start();

		Group root = new Group();

		Scene scene = new Scene(root, 0, 0);

		primaryStage.setTitle("MyDash Player");

		MediaControl mediaControl = new MediaControl(scene);
		scene.setRoot(mediaControl);

		primaryStage.setScene(scene);
		primaryStage.show();

		instance.put(VlcMediaPlayer.this);

		if (!Files.isExecutable(new File(VLC).toPath()))
			throw new RuntimeException("VLC needs to point to vlc executable...");

		ProcessBuilder pb = new ProcessBuilder(VLC, "--width=400", "--height=400", "-vvv",
				String.format(URL_TEMPLATE, server.localPort()));
		pb.redirectErrorStream(true);
		pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		vlc = pb.start();

		// Instance is now ready...
	}

	class MediaControl extends BorderPane {
		public MediaControl(Scene scene) {
			setStyle("-fx-background-color: black;");

			Pane mvPane = new Pane() {
			};

			stats1.setStyle("-fx-text-fill: yellow;-fx-font-size: 12pt;");
			stats2.setStyle("-fx-text-fill: green;-fx-font-size: 8pt;");

			mvPane.getChildren().add(stats1);
			mvPane.getChildren().add(stats2);
			setCenter(mvPane);
			mvPane.autosize();
		}
	}

	class HttpServer extends Thread {

		final ServerSocket ss;

		HttpServer() throws IOException {
			super.setDaemon(true);
			ss = new ServerSocket(0);
		}

		int localPort() {
			return ss.getLocalPort();
		}

		public void run() {
			try {
				for (;;) {
					Socket cs = ss.accept();
					handleRequest(cs);
				}
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unused")
	void handleRequest(Socket cs) {
		try {
			InputStream is = cs.getInputStream();
			OutputStream os = cs.getOutputStream();

			String request = HTTPUtilities.readLine(is), header;
			// System.err.println(request);
			String[] parts = HTTPUtilities.parseHttpRequest(request);

			while ((header = HTTPUtilities.readLine(is)).length() > 0) {
				// System.err.println(header);
			}

			String action = parts[0];
			String filename = parts[1];

			os.write("HTTP/1.0 200 OK\r\n".getBytes());
			if (filename.endsWith(".m3u8")) {
				os.write("Content-Type: application/x-mpegURL\r\n\r\n".getBytes());
				if (action.equals("GET")) {
					while (indexData == null)
						Thread.sleep(10);

					os.write(indexData);
				}
			} else {
				byte[] data = queue.take();
				int length = data.length - Integer.BYTES;
				os.write(String.format("Content-Length: %d\r\nContent-Type: video/MP2T\r\n\r\n", length).getBytes());
				os.write(data, 0, length);
			}
			os.close();
			cs.close();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

}
