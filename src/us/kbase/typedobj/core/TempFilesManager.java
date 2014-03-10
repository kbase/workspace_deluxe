package us.kbase.typedobj.core;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * Manager of temporary files. Should delete all temporary files on start (TODO?).
 * @author rsutormin
 */
public class TempFilesManager {
	
	private static final String WS_PREFIX = "ws.";
	private File tempDir;
	
	private static final FileFilter ff = new FileFilter() {
		
		@Override
		public boolean accept(final File pathname) {
			if (pathname.getName().startsWith(WS_PREFIX)) {
				return true;
			}
			return false;
		}
	};
	
	public TempFilesManager(final File tempDir) {
		this.tempDir = tempDir;
		if (tempDir.exists()) {
			if (!tempDir.isDirectory())
				throw new IllegalStateException(
						"It should be directory: " + tempDir);
		} else {
			tempDir.mkdir();
		}
	}
	
	public File getTempDir() {
		return tempDir;
	}
	
	public synchronized File generateTempFile(String prefix, String extension) {
		try {
			return File.createTempFile(WS_PREFIX + prefix, "." + extension, tempDir);
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
	
	public synchronized void cleanup() {
		for (File f : tempDir.listFiles(ff)) {
			f.delete();
		}
	}
	
	public static TempFilesManager forTests() {
		return new TempFilesManager(new File("temp_files"));
	}

	public boolean isEmpty() {
		if(tempDir.listFiles(ff).length > 0) {
			return false;
		}
		return true;
	}
}
