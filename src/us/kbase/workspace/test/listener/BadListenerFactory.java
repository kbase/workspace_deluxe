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

/** Doesn't have a zero argument constructor, and so fails to start.
 * @author gaprice@lbl.gov
 *
 */
public class BadListenerFactory implements WorkspaceEventListenerFactory {

	public BadListenerFactory(final int foo) {}
	
	@Override
	public WorkspaceEventListener configure(Map<String, String> cfg)
			throws ListenerInitializationException {
		return new BadListener();
	}
	
	public class BadListener implements WorkspaceEventListener {

		@Override
		public void createWorkspace(long id, Instant time) {}

		@Override
		public void cloneWorkspace(long id, boolean isPublic, Instant time) {}

		@Override
		public void setWorkspaceMetadata(long id, Instant time) {}

		@Override
		public void lockWorkspace(long id, Instant time) {}

		@Override
		public void renameWorkspace(long id, String newName, Instant time) {}

		@Override
		public void setGlobalPermission(long id, Permission permission, Instant time) {}

		@Override
		public void setPermissions(
				long id,
				Permission permission,
				List<WorkspaceUser> users,
				Instant time) {}

		@Override
		public void setWorkspaceDescription(long id, Instant time) {}

		@Override
		public void setWorkspaceOwner(
				long id,
				WorkspaceUser
				newUser,
				Optional<String> newName,
				Instant time) {}

		@Override
		public void setWorkspaceDeleted(long id, boolean delete, long maxObjectID, Instant time) {}

		@Override
		public void renameObject(long workspaceId, long objectId, String newName, Instant time) {}

		@Override
		public void revertObject(ObjectInformation obj, boolean isPublic) {}

		@Override
		public void setObjectDeleted(
				long workspaceId,
				long objectId,
				boolean delete,
				Instant time) {}

		@Override
		public void copyObject(ObjectInformation object, boolean isPublic) {}
		
		@Override
		public void copyObject(
				long workspaceId,
				long objectId,
				int latestVersion,
				Instant time,
				boolean isPublic) {}

		@Override
		public void saveObject(ObjectInformation oi, boolean isPublic) {}
	}

}
