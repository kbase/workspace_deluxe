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
import us.kbase.common.test.controllers.shock.ShockController;


/** Q&D Utility to run Handle Service/Manager servers for the purposes of
 * testing from Java.
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
		if (handleServiceDir == null) {
			downloadSourceFiles(lib_root);
		}
		else {
			FileUtils.copyDirectory(new File(handleServiceDir), lib_root.toFile());
		}

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

	private void downloadSourceFiles(Path lib_root) throws IOException {
		// download source files from github repo

		Files.createDirectories(lib_root);

		Path handle_dir = lib_root.resolve("AbstractHandle");
		Files.createDirectories(handle_dir);

		String handle_repo_prefix = "https://raw.githubusercontent.com/kbase/handle_service2/develop/lib/AbstractHandle/";
		String [] handle_impl_files = {"__init__.py", "AbstractHandleImpl.py",
				"AbstractHandleServer.py", "authclient.py", "baseclient.py"};
		for (String file_name : handle_impl_files) {
			FileUtils.copyURLToFile(new URL(handle_repo_prefix + file_name),
					handle_dir.resolve(file_name).toFile());
		}

		Path handle_utils_dir = handle_dir.resolve("Utils");
		Files.createDirectories(handle_utils_dir);
		String [] handle_util_files = {"__init__.py", "Handler.py", "MongoUtil.py",
				"ShockUtil.py", "TokenCache.py"};
		for (String file_name : handle_util_files) {
			FileUtils.copyURLToFile(new URL(handle_repo_prefix + "Utils/" + file_name),
					handle_utils_dir.resolve(file_name).toFile());
		}

		Path biokbase_dir = lib_root.resolve("biokbase");
		Files.createDirectories(biokbase_dir);

		String biokbase_repo_prefix = "https://raw.githubusercontent.com/kbase/sdkbase2/python/";
		String [] biokbase_files = {"log.py"};
		for (String file_name : biokbase_files) {
			FileUtils.copyURLToFile(new URL(biokbase_repo_prefix + file_name),
					biokbase_dir.resolve(file_name).toFile());
		}

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

	public static void main(String[] args) throws Exception {
		MongoController monc = new MongoController(
				"/kb/runtime/bin/mongod",
				Paths.get("workspacetesttemp"), false);
		ShockController sc = new ShockController(
				"/kb/deployment/bin/shock-server",
				"0.9.6",
				Paths.get("workspacetesttemp"),
				System.getProperty("test.user1"),
				"localhost:" + monc.getServerPort(),
				"shockdb", "foo", "foo", new URL("http://foo.com"));
		HandleServiceController hsc = new HandleServiceController(
				monc,
				"http://localhost:" + sc.getServerPort(),
				null, //this will break the hm, need a token
				Paths.get("workspacetesttemp"),
				new URL("http://foo.com"),
				"KBASE_ADMIN",
				"/kb/deployment/lib",
				"handle_controller_test_handle_db");
		System.out.println("handlesrv: " + hsc.getHandleServerPort());
		System.out.println(hsc.getTempDir());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		hsc.destroy(false);
		sc.destroy(false);
		monc.destroy(false);
		reader.close();
	}
}
