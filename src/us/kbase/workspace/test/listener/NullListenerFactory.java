package us.kbase.workspace.test.listener;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

import us.kbase.workspace.database.ObjectInformation;
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
		public void createWorkspace(long id, Instant time) {
			print(String.format("createWorkspace %s %s", id, time));
		}

		@Override
		public void cloneWorkspace(long id, boolean isPublic, Instant time) {
			print(String.format("cloneWorkspace %s %s %s", id, isPublic, time));
		}

		@Override
		public void setWorkspaceMetadata(long id, Instant time) {
			print(String.format("setWorkspaceMetadata %s %s", id, time));
		}

		@Override
		public void lockWorkspace(long id, Instant time) {
			print(String.format("lockWorkspace %s %s", id, time));
		}

		@Override
		public void renameWorkspace(long id, String newName, Instant time) {
			print(String.format("renameWorkspace %s %s %s", id, newName, time));
		}

		@Override
		public void setGlobalPermission(long id, Permission permission, Instant time) {
			print(String.format("setGlobalPermission %s %s %s", id, permission, time));
		}

		@Override
		public void setPermissions(
				long id,
				Permission permission,
				List<WorkspaceUser> users,
				Instant time) {
			print(String.format("setPermissions %s %s %s %s", id, permission, users, time));
		}

		@Override
		public void setWorkspaceDescription(long id, Instant time) {
			print(String.format("setWorkspaceDescription %s %s", id, time));
		}

		@Override
		public void setWorkspaceOwner(
				long id,
				WorkspaceUser newUser,
				Optional<String> newName,
				Instant time) {
			print(String.format("setWorkspaceOwner %s %s %s %s", id, newUser.getUser(), newName,
					time));
		}

		@Override
		public void setWorkspaceDeleted(long id, boolean delete, long maxObjectID, Instant time) {
			print(String.format("setWorkspaceDeleted %s %s %s", id, delete, time));
		}

		@Override
		public void renameObject(long workspaceId, long objectId, String newName, Instant time) {
			print(String.format("renameObject %s %s %s %s", workspaceId, objectId, newName, time));
		}

		@Override
		public void revertObject(ObjectInformation oi, boolean isPublic) {
			print(String.format("revertObject %s %s", oi, isPublic));
		}

		@Override
		public void setObjectDeleted(
				long workspaceId,
				long objectId,
				boolean delete,
				Instant time) {
			print(String.format("setObjectDeleted %s %s %s %s",
					workspaceId, objectId, delete, time));
		}
		
		@Override
		public void copyObject(ObjectInformation object, boolean isPublic) {
			print(String.format("copyObject %s %s", object, isPublic));
		}
		
		@Override
		public void copyObject(
				long workspaceId,
				long objectId,
				int latestVersion,
				Instant time,
				boolean isPublic) {
			print(String.format("copyObject %s %s %s %s %s",
					workspaceId, objectId, latestVersion, time, isPublic));
		}

		@Override
		public void saveObject(ObjectInformation oi, boolean isPublic) {
			print(String.format("saveObject %s %s", oi, isPublic));
		}
	}
}
