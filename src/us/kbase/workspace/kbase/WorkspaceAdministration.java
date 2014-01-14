package us.kbase.workspace.kbase;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;

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
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.RemoveModuleOwnershipParams;
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
				final CreateWorkspaceParams params = getParams(c, CreateWorkspaceParams.class);
				return wsmeth.createWorkspace(params, getUser(c));
			}
			if ("saveObjects".equals(fn)) {
				final SaveObjectsParams params = getParams(c, SaveObjectsParams.class);
				return wsmeth.saveObjects(params, getUser(c));
			}
			if ("grantModuleOwnership".equals(fn)) {
				final GrantModuleOwnershipParams params = getParams(c, GrantModuleOwnershipParams.class);
				wsmeth.grantModuleOwnership(params, null, true);
				return null;
			}
			if ("removeModuleOwnership".equals(fn)) {
				final RemoveModuleOwnershipParams params = getParams(c, RemoveModuleOwnershipParams.class);
				wsmeth.removeModuleOwnership(params, null, true);
				return null;
			}
		}
		throw new IllegalArgumentException(
				"I don't know how to process the command:\n" + cmd);
	}

	private WorkspaceUser getUser(final Map<String, Object> input) {
		final WorkspaceUser user = new WorkspaceUser((String) input.get("user")); //TODO check user
		return user;
	}

	private <T> T getParams(final Map<String, Object> input, Class<T> clazz) {
		return UObject.transformObjectToObject(input.get("params"), clazz);
	}
	
	//why doesn't this work?
	@SuppressWarnings("unused")
	private <T> T getParams(final Map<String, Object> input) {
		return UObject.transformObjectToObject(input.get("params"),
				new TypeReference<T>() {});
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
