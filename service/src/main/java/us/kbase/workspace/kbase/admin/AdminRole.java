package us.kbase.workspace.kbase.admin;

/** An administration role possessed by a user.
 * @author gaprice@lbl.gov
 *
 */
public enum AdminRole {

	/** No administration privileges. */
	NONE,
	
	/** Read only privileges. */
	READ_ONLY,
	
	/** Full administration privileges. */
	ADMIN;
}
