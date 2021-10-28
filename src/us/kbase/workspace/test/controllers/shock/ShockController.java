package us.kbase.workspace.test.controllers.shock;

import static us.kbase.common.test.controllers.ControllerCommon.checkExe;
import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoDatabase;

/** Q&D Utility to run a Shock server for the purposes of testing from
 * Java.
 * @author gaprice@lbl.gov
 *
 */
public class ShockController {
	
	private final static String SHOCK_CONFIG_FN = "shock.cfg";
	private final static String SHOCK_CONFIG =
			"us/kbase/workspace/test/controllers/shock/conf/" +
					SHOCK_CONFIG_FN;
	
	private final static List<String> TEMP_DIRS =
			new LinkedList<String>();
	static {
		TEMP_DIRS.add("shock/site");
		TEMP_DIRS.add("shock/data");
		TEMP_DIRS.add("shock/logs");
	}
	
	//TODO might need a proper version class that can do ranges etc
	private final static Set<String> VERSION_SET =
			new HashSet<String>(Arrays.asList("0.8.23", "0.9.6", "0.9.12"));
	
	private final Path tempDir;
	
	private final Process shock;
	private final int port;
	private final String knownVersion;

	public ShockController(
			final String shockExe,
			final String shockVersion,
			final Path rootTempDir,
			final String adminUser,
			final String mongohost,
			final String shockMongoDBname,
			final String mongouser,
			final String mongopwd,
			final URL authurl)
			throws Exception {
		tempDir = makeTempDirs(rootTempDir, "ShockController-", TEMP_DIRS);
		port = findFreePort();
		
		if (!VERSION_SET.contains(shockVersion)) {
			knownVersion = null;
		} else {
			knownVersion = shockVersion;
		}
		
		checkExe(shockExe, "shock server");
		
		Velocity.init();
		VelocityContext context = new VelocityContext();
		context.put("port", port);
		context.put("tempdir", tempDir.toAbsolutePath().toString());
		context.put("mongohost", mongohost);
		context.put("mongodbname", shockMongoDBname);
		context.put("mongouser", mongouser == null ? "" : mongouser);
		context.put("mongopwd", mongopwd == null ? "" : mongopwd);
		context.put("shockadmin", adminUser);
		context.put("authurl", authurl.toString());
		
		File shockcfg = tempDir.resolve(SHOCK_CONFIG_FN).toFile();
		
		generateConfig(SHOCK_CONFIG, context, shockcfg);
		
		final MongoClient mc = buildMongo(mongohost, shockMongoDBname, mongouser, mongopwd);
		final MongoDatabase shockDB = mc.getDatabase(shockMongoDBname);
		
		setupWorkarounds(shockDB, adminUser, knownVersion);

		ProcessBuilder servpb = new ProcessBuilder(shockExe, "--conf",
				shockcfg.toString())
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("shock_server.log").toFile());
		
		shock = servpb.start();
		Thread.sleep(1000); //wait for server to start
	}
	
	private MongoClient buildMongo(
			final String mongohost,
			final String mongodbname,
			final String mongouser,
			final String mongopwd) {
		if (mongouser != null) {
			final MongoCredential creds = MongoCredential.createCredential(
					mongouser, mongodbname, mongopwd.toCharArray());
			return new MongoClient(new ServerAddress(mongohost), creds,
					MongoClientOptions.builder().build());
		} else {
			return new MongoClient(new ServerAddress(mongohost));
		}
	}
	
	private void setupWorkarounds(
			final MongoDatabase shockDB,
			final String adminUser,
			final String version) {
		if ("0.8.23".equals(version)) {
			// the version of 0.8.23 above actually works fine without this,
			// but it's a few commits beyond the actual 0.8.23 tag, so if the
			// exact tagged version is added it'll need the admin insert
			addAdminUser(shockDB, adminUser);
		} else if ("0.9.6".equals(version)) {
			setCollectionVersions(shockDB, 2, 2, 1);
			addAdminUser(shockDB, adminUser);
		} else if ("0.9.12".equals(version)) {
			setCollectionVersions(shockDB, 4, 2, 1);
			addAdminUser(shockDB, adminUser);
		} else {
			//no workarounds possible
		}
	}
	
	private void addAdminUser(final MongoDatabase shockDB, final String adminUser) {
		final Document a = new Document("username", adminUser);
		a.put("uuid", "095abbb0-07cc-43b3-8fd9-98edfb2541be");
		a.put("fullname", "");
		a.put("email", "");
		a.put("password", "");
		a.put("shock_admin", true);
		shockDB.getCollection("Users").insertOne(a);
	}

	private void setCollectionVersions(
			final MongoDatabase shockDB,
			int nodever,
			int aclver,
			int authver) {
		final Document n = new Document("name", "Node");
		n.put("version", nodever);
		final Document acl = new Document("name", "ACL");
		acl.put("version", aclver);
		final Document auth = new Document("name", "Auth");
		auth.put("version", authver);
		shockDB.getCollection("Versions").insertMany(Arrays.asList(n, acl, auth));
	}

	/** Returns the Shock version supplied in the constructor *if* the version
	 * has workarounds available. Otherwise returns null.
	 * @return the Shock version.
	 */
	public String getVersion() {
		return knownVersion;
	}

	public int getServerPort() {
		return port;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy(boolean deleteTempFiles) throws IOException {
		if (shock != null) {
			shock.destroy();
		}
		if (tempDir != null && deleteTempFiles) {
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
				"0.9.6",
				Paths.get("workspacetemp"),
				"kbasetest2",
				"localhost",
				"delete_shock_db",
				"foo", "foo", new URL("https://foo.com"));
		System.out.println(ac.getServerPort());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		ac.destroy(false);
		reader.close();
	}
	
}
