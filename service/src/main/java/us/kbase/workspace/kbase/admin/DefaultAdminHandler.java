package us.kbase.workspace.kbase.admin;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Optional;

import us.kbase.auth.AuthToken;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;

/** The standard workspace administrator handler, where the administrators are stored within
 * the workspace database.
 * 
 * Only supports the {@link AdminRole#ADMIN} role.
 * @author gaprice@lbl.gov
 *
 */
public class DefaultAdminHandler implements AdministratorHandler {

	private final Workspace ws;
	private final Optional<WorkspaceUser> startupAdmin;
	
	//should eventually change this so it doesn't depend on the Workspace class and has a separate DB. YAGNI for now.
	
	/** Create the handler.
	 * @param ws the workspace instance.
	 * @param startupAdmin a bootstrap admin that can be used to add other admins the first time
	 * the workspace starts up.
	 */
	public DefaultAdminHandler(final Workspace ws, final WorkspaceUser startupAdmin) {
		checkNotNull(ws, "ws");
		this.ws = ws;
		this.startupAdmin = Optional.fromNullable(startupAdmin);
	}
	
	@Override
	public Set<WorkspaceUser> getAdmins() throws AdministratorHandlerException {
		final Set<WorkspaceUser> strAdm = new HashSet<>();
		try {
			strAdm.addAll(ws.getAdmins());
		} catch (WorkspaceCommunicationException e) {
			throw new AdministratorHandlerException(
					"Couldn't retrieve list of administrators: " + e.getMessage(), e);
		}
		if (startupAdmin.isPresent()) {
			strAdm.add(startupAdmin.get());
		}
		return strAdm;
	}

	@Override
	public void addAdmin(final WorkspaceUser user) throws AdministratorHandlerException {
		try {
			ws.addAdmin(user);
		} catch (WorkspaceCommunicationException e) {
			throw new AdministratorHandlerException(
					"Couldn't add administrator: " + e.getMessage(), e);
		}
	}

	@Override
	public void removeAdmin(final WorkspaceUser user) throws AdministratorHandlerException {
		try {
			ws.removeAdmin(user);
		} catch (WorkspaceCommunicationException e) {
			throw new AdministratorHandlerException(
					"Couldn't remove administrator: " + e.getMessage(), e);
		}
	}

	@Override
	public AdminRole getAdminRole(final AuthToken token) throws AdministratorHandlerException {
		checkNotNull(token, "token");
		if (startupAdmin.isPresent() && token.getUserName().equals(startupAdmin.get().getUser())) {
			return AdminRole.ADMIN;
		}
		try {
			return ws.isAdmin(new WorkspaceUser(token.getUserName())) ?
					AdminRole.ADMIN : AdminRole.NONE;
		} catch (WorkspaceCommunicationException e) {
			throw new AdministratorHandlerException(
					"Couldn't verify administrator: " + e.getMessage(), e);
		}
	}

}
