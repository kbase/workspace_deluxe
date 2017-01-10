package us.kbase.workspace.database;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.refsearch.ReferenceGraphSearch;
import us.kbase.workspace.database.refsearch.ReferenceGraphTopologyProvider;
import us.kbase.workspace.database.refsearch.ReferenceProviderException;
import us.kbase.workspace.database.refsearch.ReferenceSearchFailedException;
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

import com.fasterxml.jackson.core.JsonParseException;

public class Workspace {
	
	//TODO MEM limit all methods that return a set or list or map
	
	//TODO TEST general unit tests
	//TODO GC garbage collection - see WOR-45
	//TODO SEARCH separate service - search interface, return changes since date, store most recent update to avoid queries
	//TODO SEARCH separate service - get object changes since date (based on type collection and pointers collection
	//TODO SEARCH index typespecs
	//TODO CODE look into eliminating all the DB implementation specific classes, too much of a pain just to ensure not moving objects between implementations
	
	public static final User ALL_USERS = new AllUsers('*');
	
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
	private int maximumObjectSearchCount;
	
	public Workspace(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final TypedObjectValidator validator) {
		if (db == null) {
			throw new NullPointerException("db cannot be null");
		}
		if (cfg == null) {
			throw new NullPointerException("cfg cannot be null");
		}
		if (validator == null) {
			throw new NullPointerException("validator cannot be null");
		}
		this.db = db;
		//TODO DBCONSIST check that a few object types exist to make sure the type provider is ok.
		this.validator = validator;
		rescfg = cfg;
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
	
	private void comparePermission(final WorkspaceUser user,
			final Permission required, final Permission available,
			final ObjectIdentifier oi, final String operation) throws
			WorkspaceAuthorizationException {
		final WorkspaceAuthorizationException wae =
				comparePermission(user, required, available,
						oi.getWorkspaceIdentifierString(), operation);
		if (wae != null) {
			wae.addDeniedCause(oi);
			throw wae;
		}
	}
	
	private void comparePermission(final WorkspaceUser user,
			final Permission required, final Permission available,
			final WorkspaceIdentifier wsi, final String operation) throws
			WorkspaceAuthorizationException {
		final WorkspaceAuthorizationException wae =
				comparePermission(user, required, available,
						wsi.getIdentifierString(), operation);
		if (wae != null) {
			wae.addDeniedCause(wsi);
			throw wae;
		}
	}
	
	private WorkspaceAuthorizationException comparePermission(
			final WorkspaceUser user, final Permission required,
			final Permission available, final String identifier,
			final String operation) {
		if(required.compareTo(available) > 0) {
			final String err = user == null ?
					"Anonymous users may not %s workspace %s" :
					"User " + user.getUser() + " may not %s workspace %s";
			final WorkspaceAuthorizationException wae = 
					new WorkspaceAuthorizationException(String.format(
					err, operation, identifier));
			return wae;
		}
		return null;
	}
	
	private void checkLocked(final Permission perm,
			final ResolvedWorkspaceID rwsi)
			throws WorkspaceAuthorizationException {
		if (perm.compareTo(Permission.READ) > 0 && rwsi.isLocked()) {
			throw new WorkspaceAuthorizationException("The workspace with id "
					+ rwsi.getID() + ", name " + rwsi.getName() +
					", is locked and may not be modified");
		}
	}
	
	private ResolvedWorkspaceID checkPerms(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission perm,
			final String operation)
			throws CorruptWorkspaceDBException, WorkspaceAuthorizationException,
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		return checkPerms(user, wsi, perm, operation, false, false);
	}
	
