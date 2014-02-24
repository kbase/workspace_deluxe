package us.kbase.typedobj.core;

import java.io.File;

public class TempFilesManager {
	private File tempDir;
	private long lastUsedSuffix = 0;
	
	public TempFilesManager(File tempDir) {
		this.tempDir = tempDir;
		if (!tempDir.exists())
			tempDir.mkdir();
	}
	
	public synchronized File generateTempFile(String prefix, String extention) {
		long suffix = System.currentTimeMillis();
		if (suffix <= lastUsedSuffix)
			suffix = lastUsedSuffix + 1;
		File tempFile = null;
		while (true) {
			tempFile = new File(tempDir, prefix + suffix + "." + extention);
			if (!tempFile.exists())
				break;
			suffix++;
		}
		lastUsedSuffix = suffix;
		return tempFile;
	}
	
	public static TempFilesManager forTests() {
		return new TempFilesManager(new File("temp_files"));
	}
}
