package us.kbase.common.util;

public class KeyDuplicationException extends Exception {
	private static final long serialVersionUID = 1L;

	private String path;
	private String key;
	
	public KeyDuplicationException(String path, String key) {
		super("Duplicated key '" + key + "' was found at " + path);
	}
	
	public String getPath() {
		return path;
	}
	
	public String getKey() {
		return key;
	}
}
