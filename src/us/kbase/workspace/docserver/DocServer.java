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

public class DocServer extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(
			final HttpServletRequest request,
			final HttpServletResponse response)
			throws ServletException, IOException {
		//TODO needs logging
		
		String path = request.getPathInfo();
		if (path.equals("/")) {
			path = "/index.html";
		}
		path = "" + path; //TODO add resources dir
		System.out.println("path: " + path);
		final InputStream is = getClass().getResourceAsStream(path);
		if (is == null) {
			response.sendError(404, path); //path here
			return;
		}
		final byte[] page = IOUtils.toByteArray(is);
		System.out.println("page length: " + page.length);
		
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
