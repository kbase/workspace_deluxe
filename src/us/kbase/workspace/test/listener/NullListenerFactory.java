package us.kbase.workspace.test.listener;

import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceUser;
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

		@Override
		public void setPermissions(long id, Permission permission, List<WorkspaceUser> users) {
			print("setPermissions " + id + " " + permission + " " + users);
		}

		@Override
		public void setWorkspaceDescription(long id) {
			print("setWorkspaceDescription " + id);
		}

		@Override
		public void setWorkspaceOwner(long id, WorkspaceUser newUser, Optional<String> newName) {
			print("setWorkspaceOwner " + id + " " + newUser.getUser() + " " + newName);
		}

		@Override
		public void setWorkspaceDeleted(long id, boolean delete) {
			print("setWorkspaceDeleted " + id + " " + delete);
		}

		@Override
		public void renameObject(long workspaceId, long objectId, String newName) {
			print(String.format("renameObject %s %s %s", workspaceId, objectId, newName));
		}

		@Override
		public void revertObject(long workspaceId, long objectId, int version) {
			print(String.format("revertObject %s %s %s", workspaceId, objectId, version));
		}

		@Override
		public void setObjectDeleted(long workspaceId, long objectId, boolean delete) {
			print(String.format("setObjectDeleted %s %s %s", workspaceId, objectId, delete));
		}

		@Override
		public void copyObject(
				long workspaceId,
				long objectId,
				int version,
				boolean allVersionsCopied) {
			print(String.format("copyObject %s %s %s %s", workspaceId, objectId, version,
					allVersionsCopied));
		}

		@Override
		public void saveObject(
				long workspaceId,
				long objectId,
				int version,
				String type,
				boolean isPublic) {
			print(String.format("saveObject %s %s %s %s %s",
					workspaceId, objectId, version, type, isPublic));
		}
	}
}
