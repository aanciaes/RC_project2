package player;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import utils.HTTPUtilities;

public class VlcFilePlayer {
	
	private static Segment[] toPlay; // Array com todos os segmentos prontos a ser reproduzidos
    private static int added_to_Play; // Numero de segmentos disponiveis para ser reproduzidos
    private static int played; // Numero de segmentos que ja foram reproduzidos
    private static int playout_delay; // Atraso inicial da reproduçao
	private static int lastIndex; // Numero de segmentos-1
	private static String bitrate; // Bitrate a que o video esta a ser reproduzido
	private static String[] availableBitrates; // Array com todas as bitrates disponiveis
	private static int numAvailableBitrates; // Numero de bitrates disponiveis
	private static boolean isDownloading; // Indica se o utilizador ainda esta a fazer download de segmentos
	private static long[] statistics;

	@SuppressWarnings("static-access")
	public static void main(String[] args) throws Exception {

		Map<String,Map<Integer,Segment>> segments_by_bitrate = new HashMap<String,Map<Integer,Segment>>();
		Map<Integer,Segment> current_segments = new HashMap<Integer,Segment>();
		
		playout_delay = 3; // terá de ser definido atraves dos argumentos como diz no enunciado, esta assim para ser mais facil testar
		lastIndex = -1;
		added_to_Play = 0;
		played = 0;
		
		// Le o ficheiro index.dat e regista todos os dados dos segmentos de todas as qualidades disponiveis
		readIndex(args, segments_by_bitrate, current_segments);
		
		// Encontra as bitrates disponiveis para o filme e ficam dispostas ordenadamente(da mais baixa para a mais alta)
		Set<String> keys = segments_by_bitrate.keySet();
		String[] array = keys.toArray(new String[keys.size()]);
		int[] array2 = new int[keys.size()];
		availableBitrates = new String[keys.size()];
		for(int j=0;j<keys.size();j++) {
			array[j] = array[j].replace(".ts", "");
			int a = Integer.parseInt(array[j]);
			array2[j] = a;
		}
		Arrays.sort(array2);
		for(int k=0;k<keys.size();k++) {
			availableBitrates[k] = array2[k] + ".ts";
			numAvailableBitrates++;
		}
		
		// Thread que trata do download da data dos segmentos
		Thread downloading = new Thread(new Runnable() {
			public void run() {
				try {
					String url = args.length == 1 ? args[0] : "http://localhost:8080/";
					URL fileUrl = new URL(url);

					int port = fileUrl.getPort();
					if (port == -1) {
						port = 8080;
					}
					if(numAvailableBitrates>0) {
						bitrate = availableBitrates[0];
					}
					else {
						bitrate = "failed";
					}
					String bitrate_file;
					int initialRange = -1;
					int finalRange = -1;
					long initialTime;
					isDownloading = true;
					for (int i = 0; i < lastIndex-1; i++) {
						bitrate_file = String.format("/the-good-dinossaur/%s", bitrate);
						
						initialTime = System.currentTimeMillis();
						
						if (segments_by_bitrate.containsKey(bitrate)) {
							Socket sock = new Socket(fileUrl.getHost(), port);
							OutputStream toServer = sock.getOutputStream();
							InputStream fromServer = sock.getInputStream();
							DataInputStream dis = new DataInputStream(fromServer);
							System.out.println("Connected to server");

							initialRange = segments_by_bitrate.get(bitrate).get(i).getOffset();
							finalRange = segments_by_bitrate.get(bitrate).get(i + 1).getOffset() - 1;
							segments_by_bitrate.get(bitrate).get(i).setDimension((finalRange+1)-initialRange);
							String request = String.format("GET %s HTTP/1.0\r\n" + "User-Agent: 43367-43776\r\n"
									+ "Range: bytes=%d-%d\r\n\r\n", bitrate_file, initialRange, finalRange);

							toServer.write(request.getBytes());
							System.out.println("Sent request: " + request);
							String answerLine = HTTPUtilities.readLine(fromServer);
							answerLine = HTTPUtilities.readLine(fromServer);
							System.out.println("Got answer: " + answerLine);

							// ??????????????????????????????
							// Falta Interpretar HTTP headers ///
							// ??????????????????????????????

							while (!answerLine.equals("")) {
								answerLine = HTTPUtilities.readLine(fromServer);
								// System.out.println(answerLine);
							}
							
							dis.readUTF(); //
							dis.readLong(); // timestamp
							dis.readLong(); // duration
							byte[] data = new byte[dis.readInt()];
							dis.readFully(data);
							Segment current = segments_by_bitrate.get(bitrate).get(i);
							current.addContent(data);
							current.setTimeToDownload(System.currentTimeMillis()-initialTime);
							toPlay[added_to_Play] = current;
							statistics[added_to_Play++] = (current.getDimension()/current.getTimeToDownload());
							
							System.out.println("Dimension (Bytes): " + current.getDimension());
							System.out.println("Time To Download: (s) " + current.getTimeToDownload());
							System.out.println("Speed: " + (current.getDimension())/current.getTimeToDownload());
							
							dis.close();
							sock.close();
						}
						else {
							System.out.println("ERROR: " + bitrate.replace(".ts", "") + " bitrate not available ("
									+ bitrate_file + " doesnt exist).");
							break;
						}
					}
				} catch (Exception e) {}
				isDownloading = false;
			}
		});
		downloading.start();
		
		// Thread que trata de reproduzir o conteudo que o utilizador ja tem disponivel
		Thread playing = new Thread(new Runnable() {
		     public void run() {
		    	 
		    	Player player = VlcMediaPlayer.getInstance().setSize(700, 80); 
		     
		 		while(played != lastIndex+1) {
		 			if(toPlay[played] != null) {
		 				player.decode(toPlay[played].getContent());
		 				played++;
		 				try {
							Thread.sleep(toPlay[played-1].getDuration());
						} catch (InterruptedException e) {}
		 			}
		 			else {
		 				System.out.println("Buffering...");
		 				try {
		 					if(played>0) {
		 						Thread.sleep(toPlay[played-1].getDuration()*2);
		 					}
		 					else {
		 						bitrate = availableBitrates[0];
		 						Thread.sleep(2000);
		 					}
						} catch (InterruptedException e) {}	
		 			}
		 		}
		     }
		});
		playing.sleep(playout_delay*1000);
		playing.start();
		
		// Thread que trata de controlar a bitrate a que o video e reproduzido
		Thread control = new Thread(new Runnable() {
		     public void run() {
		    	 
		    	 while(isDownloading) {
		    		 break;
		    	 }
		    	 
		     }
		});
		control.start();
	}
	
