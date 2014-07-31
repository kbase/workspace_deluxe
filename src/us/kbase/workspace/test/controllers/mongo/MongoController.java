package us.kbase.workspace.test.controllers.mongo;

import static us.kbase.workspace.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.workspace.test.controllers.ControllerCommon.checkExe;
import static us.kbase.workspace.test.controllers.ControllerCommon.makeTempDirs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;


/** Q&D Utility to run a Shock server for the purposes of testing from
 * Java.
 * @author gaprice@lbl.gov
 *
 */
public class MongoController {
	
	private final static String DATA_DIR = "data";
	
	private final static List<String> tempDirectories =
			new LinkedList<String>();
	static {
		tempDirectories.add(DATA_DIR);
	}
	
	private final Path tempDir;
	
	private final Process mongo;
	private final int port;
	private final boolean deleteTempDirOnExit;

	public MongoController(
			final String mongoExe,
			final boolean deleteTempDirOnExit)
					throws Exception {
		this.deleteTempDirOnExit = deleteTempDirOnExit;
		checkExe(mongoExe, "mongod server");
		tempDir = makeTempDirs("MongoController-", tempDirectories);
		port = findFreePort();
		
		
		ProcessBuilder servpb = new ProcessBuilder(mongoExe, "--port",
				"" + port, "--dbpath", tempDir.resolve(DATA_DIR).toString(),
				"--nojournal")
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("mongo.log").toFile());
		
		mongo = servpb.start();
	}

	public int getServerPort() {
		return port;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy() throws IOException {
		if (mongo != null) {
			mongo.destroy();
		}
		if (tempDir != null && deleteTempDirOnExit) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}

	public static void main(String[] args) throws Exception {
		MongoController ac = new MongoController(
				"/kb/runtime/bin/mongod",
				false);
		System.out.println(ac.getServerPort());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		ac.destroy();
	}
	
}
