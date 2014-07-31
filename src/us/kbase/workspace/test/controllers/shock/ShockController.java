package us.kbase.workspace.test.controllers.shock;

import static us.kbase.workspace.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.workspace.test.controllers.ControllerCommon.checkExe;
import static us.kbase.workspace.test.controllers.ControllerCommon.makeTempDirs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

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
			"us/kbase/workspace/test/controllers/shock/conf/" +
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
			final Path rootTempDir,
			final String adminUser,
			final String mongohost,
			final String shockMongoDBname,
			final String mongouser,
			final String mongopwd,
			final boolean deleteTempDirOnExit)
					throws Exception {
		tempDir = makeTempDirs(rootTempDir, "ShockController-", tempDirectories);
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
	
	public static void main(String[] args) throws Exception {
		ShockController ac = new ShockController(
				"/kb/deployment/bin/shock-server",
				Paths.get("workspacetemp"),
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
