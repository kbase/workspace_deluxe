package us.kbase.workspace.test.controllers.handle;

import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;
import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import us.kbase.auth.AuthToken;
import us.kbase.common.test.controllers.mongo.MongoController;


/** Q&D Utility to run the Handle Service for the purposes of testing from Java.
 * @author gaprice@lbl.gov
 *
 */
public class HandleServiceController {

	private final Process handleService;
	private final int handleServicePort;
	private final Path tempDir;
	private final static String HANDLE_SERVICE_NAME = "handle_service";
	private final Path logfile;

	public HandleServiceController(
			final MongoController mongo,
			final String shockHost,
			final AuthToken shockAdminToken,
			final Path rootTempDir,
			final URL authURL,
			final String handleAdminRole,
			final String handleServiceDir,
			final String mongoDB)
			throws Exception {

		tempDir = makeTempDirs(rootTempDir, "HandleServiceController-",
				new LinkedList<String>());

		handleServicePort = findFreePort();
		File hsIniFile = createHandleServiceDeployCfg(mongo, shockHost, authURL,
				shockAdminToken, handleAdminRole, mongoDB);

		String lib_dir = "lib";
		Path lib_root = tempDir.resolve(lib_dir);
		FileUtils.copyDirectory(new File(handleServiceDir), lib_root.toFile());

		final String lib_dir_path = lib_root.toAbsolutePath().toString();
		logfile = tempDir.resolve("handle_service.log");
		ProcessBuilder handlepb = new ProcessBuilder("uwsgi", "--http",
				":" + handleServicePort, "--wsgi-file",
				"AbstractHandle/AbstractHandleServer.py", "--pythonpath", lib_dir_path)
				.redirectErrorStream(true)
				.redirectOutput(logfile.toFile());
		Map<String, String> env = handlepb.environment();
		env.put("KB_DEPLOYMENT_CONFIG", hsIniFile.getAbsolutePath().toString());
		env.put("KB_SERVICE_NAME", HANDLE_SERVICE_NAME);
		env.put("PYTHONPATH", lib_dir_path);
		handlepb.directory(new File(lib_dir_path));
		handleService = handlepb.start();

		Thread.sleep(1000); //let the service start up
	}

	private File createHandleServiceDeployCfg(
			final MongoController mongo,
			final String shockHost,
			final URL authURL,
			final AuthToken shockAdminToken,
			final String handleAdminRole,
			final String mongoDB) throws IOException {
		final File iniFile = tempDir.resolve("handleService.cfg").toFile();
		if (iniFile.exists()) {
			iniFile.delete();
		}

		final Ini ini = new Ini();
		final Section hs = ini.add(HANDLE_SERVICE_NAME);
		hs.add("self-url", "http://localhost:" + handleServicePort);
		hs.add("service-port", "" + handleServicePort);
		hs.add("service-host", "localhost");
		URL authServiceURL = new URL(authURL.toString() + "/api/legacy/KBase/Sessions/Login");
		hs.add("auth-service-url", authServiceURL.toString());
		hs.add("auth-url", authURL.toString());
		hs.add("shock-url", shockHost);
		hs.add("admin-token", shockAdminToken.getToken().toString());
		hs.add("admin-roles", handleAdminRole);

		hs.add("mongo-host", "127.0.0.1");
		hs.add("mongo-port", "" + mongo.getServerPort());
		hs.add("mongo-database", mongoDB);
		hs.add("mongo-user", "");
		hs.add("mongo-password", "");

		hs.add("start-local-mongo", 0);
		hs.add("namespace", "KBH");

		ini.store(iniFile);
		return iniFile;
	}


	public int getHandleServerPort() {
		return handleServicePort;
	}

	public Path getTempDir() {
		return tempDir;
	}

	public void destroy(final boolean deleteTempFiles) throws IOException {
		destroy(deleteTempFiles, false);
	}
	
	public void destroy(final boolean deleteTempFiles, final boolean dumpLogToStdOut)
			throws IOException {
		if (handleService != null) {
			handleService.destroy();
		}
		if (dumpLogToStdOut) {
			try (final BufferedReader is = Files.newBufferedReader(logfile)) {
				is.lines().forEach(l -> System.out.println(l));
			}
		}
		if (tempDir != null && deleteTempFiles) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}
}
