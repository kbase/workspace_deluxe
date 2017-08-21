package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.ArgUtils.checkLong;
import static us.kbase.workspace.kbase.ArgUtils.chooseDate;
import static us.kbase.workspace.kbase.ArgUtils.getGlobalWSPerm;
import static us.kbase.workspace.kbase.ArgUtils.wsInfoToTuple;
import static us.kbase.workspace.kbase.ArgUtils.processProvenance;
import static us.kbase.workspace.kbase.ArgUtils.toObjectPaths;
import static us.kbase.workspace.kbase.ArgUtils.translateObjectData;
import static us.kbase.workspace.kbase.ArgUtils.longToBoolean;
import static us.kbase.workspace.kbase.ArgUtils.longToInt;
import static us.kbase.workspace.kbase.ArgUtils.objInfoToTuple;
import static us.kbase.workspace.kbase.IdentifierUtils.processObjectIdentifier;
import static us.kbase.workspace.kbase.IdentifierUtils.processObjectSpecifications;
import static us.kbase.workspace.kbase.IdentifierUtils.processWorkspaceIdentifier;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;

import us.kbase.auth.AuthException;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.service.ServiceChecker;
import us.kbase.common.service.ServiceChecker.ServiceException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple9;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.typedobj.exceptions.TypedObjectSchemaException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.GetObjectInfo3Params;
import us.kbase.workspace.GetObjectInfo3Results;
import us.kbase.workspace.GetObjects2Params;
import us.kbase.workspace.GetObjects2Results;
import us.kbase.workspace.GrantModuleOwnershipParams;
import us.kbase.workspace.ListObjectsParams;
import us.kbase.workspace.ListWorkspaceIDsParams;
import us.kbase.workspace.ListWorkspaceIDsResults;
import us.kbase.workspace.ListWorkspaceInfoParams;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.RemoveModuleOwnershipParams;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.SetGlobalPermissionsParams;
import us.kbase.workspace.SetPermissionsParams;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.WorkspacePermissions;
import us.kbase.workspace.database.DependencyStatus;
import us.kbase.workspace.database.ListObjectsParameters;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Types;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.UserWorkspaceIDs;
import us.kbase.workspace.database.Workspace;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceSaveObject;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.WorkspaceUserMetadata;
import us.kbase.workspace.database.WorkspaceUserMetadata.MetadataException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.refsearch.ReferenceSearchMaximumSizeExceededException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class WorkspaceServerMethods {
	
	final private Workspace ws;
	final private Types types;
	final private URL handleServiceUrl;
	final private URL handleManagerUrl;
	final private AuthToken handleManagerToken;
	final private int maximumIDCount;
	final private ConfigurableAuthService auth;
	
	public WorkspaceServerMethods(
			final Workspace ws,
			final Types types,
			final URL handleServiceUrl,
			final URL handleManagerUrl,
			final AuthToken handleMgrToken,
			final int maximumIDCount,
			final ConfigurableAuthService auth) {
		this.ws = ws;
		this.types = types;
		this.handleServiceUrl = handleServiceUrl;
		this.maximumIDCount = maximumIDCount;
		this.auth = auth;
		this.handleManagerUrl = handleManagerUrl;
		this.handleManagerToken = handleMgrToken;
	}
	
	public ConfigurableAuthService getAuth() {
		return auth;
	}
	
	public URL getHandleServiceURL() {
		return handleServiceUrl;
	}
	public DependencyStatus checkHandleService() {
		try {
			ServiceChecker.checkService(handleServiceUrl);
			return new DependencyStatus(
					true, "OK", "Handle service", "Unknown");
		} catch (ServiceException se) {
			//tested manually, don't change without testing
			return new DependencyStatus(
					false, se.getMessage(), "Handle Service", "Unknown");
		}
	}
	
	public WorkspaceUser getUser(
			final String tokenstring,
			final AuthToken token)
			throws IOException, AuthException {
		if (tokenstring != null) {
			final AuthToken t = auth.validateToken(tokenstring);
			return new WorkspaceUser(t.getUserName());
		}
		if (token == null) {
			return null;
		}
		return new WorkspaceUser(token.getUserName());
	}
	
	public WorkspaceUser getUser(final AuthToken token) {
		if (token == null) {
			return null;
		}
		return new WorkspaceUser(token.getUserName());
	}
	
	public List<WorkspaceUser> convertUsers(final List<String> users) {
		final List<WorkspaceUser> wsusers = new ArrayList<WorkspaceUser>();
		if (users == null) {
			return null;
		}
		for (String u: users) {
			wsusers.add(new WorkspaceUser(u));
		}
		return wsusers;
	}

	public Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>
			createWorkspace(
			final CreateWorkspaceParams params, final WorkspaceUser user)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			MetadataException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		Permission p = getGlobalWSPerm(params.getGlobalread());
		final WorkspaceInformation meta = ws.createWorkspace(user,
				params.getWorkspace(), p.equals(Permission.READ),
				params.getDescription(),
				new WorkspaceUserMetadata(params.getMeta()));
		return wsInfoToTuple(meta);
	}
	
	/** Set permissions on a workspace.
	 * @param params the parameters for the set permissions call.
	 * @param token the user that is setting permissions.
	 * @throws IOException if an error occurs when contacting the authentication service.
	 * @throws AuthException if the authentication service could not be contacted.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the data stores.
	 * @throws NoSuchWorkspaceException if the specified workspace does not exist.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to administer the
	 * workspace.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
	 * the data stores.
	 */
	public void setPermissions(
			final SetPermissionsParams params,
			final AuthToken token)
			throws IOException, AuthException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		setPermissions(params, getUser(token), false, token);
	}
	
	/** Set permissions on a workspace as an admin.
	 * @param params the parameters for the set permissions call.
	 * @param token a token to use for user lookup in the authentication service.
	 * @return the ID of the workspace.
	 * @throws IOException if an error occurs when contacting the authentication service.
	 * @throws AuthException if the authentication service could not be contacted.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the data stores.
	 * @throws NoSuchWorkspaceException if the specified workspace does not exist.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting
	 * the data stores.
	 */
	public long setPermissionsAsAdmin(
			final SetPermissionsParams params,
			final AuthToken token)
			throws IOException, AuthException, CorruptWorkspaceDBException,
				NoSuchWorkspaceException, WorkspaceCommunicationException {
		try {
			return setPermissions(params, null, true, token);
		} catch (WorkspaceAuthorizationException e) {
			throw new RuntimeException("This shouldn't happen", e);
		}
	}
		
	private long setPermissions(
			final SetPermissionsParams params,
			final WorkspaceUser user,
			final boolean asAdmin,
			final AuthToken token)
			throws IOException, AuthException, CorruptWorkspaceDBException,
				NoSuchWorkspaceException, WorkspaceAuthorizationException,
				WorkspaceCommunicationException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final Permission p = translatePermission(params.getNewPermission());
		if (params.getUsers().size() == 0) {
			throw new IllegalArgumentException("Must provide at least one user");
		}
		final List<WorkspaceUser> users = validateUsers(params.getUsers(), token);
		return ws.setPermissions(user, wsi, users, p, asAdmin);
	}
	
	public List<WorkspaceUser> validateUsers(final List<String> users, final AuthToken token)
			throws IOException, AuthException {
		final List<WorkspaceUser> wsusers = convertUsers(users);
		final Map<String, Boolean> userok;
		try {
			userok = auth.isValidUserName(users, token);
		} catch (UnknownHostException uhe) {
			//message from UHE is only the host name
			throw new AuthException(
					"Could not contact Authorization Service host to validate user names: "
							+ uhe.getMessage(), uhe);
		}
		for (String u: userok.keySet()) {
			if (!userok.get(u)) {
				throw new IllegalArgumentException(String.format(
						"User %s is not a valid user", u));
			}
		}
		return wsusers;
	}

	public long setGlobalPermission(
			final SetGlobalPermissionsParams params,
			final WorkspaceUser user)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceAuthorizationException, WorkspaceCommunicationException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final Permission p = translatePermission(params.getNewPermission());
		return ws.setGlobalPermission(user, wsi, p);
	}
	
	//TODO EXCEPTIONS look into making CorruptWorksapceDBException a runtime exception. No reason for a caught exception. Check for places where it's caught to ensure change is ok.
	
	/** Gets the permissions for a workspace for a user. If the user has at least write permissions
	 * to the workspace, also returns permissions for other users.
	 * @param wsi the workspace in question.
	 * @param user the user for which permissions will be retrieved.
	 * @return the workspace permissions as a map from username to permission.
	 * @throws NoSuchWorkspaceException if there is no such workspace.
	 * @throws WorkspaceCommunicationException if the workspace service could not 
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the workspace.
	 */
	public Map<String, String> getPermissions(
			final WorkspaceIdentity wsi,
			final WorkspaceUser user)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		return getPermissions(Arrays.asList(wsi), user, false).getPerms().get(0);
	}
	
	/** Gets the permissions for a set of workspaces for a user. If the user has at least write
	 * permissions for a particular workspace, the other user's permissions are also returned for
	 * that workspace.
	 * @param workspaces the workspaces in question.
	 * @param user the user for which permissions will be retrieved.
	 * @param asAdmin get all permissions for the workspaces, regardless of the user's permissions.
	 * @return the workspace permissions.
	 * @throws NoSuchWorkspaceException if there is no such workspace.
	 * @throws WorkspaceCommunicationException if the workspace service could not 
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the workspace.
	 */
	public WorkspacePermissions getPermissions(
			final List<WorkspaceIdentity> workspaces,
			final WorkspaceUser user,
			final boolean asAdmin)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		
		final List<WorkspaceIdentifier> wsil =
				new LinkedList<WorkspaceIdentifier>();
		for (final WorkspaceIdentity wsi: workspaces) {
			wsil.add(processWorkspaceIdentifier(wsi));
		}
		final List<Map<User, Permission>> perms;
		if (asAdmin) {
			perms = ws.getPermissionsAsAdmin(wsil);
		} else {
			perms = ws.getPermissions(user, wsil);
		}
		final List<Map<String, String>> ret =
				new LinkedList<Map<String,String>>();
		for (final Map<User, Permission> acls: perms){
			final Map<String, String> inner = new HashMap<String, String>();
			for (final User acl: acls.keySet()) {
				inner.put(acl.getUser(), translatePermission(acls.get(acl)));
			}
			ret.add(inner);
		}
		return new WorkspacePermissions().withPerms(ret);
	}

	public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>> saveObjects(
			final SaveObjectsParams params,
			final WorkspaceUser user,
			final AuthToken token)
			throws ParseException, WorkspaceCommunicationException,
			WorkspaceAuthorizationException, NoSuchObjectException,
			CorruptWorkspaceDBException, NoSuchWorkspaceException,
			TypedObjectValidationException, TypeStorageException,
			IOException, TypedObjectSchemaException {

		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				params.getWorkspace(), params.getId());
		final List<WorkspaceSaveObject> woc = new ArrayList<WorkspaceSaveObject>();
		int count = 1;
		if (params.getObjects().isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		for (ObjectSaveData d: params.getObjects()) {
			checkAddlArgs(d.getAdditionalProperties(), d.getClass());
			final ObjectIDNoWSNoVer oi;
			try {
				oi = ObjectIDNoWSNoVer.create(d.getName(), d.getObjid());
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Object " + count + ": " + e.getMessage(), e);
			}
			final String errprefix = "Object " + count + ", " + oi.getIdentifierString() + ",";
			if (d.getData() == null) {
				throw new IllegalArgumentException(errprefix + " has no data");
			}
			TypeDefId t;
			try {
				t = TypeDefId.fromTypeString(d.getType());
			} catch (IllegalArgumentException iae) {
				throw new IllegalArgumentException(errprefix + " type error: "
						+ iae.getLocalizedMessage(), iae);
			}
			final Provenance p = processProvenance(user, d.getProvenance());
			final boolean hidden = longToBoolean(d.getHidden());
			try {
				woc.add(new WorkspaceSaveObject(oi, d.getData(), t, 
						new WorkspaceUserMetadata(d.getMeta()), p, hidden));
			} catch (MetadataException me) {
				throw new IllegalArgumentException(errprefix + " save error: "
						+ me.getLocalizedMessage(), me);
			}
			count++;
		}
		params.setObjects(null); 
		final IdReferenceHandlerSetFactory fac =
				new IdReferenceHandlerSetFactory(maximumIDCount);
		fac.addFactory(new HandleIdHandlerFactory(handleServiceUrl, token));
		
		final List<ObjectInformation> meta = ws.saveObjects(user, wsi, woc, fac); 
		return objInfoToTuple(meta, true);
	}
	
	/** Get object information.
	 * @param params the information request parameters.
	 * @param user the user making the request.
	 * @param asAdmin whether the request should be run with administrator privileges.
	 * @return the object information.
	 * @throws WorkspaceCommunicationException if a communication error with the storage system
	 * occurs.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 * @throws InaccessibleObjectException if a requested object is inaccessible.
	 * @throws NoSuchReferenceException if a reference in a reference path does not exist.
	 * @throws NoSuchObjectException if a request object does not exist.
	 * @throws ReferenceSearchMaximumSizeExceededException if a search for an object traverses to
	 * many other objects.
	 */
	public GetObjectInfo3Results getObjectInformation(
			final GetObjectInfo3Params params,
			final WorkspaceUser user,
			final boolean asAdmin)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
				InaccessibleObjectException, NoSuchReferenceException, NoSuchObjectException,
				ReferenceSearchMaximumSizeExceededException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final List<ObjectIdentifier> loi = processObjectSpecifications(params.getObjects());
		final List<ObjectInformation> infos = ws.getObjectInformation(user, loi,
				longToBoolean(params.getIncludeMetadata()),
				longToBoolean(params.getIgnoreErrors()),
				asAdmin);
		return new GetObjectInfo3Results().withInfos(objInfoToTuple(infos, true))
				.withPaths(toObjectPaths(infos));
	}
	
	/** Get objects.
	 * @param params the object request parameters.
	 * @param user the user making the request.
	 * @param asAdmin whether the request should be run with administrator privileges.
	 * @param resourcesToDelete a container into which resources that must be destroyed after
	 * they're no longer needed can be placed.
	 * @return the objects.
	 * @throws WorkspaceCommunicationException if a communication error with the storage system
	 * occurs.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 * @throws InaccessibleObjectException if a requested object is inaccessible.
	 * @throws NoSuchReferenceException if a reference in a reference path does not exist.
	 * @throws NoSuchObjectException if a request object does not exist.
	 * @throws ReferenceSearchMaximumSizeExceededException if a search for an object traverses to
	 * many other objects.
	 * @throws TypedObjectExtractionException if an error occurred extracting data from a typed
	 * object.
	 * @throws JsonParseException if an error occurs when attempting to get the object data. 
	 * @throws IOException if an error occurs when attempting to get the object data.
	 */
	public GetObjects2Results getObjects(
			final GetObjects2Params params,
			final WorkspaceUser user,
			final boolean asAdmin,
			final ThreadLocal<List<WorkspaceObjectData>> resourcesToDelete)
			throws CorruptWorkspaceDBException, WorkspaceCommunicationException,
					InaccessibleObjectException, NoSuchReferenceException, NoSuchObjectException,
					TypedObjectExtractionException, ReferenceSearchMaximumSizeExceededException,
					JsonParseException, IOException {
		// ugh, tried to see if we could code out the jpe and ioe but it's too much of a mess
		// once you get into the byte array cache and JTS
		checkAddlArgs(params.getAdditionalProperties(), GetObjects2Params.class);
		final List<ObjectIdentifier> loi =
				processObjectSpecifications(params.getObjects());
		final boolean noData = longToBoolean(params.getNoData(), false);
		final boolean ignoreErrors = longToBoolean(params.getIgnoreErrors(), false);
		final List<WorkspaceObjectData> objects = ws.getObjects(
				user, loi, noData, ignoreErrors, asAdmin);
		resourcesToDelete.set(objects);
		return new GetObjects2Results().withData(translateObjectData(
				objects, user, handleManagerUrl, handleManagerToken, true));
	}
	
	public void grantModuleOwnership(final GrantModuleOwnershipParams params,
			final WorkspaceUser user, boolean asAdmin)
			throws TypeStorageException, NoSuchPrivilegeException {
		checkAddlArgs(params.getAdditionalProperties(),
				GrantModuleOwnershipParams.class);
		types.grantModuleOwnership(params.getMod(), params.getNewOwner(),
				longToBoolean(params.getWithGrantOption()), user, asAdmin);
	}

	public void removeModuleOwnership(final RemoveModuleOwnershipParams params,
			final WorkspaceUser user, final boolean asAdmin)
			throws NoSuchPrivilegeException, TypeStorageException {
		checkAddlArgs(params.getAdditionalProperties(),
				RemoveModuleOwnershipParams.class);
		types.removeModuleOwnership(params.getMod(), params.getOldOwner(),
				user, asAdmin);
	}

	public List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String,String>>>
			listWorkspaceInfo(final ListWorkspaceInfoParams params,
			final WorkspaceUser user)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			ParseException, MetadataException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final Permission p = params.getPerm() == null ? null :
				translatePermission(params.getPerm());
		final Date after = chooseDate(params.getAfter(),
				params.getAfterEpoch(),
				"Cannot specify both timestamp and epoch for after parameter");
		final Date before = chooseDate(params.getBefore(),
				params.getBeforeEpoch(),
				"Cannot specify both timestamp and epoch for before " +
				"parameter");
		return wsInfoToTuple(ws.listWorkspaces(user,
				p, convertUsers(params.getOwners()),
				new WorkspaceUserMetadata(params.getMeta()),
				after, before,
				longToBoolean(params.getExcludeGlobal()),
				longToBoolean(params.getShowDeleted()),
				longToBoolean(params.getShowOnlyDeleted())));
	}
	
	/** Lists IDs of workspaces to which the user has access. Should be faster than
	 * {@link #listWorkspaceInfo(ListWorkspaceInfoParams, WorkspaceUser)}.
	 * @param params the method parameters.
	 * @param user the user, or null if anonymous.
	 * @return the results of the method.
	 * @throws CorruptWorkspaceDBException if corrupt data was found in the storage system.
	 * @throws WorkspaceCommunicationException if a communication error occurred with the storage
	 * system.
	 */
	public ListWorkspaceIDsResults listWorkspaceIDs(
			final ListWorkspaceIDsParams params,
			WorkspaceUser user)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		if (longToBoolean(params.getOnlyGlobal())) {
			user = null;
			params.setExcludeGlobal(0L);
		}
		final Permission p = params.getPerm() == null ? null :
			translatePermission(params.getPerm());
		final UserWorkspaceIDs wsids = ws.listWorkspaceIDs(
				user, p, longToBoolean(params.getExcludeGlobal(), true));
		return new ListWorkspaceIDsResults()
				.withWorkspaces(new LinkedList<>(wsids.getWorkspaceIDs()))
				.withPub(new LinkedList<>(wsids.getPublicWorkspaceIDs()));
	}
	
	/** List objects in one or more workspaces.
	 * @param params the parameters determining which workspace objects will be listed.
	 * @param user the user listing the objects, or null for an anonymous user.
	 * @param asAdmin true to run the method as an admin. The user is ignored and all requested
	 * data is returned without considering permissions. If true, at least one and no more than
	 * 1000 workspaces must be specified for querying.
	 * @return the objects information.
	 * @throws ParseException if a date could not be parsed.
	 * @throws MetadataException if the user supplied metadata was illegal.
	 * @throws CorruptWorkspaceDBException if corrupt data was found in the storage system.
	 * @throws NoSuchWorkspaceException if a requested workspace does not exist or is illegal.
	 * @throws WorkspaceCommunicationException if a communication error occurred with the storage
	 * system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to access one or
	 * more of the workspaces.
	 */
	public List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long,
			Map<String,String>>> listObjects(
					final ListObjectsParams params,
					final WorkspaceUser user,
					final boolean asAdmin)
					throws ParseException, MetadataException, CorruptWorkspaceDBException,
						NoSuchWorkspaceException, WorkspaceCommunicationException,
						WorkspaceAuthorizationException {
		checkAddlArgs(params.getAdditionalProperties(), params.getClass());
		final List<WorkspaceIdentifier> wsis = new LinkedList<WorkspaceIdentifier>();
		if (params.getWorkspaces() != null) {
			for (final String ws: params.getWorkspaces()) {
				wsis.add(processWorkspaceIdentifier(ws, null));
			}
		}
		if (params.getIds() != null) {
			for (final Long id: params.getIds()) {
				wsis.add(processWorkspaceIdentifier(null, id));
			}
		}
		final TypeDefId type = params.getType() == null ? null :
				TypeDefId.fromTypeString(params.getType());
		final ListObjectsParameters lop = getListObjectParameters(user, asAdmin, wsis, type);
		final Date after = chooseDate(params.getAfter(),
				params.getAfterEpoch(),
				"Cannot specify both timestamp and epoch for after parameter");
		final Date before = chooseDate(params.getBefore(),
				params.getBeforeEpoch(),
				"Cannot specify both timestamp and epoch for before " +
				"parameter");
		lop.withMinimumPermission(params.getPerm() == null ? null :
				translatePermission(params.getPerm()))
			.withSavers(convertUsers(params.getSavedby()))
			.withMetadata(new WorkspaceUserMetadata(params.getMeta()))
			.withAfter(after)
			.withBefore(before)
			.withMinObjectID(checkLong(params.getMinObjectID(), -1))
			.withMaxObjectID(checkLong(params.getMaxObjectID(), -1))
			.withShowHidden(longToBoolean(params.getShowHidden()))
			.withShowDeleted(longToBoolean(params.getShowDeleted()))
			.withShowOnlyDeleted(longToBoolean(params.getShowOnlyDeleted()))
			.withShowAllVersions(longToBoolean(params.getShowAllVersions()))
			.withIncludeMetaData(longToBoolean(params.getIncludeMetadata()))
			.withExcludeGlobal(longToBoolean(params.getExcludeGlobal()))
			.withLimit(longToInt(params.getLimit(), "Limit", -1));
		
		return objInfoToTuple(ws.listObjects(lop), false);
	}

	private ListObjectsParameters getListObjectParameters(
			final WorkspaceUser user,
			final boolean asAdmin,
			final List<WorkspaceIdentifier> wsis,
			final TypeDefId type) {
		if (type == null && wsis.isEmpty()) {
			throw new IllegalArgumentException(
					"At least one filter must be specified.");
		}
		final ListObjectsParameters lop;
		if (type == null) {
			if (asAdmin) {
				lop = new ListObjectsParameters(wsis);
			} else {
				lop = new ListObjectsParameters(user, wsis);
			}
		} else if (wsis.isEmpty()) {
			if (asAdmin) {
				throw new IllegalArgumentException("When listing objects as an admin at least " +
						"one target workspace must be provided");
			} else {
				lop = new ListObjectsParameters(user, type);
			}
		} else {
			if (asAdmin) {
				lop = new ListObjectsParameters(wsis, type);
			} else {
				lop = new ListObjectsParameters(user, wsis, type);
			}
		}
		return lop;
	}

	/** Get all versions of an object.
	 * @param object the object.
	 * @param user the user making the request.
	 * @param asAdmin true to ignore the user and request as an admin.
	 * @return the object versions.
	 * @throws InaccessibleObjectException if the object is inaccessible.
	 * @throws NoSuchObjectException if there is no such object.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 */
	public List<Tuple11<Long, String, String, String, Long, String, Long, String, String,
			Long, Map<String, String>>> getObjectHistory(
			final ObjectIdentity object,
			final WorkspaceUser user,
			final boolean asAdmin)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		final ObjectIdentifier oi = processObjectIdentifier(object);
		return objInfoToTuple(ws.getObjectHistory(user, oi, asAdmin), true);
	}
}
