package us.kbase.common.util;

public class TooManyKeysException extends Exception {
	private static final long serialVersionUID = 1L;
	private long maxMem;
	private String path;
	
	public TooManyKeysException(long maxMem, String path) {
		super("Memory necessary for sorting map keys exceeds the limit " + maxMem + " bytes at " + path);
		this.maxMem = maxMem;
		this.path = path;
	}
	
	public long getMaxMem() {
		return maxMem;
	}
	
	public String getPath() {
		return path;
	}
}
