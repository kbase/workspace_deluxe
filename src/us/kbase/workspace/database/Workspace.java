package us.kbase.workspace.database;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.JsonDocumentLocation;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.FuncDetailedInfo;
import us.kbase.typedobj.db.FuncInfo;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.db.TypeDetailedInfo;
import us.kbase.typedobj.exceptions.NoSuchFuncException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.SpecParseException;
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
	//TODO BIG GC garbage collection - make a static thread that calls a gc() method, waits until all reads done - read counting, read methods must register to static object. Set latest object version on version deletion. How delete entire object? have deleted obj collection with 30 day expiration?
	//TODO BIG SHOCK shock node pointer objects that return pointer and set ACLS on pointer.
	//TODO BIG SEARCH separate service - search interface, return changes since date, store most recent update to avoid queries
	//TODO BIG SEARCH separate service - get object changes since date (based on type collection and pointers collection
	//TODO BIG SEARCH index typespecs
	//TODO BIG SUBDATA separate service - subdata search interface. Add ability to 'install' queries that certain users can run? Test subdata creation
	//TODO BIG SUBDATA separate service - subdata search - admin can install and remove indexes.
	
	//TODO need a way to get all types matching a typedef (which might only include a typename) - already exists?
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	private final static int MAX_INFO_COUNT = 10000;
	
	private final static IdReferenceType WS_ID_TYPE = new IdReferenceType("ws");
	
	private final WorkspaceDatabase db;
	private final TypeDefinitionDB typedb;
	private final TempFilesManager tfm;
	private ResourceUsageConfiguration rescfg;
	private final ReferenceParser parser;
	
	public Workspace(
			final WorkspaceDatabase db,
			final ResourceUsageConfiguration cfg,
			final ReferenceParser parser) {
		if (db == null) {
			throw new NullPointerException("db cannot be null");
		}
		if (parser == null) {
			throw new NullPointerException("parser cannot be null");
		}
		if (cfg == null) {
			throw new NullPointerException("cfg cannot be null");
		}
		this.db = db;
		typedb = db.getTypeValidator().getDB();
		tfm = db.getTempFilesManager();
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
		return tfm;
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
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi,
				allowDeletedWorkspace);
		if (!ignoreLock) {
			checkLocked(perm, wsid);
		}
		comparePermission(user, perm, db.getPermission(user, wsid),
				wsi, operation);
		return wsid;
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
			final Map<String, String> meta)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		return db.createWorkspace(user, wsname, globalread,
				pruneWorkspaceDescription(description), meta);
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
			final WorkspaceIdentifier wsi, final Map<String, String> meta)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"alter metadata for");
		db.setWorkspaceMetaKey(wsid, meta);
	}
	
	public WorkspaceInformation cloneWorkspace(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final String newname,
			final boolean globalread, final String description,
			final Map<String, String> meta)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			PreExistingWorkspaceException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		new WorkspaceIdentifier(newname, user); //check for errors
		return db.cloneWorkspace(user, wsid, newname, globalread,
				pruneWorkspaceDescription(description), meta);
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

	public Map<User, Permission> getPermissions(final WorkspaceUser user,
				final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
				WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (user == null) {
			throw new IllegalArgumentException("User cannot be null");
		}
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi);
		final PermissionSet perms = db.getPermissions(user, wsid);
		if (Permission.WRITE.compareTo(perms.getPermission(wsid, true)) > 0) {
			final Map<User, Permission> ret = new HashMap<User, Permission>();
			ret.put(perms.getUser(), perms.getUserPermission(wsid, true));
			if (perms.isWorldReadable(wsid, true)) {
				ret.put(perms.getGlobalUser(), Permission.READ);
			}
			return ret;
		}
		return db.getAllPermissions(wsid);
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
		final ObjectIDNoWSNoVer oid = wo.getObjectIdentifier();
		return getObjectErrorId(oid, objcount);
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
		final TypedObjectValidator val = db.getTypeValidator();
		final Map<WorkspaceSaveObject, TypedObjectValidationReport> reports = 
				new HashMap<WorkspaceSaveObject, TypedObjectValidationReport>();
		int objcount = 1;
		for (final WorkspaceSaveObject wo: objects) {
			idhandler.associateObject(new IDAssociation(objcount, false));
			final TypedObjectValidationReport rep = validate(wo, val,
					idhandler, objcount);
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
			final TypedObjectValidator val,
			final IdReferenceHandlerSet<IDAssociation> idhandler,
			final int objcount)
			throws TypeStorageException, TypedObjectSchemaException,
			TypedObjectValidationException {
		final TypedObjectValidationReport rep;
		try {
			rep = val.validate(wo.getData(), wo.getType(), idhandler);
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
			final List<WorkspaceUser> users, final Map<String, String> meta,
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
	
	//insanely long method signatures get me hot
	public List<ObjectInformation> listObjects(final WorkspaceUser user,
			final List<WorkspaceIdentifier> wsis, final TypeDefId type,
			Permission minPerm, final List<WorkspaceUser> savers,
			final Map<String, String> meta, final Date after, final Date before,
			final boolean showHidden, final boolean showDeleted,
			final boolean showOnlyDeleted, final boolean showAllVers,
			final boolean includeMetaData, final boolean excludeGlobal,
			int skip, int limit)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		if (skip < 0) {
			skip = 0;
		}
		if (limit < 1 || limit > MAX_INFO_COUNT) {
			limit = MAX_INFO_COUNT;
		}
		if (minPerm == null || Permission.READ.compareTo(minPerm) > 0) {
			minPerm = Permission.READ;
		}
		if (wsis.isEmpty() && type == null) {
			throw new IllegalArgumentException("At least one filter must be specified.");
		}
		if (meta != null && meta.size() > 1) {
			throw new IllegalArgumentException("Only one metadata spec allowed");
		}
		final Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis =
				db.resolveWorkspaces(new HashSet<WorkspaceIdentifier>(wsis));
		final HashSet<ResolvedWorkspaceID> rw =
				new HashSet<ResolvedWorkspaceID>(rwsis.values());
		final PermissionSet pset = db.getPermissions(user, rw, minPerm,
				excludeGlobal);
		if (!wsis.isEmpty()) {
			for (final WorkspaceIdentifier wsi: wsis) {
				comparePermission(user, Permission.READ,
						pset.getPermission(rwsis.get(wsi), true), wsi, "read");
			}
		}
		return db.getObjectInformation(pset, type, savers, meta, after, before,
				showHidden, showDeleted, showOnlyDeleted, showAllVers,
				includeMetaData, skip, limit);
	}
	
	public List<WorkspaceObjectInformation> getObjectProvenance(
			final WorkspaceUser user, final List<ObjectIdentifier> loi)
			throws CorruptWorkspaceDBException,
			WorkspaceCommunicationException, InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, WorkspaceObjectInformation> prov =
				db.getObjectProvenance(
						new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<WorkspaceObjectInformation> ret =
				new ArrayList<WorkspaceObjectInformation>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(prov.get(ws.get(o)));
		}
		removeInaccessibleProvenanceCopyReferences(user, ret);
		return ret;
	}

	public List<WorkspaceObjectData> getObjects(final WorkspaceUser user,
			final List<ObjectIdentifier> loi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		//this is pretty gross, think about a better api here
		final Map<ObjectIDResolvedWS,
				Map<ObjectPaths, WorkspaceObjectData>> data = 
				db.getObjects(new HashSet<ObjectIDResolvedWS>(ws.values()));
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
				db.getObjects(objpaths);
		
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		for (final SubObjectIdentifier soi: loi) {
			ret.add(data.get(ws.get(soi.getObjectIdentifer()))
					.get(soi.getPaths()));
		}
		removeInaccessibleDataCopyReferences(user, ret);
		return ret;
	}

	public List<WorkspaceObjectData> getReferencedObjects(
			final WorkspaceUser user,
			final List<ObjectChain> refchains)
			throws CorruptWorkspaceDBException, WorkspaceCommunicationException,
			InaccessibleObjectException, NoSuchReferenceException {
		final LinkedList<ObjectIdentifier> first =
				new LinkedList<ObjectIdentifier>();
		final LinkedList<ObjectIdentifier> rest =
				new LinkedList<ObjectIdentifier>();
		for (final ObjectChain oc: refchains) {
			first.add(oc.getHead());
			rest.addAll(oc.getChain());
		}
		final Map<ObjectIdentifier, ObjectIDResolvedWS> resheads = 
				checkPerms(user, first, Permission.READ, "read");
		Map<ObjectIdentifier, ObjectIDResolvedWS> reschains =
				new HashMap<ObjectIdentifier, ObjectIDResolvedWS>();
		if (!rest.isEmpty()) {
			reschains = checkPerms(user, rest, Permission.NONE, "foo", true);
		}
		final Map<ObjectChain, ObjectChainResolvedWS> objs =
				new HashMap<ObjectChain, ObjectChainResolvedWS>();
		for (final ObjectChain oc: refchains) {
			final ObjectIDResolvedWS head = resheads.get(oc.getHead());
			final List<ObjectIDResolvedWS> chain =
					new LinkedList<ObjectIDResolvedWS>();
			for (final ObjectIdentifier oi: oc.getChain()) {
				chain.add(reschains.get(oi));
			}
			objs.put(oc, new ObjectChainResolvedWS(head, chain));
		}
		final Map<ObjectChainResolvedWS, WorkspaceObjectData> res =
				db.getReferencedObjects(
						new HashSet<ObjectChainResolvedWS>(objs.values()));
		final List<WorkspaceObjectData> ret =
				new LinkedList<WorkspaceObjectData>();
		for (final ObjectChain oc: refchains) {
			ret.add(res.get(objs.get(oc)));
		}
		removeInaccessibleDataCopyReferences(user, ret);
		return ret;
	}
	
	private void removeInaccessibleDataCopyReferences(
			final WorkspaceUser user,
			final List<WorkspaceObjectData> data)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		
		final List<WorkspaceObjectInformation> newdata =
				new LinkedList<WorkspaceObjectInformation>();
		for (final WorkspaceObjectData d: data) {
			newdata.add((WorkspaceObjectInformation) d);
		}
		removeInaccessibleProvenanceCopyReferences(user, newdata);
	}
	
	private void removeInaccessibleProvenanceCopyReferences(
			final WorkspaceUser user,
			final List<WorkspaceObjectInformation> info)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		
		final Set<WorkspaceIdentifier> wsis =
				new HashSet<WorkspaceIdentifier>();
		for (final WorkspaceObjectInformation d: info) {
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
		
		final Map<WorkspaceObjectInformation, ObjectIDResolvedWS> rois =
				new HashMap<WorkspaceObjectInformation, ObjectIDResolvedWS>();
		for (final WorkspaceObjectInformation d: info) {
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
		
		for (final Entry<WorkspaceObjectInformation, ObjectIDResolvedWS> e:
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

	private String getUser(WorkspaceUser user) {
		return user == null ? null : user.getUser();
	}
	
	public void requestModuleRegistration(final WorkspaceUser user,
			final String module) throws TypeStorageException {
		if (typedb.isValidModule(module)) {
			throw new IllegalArgumentException(module +
					" module already exists");
		}
		typedb.requestModuleRegistration(module, user.getUser());
	}
	
	public List<OwnerInfo> listModuleRegistrationRequests() throws
			TypeStorageException {
		try {
			return typedb.getNewModuleRegistrationRequests(null, true);
		} catch (NoSuchPrivilegeException nspe) {
			throw new RuntimeException(
					"Something is broken in the administration system", nspe);
		}
	}
	
	public void resolveModuleRegistration(final String module,
			final boolean approve)
			throws TypeStorageException {
		try {
			if (approve) {
				typedb.approveModuleRegistrationRequest(null, module, true);
			} else {
				typedb.refuseModuleRegistrationRequest(null, module, true);
			}
		} catch (NoSuchPrivilegeException nspe) {
			throw new RuntimeException(
					"Something is broken in the administration system", nspe);
		}
	}
	
	//TODO should return the version as well?
	public Map<TypeDefName, TypeChange> compileNewTypeSpec(
			final WorkspaceUser user, final String typespec,
			final List<String> newtypes, final List<String> removeTypes,
			final Map<String, Long> moduleVers, final boolean dryRun,
			final Long previousVer)
			throws SpecParseException, TypeStorageException,
			NoSuchPrivilegeException, NoSuchModuleException {
		return typedb.registerModule(typespec, newtypes, removeTypes,
				getUser(user), dryRun, moduleVers, previousVer);
	}
	
	public Map<TypeDefName, TypeChange> compileTypeSpec(
			final WorkspaceUser user, final String module,
			final List<String> newtypes, final List<String> removeTypes,
			final Map<String, Long> moduleVers, boolean dryRun)
			throws SpecParseException, TypeStorageException,
			NoSuchPrivilegeException, NoSuchModuleException {
		return typedb.refreshModule(module, newtypes, removeTypes,
				user.getUser(), dryRun, moduleVers);
	}
	
	public List<AbsoluteTypeDefId> releaseTypes(final WorkspaceUser user,
			final String module)
			throws NoSuchModuleException, TypeStorageException,
			NoSuchPrivilegeException {
		return typedb.releaseModule(module, user.getUser(), false);
	}
	
	public String getJsonSchema(final TypeDefId type, WorkspaceUser user) throws
			NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return typedb.getJsonSchemaDocument(type, getUser(user));
	}
	
	public List<String> listModules(WorkspaceUser user)
			throws TypeStorageException {
		if (user == null) {
			return typedb.getAllRegisteredModules();
		}
		return typedb.getModulesByOwner(user.getUser());
	}
	
	public ModuleInfo getModuleInfo(final WorkspaceUser user, final ModuleDefId module)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		String userId = getUser(user);
		final us.kbase.typedobj.db.ModuleInfo moduleInfo =
				typedb.getModuleInfo(module, userId, false);
		List<String> functions = new ArrayList<String>();
		for (FuncInfo fi : moduleInfo.getFuncs().values())
			functions.add(module.getModuleName() + "." + fi.getFuncName() + "-" + 
					fi.getFuncVersion());
		return new ModuleInfo(typedb.getModuleSpecDocument(module, userId, false),
				typedb.getModuleOwners(module.getModuleName()),
				moduleInfo.getVersionTime(),  moduleInfo.getDescription(),
				typedb.getJsonSchemasForAllTypes(module, userId, false), 
				moduleInfo.getIncludedModuleNameToVersion(), moduleInfo.getMd5hash(), 
				new ArrayList<String>(functions), moduleInfo.isReleased());
	}
	
	public List<Long> getModuleVersions(final String module, WorkspaceUser user)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		if (user != null && typedb.isOwnerOfModule(module, user.getUser()))
			return typedb.getAllModuleVersionsWithUnreleased(module, user.getUser(), false);
		return typedb.getAllModuleVersions(module);
	}
	
	public List<Long> getModuleVersions(final TypeDefId type, WorkspaceUser user) 
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		final List<ModuleDefId> mods =
				typedb.findModuleVersionsByTypeVersion(type, getUser(user));
		final List<Long> vers = new ArrayList<Long>();
		for (final ModuleDefId m: mods) {
			vers.add(m.getVersion());
		}
		return vers;
	}

	public HashMap<String, List<String>> translateFromMd5Types(List<String> md5TypeList) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		//return typedb.getTypeVersionsForMd5(md5TypeDef);
		HashMap<String, List<String>> ret = new LinkedHashMap<String, List<String>>();
		for (String md5TypeDef : md5TypeList) {
			List<AbsoluteTypeDefId> semantList = typedb.getTypeVersionsForMd5(TypeDefId.fromTypeString(md5TypeDef));
			List<String> retList = new ArrayList<String>();
			for (AbsoluteTypeDefId semantTypeDef : semantList)
				retList.add(semantTypeDef.getTypeString());
			ret.put(md5TypeDef, retList);
		}
		return ret;
	}

	public Map<String,String> translateToMd5Types(List<String> semanticTypeList,
			WorkspaceUser user) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		HashMap<String, String> ret = new LinkedHashMap<String, String>();
		for (String semantString : semanticTypeList) {
			TypeDefId semantTypeDef = TypeDefId.fromTypeString(semantString);
			ret.put(semantString, typedb.getTypeMd5Version(semantTypeDef, getUser(user)).getTypeString());
		}
		return ret;
	}
	
	public long compileTypeSpecCopy(String moduleName, String specDocument, Set<String> extTypeSet,
			String userId, Map<String, String> includesToMd5, Map<String, Long> extIncludedSpecVersions) 
					throws NoSuchModuleException, TypeStorageException, SpecParseException, NoSuchPrivilegeException {
		long lastLocalVer = typedb.getLatestModuleVersionWithUnreleased(moduleName, userId, false);
		Map<String, Long> moduleVersionRestrictions = new HashMap<String, Long>();
		for (Map.Entry<String, String> entry : includesToMd5.entrySet()) {
			String includedModule = entry.getKey();
			String md5 = entry.getValue();
			long extIncludedVer = extIncludedSpecVersions.get(includedModule);
			List<ModuleDefId> localIncludeVersions = new ArrayList<ModuleDefId>(
					typedb.findModuleVersionsByMD5(includedModule, md5));
			if (localIncludeVersions.size() == 0)
				throw new NoSuchModuleException("Can not find local module " + includedModule + " synchronized " +
						"with external version " + extIncludedVer + " (md5=" + md5 + ")");
			us.kbase.typedobj.db.ModuleInfo localIncludedInfo =  typedb.getModuleInfo(includedModule, 
					localIncludeVersions.get(0).getVersion());
			moduleVersionRestrictions.put(localIncludedInfo.getModuleName(), localIncludedInfo.getVersionTime());
		}
		Set<String> prevTypes = new HashSet<String>(typedb.getModuleInfo(moduleName, lastLocalVer).getTypes().keySet());
		Set<String> typesToSave = new HashSet<String>(extTypeSet);
		List<String> allTypes = new ArrayList<String>(prevTypes);
		allTypes.addAll(typesToSave);
		List<String> typesToUnregister = new ArrayList<String>();
		for (String typeName : allTypes) {
			if (prevTypes.contains(typeName)) {
				if (typesToSave.contains(typeName)) {
					typesToSave.remove(typeName);
				} else {
					typesToUnregister.add(typeName);
				}
			}
		}
		typedb.registerModule(specDocument, new ArrayList<String>(typesToSave), typesToUnregister, 
				userId, false, moduleVersionRestrictions);
		return typedb.getLatestModuleVersionWithUnreleased(moduleName, userId, false);
	}
	
	public TypeDetailedInfo getTypeInfo(String typeDef, boolean markTypeLinks, WorkspaceUser user) 
			throws NoSuchModuleException, TypeStorageException, NoSuchTypeException {
		String userId = getUser(user);
		return typedb.getTypeDetailedInfo(TypeDefId.fromTypeString(typeDef), markTypeLinks, userId);
	}
	
	public FuncDetailedInfo getFuncInfo(String funcDef, boolean markTypeLinks, WorkspaceUser user) 
			throws NoSuchModuleException, TypeStorageException, NoSuchFuncException {
		TypeDefId tempDef = TypeDefId.fromTypeString(funcDef);
		String userId = getUser(user);
		return typedb.getFuncDetailedInfo(tempDef.getType().getModule(), 
				tempDef.getType().getName(), tempDef.getVerString(), markTypeLinks, userId);
	}

	public void grantModuleOwnership(String moduleName, String newOwner, boolean withGrantOption,
			WorkspaceUser user, boolean isAdmin) throws TypeStorageException, NoSuchPrivilegeException {
		typedb.addOwnerToModule(getUser(user), moduleName, newOwner, withGrantOption, isAdmin);
	}
	
	public void removeModuleOwnership(String moduleName, String oldOwner, WorkspaceUser user, 
			boolean isAdmin) throws NoSuchPrivilegeException, TypeStorageException {
		typedb.removeOwnerFromModule(getUser(user), moduleName, oldOwner, isAdmin);
	}
	
	public Map<String, Map<String, String>> listAllTypes(boolean withEmptyModules) 
			throws TypeStorageException, NoSuchModuleException {
		Map<String, Map<String, String>> ret = new TreeMap<String, Map<String, String>>();
		for (String moduleName : typedb.getAllRegisteredModules()) {
			Map<String, String> typeMap = new TreeMap<String, String>();
			for (String key : typedb.getModuleInfo(moduleName).getTypes().keySet())
				typeMap.put(typedb.getModuleInfo(moduleName).getTypes().get(key).getTypeName(), 
						typedb.getModuleInfo(moduleName).getTypes().get(key).getTypeVersion());
			if (withEmptyModules || !typeMap.isEmpty())
				ret.put(moduleName, typeMap);
		}
		return ret;
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
