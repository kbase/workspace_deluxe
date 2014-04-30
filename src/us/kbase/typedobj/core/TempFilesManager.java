package us.kbase.typedobj.core;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager of temporary files for the workspace. All temporary files are
 * prefixed with "ws.".
 * @author rsutormin
 */
public class TempFilesManager {
	
	private static final String WS_PREFIX = "ws.";
	private File tempDir;
	private boolean failOnFileReq = false;
	
	private static final FileFilter ff = new FileFilter() {
		
		@Override
		public boolean accept(final File pathname) {
			if (pathname.getName().startsWith(WS_PREFIX)) {
				return true;
			}
			return false;
		}
	};
	
	/** Create a new temporary file manager.
	 * @param tempDir the directory in which to store temporary files.
	 */
	public TempFilesManager(final File tempDir) {
		if (tempDir == null) {
			throw new IllegalArgumentException("tempDir cannot be null");
		}
		this.tempDir = tempDir;
		if (tempDir.exists()) {
			if (!tempDir.isDirectory())
				throw new IllegalArgumentException(
						"Temporary file storage location must be a directory: "
						+ tempDir);
		} else {
			tempDir.mkdir();
		}
	}
	
	/** Get the temporary file directory.
	 * @return
	 */
	public File getTempDir() {
		return tempDir;
	}
	
	/** Instruct the TFM whether to fail when a temporary file is requested.
	 * Primarily used for testing.
	 * @param fail if true, when generateTempFile is called an
	 * IllegalStateException will be thrown.
	 */
	public void setFailOnFileRequest(boolean fail) {
		failOnFileReq = fail;
	}
	
	/** Get the fail on request state.
	 * @return if true, when generateTempFile is called an
	 * IllegalStateException will be thrown.
	 */
	public boolean getFailOnFileRequest() {
		return failOnFileReq;
	}
	
	//TODO does this need to be sync'd?
	/** Create a temporary file.
	 * @param prefix the prefix of the temporary file.
	 * @param extension the extension of the temporary file.
	 * @return a temporary file.
	 */
	public synchronized File generateTempFile(String prefix, String extension) {
		if (failOnFileReq) {
			throw new IllegalStateException(
					"The temp files manager was instructed to fail on request of a temp file. " +
					String.format("Prefix: %s, ext: %s", prefix, extension));
		}
		try {
			return File.createTempFile(WS_PREFIX + prefix, "." + extension, tempDir);
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
	
	/** Delete all the temporary files.
	 */
	public synchronized void cleanup() {
		for (File f : tempDir.listFiles(ff)) {
			f.delete();
		}
	}
	
	/** Return a TFM using the ./temp_files directory.
	 * @return
	 */
	public static TempFilesManager forTests() {
		return new TempFilesManager(new File("temp_files"));
	}

	/** Check if any temporary files exist.
	 * @return true if any temporary files exist.
	 */
	public boolean isEmpty() {
		if(tempDir.listFiles(ff).length > 0) {
			return false;
		}
		return true;
	}
	
	/** Get a list of all the temporary files.
	 * @return a list of all the temporary files.
	 */
	public List<String> getTempFileList() {
		List<String> ret = new ArrayList<String>();
		for (File f : tempDir.listFiles(ff))
			ret.add(f.getName());
		return ret;
	}
}
