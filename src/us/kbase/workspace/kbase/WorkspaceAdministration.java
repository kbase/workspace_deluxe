package us.kbase.workspace.kbase;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import us.kbase.auth.AuthToken;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.workspace.workspaces.Workspaces;

public class WorkspaceAdministration {
	
	private final Workspaces ws;
	
	//TODO add remove admin users in mongo
	
	//TODO temp storage for admins, remove
	private final Set<String> admins = new HashSet<String>(); 
	
	public WorkspaceAdministration(final Workspaces ws) {
		this.ws = ws;
		admins.add("workspaceadmin");
		admins.add("workspaceroot");
	}

	public void addAdministrator(String admin) {
		if (admin == null || admin.equals("")) {
			return;
		}
		admins.add(admin);
	}
	
	public Object runCommand(AuthToken token, Object cmd)
			throws TypeStorageException {
		if (!admins.contains(token.getUserName())) {
			throw new IllegalArgumentException("User " + token.getUserName()
					+ " is not an admin");
		}
		if (cmd instanceof Map) {
			@SuppressWarnings("unchecked")
			final Map<String, Object> c = (Map<String, Object>) cmd;
			final String fn = (String) c.get("command");
			if ("listModRequests".equals(fn)) {
				return listModRequests();
			}
			if ("approveModRequest".equals(fn)) {
				approveModRequest((String) c.get("module"), true);
				return null;
			}
			if ("denyModRequest".equals(fn)) {
				approveModRequest((String) c.get("module"), false);
				return null;
			}
			if ("listAdmins".equals(fn)) {
				return admins;
			}
		}
		throw new IllegalArgumentException(
				"I don't know how to process the command:\n" + cmd);
	}

	private void approveModRequest(final String module, final boolean approve)
			throws TypeStorageException {
		ws.resolveModuleRegistration(module, approve);
	}

	private Object listModRequests() throws TypeStorageException {
		return ws.listModuleRegistrationRequests();
	}


}