	private static void readIndex(String[] args, Map<String,Map<Integer,Segment>> segments_by_bitrate, Map<Integer,Segment> current_segments) throws Exception {
		
				String urlIndex = args.length == 1 ? args[0] : "http://localhost:8080/the-good-dinossaur/index.dat";
				URL u = new URL(urlIndex);
				
				InputStream is = u.openStream();
				BufferedReader in = new BufferedReader(new InputStreamReader(is));
				
				String contentLine = "";
		    	String lastQuality = "";
		    	boolean segments = false;
		    	boolean readAllBitRates = false;
				
			    while ((contentLine = in.readLine()) != null) {	    	
					if(!readAllBitRates) {
						if(contentLine.equals("")) {
							readAllBitRates = true;
						}
						else if(contentLine.endsWith(".ts")){
							if(lastQuality.equals("")) {
								lastQuality = contentLine;
							}
							segments_by_bitrate.put(contentLine.trim(), null);
						}
					}
					
					if(readAllBitRates && !segments) {
						String[] q = contentLine.split(" ", 2);
						if(q[0].equals(lastQuality)) {
							segments = true;
						}
					}
					
					if(segments) {
						String quality = HTTPUtilities.parseHttpReply(contentLine)[0];
						int index = Integer.parseInt(HTTPUtilities.parseHttpReply(contentLine)[1]);
						int dimension = Integer.parseInt(HTTPUtilities.parseHttpReply(contentLine)[2]);
						int duration = Integer.parseInt(HTTPUtilities.parseHttpReply(contentLine)[3]);
						
						if(!quality.equals(lastQuality)) {
							lastIndex = -1;
							segments_by_bitrate.put(lastQuality, current_segments);
							current_segments = new HashMap<Integer,Segment>();
							lastQuality = quality;
						}
						
						lastIndex++;
						Segment s = new Segment(quality, index, dimension, duration);
						current_segments.put(index, s);
					}
			    }
			    toPlay = new Segment[lastIndex];
			    statistics = new long[lastIndex];
			    Arrays.fill(toPlay, null);
			    Arrays.fill(statistics, -1);
			    is.close();
			    in.close();
	}
}
