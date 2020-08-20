package us.kbase.workspace.test.controllers.sample;

import static us.kbase.common.test.controllers.ControllerCommon.findFreePort;
import static us.kbase.common.test.controllers.ControllerCommon.makeTempDirs;
import static us.kbase.workspace.database.Util.checkString;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;

import us.kbase.workspace.test.controllers.arango.ArangoController;

/** Q&D Utility to run the Sample Service for the purposes of testing from Java.
 * @author gaprice@lbl.gov
 *
 */
public class SampleServiceController {
	
	private static final String SAMPLE_SERVICE_NAME = "SampleService";
	
	private final Process sampleService;
	private final int port;
	private final Path tempDir;
	private final Path logfile;
	
	/** A container for a multitude of arangoDB parameters required by the Sample Service
	 * controller.
	 *
	 */
	public static class SampleServiceArangoParameters {
		// ugh.
		public final String arangoUser;
		public final String arangoPwd;
		public final String arangoDB;
		public final String sampleCollection;
		public final String versionCollection;
		public final String versionEdgeCollection;
		public final String nodeCollection;
		public final String nodeEdgeCollection;
		public final String dataLinkCollection;
		public final String workspaceObjectVersionShadowCollection;
		public final String schemaCollection;
		
		/** Create the parameter container.
		 * @param arangoUser the ArangoDB user account to use to connect to ArangoDB.
		 * @param arangoPwd the user's password.
		 * @param arangoDB the database to connect to.
		 * @param sampleCollection the collection in which to store sample information.
		 * @param versionCollection the collection in which to store version information.
		 * @param versionEdgeCollection the collection in which to store version edge information.
		 * @param nodeCollection the collection in which to store node information.
		 * @param nodeEdgeCollection the collection in which to store node edge information.
		 * @param dataLinkCollection the collection in which to store data link information.
		 * @param workspaceObjectVersionShadowCollection the collection in which to store
		 * workspace shadow object information.
		 * @param schemaCollection the collection in which to store schema information.
		 */
		public SampleServiceArangoParameters(
				final String arangoUser,
				final String arangoPwd,
				final String arangoDB,
				final String sampleCollection,
				final String versionCollection,
				final String versionEdgeCollection,
				final String nodeCollection,
				final String nodeEdgeCollection,
				final String dataLinkCollection,
				final String workspaceObjectVersionShadowCollection,
				final String schemaCollection) {
			this.arangoUser = checkString(arangoUser, "arangoUser");
			this.arangoPwd = checkString(arangoPwd, "arangoPwd");
			this.arangoDB = checkString(arangoDB, "arangoDB");
			this.sampleCollection = checkString(sampleCollection, "sampleCollection");
			this.versionCollection = checkString(versionCollection, "versionCollection");
			this.versionEdgeCollection = checkString(
					versionEdgeCollection, "versionEdgeCollection");
			this.nodeCollection = checkString(nodeCollection, "nodeCollection");
			this.nodeEdgeCollection = checkString(nodeEdgeCollection, "nodeEdgeCollection");
			this.dataLinkCollection = checkString(dataLinkCollection, "dataLinkCollection");
			this.workspaceObjectVersionShadowCollection = checkString(
					workspaceObjectVersionShadowCollection,
					"workspaceObjectVersionShadowCollection");
			this.schemaCollection = checkString(schemaCollection, "schemaCollection");
		}
	}

