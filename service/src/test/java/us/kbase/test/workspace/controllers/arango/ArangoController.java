package us.kbase.test.workspace.controllers.arango;

import static us.kbase.testutils.controllers.ControllerCommon.checkExe;
import static us.kbase.testutils.controllers.ControllerCommon.findFreePort;
import static us.kbase.testutils.controllers.ControllerCommon.makeTempDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.CollectionEntity;

// ported from https://github.com/kbase/sample_service/blob/master/test/arango_controller.py

/** Q&D Utility to run an Arango server for the purposes of testing from
 * Java. Production use is not recommended.
 * @author gaprice@lbl.gov
 *
 */

public class ArangoController {
	
	private final Path tempDir;
	
	private final Process arango;
	private final int port;
	private final ArangoDB client;

	/** Create the controller.
	 * @param arangoExe the path to the ArangoDB executable.
	 * @param arangoJS the path to the ArangoDB javascript files.
	 * @param rootTempDir A temporary directory in which to store ArangoDB data and log files.
		The files will be stored inside a child directory that is unique per invocation.
	 * @throws Exception if an exception occurs.
	 */
	public ArangoController(
			final Path arangoExe,
			final Path arangoJS,
			final Path rootTempDir)
			throws Exception {
		tempDir = makeTempDirs(rootTempDir, "ArangoController-", Arrays.asList("data"));
		port = findFreePort();
		
		checkExe(arangoExe.toString(), "arango server");
		if (arangoJS == null || !Files.isDirectory(arangoJS)) {
			throw new IllegalArgumentException("arangoJS must be a directory");
		}
		
		final Path dataDir = tempDir.resolve("data");
		
		ProcessBuilder servpb = new ProcessBuilder(
				arangoExe.toString(),
				"--server.endpoint", "tcp://localhost:" + port,
				"--configuration", "none",
				"--database.directory", dataDir.toString(),
				"--javascript.startup-directory", arangoJS.toString(),
				"--javascript.app-path", dataDir.resolve("apps").toString(),
				"--log.file", tempDir.resolve("arango.log").toString())
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("arango_server.log").toFile());
		
		arango = servpb.start();
		Thread.sleep(2000); //wait for server to start
		
		client = new ArangoDB.Builder().host("localhost", port).build();
		client.db().getVersion(); // checks connection
	}
	
	/** Get a client pointed at the ArangoDB instance.
	 * @return the client.
	 */
	public ArangoDB getClient() {
		return client;
	}
	
	/** Get the Arango port.
	 * @return the port.
	 */
	public int getServerPort() {
		return port;
	}
	
	/** Get the directory in which data / log / etc. files are stored temporarily.
	 * @return
	 */
	public Path getTempDir() {
		return tempDir;
	}
	
	/** Shut down the Arango server and optionally deleted all the generated files.
	 * @param deleteTempFiles delete the data / log / etc. files.
	 * @throws IOException if an IO error occurs.
	 */
	public void destroy(boolean deleteTempFiles) throws IOException {
		if (arango != null) {
			arango.destroy();
		}
		if (tempDir != null && deleteTempFiles) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}
	
	/** Clear all data from a database.
	 * @param dbName the database to clear.
	 * @param dropIndexes remove any indexes as well as data.
	 */
	public void clearDatabase(final String dbName, final boolean dropIndexes) {
		final ArangoDatabase db = this.client.db(dbName);
		if (dropIndexes) {
			db.drop();
		} else {
			for (final CollectionEntity col: db.getCollections()) {
				if (!col.getName().startsWith("_")) {
					// don't drop collection since that drops indexes
					db.collection(col.getName()).truncate();
				}
			}
		}
	}

	public static void main(final String[] args) throws Exception {
		final Path home = Paths.get(System.getProperty("user.home"));
		ArangoController ac = new ArangoController(
				home.resolve("arango/3.5.0/usr/sbin/arangod"),
				home.resolve("arango/3.5.0/usr/share/arangodb3/js/"),
				Paths.get("arango_temp_dir"));
		System.out.println(ac.getServerPort());
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		ac.destroy(false);
		reader.close();
	}

}