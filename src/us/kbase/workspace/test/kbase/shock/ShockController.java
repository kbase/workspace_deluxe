package us.kbase.workspace.test.kbase.shock;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;


/** Q&D Utility to run a Shock server for the purposes of testing from
 * Java.
 * @author gaprice@lbl.gov
 *
 */
public class ShockController {
	
	//TODO share common code with AweController
	
	private final static String SHOCK_CONFIG_FN = "shock.cfg";
	private final static String SHOCK_CONFIG =
			"us/kbase/workspace/test/kbase/shock/conf/" +
					SHOCK_CONFIG_FN;
	
	private final static List<String> tempDirectories =
			new LinkedList<String>();
	static {
		tempDirectories.add("shock/site");
		tempDirectories.add("shock/data");
		tempDirectories.add("shock/logs");
	}
	
	private final Path tempDir;
	
	private final Process shock;
	private final int port;
	private final boolean deleteTempDirOnExit;

	public ShockController(
			final String shockExe,
			final String adminUser,
			final String mongohost,
			final String shockMongoDBname,
			final String mongouser,
			final String mongopwd,
			final boolean deleteTempDirOnExit)
					throws Exception {
		tempDir = makeTempDirs();
		port = findFreePort();
		this.deleteTempDirOnExit = deleteTempDirOnExit;
		
		checkExe(shockExe, "shock server");
		
		Velocity.init();
		VelocityContext context = new VelocityContext();
		context.put("port", port);
		context.put("tempdir", tempDir.toString());
		context.put("mongohost", mongohost);
		context.put("mongodbname", shockMongoDBname);
		context.put("mongouser", mongouser == null ? "" : mongouser);
		context.put("mongopwd", mongopwd == null ? "" : mongopwd);
		context.put("shockadmin", adminUser);
		
		File shockcfg = tempDir.resolve(SHOCK_CONFIG_FN).toFile();
		
		generateConfig(SHOCK_CONFIG, context, shockcfg);

		ProcessBuilder servpb = new ProcessBuilder(shockExe, "--conf",
				shockcfg.toString())
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("shock_server.log").toFile());
		
		shock = servpb.start();
	}

	public int getServerPort() {
		return port;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy() throws IOException {
		if (shock != null) {
			shock.destroy();
		}
		if (tempDir != null && deleteTempDirOnExit) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}
	

	private void checkExe(String shockExe, String exeType) {
		File e = new File(shockExe);
		if (!e.exists()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable does not exist:" + shockExe);
		}
		if (!e.isFile()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable is not a file:" + shockExe);
		}
		if (!e.canExecute()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable is not executable:" + shockExe);
		}
		
	}

	private Path makeTempDirs() throws IOException {
		Set<PosixFilePermission> perms =
				PosixFilePermissions.fromString("rwx------");
		FileAttribute<Set<PosixFilePermission>> attr =
				PosixFilePermissions.asFileAttribute(perms);
		Path tempDir = Files.createTempDirectory("ShockController-", attr);
		for(String p: tempDirectories) {
			Files.createDirectories(tempDir.resolve(p));
		}
		return tempDir;
	}

	private void generateConfig(final String configFile,
			final VelocityContext context, File file)
			throws IOException {
		String template = IOUtils.toString(new BufferedReader(
				new InputStreamReader(
						getClass().getClassLoader()
						.getResourceAsStream(configFile))));
		
		StringWriter sw = new StringWriter();
		Velocity.evaluate(context, sw, "shockconfig", template);
		PrintWriter pw = new PrintWriter(file);
		pw.write(sw.toString());
		pw.close();
	}
	
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
	private static int findFreePort() {
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
	
	public static void main(String[] args) throws Exception {
		ShockController ac = new ShockController(
				"/kb/deployment/bin/shock-server",
				"kbasetest2",
				"localhost",
				"delete_shock_db",
				"foo", "foo", false);
		System.out.println(ac.getServerPort());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		ac.destroy();
	}
	
}
