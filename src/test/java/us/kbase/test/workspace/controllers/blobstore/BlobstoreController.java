package us.kbase.test.workspace.controllers.blobstore;

import static us.kbase.common.test.controllers.ControllerCommon.checkExe;
import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

/** Q&D Utility to run a Blobstore server for the purposes of testing from
 * Java.
 * @author gaprice@lbl.gov
 *
 */
public class BlobstoreController {
	
	private static final String BLOBSTORE_CFG_SECTION = "BlobStore";
	private final Process blobstore;
	private final int port;
	private final Path tempDir;
	private final Path logfile;
	
	/** Create the controller.
	 * @param blobstoreExe the path to the Blobstore executable.
	 * @param rootTempDir a temporary directory in which to store blobstore data and log
	 *  files. The files will be stored inside a child directory that is unique per invocation.
	 * @param mongohost the mongo host string, e.g. localhost:27017
	 * @param mongoDBname the name of the mongo database to use for storing blobstore data.
	 * @param s3Host the s3 host string, e.g. localhost:9000
	 * @param s3Bucket the s3 bucket to use for storing blobstore data.
	 * @param s3AccessKey the s3 access key.
	 * @param s3AccessSecret the s3 access secret.
	 * @param s3Region the s3 region, e.g. us-west-1
	 * @param kbaseAuthURL the root URL of the KBase auth service.
	 * @param adminRoles the KBase auth service admin roles that the blobstore should recognize
	 * as denoting Blobstore administrative users.
	 * @throws Exception if anything goes wrong.
	 */
	public BlobstoreController(
			final String blobstoreExe,
			final Path rootTempDir,
			final String mongohost,
			final String mongoDBname,
			final String s3Host,
			final String s3Bucket,
			final String s3AccessKey,
			final String s3AccessSecret,
			final String s3Region,
			final URL kbaseAuthURL,
			final List<String> adminRoles)
			throws Exception {
		checkExe(blobstoreExe, "blobstore");
		this.tempDir = makeTempDirs(rootTempDir, "BlobstoreController-", Collections.emptyList());
		this.port = findFreePort();
		
		final File bsIniFile = createBlobstoreDeployCfg(
				mongohost,
				mongoDBname,
				s3Host,
				s3Bucket,
				s3AccessKey,
				s3AccessSecret,
				s3Region,
				kbaseAuthURL,
				adminRoles
				);
		
		this.logfile = tempDir.resolve("blobstore.log");
		
		ProcessBuilder servpb = new ProcessBuilder(blobstoreExe, "--conf", bsIniFile.toString())
				.redirectErrorStream(true)
				.redirectOutput(logfile.toFile());
		
		this.blobstore = servpb.start();
		Thread.sleep(1000); //wait for server to start
	}
	
	private File createBlobstoreDeployCfg(
			final String mongohost,
			final String mongoDBname,
			final String s3Host,
			final String s3Bucket,
			final String s3AccessKey,
			final String s3AccessSecret,
			final String s3Region,
			final URL kbaseAuthURL,
			final List<String> adminRoles)
			throws IOException {
		final File iniFile = tempDir.resolve("blobstore.cfg").toFile();
		final Ini ini = new Ini();
		final Section ss = ini.add(BLOBSTORE_CFG_SECTION);
		ss.add("host", "localhost:" + port);
		
		ss.add("kbase-auth-url", kbaseAuthURL.toString());
		ss.add("kbase-auth-admin-roles", String.join(", ", adminRoles));
		
		ss.add("mongodb-host", mongohost);
		ss.add("mongodb-database", mongoDBname);
		
		ss.add("s3-host", s3Host);
		ss.add("s3-bucket", s3Bucket);
		ss.add("s3-access-key", s3AccessKey);
		ss.add("s3-access-secret", s3AccessSecret);
		ss.add("s3-region", s3Region);
		ss.add("s3-disable-ssl", "true");

		ini.store(iniFile);
		return iniFile;
	}
	
	/** Get the blobstore port.
	 * @return the port.
	 */
	public int getPort() {
		return port;
	}

	/** Get the directory in which the blobstore is storing temporary files.
	 * @return the temporary directory.
	 */
	public Path getTempDir() {
		return tempDir;
	}

	/** Shut down the blob store.
	 * @param deleteTempFiles true to delete any temporary files.
	 * @throws IOException if an IO error occurs.
	 */
	public void destroy(final boolean deleteTempFiles) throws IOException {
		destroy(deleteTempFiles, false);
	}
	
	/** Shut down the blob store.
	 * @param deleteTempFiles true to delete any temporary files.
	 * @param dumpLogToStdOut print any blobstore logs to standard out.
	 * @throws IOException if an IO error occurs.
	 */
	public void destroy(final boolean deleteTempFiles, final boolean dumpLogToStdOut)
			throws IOException {
		if (blobstore != null) {
			blobstore.destroy();
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