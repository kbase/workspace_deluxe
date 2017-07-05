package us.kbase.workspace.listener;

import us.kbase.workspace.database.Permission;

/** A listener for workspace events.
 * @author gaprice@lbl.gov
 * 
 * @see WorkspaceEventListenerFactory
 *
 */
public interface WorkspaceEventListener {
	
	/** Notification that a workspace was created.
	 * @param id the workspace ID.
	 */
	void createWorkspace(long id);
	
	/** Notification that a workspace was cloned.
	 * @param id the workspace ID.
	 */
	void cloneWorkspace(long id);

	/** Notification that a workspace's meta data was altered.
	 * @param id the id of the workspace.
	 */
	void setWorkspaceMetadata(long id);
	
	/** Notification that a workspace has been locked.
	 * @param id the id of the workspace.
	 */
	void lockWorkspace(long id);

	/** Notification that a workspace has been renamed.
	 * @param id the id of the workspace.
	 * @param newname the new name of the workspace.
	 */
	void renameWorkspace(long id, final String newname);

	/** Notification that the global permission for a workspace has been altered.
	 * @param id the id of the workspace.
	 * @param permission the new global permission.
	 */
	void setGlobalPermission(long id, Permission permission);
	
	//TODO NOW add more events & test

}
