package us.kbase.workspace.kbase;

import static us.kbase.workspace.kbase.ArgUtils.wsInfoToTuple;
import static us.kbase.workspace.kbase.KBaseIdentifierFactory.processWorkspaceIdentifier;

import java.io.IOException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JacksonTupleModule;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectSchemaException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class WorkspaceAdministration {
	
	private final static ObjectMapper MAPPER = new ObjectMapper()
			.registerModule(new JacksonTupleModule());
	
	private final Workspace ws;
	private final WorkspaceServerMethods wsmeth;
	//TODO remove hard coded admin
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

	public Object runCommand(AuthToken token, UObject command)
			throws TypeStorageException, IOException, AuthException,
			WorkspaceCommunicationException, PreExistingWorkspaceException,
			CorruptWorkspaceDBException, NoSuchObjectException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			ParseException, NoSuchPrivilegeException,
			TypedObjectValidationException, TypedObjectSchemaException {
		final String putativeAdmin = token.getUserName();
		if (!(internaladmins.contains(putativeAdmin) ||
				ws.isAdmin(new WorkspaceUser(putativeAdmin)))) {
			throw new IllegalArgumentException("User " + putativeAdmin
					+ " is not an admin");
		}
		final AdminCommand cmd;
		try {
			cmd = command.asClassInstance(AdminCommand.class);
		} catch (IllegalStateException ise) {
			final IOException ioe = (IOException) ise.getCause();
			if (ioe instanceof JsonMappingException) {
				throw new IllegalArgumentException("Unable to deserialize " +
						"a workspace admin command from the input.", ioe);
			}
			throw ioe;
		}
		final String fn = (String) cmd.getCommand();
		if ("listModRequests".equals(fn)) {
			return ws.listModuleRegistrationRequests();
		}
		if ("approveModRequest".equals(fn)) {
			ws.resolveModuleRegistration((String) cmd.getModule(), true);
			return null;
		}
		if ("denyModRequest".equals(fn)) {
			ws.resolveModuleRegistration((String) cmd.getModule(), false);
			return null;
		}
		if ("listAdmins".equals(fn)) {
			final Set<String> strAdm = new HashSet<String>();
			strAdm.addAll(usersToStrings(ws.getAdmins()));
			strAdm.addAll(internaladmins);
			return strAdm;
		}
		if ("addAdmin".equals(fn)) {
			ws.addAdmin(getUser(cmd, token));
			return null;
		}
		if ("removeAdmin".equals(fn)) {
			final WorkspaceUser wsadmin = getUser(cmd, token);
			final String admin = wsadmin.getUser();
			if (!ROOT.equals(admin) && internaladmins.contains(admin)) {
				internaladmins.remove(admin);
			}
			ws.removeAdmin(wsadmin);
			return null;
		}
		if ("setWorkspaceOwner".equals(fn)) {
			final SetWorkspaceOwnerParams params =
					getParams(cmd, SetWorkspaceOwnerParams.class);
			
			final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
							params.wsi);
			return wsInfoToTuple(ws.setWorkspaceOwner(null, wsi,
					params.new_user == null ? null :
					getUser(params.new_user, token), params.new_name, true));
		}
		if ("createWorkspace".equals(fn)) {
			final CreateWorkspaceParams params = getParams(cmd, CreateWorkspaceParams.class);
			return wsmeth.createWorkspace(params, getUser(cmd, token));
		}
		if ("setPermissions".equals(fn)) {
			final SetPermissionsParams params = getParams(cmd, SetPermissionsParams.class);
			wsmeth.setPermissions(params, null, token, true);
			return null;
		}
		if ("getPermissions".equals(fn)) {
			final WorkspaceIdentity params = getParams(cmd, WorkspaceIdentity.class);
			return wsmeth.getPermissions(params, getUser(cmd, token));
		}
		if ("setGlobalPermission".equals(fn)) {
			final SetGlobalPermissionsParams params = getParams(cmd, SetGlobalPermissionsParams.class);
			wsmeth.setGlobalPermission(params, getUser(cmd, token));
			return null;
		}
		if ("saveObjects".equals(fn)) {
			final SaveObjectsParams params = getParams(cmd, SaveObjectsParams.class);
			return wsmeth.saveObjects(params, getUser(cmd, token), token);
		}
		if ("listWorkspaces".equals(fn)) {
			final ListWorkspaceInfoParams params = getParams(cmd, ListWorkspaceInfoParams.class);
			return wsmeth.listWorkspaceInfo(params, getUser(cmd, token));
		}
		if ("listWorkspaceOwners".equals(fn)) {
			return usersToStrings(ws.getAllWorkspaceOwners());
		}
		if ("grantModuleOwnership".equals(fn)) {
			final GrantModuleOwnershipParams params = getParams(cmd, GrantModuleOwnershipParams.class);
			wsmeth.grantModuleOwnership(params, null, true);
			return null;
		}
		if ("removeModuleOwnership".equals(fn)) {
			final RemoveModuleOwnershipParams params = getParams(cmd, RemoveModuleOwnershipParams.class);
			wsmeth.removeModuleOwnership(params, null, true);
			return null;
		}
		throw new IllegalArgumentException(
				"I don't know how to process the command: " + fn);
	}

	private List<String> usersToStrings(final Set<WorkspaceUser> users) {
		final List<String> ret = new ArrayList<String>();
		for (final WorkspaceUser u: users) {
			ret.add(u.getUser());
		}
		return ret;
	}

	private WorkspaceUser getUser(final AdminCommand cmd,
			final AuthToken token)
			throws IOException, AuthException {
		final String user = (String) cmd.getUser();
		return getUser(user, token);
	}

	private WorkspaceUser getUser(final String user, final AuthToken token)
			throws IOException, AuthException {
		if (user == null) {
			throw new NullPointerException("User may not be null");
		}
		final boolean validUser;
		try {
			validUser = AuthService.isValidUserName(Arrays.asList(user),
					token).get(user);
		} catch (UnknownHostException uhe) {
			//message from UHE is only the host name
			throw new AuthException(
					"Could not contact Authorization Service host to validate user name: "
							+ uhe.getMessage(), uhe);
		}
		if (!validUser) {
			throw new IllegalArgumentException(user +
					" is not a valid KBase user");
		}
		return new WorkspaceUser(user);
	}
	
	private static class SetWorkspaceOwnerParams {
		public WorkspaceIdentity wsi;
		public String new_user;
		public String new_name;
		
		@SuppressWarnings("unused")
		public SetWorkspaceOwnerParams() {}; //for jackson
	}
	
	private <T> T getParams(final AdminCommand input, final Class<T> clazz)
			throws IOException {
		final UObject p = input.getParams();
		if (p == null) {
			throw new NullPointerException("Method parameters " + clazz.getSimpleName()
					+ " may not be null");
		}
		try {
			return MAPPER.readValue(p.getPlacedStream(), clazz);
		} catch (JsonMappingException jme) {
			throw new IllegalArgumentException("Unable to deserialize "
					+ clazz.getSimpleName() + " out of params field.", jme);
		}
	}

	//why doesn't this work?
	@SuppressWarnings("unused")
	private <T> T getParams(final Map<String, Object> input) {
		return UObject.transformObjectToObject(input.get("params"),
				new TypeReference<T>() {});
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
