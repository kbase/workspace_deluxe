package us.kbase.workspace.test.controllers;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import us.kbase.common.test.TestException;

public class ControllerCommon {

	/** See https://gist.github.com/vorburger/3429822
	 * Returns a free port number on localhost.
	 *
	 * Heavily inspired from org.eclipse.jdt.launching.SocketUtil (to avoid a
	 * dependency to JDT just because of this).
	 * Slightly improved with close() missing in JDT. And throws exception
	 * instead of returning -1.
	 *
	 * @return a free port number on localhost
	 * @throws IllegalStateException if unable to find a free port
	 */
	public static int findFreePort() {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket(0);
			socket.setReuseAddress(true);
			int port = socket.getLocalPort();
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore IOException on close()
			}
			return port;
		} catch (IOException e) {
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
		throw new IllegalStateException(
				"Could not find a free TCP/IP port");
	}
	
	public static void checkExe(String exe, String exeType) {
		if (exe == null || exe.isEmpty()) {
			throw new TestException("Executable path for " + exeType +
					" cannot be null or the empty string ");
		}
		File e = new File(exe);
		if (!e.exists()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable does not exist:" + exe);
		}
		if (!e.isFile()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable is not a file:" + exe);
		}
		if (!e.canExecute()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable is not executable:" + exe);
		}
	}
	
	public static Path makeTempDirs(Path rootTempDir, String prefix,
			List<String> subdirs)
			throws IOException {
		Files.createDirectories(rootTempDir.toAbsolutePath());
		Set<PosixFilePermission> perms =
				PosixFilePermissions.fromString("rwx------");
		FileAttribute<Set<PosixFilePermission>> attr =
				PosixFilePermissions.asFileAttribute(perms);
		Path tempDir = Files.createTempDirectory(rootTempDir, prefix, attr);
		for(String p: subdirs) {
			Files.createDirectories(tempDir.resolve(p));
		}
		return tempDir;
	}
}
