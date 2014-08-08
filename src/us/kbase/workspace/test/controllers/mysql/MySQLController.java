package us.kbase.workspace.test.controllers.mysql;

import static us.kbase.workspace.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.workspace.test.controllers.ControllerCommon.checkExe;
import static us.kbase.workspace.test.controllers.ControllerCommon.makeTempDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import us.kbase.common.test.TestException;


/** Q&D Utility to run a MySQL server for the purposes of testing from
 * Java.
 * @author gaprice@lbl.gov
 *
 */
public class MySQLController {
	
	private final static String DATA_DIR = "data";
	
	private final static List<String> tempDirectories =
			new LinkedList<String>();
	static {
		tempDirectories.add(DATA_DIR);
	}
	
	private final Path tempDir;
	
	private final Process mysql;
	private final int port;

	private Connection client;

	/**
	 * @param mysqlExe
	 * @param mysqlInstallExe
	 * @param rootTempDir where to place temp files. Cannot have any system
	 * specific info (~, $HOME, etc).
	 * @param deleteTempDirOnExit
	 * @throws Exception
	 */
	public MySQLController(
			final String mysqlExe,
			final String mysqlInstallExe,
			final Path rootTempDir)
					throws Exception {
		checkExe(mysqlExe, "mysql server");
		checkExe(mysqlInstallExe, "mysql_install_db executable");
		tempDir = makeTempDirs(rootTempDir, "MySQLController-",
				tempDirectories).toAbsolutePath();

		final int exit = new ProcessBuilder(mysqlInstallExe, "--datadir=" +
				tempDir.resolve(DATA_DIR).toString(), "--no-defaults")
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("mysql_install.log").toFile())
				.start().waitFor();
		if (exit != 0) {
			throw new TestException(
					"Failed setting up mysql database, exit code: " + exit +
					". Check the log in " + tempDir);
		}
		
		Set<PosixFilePermission> perms =
				PosixFilePermissions.fromString("rwx------");
		FileAttribute<Set<PosixFilePermission>> attr =
				PosixFilePermissions.asFileAttribute(perms);
		Path socket = Files.createTempDirectory("mysqlsocket", attr);
		
		port = findFreePort();
		ProcessBuilder servpb = new ProcessBuilder(mysqlExe,
				"--port=" + port,
				"--datadir=" + tempDir.resolve(DATA_DIR).toString(),
				"--pid-file=" + tempDir.resolve("pid").toString(),
				"--socket=" + socket.resolve("MySQLController.sock"))
				//don't do this: http://bugs.mysql.com/bug.php?id=42512
//				"--socket=" + tempDir.resolve("socket").toString())
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("mysql.log").toFile());
		
		mysql = servpb.start();
		Thread.sleep(3000); //wait for server to start up
		
		
		Class.forName("com.mysql.jdbc.Driver");
		client = DriverManager.getConnection("jdbc:mysql://localhost:" + port,
				"root", null);
	}
	
	public Connection getClient() {
		return client;
	}

	public int getServerPort() {
		return port;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy(boolean deleteTempFiles) throws IOException {
		if (mysql != null) {
			mysql.destroy();
		}
		if (tempDir != null && deleteTempFiles) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}

	public static void main(String[] args) throws Exception {
		MySQLController ac = new MySQLController(
				"/usr/sbin/mysqld",
				"/usr/bin/mysql_install_db",
				Paths.get("workspacetesttemp"));
		System.out.println(ac.getServerPort());
		System.out.println(ac.getTempDir());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		ac.destroy(false);
	}
	
}
