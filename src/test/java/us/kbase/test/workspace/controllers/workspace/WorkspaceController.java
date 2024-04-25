package us.kbase.test.workspace.controllers.workspace;

import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import us.kbase.workspace.WorkspaceServer;


/** Q&D Utility to run a Workspace server for the purposes of testing from
 * Java. Expected to be packaged in a test jar with all dependencies.
 * 
 * Initializes a GridFS backend and does not support handles, byte streams, or listeners.
 * @author gaprice@lbl.gov
 *
 */
public class WorkspaceController {

	private final static String DATA_DIR = "temp_data";
	private static final String WS_CLASS = WorkspaceServer.class.getName();
	private static final String JAR_PATH = WorkspaceServer.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	private final static List<String> tempDirectories = new LinkedList<String>();
	static {
		tempDirectories.add(DATA_DIR);
	}

	private final Path tempDir;

	private final Process workspace;
	private final int port;

	public WorkspaceController(
			final String mongoHost,
			final String mongoDatabase,
			final String mongoTypeDatabase,
			final String adminUser,
			final URL authServiceRootURL, // expected to include /testmode endpoint
			final Path rootTempDir)
			throws Exception {
		tempDir = makeTempDirs(rootTempDir, "WorkspaceController-", tempDirectories);
		port = findFreePort();
		System.out.println("Using classpath " + JAR_PATH);
		
		final Path deployCfg = createDeployCfg(
				mongoHost, mongoDatabase, mongoTypeDatabase, authServiceRootURL, adminUser);
		final List<String> command = new LinkedList<String>();
		command.addAll(Arrays.asList("java", "-classpath", JAR_PATH, WS_CLASS, "" + port));
		final ProcessBuilder servpb = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("workspace.log").toFile());

		final Map<String, String> env = servpb.environment();
		env.put("KB_DEPLOYMENT_CONFIG", deployCfg.toString());

		workspace = servpb.start();
		// TODO TEST add periodic check w/ exponential backoff
		Thread.sleep(5000);
	}

	private Path createDeployCfg(
			final String mongoHost,
			final String mongoDatabase,
			final String mongoTypeDatabase,
			final URL authRootURL,
			final String adminUser)
					throws IOException {
		final File iniFile = new File(tempDir.resolve("test.cfg").toString());
		System.out.println("Created temporary workspace config file: " +
				iniFile.getAbsolutePath());
		final Ini ini = new Ini();
		Section ws = ini.add("Workspace");
		ws.add("mongodb-host", mongoHost);
		ws.add("mongodb-database", mongoDatabase);
		ws.add("mongodb-type-database", mongoTypeDatabase);
		ws.add("backend-type", "GridFS");
		ws.add("auth2-service-url", authRootURL);
		ws.add("ws-admin", adminUser);
		ws.add("temp-dir", tempDir.resolve("temp_data"));
		ws.add("ignore-handle-service", "true");
		ini.store(iniFile);
		return iniFile.toPath();
	}

	public int getServerPort() {
		return port;
	}

	public Path getTempDir() {
		return tempDir;
	}

	public void destroy(boolean deleteTempFiles) throws IOException {
		if (workspace != null) {
			workspace.destroy();
		}
		if (tempDir != null && deleteTempFiles) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}
}

