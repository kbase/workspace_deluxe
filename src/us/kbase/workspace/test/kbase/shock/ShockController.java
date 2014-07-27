package us.kbase.workspace.test.kbase.shock;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import us.kbase.auth.AuthToken;
import us.kbase.common.test.TestException;
import us.kbase.shock.client.BasicShockClient;



/** Q&D Utility to run an AWE server & client for the purposes of testing from
 * Java.
 * @author gaprice@lbl.gov
 *
 */
public class ShockController {
	
	//TODO share common code with AweController
	
	private final static String AWE_CONFIG_FN = "awe.cfg";
	private final static String AWEC_CONFIG_FN = "awec.cfg";
	
	private final static String AWE_CONFIG =
			"us/kbase/userandjobstate/test/awe/controller/conf/" +
					AWE_CONFIG_FN;
	private final static String AWEC_CONFIG =
			"us/kbase/userandjobstate/test/awe/controller/conf/" +
					AWEC_CONFIG_FN;
	
	private final static String CLI_SCRIPT_FN = "client_script.py";
	private final static String CLI_SCRIPT =
			"us/kbase/userandjobstate/test/awe/controller/script/" +
					CLI_SCRIPT_FN;
	
	private final static String EXISTING_CLIENTGROUP = "kbase-fake-group";
	private final static String MISSING_CLIENTGROUP = "kbase-missing-group";
	
	private final static List<String> tempDirectories =
			new LinkedList<String>();
	static {
		tempDirectories.add("awe/site");
		tempDirectories.add("awe/data");
		tempDirectories.add("awe/logs");
		tempDirectories.add("awe/awfs");
		tempDirectories.add("awec/data");
		tempDirectories.add("awec/logs");
		tempDirectories.add("awec/work");
		tempDirectories.add("awec/site");
	}
	
	private final static PoolingHttpClientConnectionManager connmgr =
			new PoolingHttpClientConnectionManager();
	static {
		connmgr.setMaxTotal(1000); 
		connmgr.setDefaultMaxPerRoute(1000);
	}
	private final static CloseableHttpClient client =
			HttpClients.custom().setConnectionManager(connmgr).build();
	
	private final Path tempDir;
	
	private final URL shockURL;
	
	private final Process awe;
	private final Process awec;
	private final int port;
	private final boolean deleteTempDirOnExit;

	public ShockController(
			final URL shockURL,
			final String aweExe,
			final String aweClientExe,
			final String mongohost,
			final String aweMongoDBname,
			final String mongouser,
			final String mongopwd,
			final boolean deleteTempDirOnExit)
					throws Exception {
		this.shockURL = shockURL;
		new BasicShockClient(shockURL); // check url
		tempDir = makeTempDirs();
		port = findFreePort();
		this.deleteTempDirOnExit = deleteTempDirOnExit;
		
		checkExe(aweExe, "awe server");
		checkExe(aweExe, "awe client");
		
		Velocity.init();
		VelocityContext context = new VelocityContext();
		context.put("port", port);
		context.put("tempdir", tempDir.toString());
		context.put("mongohost", mongohost);
		context.put("mongodbname", aweMongoDBname);
		context.put("mongouser", mongouser == null ? "" : mongouser);
		context.put("mongopwd", mongopwd == null ? "" : mongopwd);
		
		File awecfg = tempDir.resolve(AWE_CONFIG_FN).toFile();
		File aweclicfg = tempDir.resolve(AWEC_CONFIG_FN).toFile();
		
		generateConfig(AWE_CONFIG, context, awecfg);
		generateConfig(AWEC_CONFIG, context, aweclicfg);
		copyScript(CLI_SCRIPT, tempDir.resolve(CLI_SCRIPT_FN));

		ProcessBuilder servpb = new ProcessBuilder(aweExe, "--conf",
				awecfg.toString())
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("awe_server.log").toFile());
		updateProcessEnvironment(servpb);
		
		awe = servpb.start();
		Thread.sleep(1000); //wait for awe server to start
		
