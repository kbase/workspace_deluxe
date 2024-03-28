package us.kbase.workspace.docserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.productivity.java.syslog4j.SyslogIF;

import com.google.common.io.Files;

import us.kbase.common.service.JsonServerServlet;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.JsonServerSyslog.RpcInfo;
import us.kbase.common.service.JsonServerSyslog.SyslogOutput;

/** A document server that serves documentation for another service.
 * This code is configured for the Workspace service, but is easy to configure
 * for other services by changing DEFAULT_COMPANION_SERVICE_NAME.
 * @author gaprice@lbl.gov
 *
 */
public class DocServer extends HttpServlet {
	
	/** 
	 * The name of the service that this document server is serving documents
	 * for. This name will be used to find the appropriate section of the
	 * KBase deploy.cfg configuration file.
	 */
	public static final String COMPANION_SERVICE_NAME = "Workspace";
	// TODO CONFIG configure directly vs going through JsonSererServlet

	/**
	 * The name of this document server, used for logging purposes.
	 */
	public static final String DEFAULT_SERVICE_NAME = "DocServ";
	/** 
	 * Location of the documents this service will serve in relation to the
	 * classpath.
	 */
	public static final String DEFAULT_DOCS_LOC = "/server_docs";
	
	private static final String CFG_SERVICE_NAME = "doc-server-name";
	private static final String CFG_DOCS_LOC = "doc-server-docs-location";
	
	private static final String X_FORWARDED_FOR = "X-Forwarded-For";
	private static final String USER_AGENT = "User-Agent";
	
	private static final FileNameMap FILE_NAME_MAP = URLConnection.getFileNameMap();
	
	private static final String DONT_TRUST_X_IP_HEADERS = "dont_trust_x_ip_headers";
	private static final String DONT_TRUST_X_IP_HEADERS2 = "dont-trust-x-ip-headers";
	private static final String STRING_TRUE = "true";
	
	private final String docsLoc;
	private final JsonServerSyslog logger;
	
	private static String defaultDocsLoc = DEFAULT_DOCS_LOC;
	private static SyslogOutput sysLogOut = null;
	private static final String SERVER_CONTEXT_LOC = "/docs/*";
	private Integer jettyPort = null;
	private Server jettyServer = null;
	private final boolean trustX_IPHeaders;
	private static final long serialVersionUID = 1L;
	

	// could make custom 404 page at some point
	// http://www.eclipse.org/jetty/documentation/current/custom-error-pages.html
	
	/**
	 * Creates a new document server
	 */
	public DocServer() {
		//TODO JERSEY switch to a jersey endpoint when that's available, ditch logger, etc. Pretty big rewrite/simplification
		super();
		JsonServerSyslog.setStaticUseSyslog(false);
		final JsonServerSyslog templogger = getLogger(COMPANION_SERVICE_NAME, sysLogOut);

		// getConfig() gets the service name from the env if it exists which is bad
		// since the Workspace doesn't. Need to redo configuration handling at some point
		final Map<String, String> config = JsonServerServlet.getConfig(
				COMPANION_SERVICE_NAME, templogger);
		
		String serverName = config.get(CFG_SERVICE_NAME);
		if (serverName == null || serverName.isEmpty()) {
			serverName = DEFAULT_SERVICE_NAME;
		} 
		final String dlog = config.get(CFG_DOCS_LOC);
		if (dlog == null || dlog.isEmpty()) {
			docsLoc = defaultDocsLoc;
		} else {
			if (!dlog.startsWith("/")) {
				docsLoc = "/" + dlog;
			} else {
				docsLoc = dlog;
			}
		}
		logger = getLogger(serverName, sysLogOut);
		this.trustX_IPHeaders =
				!STRING_TRUE.equals(config.get(DONT_TRUST_X_IP_HEADERS)) &&
				!STRING_TRUE.equals(config.get(DONT_TRUST_X_IP_HEADERS2));
	}
	
	private JsonServerSyslog getLogger(
			final String serverName,
			final SyslogOutput output) {
		final JsonServerSyslog logger = new JsonServerSyslog(
				serverName, JsonServerServlet.KB_DEP, JsonServerSyslog.LOG_LEVEL_INFO, false);
		if (output == null) {
			logger.changeOutput(new SyslogOutput() {
				// this is manually tested
				@Override
				public void logToSystem(
						final SyslogIF log,
						final int level,
						final String message) {
					System.out.println(message);
				}
			});
		} else {
			logger.changeOutput(output);
		}
		return logger;
	}
	
