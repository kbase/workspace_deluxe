package us.kbase.workspace.kbase.admin;

/** An administration role possessed by a user.
 * @author gaprice@lbl.gov
 *
 */
public enum AdminRole {

	/** Full administration privileges. */
	ADMIN,
	
	/** Read only privileges. */
	READ_ONLY,
	
	/** No administration privileges. */
	NONE;
	
}
