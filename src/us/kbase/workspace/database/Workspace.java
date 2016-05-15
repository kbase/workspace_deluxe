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
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
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
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.DeletedObjectException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

import com.fasterxml.jackson.core.JsonParseException;

public class Workspace {
	
	//TODO limit all methods that return a set or list or map
	//TODO generalize descent into DAG for all methods
	//TODO deprecate skip
	
	//TODO general unit tests
	//TODO BIG GC garbage collection - see WOR-45
	//TODO BIG SEARCH separate service - search interface, return changes since date, store most recent update to avoid queries
	//TODO BIG SEARCH separate service - get object changes since date (based on type collection and pointers collection
	//TODO BIG SEARCH index typespecs
	
	//TODO need a way to get all types matching a typedef (which might only include a typename) - already exists? Uh, why is this needed? Probably can drop.
	
	//TODO use DigestInputStream while sorting object to calculate md5 at the same time
	
	public static final User ALL_USERS = new AllUsers('*');
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	private final static int MAX_WS_COUNT = 1000;
	private final static int NAME_LIMIT = 1000;
	
	private final static IdReferenceType WS_ID_TYPE = new IdReferenceType("ws");
	
	private final WorkspaceDatabase db;
	private ResourceUsageConfiguration rescfg;
	private final ReferenceParser parser;
	private final TypedObjectValidator validator;
	
	public Workspace(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final ReferenceParser parser,
			final TypedObjectValidator validator) {
		if (db == null) {
			throw new NullPointerException("db cannot be null");
		}
		if (parser == null) {
			throw new NullPointerException("parser cannot be null");
		}
		if (cfg == null) {
			throw new NullPointerException("cfg cannot be null");
		}
		if (validator == null) {
			throw new NullPointerException("validator cannot be null");
		}
		this.db = db;
		//TODO check that a few object types exist to make sure the type provider is ok.
		this.validator = validator;
		rescfg = cfg;
		this.parser = parser;
		db.setResourceUsageConfiguration(rescfg);
	}
	
	public ResourceUsageConfiguration getResourceConfig() {
		return rescfg;
	}
	
