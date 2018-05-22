package us.kbase.workspace.kbase.admin;

import java.util.Set;

import us.kbase.auth.AuthToken;
import us.kbase.workspace.database.WorkspaceUser;

/** A handler for dealing with workspace administrators.
 * 
 * There are two types of expected implementations:
 * 
 * 1) an internal implementation, where the workspace itself stores the admin list. This
 * implementation currently only supports full admins, not read only admins.
 * 
 * 2) external implementations that may support read only admins, and probably do not support
 * any methods other than {@link #getAdminRole(AuthToken)}.
 * @author gaprice@lbl.gov
 *
 */
public interface AdministratorHandler {

	/** Get the list of workspace admins.
	 * @return the admins.
	 * @throws AdministratorHandlerException if the admins could not be returned.
	 */
	Set<WorkspaceUser> getAdmins() throws AdministratorHandlerException;
	
	// at some point might want to allow setting roles here, but YAGNI.
	
	/** Add an administrator to the list of workspace adminstrators with full permissions.
	 * @param user the user to make an admin
	 * @throws AdministratorHandlerException if the operation failed.
	 */
	void addAdmin(WorkspaceUser user) throws AdministratorHandlerException;
	
	/** Remove an admin.
	 * @param user the user to remove from the admin list.
	 * @throws AdministratorHandlerException the the operation failed.
	 */
	void removeAdmin(WorkspaceUser user) throws AdministratorHandlerException;
	
	/** Get the role for an admin.
	 * @param token the administrator's token.
	 * @return the administrator's role.
	 * @throws AdministratorHandlerException if the operation failed.
	 */
	AdminRole getAdminRole(AuthToken token) throws AdministratorHandlerException;
	
}
