package player;

import java.io.DataInputStream;
import java.io.FileInputStream;

public class FilePlayer {

	public static void main(String[] args) throws Exception {
		Player player = JavaFXMediaPlayer.getInstance().setSize(1280, 80).mute(false);

		FileInputStream fis = new FileInputStream("the-good-dinossaur/2000.ts");
		DataInputStream dis = new DataInputStream(fis);

		while (!dis.readUTF().equals("eof")) {
			dis.readLong(); // timestamp
			dis.readLong(); // duration
			byte[] data = new byte[dis.readInt()];
			dis.readFully(data);
			player.decode(data);
		}
		dis.close();
	}

}
