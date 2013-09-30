package us.kbase.common.service;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ini4j.Ini;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonServerServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String APP_JSON = "application/json";
	private ObjectMapper mapper;
	private Map<String, Method> rpcCache;
	public static final int LOG_LEVEL_ERR = JsonServerSyslog.LOG_LEVEL_ERR;
	public static final int LOG_LEVEL_INFO = JsonServerSyslog.LOG_LEVEL_INFO;
	public static final int LOG_LEVEL_DEBUG = JsonServerSyslog.LOG_LEVEL_DEBUG;
	public static final int LOG_LEVEL_DEBUG2 = JsonServerSyslog.LOG_LEVEL_DEBUG + 1;
	public static final int LOG_LEVEL_DEBUG3 = JsonServerSyslog.LOG_LEVEL_DEBUG + 2;
	private JsonServerSyslog sysLogger;
	private JsonServerSyslog userLogger;
	final private static String KB_DEP = "KB_DEPLOYMENT_CONFIG";
	final private static String KB_SERVNAME = "KB_SERVICE_NAME";
	protected Map<String, String> config = new HashMap<String, String>();
	private static ThreadLocal<RpcInfo> rpcInfo = new ThreadLocal<RpcInfo>();
	private Server jettyServer = null;
	private Integer jettyPort = null;
	private boolean startupFailed = false;
		
	/**
	 * Starts a test jetty server on an OS-determined port. Blocks until the
	 * server is terminated.
	 * @throws Exception if the server couldn't be started.
	 */
	public void startupServer() throws Exception {
		startupServer(0);
	}
	
	/**
	 * Starts a test jetty server. Blocks until the
	 * server is terminated.
	 * @param port the port to which the server will connect.
	 * @throws Exception if the server couldn't be started.
	 */
	public void startupServer(int port) throws Exception {
		jettyServer = new Server(port);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		jettyServer.setHandler(context);
		context.addServlet(new ServletHolder(this),"/*");
		jettyServer.start();
		jettyPort = jettyServer.getConnectors()[0].getLocalPort();
		jettyServer.join();
	}
	
	/**
	 * Get the jetty test server port. Returns null if the server is not running or starting up.
	 * @return the port
	 */
	public Integer getServerPort() {
		return jettyPort;
	}
	
	/**
	 * Stops the test jetty server.
	 * @throws Exception if there was an error stopping the server.
	 */
	public void stopServer() throws Exception {
		jettyServer.stop();
		jettyServer = null;
		jettyPort = null;
		
	}
	
	public void startupFailed() {
		this.startupFailed = true;
	}
	
	public JsonServerServlet(String specServiceName) {
		this.mapper = new ObjectMapper().registerModule(new JacksonTupleModule());
		this.rpcCache = new HashMap<String, Method>();
		for (Method m : getClass().getMethods()) {
			if (m.isAnnotationPresent(JsonServerMethod.class)) {
				JsonServerMethod ann = m.getAnnotation(JsonServerMethod.class);
				rpcCache.put(ann.rpc(), m);
			}
		}
		
		String serviceName = System.getProperty(KB_SERVNAME) == null ?
				System.getenv(KB_SERVNAME) : System.getProperty(KB_SERVNAME);
		if (serviceName == null) {
			serviceName = specServiceName;
			if (serviceName.contains(":"))
				serviceName = serviceName.substring(0, serviceName.indexOf(':')).trim();
		}
		String file = System.getProperty(KB_DEP) == null ?
				System.getenv(KB_DEP) : System.getProperty(KB_DEP);
		sysLogger = new JsonServerSyslog(serviceName, KB_DEP, LOG_LEVEL_INFO);
		userLogger = new JsonServerSyslog(sysLogger);
		//read the config file
		if (file == null) 
			return;
		File deploy = new File(file);
		Ini ini = null;
		try {
			ini = new Ini(deploy);
		} catch (IOException ioe) {
			sysLogger.log(LOG_LEVEL_ERR, getClass().getName(), "There was an IO Error reading the deploy file "
							+ deploy + ". Traceback:\n" + ioe);
			return;
		}
		config = ini.get(serviceName);
		if (config == null) {
			config = new HashMap<String, String>();
			sysLogger.log(LOG_LEVEL_ERR, getClass().getName(), "The configuration file " + deploy + 
							" has no section " + serviceName);
		}
	}

	/**
	 * WARNING! Please use this method for testing purposes only.
	 * @param output
	 */
	public void changeSyslogOutput(JsonServerSyslog.SyslogOutput output) {
		sysLogger.changeOutput(output);
		userLogger.changeOutput(output);
	}
	
	public void logErr(String message) {
		logErr(new Exception(message), findCaller());
	}

	public void logErr(Throwable err) {
		logErr(err, findCaller());
	}
	
	private void logErr(Throwable err, String caller) {
		List<String> messages = new ArrayList<String>();
		StackTraceElement[] st = err.getStackTrace();
		int firstPos = 0;
		String packageName = "us.kbase";
		String className = JsonServerServlet.class.getName();
		for (; firstPos < st.length; firstPos++) {
			if (st[firstPos].getClassName().equals(className))
				continue;
			break;
		}
		int lastPos = st.length - 1;
		for (; lastPos > firstPos; lastPos--) {
			if (st[lastPos].getClassName().startsWith(packageName) && 
					!st[lastPos].getClassName().equals(className))
				break;
		}
		messages.add("Traceback (most recent call last):");
		for (int pos = lastPos; pos >= firstPos; pos--) {
			messages.add("Class \"" + st[pos].getClassName() + "\", file \"" + st[pos].getFileName() + 
					"\", line " + st[pos].getLineNumber() + ", in " + st[pos].getMethodName());
		}
		String errorPrefix = err.getClass().equals(Exception.class) ? "Error: " :
			(err.getClass().getName() + ": ");
		messages.add(errorPrefix + err.getMessage());
		userLogger.log(LOG_LEVEL_ERR, caller, messages.toArray(new String[messages.size()]));
	}
	
	public void logInfo(String message) {
		userLogger.log(LOG_LEVEL_INFO, findCaller(), message);
	}
	
	public void logDebug(String message) {
		userLogger.log(LOG_LEVEL_DEBUG, findCaller(), message);
	}
	
	public void logDebug(String message, int debugLevelFrom1to3) {
		if (debugLevelFrom1to3 < 1 || debugLevelFrom1to3 > 3)
			throw new IllegalStateException("Wrong debug log level, it should be between 1 and 3");
		userLogger.log(LOG_LEVEL_DEBUG + (debugLevelFrom1to3 - 1), findCaller(), message);
	}

	public static String findCaller() {
		StackTraceElement[] st = Thread.currentThread().getStackTrace();
		String packageName = "us.kbase";
		String className = JsonServerServlet.class.getName();
		for (int pos = 0; pos < st.length; pos++) {
			if (st[pos].getClassName().equals(className) ||
					!st[pos].getClassName().startsWith(packageName))
				continue;
			return st[pos].getClassName();
		}
		throw new IllegalStateException();
	}
	
	public int getLogLevel() {
		return userLogger.getLogLevel();
	}
	
	public void setLogLevel(int level) {
		userLogger.setLogLevel(level);
	}
	
	public void clearLogLevel() {
		userLogger.clearLogLevel();
	}
	
	public boolean isLogDebugEnabled() {
		return userLogger.getLogLevel() >= LOG_LEVEL_DEBUG;
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType(APP_JSON);
		OutputStream output	= response.getOutputStream();
		getCurrentRpcInfo().reset();
		if (startupFailed) {
			writeError(response, -32603, "The server did not start up properly. Please check the log files for the cause.", output);
			return;
		}
		writeError(response, -32300, "HTTP GET not allowed.", output);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType(APP_JSON);
		OutputStream output	= response.getOutputStream();
		RpcInfo info = getCurrentRpcInfo().reset();
		String rpcName = null;
		AuthToken userProfile = null;
		try {
			InputStream input = request.getInputStream();
			JsonNode node;
			try {
				node = mapper.readTree(new UnclosableInputStream(input));
			} catch (Exception ex) {
				writeError(response, -32700, "Parse error (" + ex.getMessage() + ")", output);
				return;
			}
			JsonNode idNode = node.get("id");
			try {
				info.id = idNode == null || node.isNull() ? null : idNode.asText();
			} catch (Exception ex) {}
			JsonNode methodNode = node.get("method");
			ArrayNode paramsNode = (ArrayNode)node.get("params");
			rpcName = (methodNode!=null && !methodNode.isNull()) ? methodNode.asText() : null;
			if (rpcName.contains(".")) {
				int pos = rpcName.indexOf('.');
				info.module = rpcName.substring(0, pos);
				info.method = rpcName.substring(pos + 1);
			} else {
				info.method = rpcName;
			}
			Method rpcMethod = rpcCache.get(rpcName);
			if (rpcMethod == null) {
				writeError(response, -32601, "Can not find method [" + rpcName + "] in server class " + getClass().getName(), output);
				return;
			}
			int rpcArgCount = rpcMethod.getGenericParameterTypes().length;
			Object[] methodValues = new Object[rpcArgCount];			
			if (rpcArgCount > 0 && rpcMethod.getParameterTypes()[rpcArgCount - 1].equals(AuthToken.class)) {
				String token = request.getHeader("Authorization");
				if (token != null || !rpcMethod.getAnnotation(JsonServerMethod.class).authOptional()) {
					try {
						userProfile = validateToken(token);
						if (userProfile != null)
							info.user = userProfile.getClientId();
					} catch (Throwable ex) {
						writeError(response, -32400, "Token validation failed: " + ex.getMessage(), output);
						return;
					}
				}
				rpcArgCount--;
			}
			if (startupFailed) {
				writeError(response, -32603, "The server did not start up properly. Please check the log files for the cause.", output);
				return;
			}
			if (paramsNode.size() != rpcArgCount) {
				writeError(response, -32602, "Wrong parameter count for method " + rpcName, output);
				return;
			}
			for (int typePos = 0; typePos < paramsNode.size(); typePos++) {
				JsonNode jsonData = paramsNode.get(typePos);
				Type paramType = rpcMethod.getGenericParameterTypes()[typePos];
				PlainTypeRef paramJavaType = new PlainTypeRef(paramType);
				try {
					methodValues[typePos] = mapper.readValue(mapper.treeAsTokens(jsonData), paramJavaType);
				} catch (Exception ex) {
					writeError(response, -32602, "Wrong type of parameter " + typePos + " for method " + rpcName + " (" + ex.getMessage() + ")", output);	
					return;
				}
			}
			if (userProfile != null && methodValues[methodValues.length - 1] == null)
				methodValues[methodValues.length - 1] = userProfile;
			Object result;
			try {
				sysLogger.log(LOG_LEVEL_INFO, getClass().getName(), "start method");
				result = rpcMethod.invoke(this, methodValues);
				sysLogger.log(LOG_LEVEL_INFO, getClass().getName(), "end method");
			} catch (Throwable ex) {
				if (ex instanceof InvocationTargetException && ex.getCause() != null) {
					ex = ex.getCause();
				}
				logErr(ex, getClass().getName());
				writeError(response, -32500, ex, output);
				return;
			}
			boolean isTuple = rpcMethod.getAnnotation(JsonServerMethod.class).tuple();
			if (!isTuple) {
				result = Arrays.asList(result);
			}
			ObjectNode ret = mapper.createObjectNode();
			ret.put("version", "1.1");
			ret.put("result", mapper.valueToTree(result));
			mapper.writeValue(new UnclosableOutputStream(output), ret);
			output.flush();
		} catch (Exception ex) {
			writeError(response, -32400, "Unexpected internal error (" + ex.getMessage() + ")", output);	
		}
	}
	
	public static RpcInfo getCurrentRpcInfo() {
		RpcInfo ret = rpcInfo.get();
		if (ret == null) {
			ret = new RpcInfo();
			rpcInfo.set(ret);
		}
		return ret;
	}
	
	private static AuthToken validateToken(String token) throws Exception {
		if (token == null)
			throw new IllegalStateException("Token is not defined in http request header");
		AuthToken ret = new AuthToken(token);
		if (!AuthService.validateToken(ret)) {
			throw new IllegalStateException("Token was not validated");
		}
		return ret;
	}

	public static AuthUser getUserProfile(AuthToken token) throws KeyManagementException, UnsupportedEncodingException, NoSuchAlgorithmException, IOException, AuthException {
		return AuthService.getUserFromToken(token);
	}
	
	private void writeError(HttpServletResponse response, int code, String message, OutputStream output) {
		writeError(response, code, message, null, output);
	}
	
	private void writeError(HttpServletResponse response, int code, Throwable ex, OutputStream output) {
		StringWriter sw = new StringWriter();
		ex.printStackTrace(new PrintWriter(sw));
		String errorMessage = ex.getLocalizedMessage();
		if (errorMessage == null)
			errorMessage = ex.getMessage();
		writeError(response, code, ex.getLocalizedMessage(), sw.toString(), output);
	}
	
	private void writeError(HttpServletResponse response, int code, String message, String data, OutputStream output) {
		response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		ObjectNode ret = mapper.createObjectNode();
		ObjectNode error = mapper.createObjectNode();
		error.put("name", "JSONRPCError");
		error.put("code", code);
		error.put("message", message);
		error.put("error", data);
		ret.put("version", "1.1");
		ret.put("error", error);
		String id = getCurrentRpcInfo().getId();
		if (id != null)
			ret.put("id", id);
		try {
			ByteArrayOutputStream bais = new ByteArrayOutputStream();
			mapper.writeValue(bais, ret);
			bais.close();
			byte[] bytes = bais.toByteArray();
			String logMessage = new String(bytes);
			sysLogger.log(LOG_LEVEL_ERR, getClass().getName(), logMessage);
			output.write(bytes);
			output.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static class PlainTypeRef extends TypeReference<Object> {
		Type type;
		PlainTypeRef(Type type) {
			this.type = type;
		}
		
		@Override
		public Type getType() {
			return type;
		}
	}
	
	private static class UnclosableInputStream extends InputStream {
		private InputStream inner;
		private boolean isClosed = false;
		
		public UnclosableInputStream(InputStream inner) {
			this.inner = inner;
		}
		
		@Override
		public int read() throws IOException {
			if (isClosed)
				return -1;
			return inner.read();
		}
		
		@Override
		public int available() throws IOException {
			if (isClosed)
				return 0;
			return inner.available();
		}
		
		@Override
		public void close() throws IOException {
			isClosed = true;
		}
		
		@Override
		public synchronized void mark(int readlimit) {
			inner.mark(readlimit);
		}
		
		@Override
		public boolean markSupported() {
			return inner.markSupported();
		}
		
		@Override
		public int read(byte[] b) throws IOException {
			if (isClosed)
				return 0;
			return inner.read(b);
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (isClosed)
				return 0;
			return inner.read(b, off, len);
		}
		
		@Override
		public synchronized void reset() throws IOException {
			if (isClosed)
				return;
			inner.reset();
		}
		
		@Override
		public long skip(long n) throws IOException {
			if (isClosed)
				return 0;
			return inner.skip(n);
		}
	}
	
	private static class UnclosableOutputStream extends OutputStream {
		OutputStream inner;
		boolean isClosed = false;
		
		public UnclosableOutputStream(OutputStream inner) {
			this.inner = inner;
		}
		
		@Override
		public void write(int b) throws IOException {
			if (isClosed)
				return;
			inner.write(b);
		}
		
		@Override
		public void close() throws IOException {
			isClosed = true;
		}
		
		@Override
		public void flush() throws IOException {
			inner.flush();
		}
		
		@Override
		public void write(byte[] b) throws IOException {
			if (isClosed)
				return;
			inner.write(b);
		}
		
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (isClosed)
				return;
			inner.write(b, off, len);
		}
	}
	
	public static class RpcInfo {
		private String id;
		private String module;
		private String method;
		private String user;
		
		private RpcInfo reset() {
			id = null;
			module = null;
			method = null;
			user = null;
			return this;
		}
		
		public String getId() {
			return id;
		}
		
		public String getModule() {
			return module;
		}
		
		public String getMethod() {
			return method;
		}
		
		public String getUser() {
			return user;
		}
	}
}