	private ResolvedWorkspaceID checkPerms(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission perm,
			final String operation, final boolean allowDeletedWorkspace,
			final boolean ignoreLock)
			throws CorruptWorkspaceDBException, WorkspaceAuthorizationException,
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		if (wsi == null) {
			throw new IllegalArgumentException(
					"Workspace identifier cannot be null");
		}
		return checkPermsMass(user, Arrays.asList(wsi), perm, operation,
				allowDeletedWorkspace, ignoreLock).get(wsi);
	}
	
	private Map<WorkspaceIdentifier, ResolvedWorkspaceID> checkPermsMass(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis,
			final Permission perm,
			final String operation,
			final boolean allowDeletedWorkspace,
			final boolean ignoreLock)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			WorkspaceAuthorizationException, CorruptWorkspaceDBException {
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				db.resolveWorkspaces(new HashSet<WorkspaceIdentifier>(wsis),
						allowDeletedWorkspace, false);
		final PermissionSet perms = db.getPermissions(user,
				new HashSet<ResolvedWorkspaceID>(rwsis.values()));
		for (final Entry<WorkspaceIdentifier, ResolvedWorkspaceID> e:
				rwsis.entrySet()) {
			if (!ignoreLock) {
				checkLocked(perm, e.getValue());
			}
			comparePermission(
					user, perm, perms.getPermission(e.getValue(), true),
					e.getKey(), operation);
		}
		return rwsis;
	}
	
	private Map<ObjectIdentifier, ObjectIDResolvedWS> checkPerms(
			final WorkspaceUser user, final List<ObjectIdentifier> loi,
			final Permission perm, final String operation)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException {
		return checkPerms(user, loi, perm, operation, false);
	}
	
	private Map<ObjectIdentifier, ObjectIDResolvedWS> checkPerms(
			final WorkspaceUser user, final List<ObjectIdentifier> loi,
			final Permission perm, final String operation,
			final boolean allowDeleted)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException {
		return checkPerms(user, loi, perm, operation, allowDeleted, false,
				false);
	}
	
	private Map<ObjectIdentifier, ObjectIDResolvedWS> checkPerms(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final Permission perm,
			final String operation,
			final boolean allowDeleted,
			final boolean allowMissing,
			final boolean allowInaccessible)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException {
		if (loi.isEmpty()) {
			throw new IllegalArgumentException("No object identifiers provided");
		}
		//map is for error purposes only - only stores the most recent object
		//associated with a workspace
		final Map<WorkspaceIdentifier, ObjectIdentifier> wsis =
				new HashMap<WorkspaceIdentifier, ObjectIdentifier>();
		for (final ObjectIdentifier o: loi) {
			wsis.put(o.getWorkspaceIdentifier(), o);
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis;
		try {
			rwsis = db.resolveWorkspaces(wsis.keySet(), allowDeleted,
					allowMissing);
		} catch (NoSuchWorkspaceException nswe) {
			final ObjectIdentifier obj = wsis.get(nswe.getMissingWorkspace());
			throw new InaccessibleObjectException(String.format(
					"Object %s cannot be accessed: %s",
					obj.getIdentifierString(), nswe.getLocalizedMessage()),
					obj, nswe);
		}
		final PermissionSet perms = db.getPermissions(user,
						new HashSet<ResolvedWorkspaceID>(rwsis.values()));
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ret =
				new HashMap<ObjectIdentifier, ObjectIDResolvedWS>();
		for (final ObjectIdentifier o: loi) {
			if (!rwsis.containsKey(o.getWorkspaceIdentifier())) {
				continue; //missing workspace
			}
			final ResolvedWorkspaceID r = rwsis.get(o.getWorkspaceIdentifier());
			try {
				checkLocked(perm, r);
				comparePermission(user, perm, perms.getPermission(r, true), o, operation);
			} catch (WorkspaceAuthorizationException wae) {
				if (allowInaccessible) {
					continue;
				} else {
					throw new InaccessibleObjectException(String.format(
							"Object %s cannot be accessed: %s",
							o.getIdentifierString(), wae.getLocalizedMessage()),
							o, wae);
				}
			}
			ret.put(o, o.resolveWorkspace(r));
		}
		return ret;
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
		return db.createWorkspace(user, wsname, globalread,
				pruneWorkspaceDescription(description),
				meta == null ? new WorkspaceUserMetadata() : meta);
	}
	
	//might be worthwhile to make this work on multiple values,
	// but keep things simple for now. 
	public void removeWorkspaceMetadata(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final String key)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"alter metadata for");
		db.removeWorkspaceMetaKey(wsid, key);
	}
	
	public void setWorkspaceMetadata(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final WorkspaceUserMetadata meta)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		if (meta == null || meta.isEmpty()) {
			throw new IllegalArgumentException(
					"Metadata cannot be null or empty");
		}
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"alter metadata for");
		db.setWorkspaceMeta(wsid, meta);
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
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		new WorkspaceIdentifier(newname, user); //check for errors
		return db.cloneWorkspace(user, wsid, newname, globalread,
				pruneWorkspaceDescription(description),
				meta == null ? new WorkspaceUserMetadata() : meta,
				exclude);
	}
	
	public WorkspaceInformation lockWorkspace(final WorkspaceUser user,
			final WorkspaceIdentifier wsi)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"lock");
		return db.lockWorkspace(user, wsid);
	}

	private String pruneWorkspaceDescription(final String description) {
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			return description.substring(0, MAX_WS_DESCRIPTION);
		}
		return description;
	}

	public void setWorkspaceDescription(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final String description)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"set description on");
		db.setWorkspaceDescription(wsid, pruneWorkspaceDescription(description));
	}
	
	public String getWorkspaceDescription(final WorkspaceUser user,
			final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException,
			WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		return db.getWorkspaceDescription(wsid);
	}
	
	public WorkspaceInformation setWorkspaceOwner(
			WorkspaceUser owner,
			final WorkspaceIdentifier wsi,
			final WorkspaceUser newUser,
			String newName,
			final boolean asAdmin)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		if (newUser == null) {
			throw new NullPointerException("newUser cannot be null");
		}
		if (wsi == null) {
			throw new NullPointerException("wsi cannot be null");
		}
		final ResolvedWorkspaceID rwsi;
		if (asAdmin) {
			rwsi = db.resolveWorkspace(wsi);
			owner = db.getWorkspaceOwner(rwsi);
		} else {
			rwsi = checkPerms(owner, wsi, Permission.OWNER,
				"change the owner of");
		}
		final Permission p = db.getPermission(newUser, rwsi);
		if (p.equals(Permission.OWNER)) {
			throw new IllegalArgumentException(newUser.getUser() +
					" already owns workspace " + rwsi.getName());
		}
		if (newName == null) {
			final String[] oldWsName = WorkspaceIdentifier.splitUser(
					rwsi.getName());
			if (oldWsName[0] != null) { //includes user name
				newName = newUser.getUser() +
						WorkspaceIdentifier.WS_NAME_DELIMITER +
						oldWsName[1];
			} // else don't change the name
		} else {
			if (newName.equals(rwsi.getName())) {
				newName = null; // no need to change name
			} else {
				new WorkspaceIdentifier(newName, newUser); //checks for illegal names
			}
		}
		return db.setWorkspaceOwner(rwsi, owner, newUser, newName);
	}
			

	public void setPermissions(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final List<WorkspaceUser> users,
			final Permission permission)
			throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		setPermissions(user, wsi, users, permission, false);
	}
	
	public void setPermissions(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final List<WorkspaceUser> users,
			final Permission permission, final boolean asAdmin)
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
				db.getPermissions(user, wsid).getUserPermission(wsid, true);
		if (currentPerm.equals(Permission.NONE)) {
			//always throw exception here
			checkPerms(user, wsi, Permission.ADMIN, "set permissions on");
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
		db.setPermissions(wsid, users, permission);
	}
	
	public void setGlobalPermission(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission permission)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceAuthorizationException, WorkspaceCommunicationException {
		if (Permission.READ.compareTo(permission) < 0) {
			throw new IllegalArgumentException(
					"Global permissions cannot be greater than read");
		}
		final boolean ignoreLock = permission.equals(Permission.READ);
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"set global permission on", false, ignoreLock);
		db.setGlobalPermission(wsid, permission);
	}

	public List<Map<User, Permission>> getPermissions(
			final WorkspaceUser user,
			final List<WorkspaceIdentifier> wslist)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		if (wslist == null) {
			throw new NullPointerException("wslist cannot be null");
		}
		if (wslist.size() > MAX_WS_COUNT) {
			throw new IllegalArgumentException(
					"Maximum number of workspaces allowed for input is " +
							MAX_WS_COUNT);
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwslist =
				db.resolveWorkspaces(new HashSet<WorkspaceIdentifier>(wslist));
		final Map<ResolvedWorkspaceID, Map<User, Permission>> perms =
				db.getAllPermissions(new HashSet<ResolvedWorkspaceID>(
						rwslist.values()));
		final List<Map<User, Permission>> ret =
				new LinkedList<Map<User,Permission>>();
		for (final WorkspaceIdentifier wsi: wslist) {
			final ResolvedWorkspaceID rwsi = rwslist.get(wsi);
			final Map<User, Permission> wsperm = perms.get(rwsi);
			final Permission p = wsperm.get(user); // will be null for null user
			if (p == null || Permission.WRITE.compareTo(p) > 0) { //read or no perms
				final Map<User, Permission> wsp =
						new HashMap<User, Permission>();
				if (wsperm.containsKey(ALL_USERS)) {
					wsp.put(ALL_USERS, wsperm.get(ALL_USERS));
				}
				if (user != null) {
					if (p == null) {
						wsp.put(user, Permission.NONE);
					} else {
						wsp.put(user, p);
					}
				}
				ret.add(wsp);
			} else {
				ret.add(wsperm);
			}
		}
		return ret;
	}

	public WorkspaceInformation getWorkspaceInformation(
			final WorkspaceUser user, final WorkspaceIdentifier wsi)
			throws NoSuchWorkspaceException, WorkspaceCommunicationException,
			CorruptWorkspaceDBException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		return db.getWorkspaceInformation(user, wsid);
	}
	
	public String getBackendType() {
		return db.getBackendType();
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
		final ResolvedWorkspaceID rwsi = checkPerms(user, wsi, Permission.WRITE,
				"write to");
		idHandlerFac.addFactory(getHandlerFactory(user));
		final IdReferenceHandlerSet<IDAssociation> idhandler =
				idHandlerFac.createHandlers(IDAssociation.class);
		
		final Map<WorkspaceSaveObject, ValidatedTypedObject> reports = 
				validateObjectsAndExtractReferences(objects, idhandler);
		
		processIds(objects, idhandler, reports);
		
		//handle references and calculate size with new references
		final List<ResolvedSaveObject> saveobjs =
				new ArrayList<ResolvedSaveObject>();
		long ttlObjSize = 0;
		int objcount = 1;
		for (WorkspaceSaveObject wo: objects) {
			//maintain ordering
			wo.getProvenance().setWorkspaceID(new Long(rwsi.getID()));
			final List<Reference> provrefs = new LinkedList<Reference>();
			for (final Provenance.ProvenanceAction action:
					wo.getProvenance().getActions()) {
				for (final String ref: action.getWorkspaceObjects()) {
					provrefs.add((Reference)
							idhandler.getRemappedId(WS_ID_TYPE, ref));
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
		
		try {
			sortObjects(saveobjs, ttlObjSize);
			return db.saveObjects(user, rwsi, saveobjs);
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
		for (ResolvedSaveObject ro: saveobjs) {
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
			final ValidatedTypedObject rep = validate(wo, idhandler,
					objcount);
			reports.put(wo, rep);
			idhandler.associateObject(new IDAssociation(objcount, true));
			try {
				for (final Provenance.ProvenanceAction action:
						wo.getProvenance().getActions()) {
					for (final String pref: action.getWorkspaceObjects()) {
						if (pref == null) {
							throw new TypedObjectValidationException(
									String.format(
									"Object %s has a null provenance reference",
									getObjectErrorId(wo, objcount)));
						}
						idhandler.addStringId(new IdReference<String>(
								WS_ID_TYPE, pref, null));
					}
				}
			} catch (IdReferenceHandlerException ihre) {
				throw new TypedObjectValidationException(String.format(
						"Object %s has invalid provenance reference: ",
						getObjectErrorId(wo, objcount)) + 
						ihre.getMessage(), ihre);
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

	//should probably make an options builder
	public List<WorkspaceInformation> listWorkspaces(
			final WorkspaceUser user, Permission minPerm,
			final List<WorkspaceUser> users, final WorkspaceUserMetadata meta,
			final Date after, final Date before,
			final boolean excludeGlobal, final boolean showDeleted,
			final boolean showOnlyDeleted)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		if (minPerm == null || Permission.READ.compareTo(minPerm) > 0) {
			minPerm = Permission.READ;
		}
		if (meta != null && meta.size() > 1) {
			throw new IllegalArgumentException("Only one metadata spec allowed");
		}
		final PermissionSet perms =
				db.getPermissions(user, minPerm, excludeGlobal);
		return db.getWorkspaceInformation(perms, users, meta, after, before,
				showDeleted, showOnlyDeleted);
	}
	
	public List<ObjectInformation> listObjects(
			final ListObjectsParameters params)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {

		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				db.resolveWorkspaces(params.getWorkspaces());
		final HashSet<ResolvedWorkspaceID> rw =
				new HashSet<ResolvedWorkspaceID>(rwsis.values());
		final PermissionSet pset = db.getPermissions(params.getUser(), rw,
				params.getMinimumPermission(), params.isExcludeGlobal(), true);
		if (!params.getWorkspaces().isEmpty()) {
			for (final WorkspaceIdentifier wsi: params.getWorkspaces()) {
				comparePermission(params.getUser(), Permission.READ,
						pset.getPermission(rwsis.get(wsi), true), wsi, "read");
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
		return getObjects(user, loi, noData, false);
	}
	
	public List<WorkspaceObjectData> getObjects(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final boolean noData,
			boolean nullIfInaccessible)
			throws CorruptWorkspaceDBException,
				WorkspaceCommunicationException, InaccessibleObjectException,
				NoSuchReferenceException, TypedObjectExtractionException,
				ReferenceSearchMaximumSizeExceededException, NoSuchObjectException {
		
		final ResolvedRefPaths res = resolveObjects(user, loi, nullIfInaccessible);
		
		final Map<ObjectIDResolvedWS, Set<SubsetSelection>> refpaths =
				setupObjectPaths(res.withpath);
		final Map<ObjectIDResolvedWS, Set<SubsetSelection>> stdpaths =
				setupObjectPaths(res.nopath);
		
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
				// works if res.nochain.get(o) is null or stddata doesn't have key
				if (stddata.containsKey(res.nopath.get(o))) {
					wod = stddata.get(res.nopath.get(o)).get(ss);
				} else if (refdata.containsKey(res.withpath.get(o))) {
					final WorkspaceObjectData prewod = refdata.get(res.withpath.get(o)).get(ss);
					wod = prewod.updateObjectReferencePath(res.withpathRefPath.get(o));
				} else {
					wod = null;
				}
				ret.add(wod);
			}
			res.nopath.clear();
			res.withpath.clear();
			res.withpathRefPath.clear();
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
			final Map<ObjectIdentifier, ObjectIDResolvedWS> objs) {
		final Map<ObjectIDResolvedWS, Set<SubsetSelection>> paths =
				new HashMap<ObjectIDResolvedWS, Set<SubsetSelection>>();
		for (final ObjectIdentifier o: objs.keySet()) {
			final ObjectIDResolvedWS roi = objs.get(o);
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
	
	private ResolvedRefPaths resolveReferencePaths(
			final WorkspaceUser user,
			final List<ObjectIDWithRefPath> objsWithRefpaths,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> heads,
			final boolean ignoreErrors)
			throws WorkspaceCommunicationException,
				InaccessibleObjectException, CorruptWorkspaceDBException,
				NoSuchReferenceException {
		
		if (!hasItems(objsWithRefpaths)) {
			return new ResolvedRefPaths(null, null);
		}
		
		final List<ObjectIdentifier> allRefPathEntries = new LinkedList<>();
		for (final ObjectIDWithRefPath oc: objsWithRefpaths) {
			if (oc != null) {
				/* allow nulls in list to maintain object count in the case
				 * calling method input includes objectIDs with and without
				 * chains
				 */
				allRefPathEntries.addAll(oc.getRefPath());
			}
		}
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> headrefs =
				getObjectOutgoingReferences(heads, ignoreErrors, false);
		/* ignore all errors when getting chain objects until actually getting
		 * to the point where we need the data. Otherwise an attacker can
		 * explore what objects exist in arbitrary workspaces.
		 */
		final Map<ObjectIdentifier, ObjectIDResolvedWS> resolvedRefPathObjs =
				checkPerms(user, allRefPathEntries, Permission.NONE,
						"somthinsbroke", true, true, true);
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> outrefs =
				getObjectOutgoingReferences(resolvedRefPathObjs, true, true);
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> resolvedObjects = new HashMap<>();
		final Map<ObjectIdentifier, List<Reference>> refpaths = new HashMap<>();
		int chnum = 1;
		for (final ObjectIDWithRefPath owrp: objsWithRefpaths) {
			if (owrp != null) {
				final ObjectReferenceSet refs = headrefs.get(heads.get(owrp));
				if (refs != null) {
					final List<Reference> resRefPath = getResolvedRefPath(owrp, refs,
							resolvedRefPathObjs, outrefs, ignoreErrors, chnum);
					if (resRefPath != null) {
						final Reference ref = resRefPath.get(resRefPath.size() - 1);
						final ObjectIDResolvedWS end = resolvedRefPathObjs.get(owrp.getLast());
						final ObjectIDResolvedWS res = new ObjectIDResolvedWS(
								end.getWorkspaceIdentifier(), ref.getObjectID(), ref.getVersion());
						resolvedObjects.put(owrp, res);
						refpaths.put(owrp, resRefPath);
					}
				}
			}
			chnum++;
		}
		return new ResolvedRefPaths(resolvedObjects, refpaths);
	}

	private Map<ObjectIDResolvedWS, ObjectReferenceSet> getObjectOutgoingReferences(
			final Map<ObjectIdentifier, ObjectIDResolvedWS> objs,
			final boolean ignoreErrors,
			final boolean includeDeleted)
			throws WorkspaceCommunicationException, InaccessibleObjectException {
		try {
			return db.getObjectOutgoingReferences(new HashSet<ObjectIDResolvedWS>(objs.values()),
					!ignoreErrors, includeDeleted, !ignoreErrors);
		} catch (NoSuchObjectException nsoe) {
			for (final Entry<ObjectIdentifier, ObjectIDResolvedWS> e: objs.entrySet()) {
				if (e.getValue().equals(nsoe.getResolvedInaccessibleObject())) {
					throw new InaccessibleObjectException(nsoe.getMessage(), e.getKey(), nsoe);
				}
			}
			throw new RuntimeException("Programming error - couldn't translate resolved " +
					"object ID to object ID", nsoe);
		}
	}
	
	private List<Reference> getResolvedRefPath(
			final ObjectIDWithRefPath refpath,
			final ObjectReferenceSet headrefs,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resRefPathObjs,
			final Map<ObjectIDResolvedWS, ObjectReferenceSet> outgoingRefs,
			final boolean ignoreErrors,
			final int chainNumber)
			throws NoSuchReferenceException {
		final List<Reference> resolvedRefPath = new LinkedList<>();
		ObjectIdentifier pos = refpath;
		ObjectReferenceSet refs = headrefs;
		resolvedRefPath.add(refs.getObjectReference());
		int posnum = 1;
		for (final ObjectIdentifier oi: refpath.getRefPath()) {
			/* refs are guaranteed to exist, so if the db didn't find
			 * it the user specified it incorrectly
			 */
			/* only throw the exception from one
			 * place, otherwise an attacker can tell if the object in the
			 * ref chain is in the DB or not
			 */
			final ObjectIDResolvedWS oir = resRefPathObjs.get(oi);
			final ObjectReferenceSet current = oir == null ? null : outgoingRefs.get(oir);
			if (current == null || !refs.contains(current.getObjectReference())) {
				if (ignoreErrors) {
					return null;
				}
				throw new NoSuchReferenceException(null, chainNumber, posnum, refpath, pos, oi);
			}
			resolvedRefPath.add(current.getObjectReference());
			pos = oi;
			refs = current;
			posnum++;
		}
		return resolvedRefPath;
	}

	private boolean hasItems(final List<?> l) {
		if (l == null || l.isEmpty()) {
			return false;
		}
		for (final Object item: l) {
			if (item != null) {
				return true;
			}
		}
		return false;
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
			rwsis = db.resolveWorkspaces(wsis, true, true);
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
				final WorkspaceIdentifier wsi = new WorkspaceIdentifier(
						cref.getWorkspaceID());
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
				checkPerms(user, loi, Permission.READ, "read");
		final PermissionSet perms =
				db.getPermissions(user, Permission.READ, false);
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
				checkPerms(user, loi, Permission.READ, "read");
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
	
	public List<ObjectInformation> getObjectHistory(final WorkspaceUser user,
			final ObjectIdentifier oi) throws WorkspaceCommunicationException,
			InaccessibleObjectException, CorruptWorkspaceDBException, NoSuchObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, Arrays.asList(oi), Permission.READ, "read");
		return db.getObjectHistory(ws.get(oi));
	}
	
	private static class ResolvedRefPaths {
		public Map<ObjectIdentifier, ObjectIDResolvedWS> nopath;
		public Map<ObjectIdentifier, ObjectIDResolvedWS> withpath;
		public Map<ObjectIdentifier, List<Reference>> withpathRefPath;

		private ResolvedRefPaths(
				final Map<ObjectIdentifier, ObjectIDResolvedWS> withpath,
				final Map<ObjectIdentifier, List<Reference>> withpathRefPath) {
			super();
			this.withpath = withpath;
			if (withpath == null) {
				this.withpath = new HashMap<>();
			}
			this.withpathRefPath = withpathRefPath;
			if (withpathRefPath == null) {
				this.withpathRefPath = new HashMap<>();
			}
		}
		
		public ResolvedRefPaths withStandardObjects(
				final Map<ObjectIdentifier, ObjectIDResolvedWS> std) {
			if (std == null) {
				nopath = new HashMap<>();
			} else {
				nopath = std;
			}
			return this;
		}

		public ResolvedRefPaths merge(final ResolvedRefPaths merge) {
			nopath.putAll(merge.nopath);
			withpath.putAll(merge.withpath);
			withpathRefPath.putAll(merge.withpathRefPath);
			return this;
		}
		
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
	
		final ResolvedRefPaths res = resolveObjects(user, loi, nullIfInaccessible);
		
		final Map<ObjectIDResolvedWS, ObjectInformation> stdmeta = db.getObjectInformation(
				new HashSet<ObjectIDResolvedWS>(res.nopath.values()),
				includeMetadata, !nullIfInaccessible, false, !nullIfInaccessible);
		
		final Map<ObjectIDResolvedWS, ObjectInformation> resmeta = db.getObjectInformation(
				new HashSet<ObjectIDResolvedWS>(res.withpath.values()),
				includeMetadata, false, true, true);
				// at this point the object at the chain end must exist
		
		final List<ObjectInformation> ret = new ArrayList<>();
		for (final ObjectIdentifier o: loi) {
			if (res.nopath.containsKey(o) && stdmeta.containsKey(res.nopath.get(o))) {
				ret.add(stdmeta.get(res.nopath.get(o)));
			} else if (res.withpath.containsKey(o) && resmeta.containsKey(res.withpath.get(o))) {
				ret.add(resmeta.get(res.withpath.get(o))
						.updateReferencePath(res.withpathRefPath.get(o)));
			} else {
				ret.add(null);
			}
		}
		return ret;
	}

	/* used to resolve object IDs that might contain reference chains */
	private ResolvedRefPaths resolveObjects(
			final WorkspaceUser user,
			final List<ObjectIdentifier> loi,
			final boolean nullIfInaccessible)
			throws WorkspaceCommunicationException,
				InaccessibleObjectException, CorruptWorkspaceDBException,
				NoSuchReferenceException, ReferenceSearchMaximumSizeExceededException {
		if (loi.isEmpty()) {
			throw new IllegalArgumentException("No object identifiers provided");
		}
		Set<ObjectIdentifier> lookup = new HashSet<>();
		List<ObjectIdentifier> nolookup = new LinkedList<>();
		for (final ObjectIdentifier o: loi) {
			if (o instanceof ObjectIDWithRefPath && ((ObjectIDWithRefPath) o).isLookupRequired()) {
				lookup.add(o);
			} else {
				nolookup.add(o);
			}
		}

		//handle the faster cases first, fail before the searches
		Map<ObjectIdentifier, ObjectIDResolvedWS> ws = new HashMap<>();
		if (!nolookup.isEmpty()) {
			ws = checkPerms(user, nolookup, Permission.READ, "read",
						nullIfInaccessible, nullIfInaccessible, nullIfInaccessible);
		}
		nolookup = null; //gc
		
		final List<ObjectIDWithRefPath> refpaths = new LinkedList<>();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> heads = new HashMap<>();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> std = new HashMap<>();
		for (final ObjectIdentifier o: loi) {
			if (lookup.contains(o)) { // need to do a lookup on this one, skip
				refpaths.add(null); //maintain count for error reporting
			} else if (ws.get(o) == null) { // skip, workspace wasn't resolved
				// error reporting is off, so no need to keep track of location in list
			} else if (o instanceof ObjectIDWithRefPath &&
					((ObjectIDWithRefPath) o).hasRefPath()) {
				refpaths.add((ObjectIDWithRefPath) o);
				heads.put(o, ws.get(o));
			} else {
				refpaths.add(null); // maintain count for error reporting
				std.put(o, ws.get(o));
			}
		}
		ws = null; //GC

		// this should exclude any heads that are deleted, even if nullIfInaccessible is true
		// do this before starting the search, fail early before the expensive part
		final ResolvedRefPaths resolvedPaths = resolveReferencePaths(
				user, refpaths, heads, nullIfInaccessible).withStandardObjects(std);
		
		return resolvedPaths.merge(searchObjectDAG(user, lookup, nullIfInaccessible));
	}
	
	//TODO REF LOOKUP positive and negative caches (?)

	/* Modifies lookup in place to remove objects that don't need lookup.
	 * Note the reference path returned for looked up objects is currently incorrect. 
	 */
	private ResolvedRefPaths searchObjectDAG(
			final WorkspaceUser user,
			final Set<ObjectIdentifier> lookup,
			final boolean nullIfInaccessible)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, ReferenceSearchMaximumSizeExceededException {
		if (lookup.isEmpty()) {
			return new ResolvedRefPaths(null, null).withStandardObjects(null);
		}
		//could make a method to just get IDs of workspace with specific permission to save mem
		PermissionSet pset = db.getPermissions(user, Permission.READ, false);
		Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				searchObjectDAGResolveWorkspaces(lookup);
		final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs = new HashMap<>();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> nolookup = new HashMap<>();
		final Iterator<ObjectIdentifier> oiter = lookup.iterator();
		while (oiter.hasNext()) {
			final ObjectIdentifier o = oiter.next();
			final ResolvedWorkspaceID rwsi = rwsis.get(o.getWorkspaceIdentifier());
			if (rwsi != null) {
				final ObjectIDResolvedWS oid = o.resolveWorkspace(rwsi);
				if (pset.hasWorkspace(rwsi) && !rwsi.isDeleted()) { // workspace has read perm
					nolookup.put(o, oid);
					oiter.remove();
				} else {
					resobjs.put(o, oid);
				}
			}
		}
		if (lookup.isEmpty()) {
			return new ResolvedRefPaths(null, null).withStandardObjects(nolookup);
		}
		final Set<Long> wsIDs = searchObjectDAGGetWorkspaceIDs(pset);
		pset = null;
		rwsis = null;
		if (wsIDs.isEmpty()) {
			if (nullIfInaccessible) {
				return new ResolvedRefPaths(null, null).withStandardObjects(nolookup);
			} else {
				throw generateInaccessibleObjectException(user, lookup.iterator().next());
			}
		}
		return searchObjectDAG(user, wsIDs, lookup, resobjs, nullIfInaccessible)
				.withStandardObjects(nolookup);
	}

	private ResolvedRefPaths searchObjectDAG(
			final WorkspaceUser user,
			final Set<Long> wsIDs,
			final Set<ObjectIdentifier> lookup,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs,
			final boolean nullIfInaccessible)
			throws WorkspaceCommunicationException, ReferenceSearchMaximumSizeExceededException,
				InaccessibleObjectException {
		
		final Map<ObjectIDResolvedWS, Reference> objrefs = db.getObjectReference(
				new HashSet<>(resobjs.values()));
		try {
			// will throw an exception if can't find a ref for any object in lookup
			final Set<Reference> startingRefs = searchObjectDAGGetStartingRefs(
					lookup, resobjs, objrefs, nullIfInaccessible);
			//so starting refs can't be empty unless nullIfInaccessible is true
			if (nullIfInaccessible && startingRefs.isEmpty()) {
				return new ResolvedRefPaths(null, null);
			}
			final ReferenceGraphTopologyProvider refProvider = new ReferenceGraphTopologyProvider() {
				
				@Override
				public Map<Reference, Map<Reference, Boolean>> getAssociatedReferences(
						final Set<Reference> sourceRefs)
						throws ReferenceProviderException {
					try {
						final Map<Reference, ObjectReferenceSet> refs =
								db.getObjectIncomingReferences(sourceRefs);
						final Set<Reference> readable = new HashSet<>();
						for (final ObjectReferenceSet refset: refs.values()) {
							for (final Reference r: refset.getReferenceSet()) {
								if (wsIDs.contains(r.getWorkspaceID())) {
									readable.add(r);
								}
							}
						}
						final Map<Reference, Boolean> exists = db.getObjectExistsRef(readable);
						final Map<Reference, Map<Reference, Boolean>> refToRefs = new HashMap<>();
						for (final Reference r: refs.keySet()) {
							final Map<Reference, Boolean> termCritera = new HashMap<>();
							refToRefs.put(r, termCritera);
							for (final Reference inc: refs.get(r).getReferenceSet()) {
								termCritera.put(inc, exists.containsKey(inc) && exists.get(inc));
							}
							
						}
						return refToRefs;
					} catch (WorkspaceCommunicationException e) {
						throw new ReferenceProviderException("foo", e);
					}
				}
			};
			final ReferenceGraphSearch search = new ReferenceGraphSearch(startingRefs,
					refProvider, maximumObjectSearchCount, !nullIfInaccessible);
			return searchObjectDAGBuildResolvedObjectPaths(resobjs, objrefs, search);
		} catch (final ReferenceSearchFailedException |
				ObjectDAGSearchFromObjectIDFailedException e) {
			final ObjectIdentifier failedOn = searchObjectDAGGetSearchFailedTarget(
					e, objrefs, resobjs);
			// ensure exceptions are thrown from the same place so users can't probe arbitrary
			// workspaces. Returning the stack trace for errors just might have been a bad idea.
			throw generateInaccessibleObjectException(user, failedOn);
		} catch (final ReferenceProviderException e) {
			throw (WorkspaceCommunicationException) e.getCause();
		}
	}

	private Set<Reference> searchObjectDAGGetStartingRefs(
			final Set<ObjectIdentifier> lookup,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs,
			final Map<ObjectIDResolvedWS, Reference> objrefs,
			final boolean nullIfInaccessible)
			throws ObjectDAGSearchFromObjectIDFailedException {

		final Set<Reference> startingRefs = new HashSet<>();
		for (final ObjectIdentifier o: lookup) {
			final ObjectIDResolvedWS res = resobjs.get(o);
			final Reference ref = objrefs.get(res);
			if (ref == null) { // invalid objectidentifier
				if (!nullIfInaccessible) {
					throw new ObjectDAGSearchFromObjectIDFailedException(o);
				}
			} else {
				startingRefs.add(ref);
			}
		}
		return startingRefs;
	}

	private ResolvedRefPaths searchObjectDAGBuildResolvedObjectPaths(
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs,
			final Map<ObjectIDResolvedWS, Reference> objrefs,
			final ReferenceGraphSearch paths) {
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> absObjectIDs = new HashMap<>();
		final Map<ObjectIdentifier, List<Reference>> oiPaths = new HashMap<>();
		
		for (final Entry<ObjectIdentifier, ObjectIDResolvedWS> e: resobjs.entrySet()) {
			final Reference r = objrefs.get(e.getValue());
			if (paths.isPathFound(r)) { // objid was valid and path was found
				//absolutize the ObjectIDResolvedWS
				absObjectIDs.put(e.getKey(), new ObjectIDResolvedWS(
						e.getValue().getWorkspaceIdentifier(), r.getObjectID(), r.getVersion()));
				oiPaths.put(e.getKey(), paths.getPath(r));
			}
		}
		return new ResolvedRefPaths(absObjectIDs, oiPaths);
	}
	
	// this is a little filthy
	private ObjectIdentifier searchObjectDAGGetSearchFailedTarget(
			final Exception e,
			final Map<ObjectIDResolvedWS, Reference> objrefs,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs) {

		if (e instanceof ObjectDAGSearchFromObjectIDFailedException) {
			return ((ObjectDAGSearchFromObjectIDFailedException) e).getSearchTarget();
		} else if (e instanceof ReferenceSearchFailedException) {
			final Reference failedOn = ((ReferenceSearchFailedException) e).getFailedReference();
			for (final Entry<ObjectIdentifier, ObjectIDResolvedWS> es: resobjs.entrySet()) {
				if (failedOn.equals(objrefs.get(es.getValue()))) {
					return es.getKey();
				}
				
			}
			throw new RuntimeException("Something is extremely wrong, couldn't find target objid");
		} else {
			throw new RuntimeException("Unexpected exception type");
		}
	}
	
	private InaccessibleObjectException generateInaccessibleObjectException(
			final WorkspaceUser user,
			final ObjectIdentifier o)
			throws InaccessibleObjectException {
		final String verString = o.getVersion() == null ? "The latest version of " :
				String.format("Version %s of ", o.getVersion());
		final String userStr = user == null ? "anonymous users" : "user " + user.getUser();
		return new InaccessibleObjectException(String.format(
				"%sobject %s in workspace %s is not accessible to %s",
				verString, o.getIdentifierString(), o.getWorkspaceIdentifierString(), userStr), o);
	}

	@SuppressWarnings("serial")
	private static class ObjectDAGSearchFromObjectIDFailedException extends Exception {
		
		private final ObjectIdentifier objtarget;
		
		public ObjectDAGSearchFromObjectIDFailedException(final ObjectIdentifier searchTarget) {
			super();
			objtarget = searchTarget;
		}
		
		public ObjectIdentifier getSearchTarget() {
			return objtarget;
		}
	}
	
	private Set<Long> searchObjectDAGGetWorkspaceIDs(final PermissionSet pset) {
		final Set<Long> wsids = new HashSet<>();
		for (final ResolvedWorkspaceID rwsi: pset.getWorkspaces()) {
			if (!rwsi.isDeleted()) {
				wsids.add(rwsi.getID());
			}
		}
		return wsids;
	}

	private Map<WorkspaceIdentifier, ResolvedWorkspaceID> searchObjectDAGResolveWorkspaces(
			final Set<ObjectIdentifier> lookup)
			throws WorkspaceCommunicationException {
		final Set<WorkspaceIdentifier> wsis = new HashSet<>();
		for (final ObjectIdentifier o: lookup) {
			wsis.add(o.getWorkspaceIdentifier());
		}
		try {
			return db.resolveWorkspaces(wsis, true, true);
		} catch (NoSuchWorkspaceException e) {
			throw new RuntimeException("Threw exception when explicitly told not to", e);
		}
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
		
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				checkPermsMass(user, wsis, Permission.READ, "read", false,
						false);
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
	
	public WorkspaceInformation renameWorkspace(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final String newname)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.OWNER,
				"rename");
		new WorkspaceIdentifier(newname, user); //check for errors
		return db.renameWorkspace(user, wsid, newname);
	}
	
	public ObjectInformation renameObject(final WorkspaceUser user,
			final ObjectIdentifier oi, final String newname)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = checkPerms(user,
				Arrays.asList(oi), Permission.WRITE, "rename objects in");
		ObjectIDNoWSNoVer.checkObjectName(newname);
		return db.renameObject(ws.get(oi), newname);
	}
	
	public ObjectInformation copyObject(final WorkspaceUser user,
			final ObjectIdentifier from, final ObjectIdentifier to)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		final ObjectIDResolvedWS f = checkPerms(user,
				Arrays.asList(from), Permission.READ, "read").get(from);
		final ObjectIDResolvedWS t = checkPerms(user,
				Arrays.asList(to), Permission.WRITE, "write to").get(to);
		return db.copyObject(user, f, t);
	}
	
	public ObjectInformation revertObject(WorkspaceUser user,
			ObjectIdentifier oi)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		final ObjectIDResolvedWS target = checkPerms(user,
				Arrays.asList(oi), Permission.WRITE, "write to").get(oi);
		return db.revertObject(user, target);
	}
	
	public void setObjectsHidden(final WorkspaceUser user,
			final List<ObjectIdentifier> loi, final boolean hide)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException, NoSuchObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.WRITE,
						(hide ? "" : "un") + "hide objects from");
		db.setObjectsHidden(new HashSet<ObjectIDResolvedWS>(ws.values()),
				hide);
	}
	
	public void setObjectsDeleted(final WorkspaceUser user,
			final List<ObjectIdentifier> loi, final boolean delete)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			InaccessibleObjectException, NoSuchObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.WRITE,
						(delete ? "" : "un") + "delete objects from");
		db.setObjectsDeleted(new HashSet<ObjectIDResolvedWS>(ws.values()),
				delete);
	}

	public void setWorkspaceDeleted(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final boolean delete)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.OWNER,
				(delete ? "" : "un") + "delete", !delete, false);
		db.setWorkspaceDeleted(wsid, delete);
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
			final ResolvedRefPaths wsresolvedids = resolveIDs(idset);
			
			final Map<ObjectIDResolvedWS, TypeAndReference> objtypes =
					getObjectTypes(wsresolvedids);

			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					final ObjectIdentifier oi = parseIDString(id, assObj);
					final ObjectIDResolvedWS roi;
					if (wsresolvedids.nopath.containsKey(oi)) {
						roi = wsresolvedids.nopath.get(oi);
					} else {
						roi = wsresolvedids.withpath.get(oi);
					}
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
				final ResolvedRefPaths wsresolvedids)
				throws IdReferenceHandlerException {
			final Map<ObjectIDResolvedWS, TypeAndReference> objtypes = new HashMap<>();
			if (!wsresolvedids.nopath.isEmpty()) {
				try {
					objtypes.putAll(db.getObjectType(
							new HashSet<>(wsresolvedids.nopath.values()), false));
				} catch (NoSuchObjectException nsoe) {
					final ObjectIDResolvedWS cause = nsoe.getResolvedInaccessibleObject();
					ObjectIdentifier oi = null;
					for (final ObjectIdentifier o: wsresolvedids.nopath.keySet()) {
						if (wsresolvedids.nopath.get(o).equals(cause)) {
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
			if (!wsresolvedids.withpath.isEmpty()) {
				// these object must be available since they're at the end of a ref path
				try {
					objtypes.putAll(db.getObjectType(
							new HashSet<>(wsresolvedids.withpath.values()), true));
				} catch (NoSuchObjectException nsoe) {
					throw new RuntimeException("Threw exception when explicitly told not to");
				} catch (WorkspaceCommunicationException e) {
					throw new IdReferenceHandlerException(
							"Workspace communication exception", getIdType(), e);
				}
			} // otherwise do nothing
			return objtypes;
		}

		private ResolvedRefPaths resolveIDs(
				final Set<ObjectIdentifier> idset)
				throws IdReferenceHandlerException {
			if (!idset.isEmpty()) {
				try {
					return resolveObjects(user, new LinkedList<>(idset), false);
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
				return new ResolvedRefPaths(null, null).withStandardObjects(null);
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