	public void setResourceConfig(ResourceUsageConfiguration rescfg) {
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
			final WorkspaceUser user, final List<ObjectIdentifier> loi,
			final Permission perm, final String operation,
			final boolean allowDeleted, final boolean allowMissing,
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
				comparePermission(user, perm, perms.getPermission(r, true), o,
						operation);
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
	
	public WorkspaceInformation cloneWorkspace(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final String newname,
			final boolean globalread, final String description,
			final WorkspaceUserMetadata meta)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			PreExistingWorkspaceException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		new WorkspaceIdentifier(newname, user); //check for errors
		return db.cloneWorkspace(user, wsid, newname, globalread,
				pruneWorkspaceDescription(description),
				meta == null ? new WorkspaceUserMetadata() : meta);
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
	
	private static String getObjectErrorId(final WorkspaceSaveObject wo,
			final int objcount) {
		return getObjectErrorId(wo.getObjectIdentifier(), objcount);
	}
	
	private static String getObjectErrorId(final ObjectIDNoWSNoVer oi,
			final int objcount) {
		String objErrId = "#" + objcount;
		objErrId += oi == null ? "" : ", " + oi.getIdentifierString();
		return objErrId;
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
		
		final Map<WorkspaceSaveObject, TypedObjectValidationReport> reports = 
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
						extractedIDs.put(irt, idhandler.getRemappedIds(
								irt, new IDAssociation(objcount, false)));
					}
				}
			}
			final Set<RemappedId> refids = idhandler.getRemappedIds(
					WS_ID_TYPE,  new IDAssociation(objcount, false));
			final Set<Reference> refs = new HashSet<Reference>();
			for (final RemappedId id: refids) {
				refs.add((Reference) id);
			}
			
			final TypedObjectValidationReport rep = reports.get(wo);
			saveobjs.add(wo.resolve(rep, refs, provrefs, extractedIDs));
			ttlObjSize += rep.getRelabeledSize();
			objcount++;
		}
		objects = null;
		reports.clear();
		
		sortObjects(saveobjs, ttlObjSize);
		return db.saveObjects(user, rwsi, saveobjs);
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

	private Map<WorkspaceSaveObject, TypedObjectValidationReport>
			validateObjectsAndExtractReferences(
			final List<WorkspaceSaveObject> objects,
			final IdReferenceHandlerSet<IDAssociation> idhandler)
			throws TypeStorageException, TypedObjectSchemaException,
			TypedObjectValidationException {
		final Map<WorkspaceSaveObject, TypedObjectValidationReport> reports = 
				new HashMap<WorkspaceSaveObject, TypedObjectValidationReport>();
		int objcount = 1;
		for (final WorkspaceSaveObject wo: objects) {
			idhandler.associateObject(new IDAssociation(objcount, false));
			final TypedObjectValidationReport rep = validate(wo, idhandler,
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
			final Map<WorkspaceSaveObject, TypedObjectValidationReport> reports)
			throws TypedObjectValidationException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		try {
			idhandler.processIDs();
		} catch (IdParseException ipe) {
			final IDAssociation idloc =
					(IDAssociation) ipe.getAssociatedObject();
			final WorkspaceSaveObject wo = objects.get(idloc.objnum - 1);
			throw new TypedObjectValidationException(String.format(
					"Object %s has unparseable %sreference %s: %s%s",
					getObjectErrorId(wo, idloc.objnum),
					(idloc.provenance ? "provenance " : ""),
					ipe.getId(),
					ipe.getLocalizedMessage(),
					idloc.provenance ? "" : " at " +
							getIDPath(reports.get(wo), ipe.getIdReference())),
					ipe);
		} catch (IdReferenceException ire) {
			final IDAssociation idloc =
					(IDAssociation) ire.getAssociatedObject();
			final WorkspaceSaveObject wo = objects.get(idloc.objnum - 1);
			throw new TypedObjectValidationException(String.format(
					"Object %s has invalid %sreference: %s%s",
					getObjectErrorId(wo, idloc.objnum),
					(idloc.provenance ? "provenance " : ""),
					ire.getLocalizedMessage(),
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
						"An error occured while processing IDs: " +
						irhe.getLocalizedMessage(), irhe);
			}
		}
	}

	private String getIDPath(TypedObjectValidationReport r,
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

	private TypedObjectValidationReport validate(
			final WorkspaceSaveObject wo,
			final IdReferenceHandlerSet<IDAssociation> idhandler,
			final int objcount)
			throws TypeStorageException, TypedObjectSchemaException,
			TypedObjectValidationException {
		final TypedObjectValidationReport rep;
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
	
	public List<WorkspaceObjectData> getObjectProvenance(
			final WorkspaceUser user, final List<ObjectIdentifier> loi)
			throws CorruptWorkspaceDBException,
			WorkspaceCommunicationException, InaccessibleObjectException {
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, Set<ObjectPaths>> paths =
				new HashMap<ObjectIDResolvedWS, Set<ObjectPaths>>();
		for (final ObjectIDResolvedWS o: ws.values()) {
			paths.put(o, null);
		}
		//this is pretty gross, think about a better api here
		final Map<ObjectIDResolvedWS,
				Map<ObjectPaths, WorkspaceObjectData>> prov;
		try {
			prov = db.getObjects(paths, true, true);
		} catch (TypedObjectExtractionException toee) {
			throw new RuntimeException(
					"There was an extraction exception even though " +
					"extraction wasn't requested. GG programmers", toee);
		}
		
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(prov.get(ws.get(o)).get(null));
		}
		removeInaccessibleDataCopyReferences(user, ret);
		return ret;
	}

	public List<WorkspaceObjectData> getObjects(final WorkspaceUser user,
			final List<ObjectIdentifier> loi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, Set<ObjectPaths>> paths =
				new HashMap<ObjectIDResolvedWS, Set<ObjectPaths>>();
		for (final ObjectIDResolvedWS o: ws.values()) {
			paths.put(o, null);
		}
		//this is pretty gross, think about a better api here
		final Map<ObjectIDResolvedWS,
				Map<ObjectPaths, WorkspaceObjectData>> data;
		try {
			data = db.getObjects(paths, false, true);
		} catch (TypedObjectExtractionException toee) {
			throw new RuntimeException(
					"There was an extraction exception even though " +
					"extraction wasn't requested. GG programmers", toee);
		}
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(data.get(ws.get(o)).get(null));
		}
		removeInaccessibleDataCopyReferences(user, ret);
		return ret;
	}
	
	public List<WorkspaceObjectData> getObjectsSubSet(final WorkspaceUser user,
			final List<SubObjectIdentifier> loi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException,
			InaccessibleObjectException, TypedObjectExtractionException {
		final List<ObjectIdentifier> objs = new LinkedList<ObjectIdentifier>();
		for (final SubObjectIdentifier soi: loi) {
			objs.add(soi.getObjectIdentifer());
		}
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, objs, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, Set<ObjectPaths>> objpaths =
				new HashMap<ObjectIDResolvedWS, Set<ObjectPaths>>();
		for (final SubObjectIdentifier soi: loi) {
			final ObjectIDResolvedWS o = ws.get(soi.getObjectIdentifer());
			if (!objpaths.containsKey(o)) {
				objpaths.put(o, new HashSet<ObjectPaths>());
			}
			objpaths.get(o).add(soi.getPaths());
		}
		
		//this is kind of disgusting, think about the api here
		final Map<ObjectIDResolvedWS,
				Map<ObjectPaths, WorkspaceObjectData>> data = 
				db.getObjects(objpaths, false, true);
		
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		for (final SubObjectIdentifier soi: loi) {
			ret.add(data.get(ws.get(soi.getObjectIdentifer()))
					.get(soi.getPaths()));
		}
		removeInaccessibleDataCopyReferences(user, ret);
		return ret;
	}
	
	//TODO REFS make static empty object path
	//TODO REFS ObjectChain merge into ObjectIdentity
	//TODO REFS Update docs
	//TODO REFS release notes
	public List<WorkspaceObjectData> getReferencedObjects(
			final WorkspaceUser user,
			final List<ObjectChain> refchains)
			throws CorruptWorkspaceDBException, WorkspaceCommunicationException,
			InaccessibleObjectException, NoSuchReferenceException {
		final LinkedList<ObjectIdentifier> first =
				new LinkedList<ObjectIdentifier>();
		final LinkedList<ObjectIdentifier> chains =
				new LinkedList<ObjectIdentifier>();
		for (final ObjectChain oc: refchains) {
			first.add(oc.getHead());
			chains.addAll(oc.getChain());
		}
		
		final Map<ObjectIdentifier, ObjectIDResolvedWS> resheads = 
				checkPerms(user, first, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> headrefs =
				getObjectOutGoingReferences(resheads, true);
		/* ignore all errors when getting chain objects until actually getting
		 * to the point where we need the data. Otherwise a clever hacker can
		 * explore what objects exist in arbitrary workspaces.
		 */
		final Map<ObjectIdentifier, ObjectIDResolvedWS> reschains =
				checkPerms(user, chains, Permission.NONE, "somthinsbroke",
						true, true, true);
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> chainrefs =
				getObjectOutGoingReferences(reschains, false);
		
		final Map<ObjectIDResolvedWS, Set<ObjectPaths>> toGet =
				new HashMap<ObjectIDResolvedWS, Set<ObjectPaths>>();
		// TODO NOW test ability to specify any object ID (coverage of this method & throwNoSuchRef)
		int chnum = 1;
		for (final ObjectChain chain: refchains) {
			ObjectIdentifier pos = chain.getHead();
			ObjectReferenceSet refs = headrefs.get(resheads.get(pos));
			int posnum = 1;
			for (final ObjectIdentifier oi: chain.getChain()) {
				// refs are guaranteed to exist, so if the db didn't find it
				// the user specified it incorrectly
				if (!reschains.containsKey(oi) ||
						!(chainrefs.containsKey(reschains.get(oi)))) {
					throwNoSuchRefException(pos, oi, chnum, posnum);
				}
				final ObjectReferenceSet current =
						chainrefs.get(reschains.get(oi));
				if (!refs.contains(current.getObjectReference())) {
					throwNoSuchRefException(pos, oi, chnum, posnum);
				}
				pos = oi;
				refs = current;
				posnum++;
			}
			toGet.put(reschains.get(chain.getLast()), null);
			chnum++;
		}
		// GC time
		headrefs.clear();
		chainrefs.clear();
		resheads.clear();
		
		//this is kind of disgusting, think about the api here
		final Map<ObjectIDResolvedWS,
				Map<ObjectPaths, WorkspaceObjectData>> data;
		try {
			data = db.getObjects(toGet, false, false);
		} catch (TypedObjectExtractionException toee) {
			throw new RuntimeException(
					"There was an extraction exception even though " +
					"extraction wasn't requested. GG programmers", toee);
		}
		toGet.clear();
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		for (final ObjectChain oc: refchains) {
			ret.add(data.get(reschains.get(oc.getLast())).get(null));
		}
		removeInaccessibleDataCopyReferences(user, ret);
		return ret;
	}

	private void throwNoSuchRefException(
			final ObjectIdentifier from,
			final ObjectIdentifier to, int chnum, int posnum)
			throws NoSuchReferenceException {
		throw new NoSuchReferenceException(
				String.format(
				"Reference chain #%s, position %s: Object %s %sin " +
				"workspace %s does not contain a reference to " +
				"object %s %sin workspace %s",
				chnum, posnum,
				from.getIdentifierString(),
				from.getVersion() == null ? "" :
					"with version " + from.getVersion() + " ",
				from.getWorkspaceIdentifierString(),
				to.getIdentifierString(),
				to.getVersion() == null ? "" :
					"with version " + to.getVersion() + " ",
				to.getWorkspaceIdentifierString()),
				from, to);
	}

	private Map<ObjectIDResolvedWS, ObjectReferenceSet>
			getObjectOutGoingReferences(
				final Map<ObjectIdentifier, ObjectIDResolvedWS> objs,
				final boolean exceptIfMissingOrDeleted)
				throws WorkspaceCommunicationException, NoSuchObjectException {
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> refs;
		try {
			refs = db.getObjectOutgoingReferences(
					new HashSet<ObjectIDResolvedWS>(objs.values()),
					exceptIfMissingOrDeleted, exceptIfMissingOrDeleted);
		} catch (NoSuchObjectException nsoe) {
			final ObjectIDResolvedWS e = nsoe.getResolvedInaccessibleObject();
			for (Entry<ObjectIdentifier, ObjectIDResolvedWS> entry:
					objs.entrySet()) {
				if (entry.getValue().equals(e)) {
					throw new NoSuchObjectException(
							formatInaccessibleObjectException(
									entry.getKey(), nsoe),
							entry.getValue(), nsoe);
				}
				
			}
			throw new RuntimeException("Something went very wrong here", nsoe);
		}
		return refs;
	}
	
	private static String formatInaccessibleObjectException(
			final ObjectIdentifier oi,
			final InaccessibleObjectException nsoe) {
		final StringBuilder sb = new StringBuilder("Object ");
		sb.append(oi.getIdentifierString());
		sb.append(oi.getVersion() == null ? "" :
			" with version " + oi.getVersion());
		if (nsoe instanceof DeletedObjectException) {
			sb.append(" in workspace ");
		} else if (nsoe instanceof NoSuchObjectException) {
			sb.append(" does not exist in workspace ");
		} else {
			sb.append(" in workspace ");
		}
		sb.append(oi.getWorkspaceIdentifierString());
		if (nsoe instanceof DeletedObjectException) {
			sb.append(" has been deleted");
		} else if (!(nsoe instanceof NoSuchObjectException)) {
			sb.append(" is inaccessible");
		}
		return sb.toString();
	}
	
	private void removeInaccessibleDataCopyReferences(
			final WorkspaceUser user,
			final List<WorkspaceObjectData> data)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		
		final Set<WorkspaceIdentifier> wsis =
				new HashSet<WorkspaceIdentifier>();
		for (final WorkspaceObjectData d: data) {
			if (d.getCopyReference() != null) {
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
			final Reference cref = d.getCopyReference();
			if (cref == null) {
				continue;
			}
			final WorkspaceIdentifier wsi = new WorkspaceIdentifier(
					cref.getWorkspaceID());
			if (!rwsis.containsKey(wsi)) {
				d.setCopySourceInaccessible();
			} else {
				rois.put(d, new ObjectIDResolvedWS(rwsis.get(wsi),
						cref.getObjectID(), cref.getVersion()));
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
			CorruptWorkspaceDBException {
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
			CorruptWorkspaceDBException {
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
			InaccessibleObjectException, CorruptWorkspaceDBException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, Arrays.asList(oi), Permission.READ, "read");
		return db.getObjectHistory(ws.get(oi));
	}
	
	public List<ObjectInformation> getObjectInformation(
			final WorkspaceUser user, final List<ObjectIdentifier> loi,
			final boolean includeMetadata, final boolean nullIfInaccessible)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read",
						nullIfInaccessible, nullIfInaccessible,
						nullIfInaccessible);
		final Map<ObjectIDResolvedWS, ObjectInformation> meta = 
				db.getObjectInformation(
						new HashSet<ObjectIDResolvedWS>(ws.values()),
						includeMetadata, nullIfInaccessible);
		final List<ObjectInformation> ret =
				new ArrayList<ObjectInformation>();
		
		for (final ObjectIdentifier o: loi) {
			if (!ws.containsKey(o) || !meta.containsKey(ws.get(o))) {
				ret.add(null);
			} else {
				ret.add(meta.get(ws.get(o)));
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
			CorruptWorkspaceDBException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = checkPerms(user,
				Arrays.asList(oi), Permission.WRITE, "rename objects in");
		ObjectIDNoWSNoVer.checkObjectName(newname);
		return db.renameObject(ws.get(oi), newname);
	}
	
	public ObjectInformation copyObject(final WorkspaceUser user,
			final ObjectIdentifier from, final ObjectIdentifier to)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException {
		final ObjectIDResolvedWS f = checkPerms(user,
				Arrays.asList(from), Permission.READ, "read").get(from);
		final ObjectIDResolvedWS t = checkPerms(user,
				Arrays.asList(to), Permission.WRITE, "write to").get(to);
		return db.copyObject(user, f, t);
	}
	
	public ObjectInformation revertObject(WorkspaceUser user,
			ObjectIdentifier oi)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException {
		final ObjectIDResolvedWS target = checkPerms(user,
				Arrays.asList(oi), Permission.WRITE, "write to").get(oi);
		return db.revertObject(user, target);
	}
	
	public void setObjectsHidden(final WorkspaceUser user,
			final List<ObjectIdentifier> loi, final boolean hide)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
			CorruptWorkspaceDBException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.WRITE,
						(hide ? "" : "un") + "hide objects from");
		db.setObjectsHidden(new HashSet<ObjectIDResolvedWS>(ws.values()),
				hide);
	}
	
	public void setObjectsDeleted(final WorkspaceUser user,
			final List<ObjectIdentifier> loi, final boolean delete)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			InaccessibleObjectException {
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
		private final Map<T, Map<String, Set<List<String>>>> ids =
				new HashMap<T, Map<String, Set<List<String>>>>();
		private final Map<String, RemappedId> remapped =
				new HashMap<String, RemappedId>();
		
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
				ids.put(associatedObject,
						new HashMap<String, Set<List<String>>>());
			}
			if (!ids.get(associatedObject).containsKey(id)) {
				ids.get(associatedObject).put(id,
						new HashSet<List<String>>());
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
			final Set<ObjectIdentifier> idset =
					new HashSet<ObjectIdentifier>();
			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					final ObjectIdentifier oi;
					try {
						oi = parser.parse(id);
						//Illegal arg is probably not the right exception
					} catch (IllegalArgumentException iae) {
						final List<String> attribs =
								getAnyAttributeSet(assObj, id);
						throw new IdParseException(iae.getMessage(),
								getIdType(), assObj, id, attribs, iae);
					}
					idset.add(oi);
				}
			}
			final Map<ObjectIdentifier, ObjectIDResolvedWS> wsresolvedids =
					resolveIDs(idset);
			
			final Map<ObjectIDResolvedWS, TypeAndReference> objtypes =
					getObjectTypes(wsresolvedids);

			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					final ObjectIdentifier oi = parser.parse(id);
					final TypeAndReference tnr =
							objtypes.get(wsresolvedids.get(oi));
					typeCheckReference(id, tnr.getType(), assObj);
					remapped.put(id, tnr.getReference());
				}
			}
		}

		//use this method when an ID is bad regardless of the attribute set
		//parse error, deleted object, etc.
		private List<String> getAnyAttributeSet(final T assObj, final String id) {
			final List<String> attribs;
			final Set<List<String>> attribset =
					ids.get(assObj).get(id);
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
				final List<TypeDefName> allowedTypes =
						new ArrayList<TypeDefName>();
				for (final String t: allowed) {
					allowedTypes.add(new TypeDefName(t));
				}
				if (!allowedTypes.contains(type.getType())) {
					throw new IdReferenceException(String.format(
							"The type %s of reference %s " + 
							"in this object is not " +
							"allowed - allowed types are %s",
							type.getTypeString(), id, allowed),
							getIdType(), assObj, id, allowed, null);
				}
			}
		}

		private Map<ObjectIDResolvedWS, TypeAndReference> getObjectTypes(
				final Map<ObjectIdentifier, ObjectIDResolvedWS> wsresolvedids)
				throws IdReferenceHandlerException {
			final Map<ObjectIDResolvedWS, TypeAndReference> objtypes;
			if (!wsresolvedids.isEmpty()) {
				try {
					objtypes = db.getObjectType(
							new HashSet<ObjectIDResolvedWS>(
									wsresolvedids.values()));
				} catch (NoSuchObjectException nsoe) {
					final ObjectIDResolvedWS cause =
							nsoe.getResolvedInaccessibleObject();
					ObjectIdentifier oi = null;
					for (final ObjectIdentifier o: wsresolvedids.keySet()) {
						if (wsresolvedids.get(o).equals(cause)) {
							oi = o;
							break;
						}
					}
					throw generateInaccessibleObjectException(nsoe, oi);
				} catch (WorkspaceCommunicationException e) {
					throw new IdReferenceHandlerException(
							"Workspace communication exception", getIdType(),
							e);
				}
			} else {
				objtypes = new HashMap<ObjectIDResolvedWS, TypeAndReference>();
			}
			return objtypes;
		}

		private Map<ObjectIdentifier, ObjectIDResolvedWS> resolveIDs(
				final Set<ObjectIdentifier> idset)
				throws IdReferenceHandlerException {
			final Map<ObjectIdentifier, ObjectIDResolvedWS> wsresolvedids;
			if (!idset.isEmpty()) {
				try {
					wsresolvedids = checkPerms(user, 
							new LinkedList<ObjectIdentifier>(idset),
							Permission.READ, "read");
				} catch (InaccessibleObjectException ioe) {
					throw generateInaccessibleObjectException(ioe);
				} catch (WorkspaceCommunicationException e) {
					throw new IdReferenceHandlerException(
							"Workspace communication exception",
							getIdType(), e);
				} catch (CorruptWorkspaceDBException e) {
					throw new IdReferenceHandlerException(
							"Corrupt workspace exception", getIdType(), e);
				}
			} else {
				wsresolvedids = new HashMap<ObjectIdentifier,
						ObjectIDResolvedWS>();
			}
			return wsresolvedids;
		}

		private IdReferenceException generateInaccessibleObjectException(
				final InaccessibleObjectException ioe) {
			String exception = "No read access to id ";
			return generateInaccessibleObjectException(ioe,
					ioe.getInaccessibleObject(), exception);
		}
		
		private IdReferenceException generateInaccessibleObjectException(
				final NoSuchObjectException ioe,
				final ObjectIdentifier originalObject) {
			String exception = "There is no object with id ";
			return generateInaccessibleObjectException(ioe, originalObject,
					exception);
		}

		private IdReferenceException generateInaccessibleObjectException(
				final InaccessibleObjectException ioe,
				final ObjectIdentifier originalObject,
				final String exception) {
			for (final T assObj: ids.keySet()) {
				for (final String id: ids.get(assObj).keySet()) {
					final ObjectIdentifier oi = parser.parse(id);
					if (oi.equals(originalObject)) {
						final List<String> attribs =
								getAnyAttributeSet(assObj, id);
						return new IdReferenceException(
								exception + id + ": " + ioe.getMessage(),
								getIdType(), assObj,
								id, attribs, ioe);
					}
				}
			}
			return null;
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
