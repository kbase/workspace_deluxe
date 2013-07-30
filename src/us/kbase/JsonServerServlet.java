package us.kbase;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.AuthUser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
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

public class JsonServerServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String APP_JSON = "application/json";
	private ObjectMapper mapper;
	private Map<String, Method> rpcCache;
	private static Map<String, AuthUser> token2user = Collections.synchronizedMap(new HashMap<String, AuthUser>());

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
			this.logError("There was an IO Error reading the deploy file "
							+ deploy + ". Traceback:\n" + ioe);
			return;
		}
		String name = System.getenv(KB_SERVNAME);
		if (name == null) {
			this.logError("Deploy config file " + deploy + " exists but no " + 
							KB_SERVNAME + " is provided in the environment");
			return;
		}
		config = ini.get(name);
		if (config == null) {
			config = new HashMap<String, String>();
			this.logError("The configuration file " + deploy + 
							" has no section " + name);
		}
		
	}
	
	// temporary until logging is dealt with
	private void logError(String error) {
		System.err.println(error);
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType(APP_JSON);
		OutputStream output	= response.getOutputStream();
		writeError(response, -32300, "HTTP GET not allowed.", null, output);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType(APP_JSON);
		OutputStream output	= response.getOutputStream();
		String id = null;
		try {
			InputStream input = request.getInputStream();
			JsonNode node;
			try {
				node = mapper.readTree(new UnclosableInputStream(input));
			} catch (Exception ex) {
				writeError(response, -32700, "Parse error (" + ex.getMessage() + ")", null, output);
				return;
			}
			JsonNode idNode = node.get("id");
			try {
				id = idNode == null || node.isNull() ? null : idNode.asText();
			} catch (Exception ex) {}
			JsonNode methodNode = node.get("method");
			ArrayNode paramsNode = (ArrayNode)node.get("params");
			String rpcName	= (methodNode!=null && !methodNode.isNull()) ? methodNode.asText() : null;
			Method rpcMethod = rpcCache.get(rpcName);
			if (rpcMethod == null) {
				writeError(response, -32601, "Can not find method [" + rpcName + "] in server class " + getClass().getName(), id, output);
				return;
			}
			int rpcArgCount = rpcMethod.getGenericParameterTypes().length;
			Object[] methodValues = new Object[rpcArgCount];			
			AuthUser userProfile = null;
			if (rpcArgCount > 0 && rpcMethod.getParameterTypes()[rpcArgCount - 1].equals(AuthUser.class)) {
				String token = request.getHeader("Authorization");
				userProfile = token == null ? null : token2user.get(token);
				if (userProfile == null) {
					if (token != null || !rpcMethod.getAnnotation(JsonServerMethod.class).authOptional()) {
						try {
							userProfile = loadUserProfile(token);
						} catch (Throwable ex) {
							writeError(response, -32400, "Error during authorization check (" + ex.getMessage() + ")", id, output);
							return;
						}
						token2user.put(token, userProfile);
					}
				}
				rpcArgCount--;
			}
			if (paramsNode.size() != rpcArgCount) {
				writeError(response, -32602, "Wrong parameter count for method " + rpcName, null, output);
				return;
			}
			for (int typePos = 0; typePos < paramsNode.size(); typePos++) {
				JsonNode jsonData = paramsNode.get(typePos);
				Type paramType = rpcMethod.getGenericParameterTypes()[typePos];
				PlainTypeRef paramJavaType = new PlainTypeRef(paramType);
				try {
					methodValues[typePos] = mapper.readValue(jsonData, paramJavaType);
				} catch (Exception ex) {
					writeError(response, -32602, "Wrong type of parameter " + typePos + " for method " + rpcName + " (" + ex.getMessage() + ")", id, output);	
					return;
				}
			}
			if (userProfile != null && methodValues[methodValues.length - 1] == null)
				methodValues[methodValues.length - 1] = userProfile;
			Object result;
			try {
				result = rpcMethod.invoke(this, methodValues);
			} catch (Throwable ex) {
				if (ex instanceof InvocationTargetException && ex.getCause() != null) {
					ex = ex.getCause();
				}
				writeError(response, -32500, "Error while executing method " + rpcName + " (" + ex.getMessage() + ")", id, output);	
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
			writeError(response, -32400, "Unexpected internal error (" + ex.getMessage() + ")", id, output);	
		}
	}

	
	private static AuthUser loadUserProfile(String token) throws Exception {
		if (token == null)
			throw new IllegalStateException("Token is not defined in http request header");
		return AuthService.getUserFromToken(new AuthToken(token));
	}
	
	private void writeError(HttpServletResponse response, int code, String message, String id, OutputStream output) {
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
			mapper.writeValue(new UnclosableOutputStream(output), ret);
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
