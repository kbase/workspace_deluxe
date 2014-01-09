package us.kbase.workspace.kbase;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.exceptions.BadJsonSchemaDocumentException;
import us.kbase.typedobj.exceptions.InstanceValidationException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.lib.Workspace;

public class WorkspaceAdministration {
	
	private final Workspace ws;
	private final WorkspaceServerMethods wsmeth;
	private static final String ROOT = "workspaceadmin";
	
	//TODO tests for all this
	private final Set<String> internaladmins = new HashSet<String>(); 
	
	public WorkspaceAdministration(final Workspace ws, 
			final WorkspaceServerMethods wsmeth, final String admin) {
		this.ws = ws;
		this.wsmeth = wsmeth;
		internaladmins.add(ROOT);
		if (admin != null && !admin.isEmpty()) {
			internaladmins.add(admin);
		}
	}

	public Object runCommand(AuthToken token, Object cmd)
			throws TypeStorageException, IOException, AuthException,
			WorkspaceCommunicationException, PreExistingWorkspaceException,
			CorruptWorkspaceDBException, NoSuchObjectException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			TypedObjectValidationException, BadJsonSchemaDocumentException,
			InstanceValidationException, ParseException, NoSuchPrivilegeException {
		final String putativeAdmin = token.getUserName();
		if (!(internaladmins.contains(putativeAdmin) ||
				ws.isAdmin(new WorkspaceUser(putativeAdmin)))) {
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
				final Set<String> strAdm = new HashSet<String>();
				for (final WorkspaceUser u: ws.getAdmins()) {
					strAdm.add(u.getUser());
				}
				strAdm.addAll(internaladmins);
				return strAdm;
			}
			if ("addAdmin".equals(fn)) {
				setAdmin((String) c.get("user"), token, false);
				return null;
			}
			if ("removeAdmin".equals(fn)) {
				final String admin = (String) c.get("user");
				if (!ROOT.equals(admin) && internaladmins.contains(admin)) {
					internaladmins.remove(admin);
				}
				setAdmin((String) c.get("user"), token, true);
				return null;
			}
			if ("createWorkspace".equals(fn)) {
				final CreateWorkspaceParams params = UObject.transformObjectToObject(
						c.get("params"), CreateWorkspaceParams.class);
				final WorkspaceUser user = new WorkspaceUser((String) c.get("user"));
				return wsmeth.createWorkspace(params, user);
			}
			if ("saveObjects".equals(fn)) {
				final SaveObjectsParams params = UObject.transformObjectToObject(
						c.get("params"), SaveObjectsParams.class);
				final WorkspaceUser user = new WorkspaceUser((String) c.get("user"));
				return wsmeth.saveObjects(params, user);
			}
			if ("grantModuleOwnership".equals(fn)) {
				String moduleName = (String)c.get("moduleName");
				String newOwner = (String)c.get("newOwner");
				boolean withGrantOption = false;
				Object par3 = c.get("withGrantOption");
				if (par3 != null) {
					if (par3 instanceof Boolean) {
						withGrantOption = (Boolean)par3;
					} else if (par3 instanceof String) {
						withGrantOption = "true".equals(par3) || "1".equals(par3);
					} else if (par3 instanceof Number) {
						withGrantOption = ((Number)par3).intValue() == 1;
					}
				}
				grantModuleOwnership(moduleName, newOwner, withGrantOption);
				return null;
			}
			if ("removeModuleOwnership".equals(fn)) {
				String moduleName = (String)c.get("moduleName");
				String newOwner = (String)c.get("newOwner");
				removeModuleOwnership(moduleName, newOwner);
				return null;
			}
		}
		throw new IllegalArgumentException(
				"I don't know how to process the command:\n" + cmd);
	}
	
	private void setAdmin(final String user, final AuthToken token,
			final boolean remove)
			throws IOException, AuthException, WorkspaceCommunicationException {
		if (!AuthService.isValidUserName(Arrays.asList(user), token).get(user)) {
			throw new IllegalArgumentException(user +
					" is not a valid KBase user");
		}
		if (remove) {
			ws.removeAdmin(new WorkspaceUser(user));
		} else {
			ws.addAdmin(new WorkspaceUser(user));
		}
	}

	private void approveModRequest(final String module, final boolean approve)
			throws TypeStorageException {
		ws.resolveModuleRegistration(module, approve);
	}

	private Object listModRequests() throws TypeStorageException {
		return ws.listModuleRegistrationRequests();
	}

	private void grantModuleOwnership(String moduleName, String newOwner, boolean withGrantOption) 
			throws TypeStorageException, NoSuchPrivilegeException {
		ws.grantModuleOwnership(moduleName, newOwner, withGrantOption, null, true);
	}
	
	private void removeModuleOwnership(String moduleName, String oldOwner) 
			throws NoSuchPrivilegeException, TypeStorageException {
		ws.removeModuleOwnership(moduleName, oldOwner, null, true);
	}

	//All the email methods are dead code for now, don't use them
//	@SuppressWarnings("unused")
//	private void notifyOnModuleRegRequest(final AuthToken authPart,
//			final WorkspaceUser user, final String module) throws IOException {
//		final Map<String, UserDetail> admininfo;
//		try {
//			admininfo = AuthService.fetchUserDetail(
//					new LinkedList<String>(admins), authPart);
//		} catch (AuthException ae) { //the token was just authorized
//			throw new RuntimeException("Something's broken");
//		}
//		final List<String> emails = new LinkedList<String>();
//		for (final UserDetail ud: admininfo.values()) {
//			emails.add(ud.getEmail());
//		}
//		emailModuleRegistrationRequested(emails, user, module);
//	}
//
//	private static final String EMAIL_MOD_REG = 
//			"This is a notification to the administrators of the KBase Workspace Service. " +
//			"Please do not reply to this message.\n\n" +
//			"Notification: the user %s has requested ownership of the module %s.";
//	private static final String EMAIL_FROM = "do-not-reply@workspaceservice.kbase.us";
//	private static final String EMAIL_MOD_REG_SUBJ = 
//			"[Workspace Service] Module ownership request notification";
//	
//	private void emailModuleRegistrationRequested(final List<String> emails,
//			final WorkspaceUser user, final String module) {
//		//TO_DO test mode shuts email off or changes headers
//		final Properties props = new Properties();
//		props.put("mail.smtp.host", "localhost");
//		final Session session = Session.getInstance(props, null);
//		for (final String email: emails) {
//			try {
//				MimeMessage msg = new MimeMessage(session);
//				msg.setFrom(new InternetAddress(EMAIL_FROM));
//				msg.setRecipient(Message.RecipientType.TO,
//						new InternetAddress(email));
//				msg.setSubject(EMAIL_MOD_REG_SUBJ);
//				msg.setSentDate(new Date());
//				msg.setText(String.format(
//						EMAIL_MOD_REG, user.getUser(), module));
//				Transport.send(msg);
//			} catch (MessagingException me) {
//				me.printStackTrace();
//				//TO_DO log exception when mailing fails
//			}
//		}
//	}
}