		ProcessBuilder clipb = new ProcessBuilder(aweClientExe, "--conf",
				aweclicfg.toString())
				.redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("awe_client.log").toFile());
		updateProcessEnvironment(clipb);
				
		awec = clipb.start();
	}
	
	private void updateProcessEnvironment(ProcessBuilder pb) {
		Map<String, String> env = pb.environment();
		if (env.get("PATH") == null) {
			throw new TestException("WTF no path in the environment?");
		}
		String path = env.get("PATH") + File.pathSeparator + tempDir.toString();
		env.put("PATH", path);
	}

	public int getServerPort() {
		return port;
	}
	
	public Path getTempDir() {
		return tempDir;
	}
	
	public void destroy() throws IOException {
		if (awe != null) {
			awe.destroy();
		}
		if (awec != null) {
			awec.destroy();
		}
		if (tempDir != null && deleteTempDirOnExit) {
			FileUtils.deleteDirectory(tempDir.toFile());
		}
	}
	
	public TestAweJob createJob(String service, String description) {
		return new TestAweJob(service, description, EXISTING_CLIENTGROUP);
	}
	
	public TestAweJob createJobWithNoClient(String service,
			String description) {
		return new TestAweJob(service, description, MISSING_CLIENTGROUP);
	}
	
	public String submitJob(TestAweJob job, AuthToken token)
			throws IOException {
		if (job.tasks.isEmpty()) {
			throw new IllegalStateException("no tasks");
		}
		byte[] jobdoc = makeJobDoc(job);
		HttpPost hp = new HttpPost("http://localhost:" + port + "/job"); 
		hp.setHeader("Authorization", "OAuth " + token);
		hp.setHeader("Datatoken", token.toString());
		final MultipartEntityBuilder mpeb = MultipartEntityBuilder.create();
		mpeb.addBinaryBody("upload", jobdoc, ContentType.DEFAULT_BINARY, "foo");
		hp.setEntity(mpeb.build());
		final CloseableHttpResponse response = client.execute(hp);
		final String resp = EntityUtils.toString(response.getEntity());
		if (response.getStatusLine().getStatusCode() != 200) {
			throw new TestException("failed to submit job to AWE: " +
					response.getStatusLine().toString() + " | " + resp);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> j = new ObjectMapper().readValue(resp, Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> data = (Map<String, Object>)j.get("data");
		return (String) data.get("id");
	}

	private byte[] makeJobDoc(TestAweJob job) {
		Map<String, Object> j = new HashMap<String, Object>();
		Map<String, Object> info = new HashMap<String, Object>();
		if (job.service != null) {
			info.put("service", job.service);
		}
		if (job.description != null) {
			info.put("description", job.description);
		}
		info.put("noretry", true);
		info.put("clientgroups", job.clientgroup);
		j.put("info", info);
		List<Map<String, Object>> tasks = new LinkedList<Map<String,Object>>();
		int count = 0;
		for (TestAweTask task: job.tasks) {
			Map<String, Object> t = new HashMap<String, Object>();
			t.put("taskid", "" + count);
			if (count > 0) {
				t.put("dependsOn", Arrays.asList("" + (count - 1)));
			}
			Map<String, String> cmd = new HashMap<String, String>();
			cmd.put("name", "client_script.py");
			cmd.put("args", task.getArgs());
			t.put("cmd", cmd);
			if (!task.getInputs().isEmpty()) {
				Map<String, Map<String, String>> inputs =
						new HashMap<String, Map<String, String>>();
				for (String i: task.getInputs()) {
					i = i.substring(1); //remove @
					inputs.put(i, new HashMap<String, String>());
					inputs.get(i).put("host", shockURL.toExternalForm());
					inputs.get(i).put("origin", "" + (count - 1));
				}
				t.put("inputs", inputs);
			}
			if (!task.getOutputs().isEmpty()) {
				Map<String, Map<String, Object>> outputs =
						new HashMap<String, Map<String, Object>>();
				Iterator<Boolean> temp = task.getTemporaryOutput().iterator(); 
				for (String i: task.getOutputs()) {
					outputs.put(i, new HashMap<String, Object>());
					outputs.get(i).put("host", shockURL.toExternalForm());
					outputs.get(i).put("temporary", temp.next());
				}
				t.put("outputs", outputs);
			}
			
			tasks.add(t);
			count++;
		}
		
		j.put("tasks", tasks);
		try {
			return new ObjectMapper().writeValueAsBytes(j);
		} catch (JsonProcessingException jpe) {
			throw new IllegalStateException(
					"bug in test code for writing job doc: " +
							jpe.getMessage(), jpe);
		}
	}

	private void checkExe(String aweExe, String exeType) {
		File e = new File(aweExe);
		if (!e.exists()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable does not exist:" + aweExe);
		}
		if (!e.isFile()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable is not a file:" + aweExe);
		}
		if (!e.canExecute()) {
			throw new IllegalArgumentException("The provided " + exeType +
					" executable is not executable:" + aweExe);
		}
		
	}


	private Path makeTempDirs() throws IOException {
		Set<PosixFilePermission> perms =
				PosixFilePermissions.fromString("rwx------");
		FileAttribute<Set<PosixFilePermission>> attr =
				PosixFilePermissions.asFileAttribute(perms);
		Path tempDir = Files.createTempDirectory("AweController-", attr);
		for(String p: tempDirectories) {
			Files.createDirectories(tempDir.resolve(p));
		}
		return tempDir;
	}

	private void generateConfig(final String configFile,
			final VelocityContext context, File file)
			throws IOException {
		String template = IOUtils.toString(new BufferedReader(
				new InputStreamReader(
						getClass().getClassLoader()
						.getResourceAsStream(configFile))));
		
		StringWriter sw = new StringWriter();
		Velocity.evaluate(context, sw, "aweconfig", template);
		PrintWriter pw = new PrintWriter(file);
		pw.write(sw.toString());
		pw.close();
	}
	
	
	private void copyScript(String cliScript, Path file) throws IOException {
		String script = IOUtils.toString(new BufferedReader(
				new InputStreamReader(
						getClass().getClassLoader()
						.getResourceAsStream(cliScript))));
		PrintWriter pw = new PrintWriter(file.toFile());
		pw.write(script);
		pw.close();
		Set<PosixFilePermission> perms =
				PosixFilePermissions.fromString("rwx------");
		Files.setPosixFilePermissions(file, perms);
		
	}

	/** See https://gist.github.com/vorburger/3429822
	 * Returns a free port number on localhost.
	 *
	 * Heavily inspired from org.eclipse.jdt.launching.SocketUtil (to avoid a
	 * dependency to JDT just because of this).
	 * Slightly improved with close() missing in JDT. And throws exception
	 * instead of returning -1.
	 *
	 * @return a free port number on localhost
	 * @throws IllegalStateException if unable to find a free port
	 */
	private static int findFreePort() {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket(0);
			socket.setReuseAddress(true);
			int port = socket.getLocalPort();
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore IOException on close()
			}
			return port;
		} catch (IOException e) {
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
		throw new IllegalStateException(
				"Could not find a free TCP/IP port");
	}
	
	public static void main(String[] args) throws Exception {
		ShockController ac = new ShockController(
				new URL("http://localhost:7044"),
				"/kb/deployment/bin/awe-server",
				"/kb/deployment/bin/awe-client",
				"localhost",
				"delete_awe_db",
				"foo", "foo", false);
		Scanner reader = new Scanner(System.in);
		System.out.println("any char to shut down");
		//get user input for a
		reader.next();
		ac.destroy();
	}
	
	public class TestAweJob {
		
		private final String description;
		private final String service;
		private final String clientgroup;
		private final List<TestAweTask> tasks = new LinkedList<TestAweTask>();
		
		
		private TestAweJob(String service, String description,
				String clientgroup) {
			this.service = service;
			this.description = description;
			this.clientgroup = clientgroup;
		}

		public void addTask() {
			tasks.add(new TestAweTask());
		}
		
		public void addErrorTask() {
			tasks.add(new TestAweErrorTask());
		}
		
		public void addDelayTask(int secDelay) {
			tasks.add(new TestAweDelayTask(secDelay));
		}
		
		public void addIOTask(final List<String> inputfiles,
				final List<String> outputfiles, List<Boolean> temporary) {
			List<String> in = inputfiles;
			if (inputfiles != null && !inputfiles.isEmpty()) {
				if (tasks.isEmpty() || !(tasks.get(tasks.size() - 1)
						instanceof TestAweIOTask)) {
					throw new IllegalArgumentException(
							"task requires input files but either no prior task or prior task has no output");
				}
				final TestAweIOTask prior =
						(TestAweIOTask) tasks.get(tasks.size() - 1);
				in = new LinkedList<String>();
				for (String s: inputfiles) {
					if (!prior.outputs.contains(s)) {
						throw new IllegalArgumentException(
								"prior task does not have output file called "
								+ s);
					}
					in.add("@" + s);
				}
			}
			tasks.add(new TestAweIOTask(in, outputfiles, temporary));
			
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TestAweJob [description=");
			builder.append(description);
			builder.append(", service=");
			builder.append(service);
			builder.append(", tasks=");
			builder.append(tasks);
			builder.append("]");
			return builder.toString();
		}
	}
	
	public class TestAweTask {
		private TestAweTask() {};
		
		protected String getArgs() {
			return "";
		}
		
		protected List<String> getInputs() {
			return new LinkedList<String>();
		}
		
		protected List<String> getOutputs() {
			return new LinkedList<String>();
		}
		
		protected List<Boolean> getTemporaryOutput() {
			return new LinkedList<Boolean>();
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TestAweTask []");
			return builder.toString();
		}
	}
	
	private class TestAweErrorTask extends TestAweTask {
		
		private TestAweErrorTask() {};
		
		@Override
		protected String getArgs() {
			return "--error";
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TestAweErrorTask []");
			return builder.toString();
		}
	}

	private class TestAweDelayTask extends TestAweTask {

		private final int secDelay;

		private TestAweDelayTask(int secDelay) {
			this.secDelay = secDelay;
		};

		@Override
		protected String getArgs() {
			return "--delay " + secDelay;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TestAweDelayTask [secDelay=");
			builder.append(secDelay);
			builder.append("]");
			return builder.toString();
		}
	}
	
	private class TestAweIOTask extends TestAweTask {
		
		private final List<String> inputs;
		private final List<String> outputs;
		private final List<Boolean> temp;
		private TestAweIOTask(final List<String> inputs,
				final List<String> outputs, final List<Boolean> temporary) {
			super();
			if (inputs == null) {
				this.inputs = new LinkedList<String>();
			} else {
				this.inputs = Collections.unmodifiableList(
						new LinkedList<String>(inputs));
				if (new HashSet<String>(inputs).size() != inputs.size()) {
					throw new IllegalArgumentException("duplicated input files");
				}
			}
			if (outputs == null) {
				this.outputs = new LinkedList<String>();
				this.temp = new LinkedList<Boolean>();
			} else {
				this.outputs = Collections.unmodifiableList(
						new LinkedList<String>(outputs));
				if (temporary == null) {
					throw new NullPointerException(
							"if output files are provided, temporary list must be as well");
				}
				if (temporary.size() != outputs.size()) {
					throw new IllegalArgumentException(
							"Must provide temporary indicator for every output file");
				}
				this.temp = Collections.unmodifiableList(
						new LinkedList<Boolean>(temporary));
				if (new HashSet<String>(outputs).size() != outputs.size()) {
					throw new IllegalArgumentException("duplicated output files");
				}
			}
		}
		
		@Override
		protected List<String> getInputs() {
			return inputs;
		}
		
		@Override
		protected List<String> getOutputs() {
			return outputs;
		}
		
		@Override
		protected List<Boolean> getTemporaryOutput() {
			return temp;
		}
		
		@Override
		protected String getArgs() {
			String ret = "";
			if (!inputs.isEmpty()) {
				ret += "--infiles " + StringUtils.join(inputs, " ") + " ";
			}
			if (!outputs.isEmpty()) {
				ret += "--outfiles " + StringUtils.join(outputs, " ");
			}
			return ret;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TestAweIOTask [inputs=");
			builder.append(inputs);
			builder.append(", outputs=");
			builder.append(outputs);
			builder.append("]");
			return builder.toString();
		}
	}
}
