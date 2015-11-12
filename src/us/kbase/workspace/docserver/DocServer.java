package us.kbase.workspace.docserver;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

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
		
		
		response.getOutputStream().println(request.getPathInfo());
		
		
		
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
