package us.kbase.workspace.test.controllers.handle;

import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	
	private final static String DB = "handle_db";
	private final static String COLLECTION = "handle";
	
	private final static String HANDLE_SERVICE_NAME = "handle_service";

	public HandleServiceController(
			final MongoController mongo,
			final String shockHost,
			final AuthToken shockAdminToken,
			final String perl5lib,
			final Path rootTempDir,
			URL authURL, 
			final String handleAdminRole)
			throws Exception {
				
		tempDir = makeTempDirs(rootTempDir, "HandleServiceController-",
				new LinkedList<String>());
		
		handleServicePort = findFreePort();
		
		File hsIniFile = createHandleServiceDeployCfg(mongo, shockHost, authURL,
				shockAdminToken, handleAdminRole);
		String lib_dir = "lib";
		downloadSourceFiles(tempDir, lib_dir);

		String lib_dir_path = tempDir.resolve(lib_dir).toAbsolutePath().toString();
		ProcessBuilder handlepb = new ProcessBuilder("uwsgi", "--http", 
				":" + handleServicePort, "--wsgi-file",
				"AbstractHandle/AbstractHandleServer.py", "--pythonpath", lib_dir_path)
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("handle_service.log").toFile());
		Map<String, String> env = handlepb.environment();
		env.put("PERL5LIB", perl5lib);
		env.put("KB_DEPLOYMENT_CONFIG", hsIniFile.getAbsolutePath().toString());
		env.put("KB_SERVICE_NAME", HANDLE_SERVICE_NAME);
		env.put("KB_AUTH_TOKEN", shockAdminToken.getToken());
		env.put("PYTHONPATH", lib_dir_path);
		handlepb.directory(new File(lib_dir_path));
		handleService = handlepb.start();
		
		Thread.sleep(1000); //let the service start up
	}
	
	private void downloadSourceFiles(Path parentDir, String subDir) throws IOException {
		// download source files from github repo
		
		Path lib_root = parentDir.resolve(subDir);
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
		
		String biokbase_repo_prefix = "https://raw.githubusercontent.com/kbase/kb_sdk/master/lib/biokbase/";
		String [] biokbase_files = {"__init__.py", "log.py"};
		for (String file_name : biokbase_files) {
			FileUtils.copyURLToFile(new URL(biokbase_repo_prefix + file_name),
					biokbase_dir.resolve(file_name).toFile());
		}
		
		Path log_file = biokbase_dir.resolve("log.py");
		Stream <String> lines = Files.lines(log_file);
		List <String> replaced = lines.map(line -> line.replaceAll("import urllib.request as _urllib", 
				"try:\n" + 
				"    import urllib.request as _urllib\n" + 
				"except ImportError:\n" + 
				"    import urllib as _urllib")).collect(Collectors.toList());
		Files.write(log_file, replaced);
		lines.close();
		
		log_file = handle_dir.resolve("AbstractHandleServer.py");
		lines = Files.lines(log_file);
		replaced = lines.map(line -> line.replaceAll("str]", "unicode]")).collect(Collectors.toList());
		Files.write(log_file, replaced);
		lines.close();
		
		log_file = handle_utils_dir.resolve("MongoUtil.py");
		lines = Files.lines(log_file);
		replaced = lines.map(line -> line.replaceAll("#print", 
				"print")).collect(Collectors.toList());
		Files.write(log_file, replaced);
		lines.close();
	}
	
	private File createHandleServiceDeployCfg(
			final MongoController mongo,
			final String shockHost,
			final URL authURL,
			final AuthToken shockAdminToken,
			final String handleAdminRole) throws IOException {
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
		hs.add("default-shock-server", shockHost);
		hs.add("admin-token", shockAdminToken.getToken().toString());
		
		hs.add("mongo-host", "127.0.0.1");
		hs.add("mongo-port", "" + mongo.getServerPort());
		hs.add("mongo-database", DB);
		hs.add("mongo-collection", COLLECTION);
		hs.add("admin-roles", handleAdminRole);
		
		ini.store(iniFile);
		return iniFile;
	}
	

	public int getHandleServerPort() {
		return handleServicePort;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy(boolean deleteTempFiles) throws IOException {
		if (handleService != null) {
			handleService.destroy();
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
				"/kb/deployment/lib",
				Paths.get("workspacetesttemp"),
				new URL("http://foo.com"),
				"KBASE_ADMIN");
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
