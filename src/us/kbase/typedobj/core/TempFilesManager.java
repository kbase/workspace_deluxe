package us.kbase.typedobj.core;

import java.io.File;
import java.io.IOException;

/**
 * Manager of temporary files. Should delete all temporary files on start (TODO?).
 * @author rsutormin
 */
public class TempFilesManager {
	private File tempDir;
	
	public TempFilesManager(File tempDir) {
		this.tempDir = tempDir;
		if (tempDir.exists()) {
			if (!tempDir.isDirectory())
				throw new IllegalStateException("It should be directory: " + tempDir);
		} else {
			tempDir.mkdir();
		}
	}
	
	public File getTempDir() {
		return tempDir;
	}
	
	public synchronized File generateTempFile(String prefix, String extention) {
		try {
			return File.createTempFile("ws." + prefix, "." + extention, tempDir);
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
	
	public static TempFilesManager forTests() {
		return new TempFilesManager(new File("temp_files"));
	}
}
