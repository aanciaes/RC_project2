package player;

public class Segment {

	private String bitRate; // Bitrate do segmento
	private int id; // Identificador do segmento
	private long timeToDownload; // Relativo ao início do ficheiro
	private long duration; // Duração do segmento em ms
	private int offset; // Offset do segmento no ficheiro
	private int dimension; // Dimensao do segmento
	private byte[] content; // Conteudo do segmento (stream)
	
	public Segment(String bitRate, int id, int offset, int duration) {
		this.bitRate = bitRate;
		this.id = id;
		timeToDownload = System.currentTimeMillis();
		this.duration = duration;
		this.offset = offset;
	}
	
	public String getBitrate() {
		return bitRate;
	}
	
	public int getId() {
		return id;
	}
	
	public long getTimeToDownload() {
		return timeToDownload;
	}
	
	public void setTimeToDownload(long time) {
		timeToDownload = time;
	}
	
	public long getDuration() {
		return duration;
	}
	
	public int getOffset() {
		return offset;
	}
	
	public byte[] getContent() {
		return content;
	}
	
	public void addContent(byte[] content) {
		this.content = content;
	}
	
	public void setDimension(int dimension) {
		this.dimension = dimension;
	}
	
	public int getDimension() {
		return dimension;
	}
}
