package us.kbase.workspace.kbase;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import us.kbase.common.mongo.GetMongoDB;

public class AppEventListener implements ServletContextListener {
	
	@Override
	public void contextInitialized(ServletContextEvent arg0) {
		// do nothing
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		// I don't think this does anything any more. Originally this fixed restarting the
		// workspace app in Glassfish and having the old mongo connections retained (e.g.
		// a resource leak).
		// Now we're in dockerized tomcat and so we never restart the app.
		// Even if we wanted to, it's not clear how we could get a handle to the current
		// connections and close them.
		GetMongoDB.closeAllConnections();
	}
}