package us.kbase.workspace.test.controllers.handle;

import static us.kbase.workspace.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.workspace.test.controllers.ControllerCommon.checkExe;
import static us.kbase.workspace.test.controllers.ControllerCommon.checkFile;
import static us.kbase.workspace.test.controllers.ControllerCommon.makeTempDirs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import us.kbase.workspace.test.controllers.mongo.MongoController;
import us.kbase.workspace.test.controllers.mysql.MySQLController;
import us.kbase.workspace.test.controllers.shock.ShockController;


/** Q&D Utility to run Handle Service/Manager servers for the purposes of
 * testing from Java.
 * @author gaprice@lbl.gov
 *
 */
public class HandleServiceController {
	
	private final Process handleService;
	private final int handleServicePort;
	private final Path tempDir;
	private boolean deleteTempDirOnExit;
	
	private final static String DB = "hsi";
	private final static String TABLE = "Handle";
	private final static String USER = "hsitest";
	private final static String PWD = "hsi-pass-test";

	public HandleServiceController(
			final String plackupExe,
			final String abstractHandlePSGIpath,
			final String handleManagerPSGIpath, //TODO handle manager
			final MySQLController mysql,
			final String shockHost,
			final String perl5lib,
			final Path rootTempDir,
			final boolean deleteTempDirOnExit)
					throws Exception {
		
		this.deleteTempDirOnExit = deleteTempDirOnExit;
		checkExe(plackupExe, "plackup");
		checkFile(abstractHandlePSGIpath, "Abstract Handle Service PSGI");
//		checkFile(handleManagerPSGIpath, "Abstract Handle Service PSGI");
		tempDir = makeTempDirs(rootTempDir, "HandleServiceController-",
				new LinkedList<String>());
		
		setUpHandleServiceMySQLTables(mysql.getClient());
		
		File iniFile = tempDir.resolve("handleService.cfg").toFile();
		if (iniFile.exists()) {
			iniFile.delete();
		}
		
		handleServicePort = findFreePort();
		
		Ini ini = new Ini();
		Section ws = ini.add("handle_service");
		ws.add("self-url", "http://localhost:" + handleServicePort);
		ws.add("service-port", "" + handleServicePort);
		ws.add("service-host", "localhost");
		ws.add("default-shock-server", shockHost);
		
		ws.add("mysql-host", "127.0.0.1");
		ws.add("mysql-port", "" + mysql.getServerPort());
		ws.add("mysql-user", USER);
		ws.add("mysql-pass", PWD);
		ws.add("data-source", "dbi:mysql:" + DB);
		
		ini.store(iniFile);
		iniFile.deleteOnExit();
		
		/*
		crusherofheads@icrushdeheads:~$ export PERL5LIB=/kb/deployment/lib
		crusherofheads@icrushdeheads:~$ export KB_DEPLOYMENT_CONFIG=/kb/deployment/deployment.cfg
		crusherofheads@icrushdeheads:~$ plackup /kb/deployment/lib/AbstractHandle.psgi
		2014/07/27 23:26:42 15811 reading config from /kb/deployment/deployment.cfg
		2014/07/27 23:26:42 15811 using http://localhost:7044 as the default shock server
		{"attribute_indexes":[""],"contact":"shock-admin@kbase.us","documentation":"http://localhost:7044/wiki/","id":"Shock","resources":["node"],"type":"Shock","url":"http://localhost:7044/","version":"0.8.16"}DBI connect('hsi;host=localhost','hsi',...) failed: Access denied for user 'hsi'@'localhost' (using password: YES) at /kb/deployment/lib/Bio/KBase/AbstractHandle/AbstractHandleImpl.pm line 67
		Cannot read config file /etc/log/log.conf at /kb/deployment/lib/Bio/KBase/Log.pm line 282.
		HTTP::Server::PSGI: Accepting connections at http://0:5000/
		
		--port for port
		 */
		
		
		ProcessBuilder handlepb = new ProcessBuilder(plackupExe, "--port",
				"" + handleServicePort, abstractHandlePSGIpath)
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("handle_service.log").toFile());
		Map<String, String> env = handlepb.environment();
		env.put("PERL5LIB", perl5lib);
		env.put("KB_DEPLOYMENT_CONFIG", iniFile.toString());
		handleService = handlepb.start();
	}
	

	public int getHandleServerPort() {
		return handleServicePort;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy() throws IOException {
		if (handleService != null) {
			handleService.destroy();
		}
		if (tempDir != null && deleteTempDirOnExit) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}
	
	private static void setUpHandleServiceMySQLTables(Connection connection)
			throws Exception {
		Statement s = connection.createStatement();
		s.execute(	"CREATE DATABASE " + DB + ";");
		s.execute(	"USE " + DB + ";");
		s.execute(	"CREATE TABLE " + TABLE + " (" +
						"hid           int NOT NULL AUTO_INCREMENT," +
						"id            varchar(256) NOT NULL DEFAULT ''," +
						"file_name     varchar(256)," +
						"type          varchar(256)," +
						"url           varchar(256)," +
						"remote_md5    varchar(256)," +
						"remote_sha1   varchar(256)," +
						"created_by    varchar(256)," +
						"creation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
						"PRIMARY KEY (hid)" +
					");");
		s.execute(	"GRANT SELECT,INSERT,UPDATE,DELETE " +
						"ON " + DB + ".* " +
						"TO '" + USER + "'@'localhost' " +
						"IDENTIFIED BY '" + PWD + "';");
		s.execute(	"GRANT SELECT,INSERT,UPDATE,DELETE " +
						"ON " + DB + ".* " +
						"TO '" + USER + "'@'127.0.0.1' " +
						"IDENTIFIED BY '" + PWD + "';");
		s.execute(	"ALTER TABLE Handle ADD CONSTRAINT unique_id UNIQUE (id);");
	}

	public static void main(String[] args) throws Exception {
		MySQLController mc = new MySQLController(
				"/usr/sbin/mysqld",
				"/usr/bin/mysql_install_db",
				Paths.get("workspacetesttemp"),
				false);
		MongoController monc = new MongoController(
				"/kb/runtime/bin/mongod",
				Paths.get("workspacetesttemp"),
				false); 
		ShockController sc = new ShockController(
				"/kb/deployment/bin/shock-server",
				Paths.get("workspacetesttemp"),
				"fakeuser",
				"localhost:" + monc.getServerPort(),
				"shocdb", "foo", "foo", false); 
		
		HandleServiceController hsc = new HandleServiceController(
				"/kb/runtime/bin/plackup",
				"/kb/deployment/lib/AbstractHandle.psgi",
				"/kb/deployment/lib/HandleManager.psgi",
				mc,
				"localhost:" + sc.getServerPort(),
				"/kb/deployment/lib",
				Paths.get("workspacetesttemp"),
				false);
		System.out.println(hsc.getHandleServerPort());
		System.out.println(hsc.getTempDir());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		hsc.destroy();
		sc.destroy();
		monc.destroy();
		mc.destroy();
	}
	
}
