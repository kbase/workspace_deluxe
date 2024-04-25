package us.kbase.test.workspace;

import us.kbase.workspace.WorkspaceServer;

public class WorkspaceServerThread extends Thread {
	private WorkspaceServer server;
	
	public WorkspaceServerThread(final WorkspaceServer server) {
		this.server = server;
	}
	
	public void run() {
		try {
			server.startupServer();
		} catch (Exception e) {
			System.err.println("Can't start server:");
			e.printStackTrace();
		}
	}
}