	/** Create the sample service controller.
	 * @param port the port on which the service should listen. Pass a number less than 1 to
	 *  generate a random port number.
	 * @param arango the ArangoDB controller where the sample service will store files.
	 * @param sampleServiceDir the directory containing the sample service repo.
	 * @param rootTempDir a temporary directory in which to store sample service data and log
	 *  files. The files will be stored inside a child directory that is unique per invocation.
	 * @param authURL the URL of the KBase authentication service.
	 * @param authToken a valid authentication service token.
	 * @param workspaceURL the URL of the KBase workspace service.
	 * @param workspaceToken an authentication token with admin read privileges to the
	 *  workspace service.
	 * @param sampleFullAdminRole an auth role that will grant full admin priviledges to the
	 *  sample service.
	 * @param arangoParams parameters for sample service communications with ArangoDB.
	 * @throws Exception if an error occurs.
	 */
	public SampleServiceController(
			final int port,
			final ArangoController arango,
			final Path sampleServiceDir,
			final Path rootTempDir,
			final URL authURL,
			final String authToken,
			final String sampleFullAdminRole,
			final URL workspaceURL,
			final String workspaceToken,
			final SampleServiceArangoParameters arangoParams)
			throws Exception {

		tempDir = makeTempDirs(rootTempDir, "SampleServiceController-", new LinkedList<String>());

		if (port < 1) {
			this.port = findFreePort();
		} else {
			this.port = port;
		}
		File ssIniFile = createSampleServiceDeployCfg(
				arango,
				authURL,
				authToken,
				workspaceURL,
				workspaceToken,
				sampleFullAdminRole,
				arangoParams);

		String libDir = "lib";
		Path libRoot = tempDir.resolve(libDir);
		FileUtils.copyDirectory(sampleServiceDir.toFile(), libRoot.toFile());

		final String libDirPath = libRoot.toAbsolutePath().toString();
		logfile = tempDir.resolve("sample_service.log");
		ProcessBuilder samplepb = new ProcessBuilder(
				"gunicorn",
				"--worker-class", "gevent",
				"--timeout", "30",
				"--workers", "1",
				"--bind", ":" + port,
				"--log-level", "info",
				String.format("%s.%sServer:application", SAMPLE_SERVICE_NAME, SAMPLE_SERVICE_NAME))
				.redirectErrorStream(true)
				.redirectOutput(logfile.toFile());
		Map<String, String> env = samplepb.environment();
		env.put("KB_DEPLOYMENT_CONFIG", ssIniFile.getAbsolutePath().toString());
		env.put("KB_SERVICE_NAME", SAMPLE_SERVICE_NAME);
		env.put("PYTHONPATH", libDirPath);
		samplepb.directory(new File(libDirPath));
		sampleService = samplepb.start();

		Thread.sleep(1000); //let the service start up
	}

	private File createSampleServiceDeployCfg(
			final ArangoController arango,
			final URL authURL,
			final String authToken,
			final URL workspaceURL,
			final String workspaceToken,
			final String sampleFullAdminRole,
			final SampleServiceArangoParameters arangoParams)
			throws IOException {
		final File iniFile = tempDir.resolve("sampleService.cfg").toFile();
		final Ini ini = new Ini();
		final Section ss = ini.add(SAMPLE_SERVICE_NAME);
		URL authServiceURL = new URL(authURL.toString() + "/api/legacy/KBase/Sessions/Login");
		ss.add("auth-service-url", authServiceURL.toString());
		ss.add("auth-root-url", authURL.toString());
		ss.add("auth-service-url-allow-insecure", "true");
		ss.add("auth-token", authToken);
		ss.add("auth-full-admin-roles", sampleFullAdminRole);
		
		ss.add("workspace-url", workspaceURL.toString());
		ss.add("workspace-read-admin-token", workspaceToken);
		
		ss.add("arango-url", "http://localhost:" + arango.getServerPort());
		ss.add("arango-db", arangoParams.arangoDB);
		ss.add("arango-user", arangoParams.arangoUser);
		ss.add("arango-pwd", arangoParams.arangoPwd);

		ss.add("sample-collection", arangoParams.sampleCollection);
		ss.add("version-collection", arangoParams.versionCollection);
		ss.add("version-edge-collection", arangoParams.versionEdgeCollection);
		ss.add("node-collection", arangoParams.nodeCollection);
		ss.add("node-edge-collection", arangoParams.nodeEdgeCollection);
		ss.add("data-link-collection", arangoParams.dataLinkCollection);
		ss.add("workspace-object-version-shadow-collection",
				arangoParams.workspaceObjectVersionShadowCollection);
		ss.add("schema-collection", arangoParams.schemaCollection);
		
		ini.store(iniFile);
		return iniFile;
	}


	public int getPort() {
		return port;
	}

	public Path getTempDir() {
		return tempDir;
	}

	public void destroy(final boolean deleteTempFiles) throws IOException {
		destroy(deleteTempFiles, false);
	}
	
	public void destroy(final boolean deleteTempFiles, final boolean dumpLogToStdOut)
			throws IOException {
		if (sampleService != null) {
			sampleService.destroy();
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
