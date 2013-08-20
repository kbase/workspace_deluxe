package us.kbase;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.type.TypeReference;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ini4j.Ini;
import org.productivity.java.syslog4j.Syslog;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.SyslogIF;

public class JsonServerServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String APP_JSON = "application/json";
	private ObjectMapper mapper;
	private Map<String, Method> rpcCache;
	private SyslogIF log;
	private int maxLogLevel = LOG_LEVEL_INFO;
	public static final int LOG_LEVEL_ERR = SyslogConstants.LEVEL_ERROR;
	public static final int LOG_LEVEL_INFO = SyslogConstants.LEVEL_INFO;
	public static final int LOG_LEVEL_DEBUG = SyslogConstants.LEVEL_DEBUG;
	public static final int LOG_LEVEL_DEBUG2 = SyslogConstants.LEVEL_DEBUG + 1;
	public static final int LOG_LEVEL_DEBUG3 = SyslogConstants.LEVEL_DEBUG + 2;
	private static ThreadLocal<SimpleDateFormat> sdf = new ThreadLocal<SimpleDateFormat>();
	private static final boolean logDateTime = false;
	final private static String KB_DEP = "KB_DEPLOYMENT_CONFIG";
	final private static String KB_SERVNAME = "KB_SERVICE_NAME";
	protected Map<String, String> config = new HashMap<String, String>();
		
	public void startupServer(int port) throws Exception {
		Server server = new Server(port);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(this),"/*");
        server.start();
        server.join();
	}
	
	public JsonServerServlet() {
		//this.log = Logger.getLogger(this.getClass());
		this.log = Syslog.getInstance("unix_syslog");
		this.log.getConfig().setFacility(SyslogConstants.FACILITY_LOCAL1);
		this.mapper = new ObjectMapper().withModule(new JacksonTupleModule());
		this.rpcCache = new HashMap<String, Method>();
		for (Method m : getClass().getMethods()) {
			if (m.isAnnotationPresent(JsonServerMethod.class)) {
				JsonServerMethod ann = m.getAnnotation(JsonServerMethod.class);
				rpcCache.put(ann.rpc(), m);
			}
		}
		
		//read the config file
		String file = System.getenv(KB_DEP);
		if (file == null) {
			return;
		}
		File deploy = new File(file);
		Ini ini = null;
		try {
			ini = new Ini(deploy);
		} catch (IOException ioe) {
			this.logErr("There was an IO Error reading the deploy file "
							+ deploy + ". Traceback:\n" + ioe);
			return;
		}
		String name = System.getenv(KB_SERVNAME);
		if (name == null) {
			this.logErr("Deploy config file " + deploy + " exists but no " + 
							KB_SERVNAME + " is provided in the environment");
			return;
		}
		config = ini.get(name);
		if (config == null) {
			config = new HashMap<String, String>();
			this.logErr("The configuration file " + deploy + 
							" has no section " + name);
		}
		
	}
	
	private void log(int level, String message) {
		if (level > maxLogLevel)
			return;
		if (level > LOG_LEVEL_DEBUG)
			level = LOG_LEVEL_DEBUG;
		String levelText = level == LOG_LEVEL_ERR ? "ERR" : (level == LOG_LEVEL_INFO ? "INFO" : "DEBUG");
		message = "[" + this.getClass().getSimpleName() + "] [" + levelText + "] [" + getCurrentMicro() + "] " + message;
		if (logDateTime)
			message = getDateFormat().format(new Date()) + " " + message;
		log.log(level, message);
		log.flush();
	}

	public void logErr(String message) {
		log(LOG_LEVEL_ERR, message);
	}
	
	public void logInfo(String message) {
		log(LOG_LEVEL_INFO, message);
	}
	
	public void logDebug(String message) {
		log(LOG_LEVEL_DEBUG, message);
	}
	
	public void logDebug(String message, int debugLevelFrom1to3) {
		if (debugLevelFrom1to3 < 1 || debugLevelFrom1to3 > 3)
			throw new IllegalStateException("Wrong debug log level, it should be between 1 and 3");
		log(LOG_LEVEL_DEBUG + (debugLevelFrom1to3 - 1), message);
	}

	public int getLogLevel() {
		return maxLogLevel;
	}
	
	public void setLogLevel(int level) {
		maxLogLevel = level;
	}
	
	public void clearLogLevel() {
		maxLogLevel = LOG_LEVEL_INFO;
	}
	
	public boolean isLogDebugEnabled() {
		return maxLogLevel >= LOG_LEVEL_DEBUG;
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType(APP_JSON);
		OutputStream output	= response.getOutputStream();
		writeError(response, -32300, "HTTP GET not allowed.", null, output, null, null);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType(APP_JSON);
		OutputStream output	= response.getOutputStream();
		String id = null;
		String rpcName = null;
		AuthToken userProfile = null;
		try {
			InputStream input = request.getInputStream();
			JsonNode node;
			try {
				node = mapper.readTree(new UnclosableInputStream(input));
			} catch (Exception ex) {
				writeError(response, -32700, "Parse error (" + ex.getMessage() + ")", null, output, null, null);
				return;
			}
			JsonNode idNode = node.get("id");
			try {
				id = idNode == null || node.isNull() ? null : idNode.asText();
			} catch (Exception ex) {}
			JsonNode methodNode = node.get("method");
			ArrayNode paramsNode = (ArrayNode)node.get("params");
			rpcName = (methodNode!=null && !methodNode.isNull()) ? methodNode.asText() : null;
			Method rpcMethod = rpcCache.get(rpcName);
			if (rpcMethod == null) {
				writeError(response, -32601, "Can not find method [" + rpcName + "] in server class " + getClass().getName(), id, output, null, rpcName);
				return;
			}
			int rpcArgCount = rpcMethod.getGenericParameterTypes().length;
			Object[] methodValues = new Object[rpcArgCount];			
			if (rpcArgCount > 0 && rpcMethod.getParameterTypes()[rpcArgCount - 1].equals(AuthToken.class)) {
				String token = request.getHeader("Authorization");
				if (token != null || !rpcMethod.getAnnotation(JsonServerMethod.class).authOptional()) {
					try {
						userProfile = validateToken(token);
					} catch (Throwable ex) {
						writeError(response, -32400, "Error during authorization check (" + ex.getMessage() + ")", id, output, userProfile, rpcName);
						return;
					}
				}
				rpcArgCount--;
			}
			if (paramsNode.size() != rpcArgCount) {
				writeError(response, -32602, "Wrong parameter count for method " + rpcName, null, output, userProfile, rpcName);
				return;
			}
			for (int typePos = 0; typePos < paramsNode.size(); typePos++) {
				JsonNode jsonData = paramsNode.get(typePos);
				Type paramType = rpcMethod.getGenericParameterTypes()[typePos];
				PlainTypeRef paramJavaType = new PlainTypeRef(paramType);
				try {
					methodValues[typePos] = mapper.readValue(jsonData, paramJavaType);
				} catch (Exception ex) {
					writeError(response, -32602, "Wrong type of parameter " + typePos + " for method " + rpcName + " (" + ex.getMessage() + ")", id, output, userProfile, rpcName);	
					return;
				}
			}
			if (userProfile != null && methodValues[methodValues.length - 1] == null)
				methodValues[methodValues.length - 1] = userProfile;
			Object result;
			try {
				String user = userProfile == null ? "-" : userProfile.getClientId();
				logInfo("[" + user + "] [" + rpcName + "]: start method, id: " + id);
				result = rpcMethod.invoke(this, methodValues);
				logInfo("[" + user + "] [" + rpcName + "]: end method, id: " + id);
			} catch (Throwable ex) {
				if (ex instanceof InvocationTargetException && ex.getCause() != null) {
					ex = ex.getCause();
				}
				StackTraceElement errPoint = ex.getStackTrace()[0];
				writeError(response, -32500, "Error while executing method " + rpcName + " (" + errPoint.getClassName() + ":" + 
				errPoint.getLineNumber() + " - " + ex.getMessage() + ")", id, output, userProfile, rpcName);	
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
			writeError(response, -32400, "Unexpected internal error (" + ex.getMessage() + ")", id, output, userProfile, rpcName);	
		}
	}

	private static SimpleDateFormat getDateFormat() {
		SimpleDateFormat ret = sdf.get();
		if (ret == null) {
			ret = new SimpleDateFormat("MMM d HH:mm:ss");
			sdf.set(ret);
		}
		return ret;
	}
	
	private static String getCurrentMicro() {
		String ret = "" + System.currentTimeMillis() + "000000";
		return ret.substring(0, ret.length() - 9) + "." + ret.substring(ret.length() - 9, ret.length() - 3);
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
	
	private void writeError(HttpServletResponse response, int code, String message, String id, OutputStream output, AuthToken userProfile, String method) {
		response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		ObjectNode ret = mapper.createObjectNode();
		ObjectNode error = mapper.createObjectNode();
		error.put("name", "JSONRPCError");
		error.put("code", code);
		error.put("message", message);
		ret.put("version", "1.1");
		ret.put("error", error);
		if (id != null)
			ret.put("id", id);
		try {
			ByteArrayOutputStream bais = new ByteArrayOutputStream();
			mapper.writeValue(bais, ret);
			bais.close();
			byte[] bytes = bais.toByteArray();
			String user = userProfile == null ? null : userProfile.getClientId();
			String logMessage = "[" + (user == null ? "-" : user) + "] [" + (method == null ? "-" : method) + "]: " + new String(bytes);
			logErr(logMessage);
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
}
