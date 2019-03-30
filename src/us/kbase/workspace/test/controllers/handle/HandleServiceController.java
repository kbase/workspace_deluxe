package us.kbase.workspace.test.controllers.handle;

import static us.kbase.common.test.controllers.ControllerCommon.checkExe;
import static us.kbase.common.test.controllers.ControllerCommon.checkFile;
import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
	
	private final static String DB = "handle_db";
	private final static String COLLECTION = "handle";
	
	private final static String HANDLE_SERVICE_NAME = "handle_service";

	public HandleServiceController(
			final String plackupExe,
			final String abstractHandlePSGIpath,
			final String handleManagerPSGIpath,
			final String handleManagerAllowedUser,
			final MongoController mongo,
			final String shockHost,
			final AuthToken shockAdminToken,
			final String perl5lib,
			final Path rootTempDir,
			URL authServiceURL)
			throws Exception {
		
		authServiceURL = new URL(authServiceURL.toString() + "/Sessions/Login");
		
		// checkExe(plackupExe, "plackup");
		// checkFile(abstractHandlePSGIpath, "Abstract Handle Service PSGI");
		// checkFile(handleManagerPSGIpath, "Handle Manager PSGI");
		tempDir = makeTempDirs(rootTempDir, "HandleServiceController-",
				new LinkedList<String>());
		
//		setUpHandleServiceMySQLTables(mongo.getClient());
		
		handleServicePort = findFreePort();
		
		File hsIniFile = createHandleServiceDeployCfg(mongo, shockHost, authServiceURL);

		//uwsgi --http :9090 --wsgi-file stractHandle/AbstractHandleServer.py
		String path = "AbstractHandle/AbstractHandleServer.py";
		ProcessBuilder handlepb = new ProcessBuilder("uwsgi", "--http",
				":" + handleServicePort, "--wsgi-file",
				path, "--pythonpath", "/home/tian/Dev/handle_service2/lib")
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("handle_service.log").toFile());
		Map<String, String> env = handlepb.environment();
		env.put("PERL5LIB", perl5lib);
		env.put("KB_DEPLOYMENT_CONFIG", hsIniFile.getAbsolutePath().toString());
		env.put("KB_SERVICE_NAME", HANDLE_SERVICE_NAME);
		env.put("KB_AUTH_TOKEN", shockAdminToken.toString());
		env.put("PYTHONPATH", "/home/tian/Dev/handle_service2/lib");
		handlepb.directory(new File("/home/tian/Dev/handle_service2/lib"));
		handleService = handlepb.start();
		
		Thread.sleep(1000); //let the service start up
	}
	
	private File createHandleServiceDeployCfg(
			final MongoController mongo,
			final String shockHost,
			final URL authServiceURL) throws IOException {
		final File iniFile = tempDir.resolve("handleService.cfg").toFile();
		if (iniFile.exists()) {
			iniFile.delete();
		}
		
		final Ini ini = new Ini();
		final Section hs = ini.add(HANDLE_SERVICE_NAME);
		hs.add("self-url", "http://localhost:" + handleServicePort);
		hs.add("service-port", "" + handleServicePort);
		hs.add("service-host", "localhost");
		hs.add("auth-service-url", authServiceURL.toString());
		hs.add("default-shock-server", shockHost);
		
		hs.add("mongo-host", "127.0.0.1");
		hs.add("mongo-port", "" + mongo.getServerPort());
		hs.add("mongo-database", DB);
		hs.add("mongo-collection", COLLECTION);
		hs.add("admin-roles", "HANDLE_ADMIN, KBASE_ADMIN");
//		hs.add("mysql-pass", PWD);
//		hs.add("data-source", "dbi:mysql:" + DB);
		
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
	
//	private static void setUpHandleServiceMySQLTables(Connection connection)
//			throws Exception {
//		Statement s = connection.createStatement();
//		s.execute(	"CREATE DATABASE " + DB + ";");
//		s.execute(	"USE " + DB + ";");
//		s.execute(	"CREATE TABLE " + TABLE + " (" +
//						"hid           int NOT NULL AUTO_INCREMENT," +
//						"id            varchar(256) NOT NULL DEFAULT ''," +
//						"file_name     varchar(256)," +
//						"type          varchar(256)," +
//						"url           varchar(256)," +
//						"remote_md5    varchar(256)," +
//						"remote_sha1   varchar(256)," +
//						"created_by    varchar(256)," +
//						"creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
//						"PRIMARY KEY (hid)" +
//					");");
//		s.execute(	"GRANT SELECT,INSERT,UPDATE,DELETE " +
//						"ON " + DB + ".* " +
//						"TO '" + USER + "'@'localhost' " +
//						"IDENTIFIED BY '" + PWD + "';");
//		s.execute(	"GRANT SELECT,INSERT,UPDATE,DELETE " +
//						"ON " + DB + ".* " +
//						"TO '" + USER + "'@'127.0.0.1' " +
//						"IDENTIFIED BY '" + PWD + "';");
//		s.execute(	"ALTER TABLE Handle ADD CONSTRAINT unique_id UNIQUE (id);");
//	}

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
				"/kb/runtime/bin/plackup",
				"/kb/deployment/lib/AbstractHandle.psgi",
				"/kb/deployment/lib/HandleMngr.psgi",
				System.getProperty("test.user2"),
				monc,
				"http://localhost:" + sc.getServerPort(),
				null, //this will break the hm, need a token
				"/kb/deployment/lib",
				Paths.get("workspacetesttemp"),
				new URL("http://foo.com"));
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
