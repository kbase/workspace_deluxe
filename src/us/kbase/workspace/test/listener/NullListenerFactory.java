package us.kbase.workspace.test.listener;

import java.util.Map;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.listener.ListenerInitializationException;
import us.kbase.workspace.listener.WorkspaceEventListener;
import us.kbase.workspace.listener.WorkspaceEventListenerFactory;

/** A trivial example of a listener implementation.
 * @author gaprice@lbl.gov
 *
 */
public class NullListenerFactory implements WorkspaceEventListenerFactory {

	@Override
	public WorkspaceEventListener configure(final Map<String, String> cfg)
			throws ListenerInitializationException {
		final boolean printEvents = "true".equals(cfg.get("printEvents"));
		if ("true".equals(cfg.get("throwException"))) {
			throw new ListenerInitializationException("But you told me to! It's so not fair!");
		}
		return new NullEventListener(printEvents);
	}
	
	public static class NullEventListener implements WorkspaceEventListener {
		
		private final boolean printEvents;
		
		private NullEventListener(final boolean printEvents) {
			this.printEvents = printEvents;
		}
		
		private void print(final String s) {
			if (printEvents) {
				System.out.println(s);
			}
		}

		@Override
		public void createWorkspace(long id) {
			print("createWorkspace " + id);
		}

		@Override
		public void cloneWorkspace(long id) {
			print("cloneWorkspace " + id);
		}

		@Override
		public void setWorkspaceMetadata(long id) {
			print("setWorkspaceMetadata " + id);
		}

		@Override
		public void lockWorkspace(long id) {
			print("lockWorkspace " + id);
		}

		@Override
		public void renameWorkspace(long id, String newName) {
			print("renameWorkspace " + id + " " + newName);
		}

		@Override
		public void setGlobalPermission(long id, Permission permission) {
			print("setGlobalPermission " + id + " " + permission);
		}

	}

}
