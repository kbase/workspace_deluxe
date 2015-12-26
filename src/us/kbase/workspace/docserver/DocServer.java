package us.kbase.workspace.docserver;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import us.kbase.common.service.JsonServerSyslog;

public class DocServer extends HttpServlet {
	
	private static final String DOCS_LOC = "/workspace_docs";
	private static final String SERVICE_NAME = "WorkspaceDocServ";

	private final JsonServerSyslog logger;
	private static final long serialVersionUID = 1L;
	
	public DocServer() {
		logger = new JsonServerSyslog(SERVICE_NAME, null);
	}
	
	@Override
	protected void doGet(
			final HttpServletRequest request,
			final HttpServletResponse response)
			throws ServletException, IOException {
		//TODO needs logging
		//TODO IP check
		//TODO stop listing files in dir if there's no file
		// not totally sure if requiring a server restart to update docs
		// is the best idea... on the other hand it makes things very simple
		// deploy wise
		
		String path = request.getPathInfo();
		
		if (path == null) { // for /docs
			response.sendError(404);
			return;
		}
		if (path.endsWith("/")) { // for /docs/
			path = path + "index.html";
		}
		path = DOCS_LOC + path;
		final InputStream is = getClass().getResourceAsStream(path);
		if (is == null) {
			response.sendError(404, path.replace(DOCS_LOC, ""));
			return;
		}
		final byte[] page = IOUtils.toByteArray(is);
		
		response.getOutputStream().write(page);
	}
	
	
	public void startupServer(int port) throws Exception {
		Server jettyServer = new Server(port);
		ServletContextHandler context =
				new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		jettyServer.setHandler(context);
		context.addServlet(new ServletHolder(this),"/docs/*");
		jettyServer.start();
		jettyServer.join();
	}
	
	public static void main(String[] args) throws Exception {
		new DocServer().startupServer(10000);
	}
	
}
