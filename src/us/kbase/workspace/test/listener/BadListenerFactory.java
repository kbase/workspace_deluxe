package us.kbase.workspace.test.listener;

import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;

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
		public void createWorkspace(long id) {}

		@Override
		public void cloneWorkspace(long id) {}

		@Override
		public void setWorkspaceMetadata(long id) {}

		@Override
		public void lockWorkspace(long id) {}

		@Override
		public void renameWorkspace(long id, String newName) {}

		@Override
		public void setGlobalPermission(long id, Permission permission) {}

		@Override
		public void setPermissions(long id, Permission permission, List<WorkspaceUser> users) {}

		@Override
		public void setWorkspaceDescription(long id) {}

		@Override
		public void setWorkspaceOwner(long id, WorkspaceUser newUser, Optional<String> newName) {}

		@Override
		public void setWorkspaceDeleted(long id, boolean delete) {}

		@Override
		public void renameObject(long workspaceId, long objectId, String newName) {}

		@Override
		public void revertObject(long workspaceId, long objectId, int version) {}
		
	}

}