	@Override
	protected void doOptions(
			final HttpServletRequest request,
			final HttpServletResponse response)
			throws ServletException, IOException {
		JsonServerServlet.setupResponseHeaders(request, response);
		response.setContentLength(0);
		response.getOutputStream().print("");
		response.getOutputStream().flush();
	}
	
	@Override
	protected void doGet(
			final HttpServletRequest request,
			final HttpServletResponse response)
			throws ServletException, IOException {
		
		final RpcInfo rpc = JsonServerSyslog.getCurrentRpcInfo();
		rpc.setId(("" + Math.random()).substring(2));
		rpc.setIp(JsonServerServlet.getIpAddress(request, trustX_IPHeaders));
		rpc.setMethod("GET");
		logHeaders(request);
	
		String path = request.getPathInfo();
		
		if (path == null) { // e.g. /docs
			handle404(request, response);
			return;
		}
		if (path.endsWith("/")) { // e.g. /docs/
			path = path + "index.html";
		}
		// the path is already normalized by the framework, so no need to
		// normalize here
		path = docsLoc + path;
		final InputStream is = getClass().getResourceAsStream(path);
		if (is == null) {
			handle404(request, response);
			return;
		}
		try {
			response.setHeader("Content-Type", getMimeType(path));
			final byte[] page = IOUtils.toByteArray(is);
			response.getOutputStream().write(page);
		} catch (IOException ioe) {
			logger.logErr(request.getRequestURI() + " 500 " + request.getHeader(USER_AGENT));
			logger.logErr(ioe);
			response.sendError(500);
			return;
		}
		logger.logInfo(request.getRequestURI() + " 200 " + request.getHeader(USER_AGENT));
	}

	private String getMimeType(final String path) {
		final String ct = FILE_NAME_MAP.getContentTypeFor(path);
		if (ct == null) {
			// kind of unbelievable js and css aren't covered. If many more types are needed
			// in the future (unlikely) maybe push own file map into URLConnection. YAGNI for now.
			final String ext = Files.getFileExtension(path);
			if (ext.equals("js")) {
				return "text/javascript";
			}
			if (ext.equals("css")) {
				return "text/css";
			}
			return "application/octet-stream";
		} else {
			return ct;
		}
	}

	private void handle404(final HttpServletRequest request,
			final HttpServletResponse response) throws IOException {
		logger.logErr(request.getRequestURI() + " 404 " + request.getHeader(USER_AGENT));
		response.sendError(404);
	}
	
	
	private void logHeaders(final HttpServletRequest req) {
		final String xFF = req.getHeader(X_FORWARDED_FOR);
		if (xFF != null && !xFF.isEmpty()) {
			logger.logInfo(X_FORWARDED_FOR + ": " + xFF);
		}
	}
	
	/** Test method to test logging. Call before creating a server.
	* @param output where logger output is to be sent.
	 */
	public static void setLoggerOutput(final SyslogOutput output) {
		sysLogOut = output;
	}
	
	/**
	 * Location of the documents this service will serve in relation to the
	 * classpath. Call before creating a server.
	 * @param path documents location
	 */
	public static void setDefaultDocsLocation(final String path) {
		defaultDocsLoc = path;
	}
	
	/**
	 * Starts a test jetty doc server on an OS-determined port at /docs. Blocks
	 * until the server is terminated.
	 * @throws Exception if the server couldn't be started.
	 */
	public void startupServer() throws Exception {
		startupServer(0);
	}
	
	/**
	 * Starts a test jetty doc server at /docs. Blocks until the
	 * server is terminated.
	 * @param port the port to which the server will connect.
	 * @throws Exception if the server couldn't be started.
	 */
	public void startupServer(int port) throws Exception {
		jettyServer = new Server(port);
		ServletContextHandler context =
				new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		jettyServer.setHandler(context);
		context.addServlet(new ServletHolder(this), SERVER_CONTEXT_LOC);
		jettyServer.start();
		jettyPort = jettyServer.getConnectors()[0].getLocalPort();
		jettyServer.join();
	}
	
	/**
	 * Get the jetty test server port. Returns null if the server is not
	 * running or starting up.
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
	
	public static void main(String[] args) throws Exception {
		new DocServer().startupServer(10000);
	}
	
}
