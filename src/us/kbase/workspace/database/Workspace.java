package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.nonNull;
import static us.kbase.workspace.database.Util.noNulls;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.common.base.Optional;

import org.apache.commons.lang3.StringUtils;

import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.JsonDocumentLocation;
import us.kbase.typedobj.core.SubsetSelection;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.ValidatedTypedObject;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;
import us.kbase.typedobj.exceptions.TypedObjectSchemaException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdParseException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandler;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.NoSuchIdException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.TooManyIdsException;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory;
import us.kbase.typedobj.idref.IdReferenceHandlerSetFactory.IdReferenceHandlerFactory;
import us.kbase.typedobj.idref.IdReferenceType;
import us.kbase.typedobj.idref.RemappedId;
import us.kbase.workspace.database.ObjectResolver.ObjectResolution;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.refsearch.ReferenceSearchMaximumSizeExceededException;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.exceptions.WorkspaceDBException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;
import us.kbase.workspace.listener.WorkspaceEventListener;

public class Workspace {
	
	//TODO MEM limit all methods that return a set or list or map
	
	//TODO TEST general unit tests
	//TODO GC garbage collection - see WOR-45
	//TODO SEARCH index typespecs
	//TODO CODE look into eliminating all the DB implementation specific classes, too much of a pain just to ensure not moving objects between implementations
	//TODO CODE wrap event listeners in try/catch, catch everything & log & continue
	
	public static final AllUsers ALL_USERS = new AllUsers('*');
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	private final static int MAX_WS_COUNT = 1000;
	private final static int NAME_LIMIT = 1000;
	/* may need to calculate memory for search tree and modify, or add a separate limit. 
	 * for now this is low enough it's not really a concern.
	 */
	private final static int MAX_OBJECT_SEARCH_COUNT_DEFAULT = 10000;
	
	private final static IdReferenceType WS_ID_TYPE = new IdReferenceType("ws");
	
	private final WorkspaceDatabase db;
	private ResourceUsageConfiguration rescfg;
	private final TypedObjectValidator validator;
	private final List<WorkspaceEventListener> listeners;
	private int maximumObjectSearchCount;
	
	public Workspace(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final TypedObjectValidator validator) {
		this(db, cfg, validator, Collections.emptyList());
	}
	
	public Workspace(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final TypedObjectValidator validator,
			final List<WorkspaceEventListener> listeners) {
		if (db == null) {
			throw new NullPointerException("db cannot be null");
		}
		if (cfg == null) {
			throw new NullPointerException("cfg cannot be null");
		}
		if (validator == null) {
			throw new NullPointerException("validator cannot be null");
		}
		nonNull(listeners, "listeners");
		noNulls(listeners, "null item in listeners");
		this.db = db;
		//TODO DBCONSIST check that a few object types exist to make sure the type provider is ok.
		this.validator = validator;
		rescfg = cfg;
		this.listeners = Collections.unmodifiableList(listeners);
		db.setResourceUsageConfiguration(rescfg);
		this.maximumObjectSearchCount = MAX_OBJECT_SEARCH_COUNT_DEFAULT;
	}
	
	/* this is temporary until we have path returning code when searching for objects.
	 * Will probably want to determine the max number of objects based on some max memory usage and
	 * on speed.
	 */
	public void setMaximumObjectSearchCount(final int count) {
		maximumObjectSearchCount = count;
	}
	
	public int getMaximumObjectSearchCount() {
		return maximumObjectSearchCount;
	}
	
	public ResourceUsageConfiguration getResourceConfig() {
		return rescfg;
	}
	
	public void setResourceConfig(final ResourceUsageConfiguration rescfg) {
		if (rescfg == null) {
			throw new NullPointerException("rescfg cannot be null");
		}
		this.rescfg = rescfg;
		db.setResourceUsageConfiguration(rescfg);
	}
	
	public TempFilesManager getTempFilesManager() {
		return db.getTempFilesManager();
	}
	
	public List<DependencyStatus> status() {
		return db.status();
	}
	
