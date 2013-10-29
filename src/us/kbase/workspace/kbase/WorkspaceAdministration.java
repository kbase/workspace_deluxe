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
