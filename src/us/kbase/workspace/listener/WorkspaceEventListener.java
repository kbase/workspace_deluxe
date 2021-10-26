package us.kbase.workspace.listener;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceUser;

/** A listener for workspace events.
 * @author gaprice@lbl.gov
 * 
 * @see WorkspaceEventListenerFactory
 *
 */
public interface WorkspaceEventListener {
	
	/** Notification that a workspace was created.
	 * @param user the user that created the workspace.
	 * @param id the workspace ID.
	 * @param time the time the create event occurred.
	 */
	void createWorkspace(WorkspaceUser user, long id, Instant time);
	
	/** Notification that a workspace was cloned.
	 * @param user the user that cloned the workspace.
	 * @param id the workspace ID.
	 * @param isPublic true if the workspace is public, false otherwise.
	 * @param time the time the clone event occurred.
	 */
	void cloneWorkspace(WorkspaceUser user, long id, boolean isPublic, Instant time);

	/** Notification that a workspace's meta data was altered.
	 * @param user the user that altered the workspace.
	 * @param id the id of the workspace.
	 * @param time the time the metadata event occurred.
	 */
	void setWorkspaceMetadata(WorkspaceUser user, long id, Instant time);
	
	/** Notification that a workspace has been locked.
	 * @param user the user that locked the workspace.
	 * @param id the id of the workspace.
	 * @param time the time the lock event occurred.
	 */
	void lockWorkspace(WorkspaceUser user, long id, Instant time);

	/** Notification that a workspace has been renamed.
	 * @param user the user that renamed the workspace.
	 * @param id the id of the workspace.
	 * @param newname the new name of the workspace.
	 * @param time the time the rename event occurred.
	 */
	void renameWorkspace(WorkspaceUser user, long id, final String newname, Instant time);

	/** Notification that the global permission for a workspace has been altered.
	 * @param user the user that altered the workspace.
	 * @param id the id of the workspace.
	 * @param permission the new global permission.
	 * @param time the time the permission event occurred.
	 */
	void setGlobalPermission(WorkspaceUser user, long id, Permission permission, Instant time);

	/** Notification that the permissions for a workspace have been altered.
	 * @param user the user that altered the workspace. May be null if the user is an admin.
	 * @param id the id of the workspace.
	 * @param permission the new permission.
	 * @param users the users that have been assigned the new permission.
	 * @param time the time the permissions event occurred.
	 */
	void setPermissions(
			WorkspaceUser user,
			long id,
			Permission permission,
			List<WorkspaceUser> users,
			Instant time);

	/** Notification that the workspace description has been set or altered.
	 * @param user the user that altered the workspace. May be null if the user is an admin.
	 * @param id the id of the workspace.
	 * @param time the time the description change event occurred.
	 */
	void setWorkspaceDescription(WorkspaceUser user, long id, Instant time);

	/** Notification that the owner of a workspace has been changed.
	 * @param user the user that changed the owner. Will be null if the user is an admin.
	 * @param id the id of the workspace.
	 * @param newUser the new owner of the workspace.
	 * @param newName the new name for the workspace, if any.
	 * @param time the time the owner change event occurred.
	 */
	void setWorkspaceOwner(
			WorkspaceUser user,
			long id,
			WorkspaceUser newUser,
			Optional<String> newName,
			Instant time);

	/** Notification that a workspace has been deleted or undeleted.
	 * @param user the user that altered the workspace. May be null if the user is an admin.
	 * @param id the id of the workspace.
	 * @param delete true for a delete event, false for an undelete event.
	 * @param maxObjectID the maximum ID for any object in the workspace.
	 * @param time the time the deletion event occurred.
	 */
	void setWorkspaceDeleted(
			WorkspaceUser user,
			long id,
			boolean delete,
			long maxObjectID,
			Instant time);

	/** Notification that an object has been renamed.
	 * @param user the user that renamed the object.
	 * @param workspaceId the id of the workspace containing the object.
	 * @param objectId the id of the object.
	 * @param newName the object's new name.
	 * @param time the time the rename event occurred.
	 */
	void renameObject(
			final WorkspaceUser user,
			final long workspaceId,
			final long objectId,
			final String newName,
			final Instant time);

	/** Notification that an object has been reverted.
	 * @param object information about the reverted object.
	 * @param isPublic true if the object is in a public workspace, false otherwise.
	 */
	void revertObject(ObjectInformation object, boolean isPublic);

	/** Notification that an object was hidden or unhidden.
	 * @param user the user that changed the hidden state of the object.
	 * @param workspaceId the workspace id.
	 * @param objectId the object id.
	 * @param hidden true if the object was hidden, false if it was unhidden.
	 * @param time the time the hide event occurred.
	 */
	void setObjectsHidden(
			WorkspaceUser user,
			long workspaceId,
			long objectId,
			boolean hidden,
			Instant time);

	/** Notification that an object was deleted or undeleted.
	 * @param user the user that changed the deletion state of the object.
	 * @param workspaceId the workspace id.
	 * @param objectId the object id.
	 * @param delete true if the object was deleted, false if it was undeleted.
	 * @param time the time the delete event occurred.
	 */
	void setObjectDeleted(
			WorkspaceUser user,
			long workspaceId,
			long objectId,
			boolean delete,
			Instant time);

	/** Notification that a single version of an object was copied.
	 * @param object information about the new object version.
	 * @param isPublic true if the new object version is in a public workspace, false otherwise.
	 */
	void copyObject(ObjectInformation object, boolean isPublic);

	/** Notification that all the versions of an object were copied.
	 * @param user the user that copied the object.
	 * @param workspaceId the workspace id of the new object.
	 * @param objectId the object id of the new object.
	 * @param latestVersion the latest version of the new object.
	 * @param time the time the last version was copied.
	 * @param isPublic true if the new object is in a public workspace, false otherwise.
	 */
	void copyObject(
			WorkspaceUser user,
			long workspaceId,
			long objectId,
			int latestVersion,
			Instant time,
			boolean isPublic);
	
	/** Notification that an object has been saved.
	 * @param object information about the object.
	 * @param isPublic true if the object is in a public workspace, false otherwise.
	 */
	void saveObject(ObjectInformation object, boolean isPublic);
}