	public WorkspaceInformation createWorkspace(final WorkspaceUser user, 
			final String wsname, boolean globalread, final String description,
			final WorkspaceUserMetadata meta)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		final WorkspaceInformation ret = db.createWorkspace(user, wsname, globalread,
				pruneWorkspaceDescription(description),
				meta == null ? new WorkspaceUserMetadata() : meta);
		for (final WorkspaceEventListener l: listeners) {
			l.createWorkspace(ret.getId(), ret.getModDate());
		}
		return ret;
	}
	
	/** Set and remove metadata for a workspace.
	 * @param user the user altering the metadata.
	 * @param wsi the workspace to alter.
	 * @param meta updated metadata. Keys will overwrite any keys already set on the workspace.
	 * Send null to make no changes.
	 * @param keysToRemove metadata keys to remove from the workspace. Send null to make no
	 * changes.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 * @throws NoSuchWorkspaceException if the workspace does not exist or is deleted.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to alter the workspace.
	 */
	public void setWorkspaceMetadata(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final WorkspaceUserMetadata meta,
			final List<String> keysToRemove)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.ADMIN)
				.withOperation("alter metadata for").check();
		boolean set = false;
		Instant time = null;
		// should merge this into one db call
		try {
			if (meta != null && !meta.isEmpty()) {
				time = db.setWorkspaceMeta(wsid, meta);
				set = true;
			}
			if (keysToRemove != null) {
				noNulls(keysToRemove, "null metadata keys are not allowed");
				for (final String key: keysToRemove) {
					time = db.removeWorkspaceMetaKey(wsid, key);
					set = true;
				}
			}
		} finally {
			if (set) {
				for (final WorkspaceEventListener l: listeners) {
					l.setWorkspaceMetadata(wsid.getID(), time);
				}
			}
		}
	}
	
	public WorkspaceInformation cloneWorkspace(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String newname,
			final boolean globalread,
			final String description,
			final WorkspaceUserMetadata meta,
			final Set<ObjectIDNoWSNoVer> exclude)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			PreExistingWorkspaceException, NoSuchObjectException {
		final ResolvedWorkspaceID wsid = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.READ).check();
		new WorkspaceIdentifier(newname, user); //check for errors
		final WorkspaceInformation info = db.cloneWorkspace(user, wsid, newname, globalread,
				pruneWorkspaceDescription(description),
				meta == null ? new WorkspaceUserMetadata() : meta,
				exclude);
		for (final WorkspaceEventListener l: listeners) {
			l.cloneWorkspace(info.getId(), info.isGloballyReadable(), info.getModDate());
		}
		return info;
	}
	
	/** Lock a workspace, preventing further changes other than making the workspace globally
	 * readable.
	 * @param user the user locking the workspace.
	 * @param wsi the workspace.
	 * @return information about the workspace.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 * @throws NoSuchWorkspaceException if the workspace does not exist or is deleted.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to lock the workspace.
	 */
	public WorkspaceInformation lockWorkspace(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.ADMIN)
				.withOperation("lock").check();
		final Instant time = db.lockWorkspace(wsid);
		for (final WorkspaceEventListener l: listeners) {
			l.lockWorkspace(wsid.getID(), time);
		}
		return db.getWorkspaceInformation(user, wsid);
	}

	private String pruneWorkspaceDescription(final String description) {
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			return description.substring(0, MAX_WS_DESCRIPTION);
		}
		return description;
	}

	public void setWorkspaceDescription(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String description)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.ADMIN)
				.withOperation("set description on").check();
		final Instant time = db.setWorkspaceDescription(
				wsid, pruneWorkspaceDescription(description));
		for (final WorkspaceEventListener l: listeners) {
			l.setWorkspaceDescription(wsid.getID(), time);
		}
	}
	
	public String getWorkspaceDescription(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.READ).check();
		return db.getWorkspaceDescription(wsid);
	}
	
	/** Change the owner of a workspace.
	 * @param owner the current owner.
	 * @param wsi the workspace.
	 * @param newUser the new owner.
	 * @param newName the new name for the workspace, if any.
	 * @param asAdmin run this command with admin privileges - e.g. the owner argument is ignored
	 * and the command proceeds whether the owner provided actually owns the workspace ornot.
	 * @return information about the workspace.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 * @throws NoSuchWorkspaceException if the workspace does not exist or is deleted.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to set the workspace
	 * owner.
	 */
	public WorkspaceInformation setWorkspaceOwner(
			WorkspaceUser owner,
			final WorkspaceIdentifier wsi,
			final WorkspaceUser newUser,
			Optional<String> newName,
			final boolean asAdmin)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		if (newUser == null) {
			throw new NullPointerException("newUser cannot be null");
		}
		if (wsi == null) {
			throw new NullPointerException("wsi cannot be null");
		}
		if (newName == null) {
			throw new NullPointerException("newName");
		}
		final ResolvedWorkspaceID rwsi;
		if (asAdmin) {
			rwsi = db.resolveWorkspace(wsi);
			owner = db.getWorkspaceOwner(rwsi);
		} else {
			rwsi = new PermissionsCheckerFactory(db, owner)
					.getWorkspaceChecker(wsi, Permission.OWNER)
					.withOperation("change the owner of").check();
		}
		final Permission p = db.getPermission(newUser, rwsi);
		if (p.equals(Permission.OWNER)) {
			throw new IllegalArgumentException(newUser.getUser() +
					" already owns workspace " + rwsi.getName());
		}
		if (!newName.isPresent()) {
			final String[] oldWsName = WorkspaceIdentifier.splitUser(rwsi.getName());
			if (oldWsName[0] != null) { //includes user name
				newName = Optional.of(newUser.getUser() + WorkspaceIdentifier.WS_NAME_DELIMITER +
						oldWsName[1]);
			} // else don't change the name
		} else {
			new WorkspaceIdentifier(newName.get(), newUser); //checks for illegal names
			if (newName.get().equals(rwsi.getName())) {
				newName = Optional.absent(); // no need to change name
			}
		}
		final Instant time = db.setWorkspaceOwner(rwsi, owner, newUser, newName);
		for (final WorkspaceEventListener l: listeners) {
			l.setWorkspaceOwner(rwsi.getID(), newUser, newName, time);
		}
		return db.getWorkspaceInformation(newUser, rwsi);
	}
			

	public long setPermissions(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceUser> users,
			final Permission permission)
			throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		return setPermissions(user, wsi, users, permission, false);
	}
	
	public long setPermissions(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final List<WorkspaceUser> users,
			final Permission permission,
			final boolean asAdmin)
			throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		if (users == null || users.isEmpty()) {
			throw new IllegalArgumentException(
					"The users list may not be null or empty");
		}
		if (Permission.OWNER.compareTo(permission) <= 0) {
			throw new IllegalArgumentException("Cannot set owner permission");
		}
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi);
		final Permission currentPerm = asAdmin ? Permission.ADMIN :
				db.getPermissions(user, wsid).getUserPermission(wsid);
		if (currentPerm.equals(Permission.NONE)) {
			//always throw exception here
			new PermissionsCheckerFactory(db, user).getWorkspaceChecker(wsi, Permission.ADMIN)
					.withOperation("set permissions on").check();
		}
		if (Permission.ADMIN.compareTo(currentPerm) > 0) {
			if (!users.equals(Arrays.asList(user))) {
				throw new WorkspaceAuthorizationException(String.format(
						"User %s may not alter other user's permissions on workspace %s",
						user.getUser(), wsi.getIdentifierString()));
			}
			if (currentPerm.compareTo(permission) < 0) {
				throw new WorkspaceAuthorizationException(String.format(
						"User %s may only reduce their permission level on workspace %s",
						user.getUser(), wsi.getIdentifierString()));
			}
		}
		final Instant time = db.setPermissions(wsid, users, permission);
		for (final WorkspaceEventListener l: listeners) {
			l.setPermissions(wsid.getID(), permission, users, time);
		}
		return wsid.getID();
	}
	
	/** Set the global permission (e.g. readable or not) for a workspace.
	 * @param user the user setting the permission.
	 * @param wsi the workspace.
	 * @param permission the new permission.
	 * @return the ID of the modified workspace.
	 * @throws NoSuchWorkspaceException if there is no such workspace or the workspace is deleted.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to access the
	 * workspace.
	 */
	public long setGlobalPermission(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final Permission permission)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceAuthorizationException, WorkspaceCommunicationException {
		//manual check perms to avoid lock check
		if (wsi == null) {
			throw new IllegalArgumentException(
					"Workspace identifier cannot be null");
		}
		if (Permission.READ.compareTo(permission) < 0) {
			throw new IllegalArgumentException(
					"Global permissions cannot be greater than read");
		}
		final ResolvedWorkspaceID rwsi = db.resolveWorkspace(wsi);
		final Permission userperm = db.getPermission(user, rwsi);
		PermissionsCheckerFactory.comparePermission(
				user, Permission.ADMIN, userperm, wsi, "set global permission on");
		if (Permission.NONE.equals(permission) && rwsi.isLocked()) {
			throw new WorkspaceAuthorizationException("The workspace with id "
					+ rwsi.getID() + ", name " + rwsi.getName() +
					", is locked and may not be modified");
		}
		final Instant time = db.setGlobalPermission(rwsi, permission);
		for (final WorkspaceEventListener l: listeners) {
			l.setGlobalPermission(rwsi.getID(), permission, time);
		}
		return rwsi.getID();
	}

	//TODO USERS make an anonymous user class instead of using null.
	//TODO WORKSPACES consider a single method that returns all workspace info in a class. Probably performance difference is trivial compared to multiple methods.
	
	/** Get user permissions for a set of workspaces. If the user has at least write permission
	 * to a particular workspace, all permissions for the workspace will be returned.
	 * @param user the user for which permissions will be returned, or null for an anonymous user.
	 * @param wslist the list of workspaces.
	 * @return a list of workspace permissions ordered as the incoming list.
	 * @throws NoSuchWorkspaceException if one or more of the workspaces does not exist.
	 * @throws WorkspaceCommunicationException if a communication error occurred when contacting
	 * the storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the workspace.
	 */
	public List<Map<User, Permission>> getPermissions(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wslist)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		return getPermissions(user, wslist, false);
	}
	
	/** Get user permissions for a set of workspaces as an administrator. Returns all permissions
	 * for all workspaces.
	 * @param wslist the list of workspaces.
	 * @return a list of workspace permissions ordered as the incoming list.
	 * @throws NoSuchWorkspaceException if one or more of the workspaces does not exist.
	 * @throws WorkspaceCommunicationException if a communication error occurred when contacting
	 * the storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the workspace.
	 */
	public List<Map<User, Permission>> getPermissionsAsAdmin(
			final List<WorkspaceIdentifier> wslist)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		return getPermissions(null, wslist, true);
	}
	
	private List<Map<User, Permission>> getPermissions(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wslist,
			final boolean asAdmin)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		if (wslist == null) { //TODO CODE copy non null from auth2
			throw new NullPointerException("wslist cannot be null");
		}
		if (wslist.size() > MAX_WS_COUNT) {
			throw new IllegalArgumentException(
					"Maximum number of workspaces allowed for input is " + MAX_WS_COUNT);
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwslist =
				db.resolveWorkspaces(new HashSet<WorkspaceIdentifier>(wslist));
		final Map<ResolvedWorkspaceID, Map<User, Permission>> perms =
				db.getAllPermissions(new HashSet<ResolvedWorkspaceID>(
						rwslist.values()));
		final List<Map<User, Permission>> ret = new LinkedList<Map<User,Permission>>();
		for (final WorkspaceIdentifier wsi: wslist) {
			final ResolvedWorkspaceID rwsi = rwslist.get(wsi);
			final Map<User, Permission> wsperm = perms.get(rwsi);
			// if user is null, got perm will be null
			final Permission p = wsperm.get(user) == null ? Permission.NONE : wsperm.get(user);
			if (asAdmin || Permission.WRITE.compareTo(p) <= 0) { //at least write perms
				ret.add(wsperm);
			} else {
				final Map<User, Permission> wsp = new HashMap<User, Permission>();
				if (wsperm.containsKey(ALL_USERS)) {
					wsp.put(ALL_USERS, wsperm.get(ALL_USERS));
				}
				if (user != null) {
					wsp.put(user, p);
				}
				ret.add(wsp);
			}
		}
		return ret;
	}

	/** Get information about a workspace.
	 * @param user the user that is attempting to access the information.
	 * @param wsi the workspace.
	 * @return the workspace information.
	 * @throws NoSuchWorkspaceException if there is no such workspace or the workspace is deleted.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to access the
	 * workspace.
	 */
	public WorkspaceInformation getWorkspaceInformation(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.READ).check();
		return db.getWorkspaceInformation(user, wsid);
	}
	
	/** Get information about a workspace as an admin. The user permission returned will always
	 * be NONE.
	 * @param wsi the workspace.
	 * @return the workspace information.
	 * @throws NoSuchWorkspaceException if there is no such workspace or the workspace is deleted.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 */
	public WorkspaceInformation getWorkspaceInformationAsAdmin(final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
				CorruptWorkspaceDBException {
		nonNull(wsi, "Workspace identifier cannot be null");
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi);
		return db.getWorkspaceInformation(null, wsid);
	}
	
	private static String getObjectErrorId(final WorkspaceSaveObject wo, final int objcount) {
		return getObjectErrorId(wo.getObjectIdentifier(), objcount);
	}
	
	private static String getObjectErrorId(final ObjectIDNoWSNoVer oi, final int objcount) {
		return "#" + objcount +  ", " + oi.getIdentifierString();
	}
	
	private static class IDAssociation {
		final int objnum;
		final boolean provenance;
		
		public IDAssociation(int objnum, boolean provenance) {
			super();
			this.objnum = objnum;
			this.provenance = provenance;
		}

		@Override
		public String toString() {
			return "IDAssociation [objnum=" + objnum + ", provenance="
					+ provenance + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + objnum;
			result = prime * result + (provenance ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			IDAssociation other = (IDAssociation) obj;
			if (objnum != other.objnum)
				return false;
			if (provenance != other.provenance)
				return false;
			return true;
		}
	}
	/** Note adds own handler factory for type ws */
	public List<ObjectInformation> saveObjects(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi, 
			List<WorkspaceSaveObject> objects,
			final IdReferenceHandlerSetFactory idHandlerFac) throws
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException, TypedObjectValidationException,
			TypeStorageException, IOException, TypedObjectSchemaException {
		if (objects.isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		final ResolvedWorkspaceID rwsi = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.WRITE).check();
		idHandlerFac.addFactory(getHandlerFactory(user));
		final IdReferenceHandlerSet<IDAssociation> idhandler =
				idHandlerFac.createHandlers(IDAssociation.class);
		
		final Map<WorkspaceSaveObject, ValidatedTypedObject> reports = 
				validateObjectsAndExtractReferences(objects, idhandler);
		
		processIds(objects, idhandler, reports);
		
		//handle references and calculate size with new references
		final List<ResolvedSaveObject> saveobjs = new ArrayList<ResolvedSaveObject>();
		long ttlObjSize = 0;
		int objcount = 1;
		for (WorkspaceSaveObject wo: objects) {
			//maintain ordering
			wo.getProvenance().setWorkspaceID(new Long(rwsi.getID()));
			final List<Reference> provrefs = new LinkedList<Reference>();
			for (final Provenance.ProvenanceAction action: wo.getProvenance().getActions()) {
				for (final String ref: action.getWorkspaceObjects()) {
					provrefs.add((Reference) idhandler.getRemappedId(WS_ID_TYPE, ref));
				}
			}
			final Map<IdReferenceType, Set<RemappedId>> extractedIDs =
					new HashMap<IdReferenceType, Set<RemappedId>>();
			for (final IdReferenceType irt: idhandler.getIDTypes()) {
				if (!WS_ID_TYPE.equals(irt)) {
					final Set<RemappedId> ids = idhandler.getRemappedIds(
							irt, new IDAssociation(objcount, false));
					if (!ids.isEmpty()) {
						extractedIDs.put(irt, ids);
					}
				}
			}
			final Set<RemappedId> refids = idhandler.getRemappedIds(
					WS_ID_TYPE, new IDAssociation(objcount, false));
			final Set<Reference> refs = new HashSet<Reference>();
			for (final RemappedId id: refids) {
				refs.add((Reference) id);
			}
			
			final ValidatedTypedObject rep = reports.get(wo);
			saveobjs.add(wo.resolve(rep, refs, provrefs, extractedIDs));
			ttlObjSize += rep.calculateRelabeledSize();
			if (rep.getRelabeledSize() > rescfg.getMaxObjectSize()) {
				throw new IllegalArgumentException(String.format(
						"Object %s data size %s exceeds limit of %s",
						getObjectErrorId(wo.getObjectIdentifier(), objcount),
						rep.getRelabeledSize(),
						rescfg.getMaxObjectSize()));
			}
			objcount++;
		}
		objects = null;
		reports.clear();
		
		final WorkspaceInformation wsinfo = db.getWorkspaceInformation(user, rwsi);
		
		try {
			sortObjects(saveobjs, ttlObjSize);
			final List<ObjectInformation> ret = db.saveObjects(user, rwsi, saveobjs);
			for (final WorkspaceEventListener l: listeners) {
				for (final ObjectInformation oi: ret) {
					l.saveObject(oi, wsinfo.isGloballyReadable());
				}
			}
			return ret;
		} finally {
			for (final ResolvedSaveObject wo: saveobjs) {
				try {
					wo.getRep().destroyCachedResources();
				} catch (RuntimeException | Error e) {
					//damn the torpedoes full speed ahead
				}
			}
		}
	}

	private void sortObjects(
			final List<ResolvedSaveObject> saveobjs,
			final long ttlObjSize)
			throws IOException, TypedObjectValidationException {
		int objcount = 1;
		final TempFilesManager tempTFM;
		if (ttlObjSize > rescfg.getMaxIncomingDataMemoryUsage()) {
			tempTFM = getTempFilesManager();
		} else {
			tempTFM = null;
		}
		final UTF8JsonSorterFactory fac = new UTF8JsonSorterFactory(
				rescfg.getMaxRelabelAndSortMemoryUsage());
		for (final ResolvedSaveObject ro: saveobjs) {
			try {
				//modifies object in place
				ro.getRep().sort(fac, tempTFM);
			} catch (KeyDuplicationException kde) {
				/* this occurs when two references in the same hash resolve
				 * to the same reference, so one value would be lost
				 */
				throw new TypedObjectValidationException(String.format(
						"Object %s: Two references in a single hash are identical when resolved, resulting in a loss of data: ",
						getObjectErrorId(ro.getObjectIdentifier(), objcount))
						+ kde.getLocalizedMessage(), kde);
			} catch (TooManyKeysException tmke) {
				throw new TypedObjectValidationException(String.format(
						"Object %s: ",
						getObjectErrorId(ro.getObjectIdentifier(), objcount))
						+ tmke.getLocalizedMessage(), tmke);
			}
			objcount++;
		}
	}

	private Map<WorkspaceSaveObject, ValidatedTypedObject>
			validateObjectsAndExtractReferences(
			final List<WorkspaceSaveObject> objects,
			final IdReferenceHandlerSet<IDAssociation> idhandler)
			throws TypeStorageException, TypedObjectSchemaException,
			TypedObjectValidationException {
		final Map<WorkspaceSaveObject, ValidatedTypedObject> reports = 
				new HashMap<WorkspaceSaveObject, ValidatedTypedObject>();
		int objcount = 1;
		for (final WorkspaceSaveObject wo: objects) {
			idhandler.associateObject(new IDAssociation(objcount, false));
			final ValidatedTypedObject rep = validate(wo, idhandler, objcount);
			reports.put(wo, rep);
			idhandler.associateObject(new IDAssociation(objcount, true));
			try {
				for (final Provenance.ProvenanceAction action: wo.getProvenance().getActions()) {
					for (final String pref: action.getWorkspaceObjects()) {
						if (pref == null) {
							throw new TypedObjectValidationException(String.format(
									"Object %s has a null provenance reference",
									getObjectErrorId(wo, objcount)));
						}
						idhandler.addStringId(new IdReference<String>(WS_ID_TYPE, pref, null));
					}
				}
			} catch (IdReferenceHandlerException ihre) {
				throw new TypedObjectValidationException(String.format(
						"Object %s has invalid provenance reference: ",
						getObjectErrorId(wo, objcount)) + ihre.getMessage(), ihre);
			} catch (TooManyIdsException tmie) {
				throw wrapTooManyIDsException(objcount, idhandler, tmie);
			}
			objcount++;
		}
		return reports;
	}

	private void processIds(
			final List<WorkspaceSaveObject> objects,
			final IdReferenceHandlerSet<IDAssociation> idhandler,
			final Map<WorkspaceSaveObject, ValidatedTypedObject> reports)
			throws TypedObjectValidationException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		try {
			idhandler.processIDs();
		} catch (IdParseException ipe) {
			final IDAssociation idloc = (IDAssociation) ipe.getAssociatedObject();
			final WorkspaceSaveObject wo = objects.get(idloc.objnum - 1);
			throw new TypedObjectValidationException(String.format(
					"Object %s has unparseable %sreference %s: %s%s",
					getObjectErrorId(wo, idloc.objnum),
					(idloc.provenance ? "provenance " : ""),
					ipe.getId(),
					ipe.getMessage(),
					idloc.provenance ? "" : " at " +
							getIDPath(reports.get(wo), ipe.getIdReference())),
					ipe);
		} catch (IdReferenceException ire) {
			final IDAssociation idloc = (IDAssociation) ire.getAssociatedObject();
			final WorkspaceSaveObject wo = objects.get(idloc.objnum - 1);
			throw new TypedObjectValidationException(String.format(
					"Object %s has invalid %sreference: %s%s",
					getObjectErrorId(wo, idloc.objnum),
					(idloc.provenance ? "provenance " : ""),
					ire.getMessage(),
					idloc.provenance ? "" : " at " +
							getIDPath(reports.get(wo), ire.getIdReference())),
					ire);
		} catch (IdReferenceHandlerException irhe) {
			if (irhe.getCause() instanceof WorkspaceCommunicationException) {
				throw (WorkspaceCommunicationException) irhe.getCause();
			} else if (irhe.getCause() instanceof CorruptWorkspaceDBException) {
				throw (CorruptWorkspaceDBException) irhe.getCause();
			} else {
				throw new TypedObjectValidationException(
						"An error occured while processing IDs: " + irhe.getMessage(), irhe);
			}
		}
	}

	private String getIDPath(ValidatedTypedObject r,
			IdReference<String> idReference) {
		try {
			final JsonDocumentLocation loc = r.getIdReferenceLocation(
					idReference);
			if (loc == null) {
				return "[An error occured when attemping to get the " +
						"location of the id. Please report this to the " +
						"server admin or help desk]";
			} else {
				return loc.getFullLocationAsString();
			}
		} catch (IOException ioe) {
			return "[IO error getting path]";
		}
	}

	private ValidatedTypedObject validate(
			final WorkspaceSaveObject wo,
			final IdReferenceHandlerSet<IDAssociation> idhandler,
			final int objcount)
			throws TypeStorageException, TypedObjectSchemaException,
			TypedObjectValidationException {
		final ValidatedTypedObject rep;
		try {
			rep = validator.validate(wo.getData(), wo.getType(), idhandler);
		} catch (NoSuchTypeException nste) {
			throw new TypedObjectValidationException(String.format(
					"Object %s failed type checking:\n",
					getObjectErrorId(wo, objcount))
					+ nste.getLocalizedMessage(), nste);
		} catch (NoSuchModuleException nsme) {
			throw new TypedObjectValidationException(String.format(
					"Object %s failed type checking:\n",
					getObjectErrorId(wo, objcount))
					+ nsme.getLocalizedMessage(), nsme);
		} catch (TooManyIdsException e) {
			throw wrapTooManyIDsException(objcount, idhandler, e);
		} catch (JsonParseException jpe) {
			throw new TypedObjectValidationException(String.format(
					"Object %s failed type checking ",
					getObjectErrorId(wo, objcount)) + 
					"- a fatal JSON processing error occurred: "
					+ jpe.getMessage(), jpe);
		} catch (IOException ioe) {
			throw new TypedObjectValidationException(String.format(
					"A fatal IO error occured while type checking object %s: ",
					getObjectErrorId(wo, objcount)) + ioe.getMessage(), ioe);
		}
		if (!rep.isInstanceValid()) {
			final List<String> e = rep.getErrorMessages();
			final String err = StringUtils.join(e, "\n");
			throw new TypedObjectValidationException(String.format(
					"Object %s failed type checking:\n",
					getObjectErrorId(wo, objcount)) + err);
		}
		return rep;
	}
	
	private TypedObjectValidationException wrapTooManyIDsException(
			final int objcount,
			final IdReferenceHandlerSet<IDAssociation> idhandler,
			final TooManyIdsException e) {
		return new TypedObjectValidationException(String.format(
				"Failed type checking at object #%s - the number of " +
				"unique IDs in the saved objects exceeds the maximum " +
				"allowed, %s",
				objcount, idhandler.getMaximumIdCount()), e);
	}

	//should definitely make an options builder
	public List<WorkspaceInformation> listWorkspaces(
			final WorkspaceUser user,
			Permission minPerm,
			final List<WorkspaceUser> users,
			final WorkspaceUserMetadata meta,
			final Date after,
			final Date before,
			final boolean excludeGlobal,
			final boolean showDeleted,
			final boolean showOnlyDeleted)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		if (minPerm == null || Permission.READ.compareTo(minPerm) > 0) {
			minPerm = Permission.READ;
		}
		if (meta != null && meta.size() > 1) {
			throw new IllegalArgumentException("Only one metadata spec allowed");
		}
		final PermissionSet perms = db.getPermissions(user, minPerm, excludeGlobal);
		return db.getWorkspaceInformation(perms, users, meta, after, before,
				showDeleted, showOnlyDeleted);
	}
	
	/** List workspace IDs to which a user has access. Returns much less data than
	 * {@link #listWorkspaces(WorkspaceUser, Permission, List, WorkspaceUserMetadata, Date, Date, boolean, boolean, boolean)}
	 * and should be faster.
	 * @param user the user for which workspace IDs will be listed. If the user is null, only
	 * public workspace IDs will be returned.
	 * @param minPerm the minimum permission of the workspaces. READ will be used if minPerm is
	 * null or NONE. If the permission is greater than READ no public workspaces will be included.
	 * @param excludeGlobal don't include public workspaces in the results.
	 * @return the workspace IDs.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 */
	public UserWorkspaceIDs listWorkspaceIDs(
			final WorkspaceUser user,
			Permission minPerm,
			final boolean excludeGlobal)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (minPerm == null || Permission.READ.compareTo(minPerm) > 0) {
			minPerm = Permission.READ;
		}
		final PermissionSet perms = db.getPermissions(
				user, null, minPerm, excludeGlobal, true, false);
		final List<Long> workspaceIDs = new LinkedList<>();
		final List<Long> publicIDs = new LinkedList<>();
		for (final ResolvedWorkspaceID ws: perms.getWorkspaces()) {
			if (perms.getUserPermission(ws).equals(Permission.NONE)) {
				publicIDs.add(ws.getID());
			} else {
				workspaceIDs.add(ws.getID());
			}
		}
		return new UserWorkspaceIDs(user, minPerm, workspaceIDs, publicIDs);
	}
	
	public List<ObjectInformation> listObjects(
			final ListObjectsParameters params)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceCommunicationException, WorkspaceAuthorizationException {

		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				db.resolveWorkspaces(params.getWorkspaces());
		final HashSet<ResolvedWorkspaceID> rw = new HashSet<ResolvedWorkspaceID>(rwsis.values());
		final PermissionSet pset = db.getPermissions(params.getUser(), rw,
				params.getMinimumPermission(), params.isExcludeGlobal(), true, params.asAdmin());
		rw.clear();
		if (!params.asAdmin()) {
			for (final WorkspaceIdentifier wsi: params.getWorkspaces()) {
				PermissionsCheckerFactory.comparePermission(params.getUser(), Permission.READ,
						pset.getPermission(rwsis.get(wsi)), wsi, "read");
			}
		}
		return db.getObjectInformation(params.generateParameters(pset));
	}
	
	public List<WorkspaceObjectData> getObjects(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi)
			throws CorruptWorkspaceDBException,
				WorkspaceCommunicationException, InaccessibleObjectException,
				NoSuchReferenceException, TypedObjectExtractionException,
				ReferenceSearchMaximumSizeExceededException, NoSuchObjectException {
			
		return getObjects(user, loi, false);
	}
	
	public List<WorkspaceObjectData> getObjects(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final boolean noData)
			throws CorruptWorkspaceDBException,
				WorkspaceCommunicationException, InaccessibleObjectException,
				NoSuchReferenceException, TypedObjectExtractionException,
				ReferenceSearchMaximumSizeExceededException, NoSuchObjectException {
		return getObjects(user, loi, noData, false, false);
	}
	
	public List<WorkspaceObjectData> getObjects(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final boolean noData,
			final boolean nullIfInaccessible,
			final boolean asAdmin)
			throws CorruptWorkspaceDBException,
				WorkspaceCommunicationException, InaccessibleObjectException,
				NoSuchReferenceException, TypedObjectExtractionException,
				ReferenceSearchMaximumSizeExceededException, NoSuchObjectException {
		
		final ObjectResolver.Builder orb = ObjectResolver.getBuilder(db, user)
				.withIgnoreInaccessible(nullIfInaccessible)
				.withAsAdmin(asAdmin)
				.withMaximumObjectsSearched(maximumObjectSearchCount);
		for (final ObjectIdentifier oi: loi) {
			orb.withObject(oi);
		}
		ObjectResolver res = orb.resolve();
		
		final Map<ObjectIDResolvedWS, Set<SubsetSelection>> refpaths =
				setupObjectPaths(res.getObjects(true), res);
		final Map<ObjectIDResolvedWS, Set<SubsetSelection>> stdpaths =
				setupObjectPaths(res.getObjects(false), res);
		
		//TODO CODE make an overall resource manager that takes the config as an arg and handles returned data as well as mem & file limits 
		final ByteArrayFileCacheManager dataMan = getDataManager(noData);
		
		//this is pretty gross, think about a better api here
		Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>> stddata = null;
		Map<ObjectIDResolvedWS, Map<SubsetSelection, WorkspaceObjectData>> refdata = null;
		try {
			stddata = db.getObjects(stdpaths, dataMan, 0,
					!nullIfInaccessible, false, !nullIfInaccessible);
			refdata = db.getObjects(refpaths, dataMan, calculateDataSize(stddata),
					//objects cannot be missing at this stage
					false, true, true);
			
			refpaths.clear();
			stdpaths.clear();
			
			final List<WorkspaceObjectData> ret = new ArrayList<>();
			for (final ObjectIdentifier o: loi) {
				final SubsetSelection ss;
				if (o instanceof ObjIDWithRefPathAndSubset) {
					ss = ((ObjIDWithRefPathAndSubset) o).getSubSet();
				} else {
					ss = SubsetSelection.EMPTY;
				}
				final WorkspaceObjectData wod;
				final ObjectResolution objres = res.getObjectResolution(o);
				if (objres.equals(ObjectResolution.INACCESSIBLE)) {
					wod = null;
				} else {
					final ObjectIDResolvedWS resobj = res.getResolvedObject(o);
					if (objres.equals(ObjectResolution.NO_PATH)) {
						if (stddata.containsKey(resobj)) {
							wod = stddata.get(resobj).get(ss);
						} else {
							wod = null; //object was deleted or missing
						}
					} else {
						// since was resolved by path, object must exist
						final WorkspaceObjectData prewod = refdata.get(resobj).get(ss);
						wod = prewod.updateObjectReferencePath(res.getReferencePath(o));
					}
				}
				ret.add(wod);
			}
			res = null;
			refdata.clear();
			stddata.clear();
			removeInaccessibleDataCopyReferences(user, ret);
			return ret;
		} catch (RuntimeException | Error | CorruptWorkspaceDBException |
				WorkspaceCommunicationException | NoSuchObjectException |
				TypedObjectExtractionException e) {
			destroyGetObjectsResources(stddata);
			destroyGetObjectsResources(refdata);
			throw e;
		}
	}

	private void destroyGetObjectsResources(
			final Map<ObjectIDResolvedWS, Map<SubsetSelection,
					WorkspaceObjectData>> data) {
		if (data == null) {
			return;
		}
		for (final Map<SubsetSelection, WorkspaceObjectData> paths:
				data.values()) {
			for (final WorkspaceObjectData d: paths.values()) {
				try {
					d.destroy();
				} catch (RuntimeException | Error e) {
					//continue
				}
			}
		}
	}

	private long calculateDataSize(
			final Map<ObjectIDResolvedWS, Map<SubsetSelection,
				WorkspaceObjectData>> stddata) {
		long dataSize = 0;
		for (final Map<SubsetSelection, WorkspaceObjectData> paths:
				stddata.values()) {
			for (final WorkspaceObjectData d: paths.values()) {
				if (d.hasData()) {
					dataSize += d.getSerializedData().getSize();
				}
			}
			
		}
		return dataSize;
	}

	private ByteArrayFileCacheManager getDataManager(final boolean noData) {
		if (noData) {
			return null;
		} else {
			return new ByteArrayFileCacheManager(
					rescfg.getMaxReturnedDataMemoryUsage(),
					/* maximum possible disk usage is when subsetting objects
					 * summing to 1G, since we have to pull the 1G objects and
					 * then subset which could take up to another 1G. The 1G
					 * originals will then be discarded
					 */
					rescfg.getMaxReturnedDataSize() * 2L,
					db.getTempFilesManager());
		}
	}

	private Map<ObjectIDResolvedWS, Set<SubsetSelection>> setupObjectPaths(
			final Set<ObjectIdentifier> objects,
			final ObjectResolver res) {
		final Map<ObjectIDResolvedWS, Set<SubsetSelection>> paths =
				new HashMap<ObjectIDResolvedWS, Set<SubsetSelection>>();
		for (final ObjectIdentifier o: objects) {
			final ObjectIDResolvedWS roi = res.getResolvedObject(o);
			if (!paths.containsKey(roi)) {
				paths.put(roi, new HashSet<SubsetSelection>());
			}
			if (o instanceof ObjIDWithRefPathAndSubset) {
				paths.get(roi).add(((ObjIDWithRefPathAndSubset) o).getSubSet());
			} else {
				paths.get(roi).add(SubsetSelection.EMPTY);
			}
		}
		return paths;
	}

	private void removeInaccessibleDataCopyReferences(
			final WorkspaceUser user,
			final List<WorkspaceObjectData> data)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		
		final Set<WorkspaceIdentifier> wsis =
				new HashSet<WorkspaceIdentifier>();
		for (final WorkspaceObjectData d: data) {
			if (d != null && d.getCopyReference() != null) {
				wsis.add(new WorkspaceIdentifier(
						d.getCopyReference().getWorkspaceID()));
			}
		}
		if (wsis.isEmpty()) {
			return;
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis;
		try {
			rwsis = db.resolveWorkspaces(wsis, true);
		} catch (NoSuchWorkspaceException nswe) {
			throw new RuntimeException(
					"Threw exception when explicitly told not to", nswe);
		}
		Iterator<Entry<WorkspaceIdentifier, ResolvedWorkspaceID>> i =
				rwsis.entrySet().iterator();
		while (i.hasNext()) {
			if (i.next().getValue().isDeleted()) {
				i.remove();
			}
		}
		
		//only includes workspaces that are at least readable
		final PermissionSet perms = db.getPermissions(user,
						new HashSet<ResolvedWorkspaceID>(rwsis.values()));
		i = rwsis.entrySet().iterator();
		while (i.hasNext()) {
			if (!perms.hasWorkspace(i.next().getValue())) {
				i.remove();
			}
		}
		
		final Map<WorkspaceObjectData, ObjectIDResolvedWS> rois =
				new HashMap<WorkspaceObjectData, ObjectIDResolvedWS>();
		for (final WorkspaceObjectData d: data) {
			if (d != null && d.getCopyReference() != null) {
				final Reference cref = d.getCopyReference();
				final WorkspaceIdentifier wsi = new WorkspaceIdentifier(cref.getWorkspaceID());
				if (!rwsis.containsKey(wsi)) {
					d.setCopySourceInaccessible();
				} else {
					rois.put(d, new ObjectIDResolvedWS(rwsis.get(wsi),
							cref.getObjectID(), cref.getVersion()));
				}
			}
		}
		
		final Map<ObjectIDResolvedWS, Boolean> objexists =
				db.getObjectExists(
						new HashSet<ObjectIDResolvedWS>(rois.values())); 
		
		for (final Entry<WorkspaceObjectData, ObjectIDResolvedWS> e:
				rois.entrySet()) {
			if (!objexists.get(e.getValue())) {
				e.getKey().setCopySourceInaccessible();
			}
		}
	}
	
	public List<Set<ObjectInformation>> getReferencingObjects(
			final WorkspaceUser user, final List<ObjectIdentifier> loi)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		//could combine these next two lines, but probably doesn't matter
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws =
				new PermissionsCheckerFactory(db, user).getObjectChecker(loi, Permission.READ)
						.check();
		final PermissionSet perms = db.getPermissions(user, Permission.READ, false);
		final Map<ObjectIDResolvedWS, Set<ObjectInformation>> refs = 
				db.getReferencingObjects(perms,
						new HashSet<ObjectIDResolvedWS>(ws.values()));
		
		final List<Set<ObjectInformation>> ret =
				new LinkedList<Set<ObjectInformation>>();
		for (final ObjectIdentifier o: loi) {
			ret.add(refs.get(ws.get(o)));
		}
		return ret;
	}
	
	/** @deprecated */
	public List<Integer> getReferencingObjectCounts(
			final WorkspaceUser user, final List<ObjectIdentifier> loi)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws =
				new PermissionsCheckerFactory(db, user).getObjectChecker(loi, Permission.READ)
					.check();
		final Map<ObjectIDResolvedWS, Integer> counts =
				db.getReferencingObjectCounts(
						new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<Integer> ret =
				new LinkedList<Integer>();
		for (final ObjectIdentifier o: loi) {
			ret.add(counts.get(ws.get(o)));
		}
		return ret;
	}
	
	/** Get all versions of an object.
	 * @param user the user making the request.
	 * @param oi the object to query.
	 * @return the versions of the object.
	 * @throws InaccessibleObjectException if the object is inaccessible.
	 * @throws NoSuchObjectException if there is no such object.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 */
	public List<ObjectInformation> getObjectHistory(
			final WorkspaceUser user,
			final ObjectIdentifier oi)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		return getObjectHistory(user, oi, false);
	}
	
	/** Get all versions of an object.
	 * @param user the user making the request.
	 * @param oi the object to query.
	 * @param asAdmin true if the user is acting as an administrator.
	 * @return the versions of the object.
	 * @throws InaccessibleObjectException if the object is inaccessible.
	 * @throws NoSuchObjectException if there is no such object.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 */
	public List<ObjectInformation> getObjectHistory(
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final boolean asAdmin)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
					CorruptWorkspaceDBException, NoSuchObjectException {
		final ObjectIDResolvedWS o = new PermissionsCheckerFactory(db, user)
						.getObjectChecker(oi, asAdmin ? Permission.NONE : Permission.READ).check();
		return db.getObjectHistory(o);
	}
	
	public List<ObjectInformation> getObjectInformation(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final boolean includeMetadata,
			final boolean nullIfInaccessible)
			throws WorkspaceCommunicationException,
				CorruptWorkspaceDBException, InaccessibleObjectException,
				NoSuchReferenceException, ReferenceSearchMaximumSizeExceededException,
				NoSuchObjectException {
		return getObjectInformation(user, loi, includeMetadata, nullIfInaccessible, false);
	}
	
	public List<ObjectInformation> getObjectInformation(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final boolean includeMetadata,
			final boolean nullIfInaccessible,
			final boolean asAdmin)
			throws WorkspaceCommunicationException,
				CorruptWorkspaceDBException, InaccessibleObjectException,
				NoSuchReferenceException, ReferenceSearchMaximumSizeExceededException,
				NoSuchObjectException {
	
		final ObjectResolver.Builder orb = ObjectResolver.getBuilder(db, user)
				.withIgnoreInaccessible(nullIfInaccessible)
				.withAsAdmin(asAdmin)
				.withMaximumObjectsSearched(maximumObjectSearchCount);
		for (final ObjectIdentifier oi: loi) {
			orb.withObject(oi);
		}
		final ObjectResolver res = orb.resolve();
		
		final Map<ObjectIDResolvedWS, ObjectInformation> stdmeta = db.getObjectInformation(
				res.getResolvedObjects(false),
				includeMetadata, !nullIfInaccessible, false, !nullIfInaccessible);
		
		final Map<ObjectIDResolvedWS, ObjectInformation> resmeta = db.getObjectInformation(
				res.getResolvedObjects(true), includeMetadata, false, true, true);
				// at this point the object at the chain end must exist
		
		final List<ObjectInformation> ret = new ArrayList<>();
		for (final ObjectIdentifier o: loi) {
			final ObjectResolution objres = res.getObjectResolution(o);
			if (objres.equals(ObjectResolution.INACCESSIBLE)) {
				ret.add(null);
			} else {
				final ObjectIDResolvedWS idres = res.getResolvedObject(o);
				if (objres.equals(ObjectResolution.NO_PATH)) {
					if (stdmeta.containsKey(idres)) {
						ret.add(stdmeta.get(idres));
					} else {
						ret.add(null); // object was deleted  or didn't exist
					}
				} else {
					// resolution was with a path, which guarantees that the object exists
					ret.add(resmeta.get(idres).updateReferencePath(res.getReferencePath(o)));
				}
			}
		}
		return ret;
	}

	/** Get object names based on a provided prefix. Returns at most 1000
	 * names in no particular order. Intended for use as an auto-completion
	 * method.
	 * @param user the user requesting names.
	 * @param wsis the workspaces in which to look for names.
	 * @param prefix the prefix returned names must have.
	 * @param includeHidden include hidden objects in the output.
	 * @param limit the maximum number of names to return, at most 1000.
	 * @return list of workspace names, listed by workspace in order of the 
	 * input workspace list.
	 * @throws NoSuchWorkspaceException if an input workspace does not exist.
	 * @throws WorkspaceCommunicationException if a communication error with
	 * the backend database occurs.
	 * @throws CorruptWorkspaceDBException if there is a data error in the
	 * database
	 * @throws WorkspaceAuthorizationException if the user is not authorized
	 * to read one of the input workspaces.
	 */
	public List<List<String>> getNamesByPrefix(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final String prefix,
			final boolean includeHidden,
			final int limit)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		if (wsis == null) {
			throw new NullPointerException("Workspace list cannot be null");
		}
		if (wsis.size() > MAX_WS_COUNT) {
			throw new IllegalArgumentException(
					"Maximum number of workspaces allowed for input is " +
							MAX_WS_COUNT);
		}
		if (prefix == null) {
			throw new NullPointerException("prefix cannot be null");
		}
		if (limit > NAME_LIMIT) {
			throw new IllegalArgumentException(
					"limit cannot be greater than " + NAME_LIMIT);
		}
		if (wsis.isEmpty()) {
			return new LinkedList<>();
		}
		
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				new PermissionsCheckerFactory(db, user).getWorkspaceChecker(wsis, Permission.READ)
					.check();
		final Map<ResolvedWorkspaceID, List<String>> names =
				db.getNamesByPrefix(
						new HashSet<ResolvedWorkspaceID>(rwsis.values()),
						prefix, includeHidden, limit);
		final List<List<String>> ret = new LinkedList<List<String>>();
		for (final WorkspaceIdentifier wi: wsis) {
			final ResolvedWorkspaceID rwi = rwsis.get(wi);
			if (!names.containsKey(rwi)) {
				ret.add(new LinkedList<String>());
			} else {
				ret.add(names.get(rwi));
			}
		}
		return ret;
	}
	
	/** Rename a workspace.
	 * @param user the user performing the rename.
	 * @param wsi the workspace.
	 * @param newname the new name for the workspace.
	 * @return information about the workspace.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the database.
	 * @throws NoSuchWorkspaceException if the workspace does not exist or is deleted.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to rename the
	 * workspace.
	 */
	public WorkspaceInformation renameWorkspace(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final String newname)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = new PermissionsCheckerFactory(db, user)
				.getWorkspaceChecker(wsi, Permission.OWNER).withOperation("rename").check();
		new WorkspaceIdentifier(newname, user); //check for errors
		final Instant time = db.renameWorkspace(wsid, newname);
		for (final WorkspaceEventListener l: listeners) {
			l.renameWorkspace(wsid.getID(), newname, time);
		}
		return db.getWorkspaceInformation(user, wsid);
	}
	
	public ObjectInformation renameObject(
			final WorkspaceUser user,
			final ObjectIdentifier oi,
			final String newname)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchObjectException {
		ObjectIDNoWSNoVer.checkObjectName(newname);
		final ObjectIDResolvedWS obj = new PermissionsCheckerFactory(db, user)
				.getObjectChecker(oi, Permission.WRITE)
				.withOperation("rename objects in").check();
		final ObjectInfoWithModDate objdate = db.renameObject(obj, newname);
		final ObjectInformation objinfo = objdate.getObjectInfo();
		for (final WorkspaceEventListener l: listeners) {
			l.renameObject(objinfo.getWorkspaceId(), objinfo.getObjectId(), newname,
					objdate.getModificationDate());
		}
		return objinfo;
	}
	
	public ObjectInformation copyObject(
			final WorkspaceUser user,
			final ObjectIdentifier from,
			final ObjectIdentifier to)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchObjectException {
		final ObjectIDResolvedWS f = new PermissionsCheckerFactory(db, user)
				.getObjectChecker(from, Permission.READ).check();
		final ObjectIDResolvedWS t = new PermissionsCheckerFactory(db, user)
				.getObjectChecker(to, Permission.WRITE).check();
		final CopyResult cr = db.copyObject(user, f, t);
		final ObjectInformation oi = cr.getObjectInformation();
		final WorkspaceInformation wsinfo = db.getWorkspaceInformation(
				user, t.getWorkspaceIdentifier());
		for (final WorkspaceEventListener l: listeners) {
			if (cr.isAllVersionsCopied()) {
				l.copyObject(oi.getWorkspaceId(), oi.getObjectId(), oi.getVersion(),
						oi.getSavedDate().toInstant(), wsinfo.isGloballyReadable());
			} else {
				l.copyObject(oi, wsinfo.isGloballyReadable());
			}
		}
		return oi;
	}
	
	public ObjectInformation revertObject(final WorkspaceUser user, final ObjectIdentifier oi)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchObjectException {
		final ObjectIDResolvedWS target = new PermissionsCheckerFactory(db, user)
				.getObjectChecker(oi, Permission.WRITE).check();
		final ObjectInformation objinfo = db.revertObject(user, target);
		final WorkspaceInformation wsinfo = db.getWorkspaceInformation(
				user, target.getWorkspaceIdentifier());
		for (final WorkspaceEventListener l: listeners) {
			l.revertObject(objinfo, wsinfo.isGloballyReadable());
		}
		return objinfo;
	}
	
	public void setObjectsHidden(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final boolean hide)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws =
				new PermissionsCheckerFactory(db, user)
						.getObjectChecker(loi, Permission.WRITE)
						.withOperation((hide ? "" : "un") + "hide objects from")
						.check();
		db.setObjectsHidden(new HashSet<ObjectIDResolvedWS>(ws.values()),
				hide);
	}
	
	public void setObjectsDeleted(final WorkspaceUser user,
			final List<ObjectIdentifier> loi, final boolean delete)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			InaccessibleObjectException, NoSuchObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws =
				new PermissionsCheckerFactory(db, user)
						.getObjectChecker(loi, Permission.WRITE)
						.withOperation((delete ? "" : "un") + "delete objects from")
						.check();
		final Map<ResolvedObjectIDNoVer, Instant> objs = db.setObjectsDeleted(
				new HashSet<ObjectIDResolvedWS>(ws.values()), delete);
		for (final WorkspaceEventListener l: listeners) {
			for (final ResolvedObjectIDNoVer o: objs.keySet()) {
				l.setObjectDeleted(o.getWorkspaceIdentifier().getID(), o.getId(), delete,
						objs.get(o));
			}
		}
	}
	
	/** Set the deletion state of a workspace.
	 * @param user the user requesting deletion or undeletion.
	 * @param wsi the workspace.
	 * @param delete true to delete, false to undelete.
	 * @return the ID of the workspace.
	 * @throws NoSuchWorkspaceException if there is no such workspace or the workspace is already
	 * deleted when trying to delete.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to access the
	 * workspace.
	 */
	public long setWorkspaceDeleted(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final boolean delete)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		return setWorkspaceDeleted(user, wsi, delete, false);
	}

	/** Set the deletion state of a workspace.
	 * @param user the user requesting deletion or undeletion.
	 * @param wsi the workspace.
	 * @param delete true to delete, false to undelete.
	 * @param asAdmin run the command as an admin, ignoring workspace permissions.
	 * @return the ID of the the workspace.
	 * @throws NoSuchWorkspaceException if there is no such workspace or the workspace is already
	 * deleted when trying to delete.
	 * @throws WorkspaceCommunicationException if a communication error occurs when contacting the
	 * storage system.
	 * @throws CorruptWorkspaceDBException if corrupt data is found in the storage system.
	 * @throws WorkspaceAuthorizationException if the user is not authorized to access the
	 * workspace.
	 */
	public long setWorkspaceDeleted(
			final WorkspaceUser user,
			final WorkspaceIdentifier wsi,
			final boolean delete,
			final boolean asAdmin)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
				WorkspaceCommunicationException, WorkspaceAuthorizationException {
		if (wsi == null) {
			throw new IllegalArgumentException("Workspace identifier cannot be null");
		}
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi, !delete);
		if (!asAdmin) {
			// skip deletion checking for std checkPerms methods.
			final Permission perm = db.getPermission(user, wsid);
			PermissionsCheckerFactory.comparePermission(
					user, Permission.OWNER, perm, wsi, (delete ? "" : "un") + "delete");
		}
		// once a workpace is locked, it's locked. Period.
		PermissionsCheckerFactory.checkLocked(Permission.ADMIN, wsid);
		final Instant time = db.setWorkspaceDeleted(wsid, delete);
		final WorkspaceInformation wsinfo = db.getWorkspaceInformation(user, wsid);
		for (final WorkspaceEventListener l: listeners) {
			l.setWorkspaceDeleted(wsid.getID(), delete, wsinfo.getMaximumObjectID(), time);
		}
		return wsid.getID();
	}

	/* admin method only, should not be exposed in public API
	 */
	public Set<WorkspaceUser> getAllWorkspaceOwners()
			throws WorkspaceCommunicationException {
		return db.getAllWorkspaceOwners();
	}
	
	/* these admin functions are provided as a convenience and have nothing
	 * to do with the rest of the DB, really. 
	 */
	public boolean isAdmin(WorkspaceUser putativeAdmin)
			throws WorkspaceCommunicationException {
		return db.isAdmin(putativeAdmin);
	}

	public Set<WorkspaceUser> getAdmins()
			throws WorkspaceCommunicationException {
		return db.getAdmins();
	}

	public void removeAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException {
		db.removeAdmin(user);
	}

	public void addAdmin(WorkspaceUser user)
			throws WorkspaceCommunicationException {
		db.addAdmin(user);
	}
	
	
	private WorkspaceIDHandlerFactory getHandlerFactory(
			final WorkspaceUser user) {
		return new WorkspaceIDHandlerFactory(user);
	}
	
	private class WorkspaceIDHandlerFactory
			implements IdReferenceHandlerFactory {

		private final WorkspaceUser user;
		
		private WorkspaceIDHandlerFactory(final WorkspaceUser user) {
			super();
			if (user == null) {
				throw new NullPointerException("user cannot be null");
			}
			this.user = user;
		}

		@Override
		public <T> IdReferenceHandler<T> createHandler(final Class<T> clazz) {
			return new WorkspaceIDHandler<T>(user);
		}

		@Override
		public IdReferenceType getIDType() {
			return WS_ID_TYPE;
		}
	}
	
	public class WorkspaceIDHandler<T> extends IdReferenceHandler<T> {

		private final WorkspaceUser user;
		
		// associatedObject -> id -> list of attributes
		private final Map<T, Map<String, Set<List<String>>>> ids = new HashMap<>();
		private final Map<String, RemappedId> remapped = new HashMap<>();
		
		private WorkspaceIDHandler(final WorkspaceUser user) {
			super();
			this.user = user;
		}

//		@Override
//		protected boolean addIdImpl(T associatedObject, Long id,
//				List<String> attributes) throws IdReferenceHandlerException,
//				HandlerLockedException {
//			throw new IdReferenceException("Workspace IDs must be strings",
//					getIdType(), associatedObject, "" + id, attributes, null);
//		}
		
		/* To conserve memory the attributes are not copied to another list,
		 * so modification of the attributes will modify the internal
		 * representation of the object.
		 */
		@Override
		protected boolean addIdImpl(
				final T associatedObject,
				final String id,
				final List<String> attributes)
				throws IdParseException {
			boolean unique = true;
			if (!ids.containsKey(associatedObject)) {
				ids.put(associatedObject, new HashMap<String, Set<List<String>>>());
			}
			if (!ids.get(associatedObject).containsKey(id)) {
				ids.get(associatedObject).put(id, new HashSet<List<String>>());
			} else {
				unique = false;
			}
			if (attributes != null && !attributes.isEmpty()) {
				ids.get(associatedObject).get(id).add(attributes);
			}
			return unique;
		}

		@Override
		protected void processIdsImpl()
				throws IdReferenceHandlerException {
			final Set<ObjectIdentifier> idset = new HashSet<ObjectIdentifier>();
			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					idset.add(parseIDString(id, assObj));
				}
			}
			final ObjectResolver wsresolvedids = resolveIDs(idset);
			
			final Map<ObjectIDResolvedWS, TypeAndReference> objtypes =
					getObjectTypes(wsresolvedids);

			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					final ObjectIdentifier oi = parseIDString(id, assObj);
					final ObjectIDResolvedWS roi = wsresolvedids.getResolvedObject(oi);
					final TypeAndReference tnr = objtypes.get(roi);
					typeCheckReference(id, tnr.getType(), assObj);
					remapped.put(id, tnr.getReference());
				}
			}
		}
		
		private ObjectIdentifier parseIDString(
				final String id,
				final T associatedObject)
				throws IdParseException {
			// cannot be null or empty at this point
			final String[] refs = id.trim().split(";");
			final List<ObjectIdentifier> ois = new LinkedList<>();
			for (int i = 0; i < refs.length; i++) {
				try {
					ois.add(ObjectIdentifier.parseObjectReference(refs[i].trim()));
					//Illegal arg is probably not the right exception
				} catch (IllegalArgumentException iae) {
					final List<String> attribs = getAnyAttributeSet(associatedObject, id);
					final String messagePrefix;
					if (refs.length == 1) {
						messagePrefix = "";
					} else {
						messagePrefix = String.format(
								"ID parse error in reference string %s at position %s: ",
								id, i + 1);
					}
					throw new IdParseException(messagePrefix + iae.getMessage(),
							getIdType(), associatedObject, id, attribs, iae);
				}
			}
			if (ois.size() == 1) {
				return ois.get(0);
			}
			return new ObjectIDWithRefPath(ois.get(0), ois.subList(1, ois.size()));
			
		}

		//use this method when an ID is bad regardless of the attribute set
		//parse error, deleted object, etc.
		private List<String> getAnyAttributeSet(final T assObj, final String id) {
			final List<String> attribs;
			final Set<List<String>> attribset = ids.get(assObj).get(id);
			if (attribset.isEmpty()) {
				attribs = null;
			} else {
				//doesn't matter which attribute set we pick -
				//if the id is bad it's bad everywhere
				attribs = attribset.iterator().next();
			}
			return attribs;
		}

		private void typeCheckReference(
				final String id,
				final AbsoluteTypeDefId type,
				final T assObj)
				throws IdReferenceException {
			final Set<List<String>> typeSets = ids.get(assObj).get(id);
			if (typeSets.isEmpty()) {
				return;
			}
			for (final List<String> allowed: typeSets) {
				final List<TypeDefName> allowedTypes = new ArrayList<TypeDefName>();
				for (final String t: allowed) {
					allowedTypes.add(new TypeDefName(t));
				}
				if (!allowedTypes.contains(type.getType())) {
					throw new IdReferenceException(String.format(
							"The type %s of reference %s in this object is not " +
							"allowed - allowed types are %s",
							type.getTypeString(), id, allowed),
							getIdType(), assObj, id, allowed, null);
				}
			}
		}

		private Map<ObjectIDResolvedWS, TypeAndReference> getObjectTypes(
				final ObjectResolver wsresolvedids)
				throws IdReferenceHandlerException {
			final Map<ObjectIDResolvedWS, TypeAndReference> objtypes = new HashMap<>();
			if (!wsresolvedids.getObjects(false).isEmpty()) {
				try {
					objtypes.putAll(db.getObjectType(
							wsresolvedids.getResolvedObjects(false), false));
				} catch (NoSuchObjectException nsoe) {
					final ObjectIDResolvedWS cause = nsoe.getResolvedInaccessibleObject();
					ObjectIdentifier oi = null;
					for (final ObjectIdentifier o: wsresolvedids.getObjects(false)) {
						if (wsresolvedids.getResolvedObject(o).equals(cause)) {
							oi = o;
							break;
						}
					}
					throw generateIDReferenceException(nsoe, oi);
				} catch (WorkspaceCommunicationException e) {
					throw new IdReferenceHandlerException(
							"Workspace communication exception", getIdType(), e);
				}
			}
			if (!wsresolvedids.getObjects(true).isEmpty()) {
				// these object must be available since they're at the end of a ref path
				try {
					objtypes.putAll(db.getObjectType(
							wsresolvedids.getResolvedObjects(true), true));
				} catch (NoSuchObjectException nsoe) {
					throw new RuntimeException("Threw exception when explicitly told not to");
				} catch (WorkspaceCommunicationException e) {
					throw new IdReferenceHandlerException(
							"Workspace communication exception", getIdType(), e);
				}
			} // otherwise do nothing
			return objtypes;
		}

		private ObjectResolver resolveIDs(
				final Set<ObjectIdentifier> idset)
				throws IdReferenceHandlerException {
			final ObjectResolver.Builder orb = ObjectResolver.getBuilder(db, user)
					.withMaximumObjectsSearched(maximumObjectSearchCount);
			if (!idset.isEmpty()) {
				try {
					for (final ObjectIdentifier oi: idset) {
						orb.withObject(oi);
					}
					return orb.resolve();
				} catch (InaccessibleObjectException ioe) {
					throw generateIDReferenceException(ioe);
				} catch (NoSuchReferenceException e) {
					throw generateIDReferenceException(e);
				} catch (WorkspaceCommunicationException e) {
					throw new IdReferenceHandlerException("Workspace communication exception",
							getIdType(), e);
				} catch (CorruptWorkspaceDBException e) {
					throw new IdReferenceHandlerException("Corrupt workspace exception",
							getIdType(), e);
				} catch (ReferenceSearchMaximumSizeExceededException e) {
					throw new RuntimeException("No search requested, yet got search error", e);
				}
			} else {
				return orb.buildEmpty();
			}
		}

		private IdReferenceException generateIDReferenceException(
				final NoSuchReferenceException e)
				throws IdParseException {
			final ObjectIdentifier start = e.getStartObject();
			final String exception = String.format("Reference path starting with %s, position " +
					"%s: Object %s does not contain a reference to %s",
					start.getReferenceString(),
					e.getFromPosition(),
					e.getFromObject().getReferenceString(),
					e.getToObject().getReferenceString());
			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					final ObjectIdentifier oi = parseIDString(id, assObj);
					if (oi.equals(start)) {
						final List<String> attribs = getAnyAttributeSet(assObj, id);
						return new IdReferenceException(
								exception, getIdType(), assObj, id, attribs, e);
					}
				}
			}
			throw new RuntimeException(String.format(
					"Programming error: Lookup of object %s failed",
					start.getReferenceString()));
		}

		private IdReferenceException generateIDReferenceException(
				final InaccessibleObjectException ioe)
				throws IdParseException {
			if (ioe.getInaccessibleObject() == null) {
				throw new RuntimeException("Programming error: no object associated with " +
						"inaccessible object exception", ioe);
			}
			final String exception = "No read access to id ";
			return generateIDReferenceException(ioe,
					ioe.getInaccessibleObject(), exception);
		}
		
		private IdReferenceException generateIDReferenceException(
				final NoSuchObjectException ioe,
				final ObjectIdentifier originalObject)
				throws IdParseException {
			final String exception = "There is no object with id ";
			return generateIDReferenceException(ioe, originalObject,
					exception);
		}

		private IdReferenceException generateIDReferenceException(
				final WorkspaceDBException e,
				final ObjectIdentifier originalObject,
				final String exception)
				throws IdParseException {
			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					final ObjectIdentifier oi = parseIDString(id, assObj);
					if (oi.equals(originalObject)) {
						final List<String> attribs = getAnyAttributeSet(assObj, id);
						return new IdReferenceException(exception + id + ": " + e.getMessage(),
								getIdType(), assObj, id, attribs, e);
					}
				}
			}
			throw new RuntimeException(String.format(
					"Programming error: Lookup of object %s failed",
					originalObject.getReferenceString()));
		}
		
		@Override
		protected RemappedId getRemappedIdImpl(final String oldId)
				throws NoSuchIdException {
			if (!remapped.containsKey(oldId)) {
				throw new NoSuchIdException(
						"No such ID contained in this mapper: " + oldId);
			}
			return remapped.get(oldId);
		}

		@Override
		protected Set<RemappedId> getRemappedIdsImpl(T associatedObject) {
			Set<RemappedId> newids = new HashSet<RemappedId>();
			if (!ids.containsKey(associatedObject)) {
				return newids;
			}
			for (final String id: ids.get(associatedObject).keySet()) {
				newids.add(remapped.get(id));
			}
			return newids;
		}

		@Override
		public IdReferenceType getIdType() {
			return WS_ID_TYPE;
		}
	}
}
