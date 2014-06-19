package us.kbase.workspace.lib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.common.utils.sortjson.UTF8JsonSorterFactory;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
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
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.workspace.database.ObjectChain;
import us.kbase.workspace.database.ObjectChainResolvedWS;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ReferenceParser;
import us.kbase.workspace.database.SubObjectIdentifier;
import us.kbase.workspace.database.TypeAndReference;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceObjectInformation;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.ResourceUsageConfigurationBuilder.ResourceUsageConfiguration;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class Workspace {
	
	//TODO general unit tests
	//TODO import shock objects
	//TODO BIG GC garbage collection - make a static thread that calls a gc() method, waits until all reads done - read counting, read methods must register to static object. Set latest object version on version deletion. How delete entire object? have deleted obj collection with 30 day expiration?
	//TODO BIG SHOCK shock acl integration. Needs auth groups. group = workspace.
	//TODO BIG SHOCK shock node pointer objects that return pointer and set ACLS on pointer.
	//TODO BIG SEARCH separate service - search interface, return changes since date, store most recent update to avoid queries
	//TODO BIG SEARCH separate service - get object changes since date (based on type collection and pointers collection
	//TODO BIG SEARCH index typespecs
	//TODO BIG SUBDATA separate service - subdata search interface. Add ability to 'install' queries that certain users can run? Test subdata creation
	//TODO BIG SUBDATA separate service - subdata search - admin can install and remove indexes.
	
	//TODO need a way to get all types matching a typedef (which might only include a typename) - already exists?
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	private final static int MAX_INFO_COUNT = 10000;
	
	private final static String WS_ID_TYPE = "ws";
	private final static char OBJECT_PATH_SEPARATOR = '/';
	
	private final WorkspaceDatabase db;
	private final TypeDefinitionDB typedb;
	private final ReferenceParser refparse;
	private final TempFilesManager tfm;
	private ResourceUsageConfiguration rescfg;
	
	public Workspace(final WorkspaceDatabase db,
			final ReferenceParser refparse, ResourceUsageConfiguration cfg) {
		if (db == null) {
			throw new IllegalArgumentException("db cannot be null");
		}
		if (refparse == null) {
			throw new IllegalArgumentException("refparse cannot be null");
		}
		this.db = db;
		typedb = db.getTypeValidator().getDB();
		this.refparse = refparse;
		tfm = db.getTempFilesManager();
		rescfg = cfg;
		db.setResourceUsageConfiguration(rescfg);
	}
	
	public ResourceUsageConfiguration getResourceConfig() {
		return rescfg;
	}
	
	public void setResourceConfig(ResourceUsageConfiguration rescfg) {
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
		if (Permission.ADMIN.compareTo(perms.getPermission(wsid, true)) > 0) {
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
	
	private static String getObjectErrorId(final ObjectIDNoWSNoVer oi,
			final int objcount) {
		String objErrId = "#" + objcount;
		objErrId += oi == null ? "" : ", " + oi.getIdentifierString();
		return objErrId;
	}
	
	private class TempObjectData {
		WorkspaceSaveObject wo;
		TypedObjectValidationReport rep;
		int order;
		
		TempObjectData(WorkspaceSaveObject wo,
				TypedObjectValidationReport rep, int order) {
			this.wo = wo;
			this.rep = rep;
			this.order = order;
		}
	}
	
	public List<ObjectInformation> saveObjects(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, 
			List<WorkspaceSaveObject> objects) throws
			WorkspaceCommunicationException, WorkspaceAuthorizationException,
			NoSuchObjectException, CorruptWorkspaceDBException,
			NoSuchWorkspaceException, TypedObjectValidationException,
			TypeStorageException, IOException {
		//TODO this method is a teensy bit long
		if (objects.isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		final ResolvedWorkspaceID rwsi = checkPerms(user, wsi, Permission.WRITE,
				"write to");
		final TypedObjectValidator val = db.getTypeValidator();
		//this method must maintain the order of the objects
		final Map<String, ObjectIdentifier> refToOid =
				new HashMap<String, ObjectIdentifier>();
		final Map<String, ObjectIdentifier> provRefToOid =
				new HashMap<String, ObjectIdentifier>();
		final Map<WorkspaceSaveObject, TempObjectData> reports = 
				new HashMap<WorkspaceSaveObject, TempObjectData>();
		//note these only contains the first object encountered with the ref.
		// For error reporting purposes only
		final Map<ObjectIdentifier, TempObjectData> oidToObject =
				new HashMap<ObjectIdentifier, TempObjectData>();
		final Map<ObjectIdentifier, TempObjectData> provOidToObject =
				new HashMap<ObjectIdentifier, TempObjectData>();
		
		int objcount = 1;
		
		//stage 1: validate & extract & parse references
		for (WorkspaceSaveObject wo: objects) {
			final ObjectIDNoWSNoVer oid = wo.getObjectIdentifier();
			final String objerrid = getObjectErrorId(oid, objcount);
			final TypedObjectValidationReport rep;
			try {
				rep = val.validate(wo.getData(), wo.getType());
			} catch (NoSuchTypeException nste) {
				throw new TypedObjectValidationException(String.format(
						"Object %s failed type checking:\n", objerrid)
						+ nste.getLocalizedMessage(), nste);
			} catch (NoSuchModuleException nsme) {
				throw new TypedObjectValidationException(String.format(
						"Object %s failed type checking:\n", objerrid)
						+ nsme.getLocalizedMessage(), nsme);
			}
			if (!rep.isInstanceValid()) {
				final List<String> e = rep.getErrorMessages();
				final String err = StringUtils.join(e, "\n");
				throw new TypedObjectValidationException(String.format(
						"Object %s failed type checking:\n", objerrid) + err);
			}
			final TempObjectData data = new TempObjectData(wo, rep, objcount);
			for (final IdReference ref: rep.getIdReferences()
					.getIds(WS_ID_TYPE)) {
				processRef(refToOid, oidToObject, objerrid, data, ref.getId(),
						false);
			}
			for (final Provenance.ProvenanceAction action:
				wo.getProvenance().getActions()) {
				for (final String pref: action.getWorkspaceObjects()) {
					processRef(provRefToOid, provOidToObject, objerrid, data,
							pref, true);
				}
			}
			reports.put(wo, data);
			objcount++;
		}
		
		//stage 2: resolve references and get types
		final Map<ObjectIdentifier, ObjectIDResolvedWS> wsresolvedids;
		final Set<ObjectIdentifier> allOids =
				new HashSet<ObjectIdentifier>(oidToObject.keySet());
		allOids.addAll(provOidToObject.keySet());
		if (!allOids.isEmpty()) {
			try {
				wsresolvedids = checkPerms(user, 
						new LinkedList<ObjectIdentifier>(allOids),
						Permission.READ, "read");
			} catch (InaccessibleObjectException ioe) {
				final TypedObjectValidationException tove =
						generateInaccessibleObjectException(refToOid,
								oidToObject, provRefToOid, provOidToObject,
								ioe, ioe.getInaccessibleObject());
				throw tove;
			}
		} else {
			wsresolvedids = new HashMap<ObjectIdentifier,
					ObjectIDResolvedWS>();
		}
		allOids.clear();
		final Map<ObjectIDResolvedWS, TypeAndReference> objtypes;
		if (!wsresolvedids.isEmpty()) {
			try {
				objtypes = db.getObjectType(
						new HashSet<ObjectIDResolvedWS>(wsresolvedids.values()));
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
				final TypedObjectValidationException tove =
						generateInaccessibleObjectException(refToOid,
								oidToObject, provRefToOid, provOidToObject,
								nsoe, oi);
				throw tove;
			}
		} else {
			objtypes = new HashMap<ObjectIDResolvedWS, TypeAndReference>();
		}
		
		oidToObject.clear();
		provOidToObject.clear();
		
		//stage 3: rewrite references
		final Map<String, Reference> newrefs = new HashMap<String, Reference>();
		final Map<String, AbsoluteTypeDefId> reftypes =
				new HashMap<String, AbsoluteTypeDefId>();
		final Set<String> allrefs = new HashSet<String>(refToOid.keySet());
		allrefs.addAll(provRefToOid.keySet());
		for (final String ref: allrefs) {
			ObjectIDResolvedWS roi = wsresolvedids.get(refToOid.get(ref));
			if (roi == null) {
				roi = wsresolvedids.get(provRefToOid.get(ref));
			}
			final TypeAndReference tv = objtypes.get(roi);
			newrefs.put(ref, tv.getReference());
			reftypes.put(ref, tv.getType());
		}
		allrefs.clear();
		wsresolvedids.clear();
		objtypes.clear();
		refToOid.clear();
		provRefToOid.clear();
		
		final List<ResolvedSaveObject> saveobjs =
				new ArrayList<ResolvedSaveObject>();
		long ttlObjSize = 0;
		for (WorkspaceSaveObject wo: objects) {
			final TypedObjectValidationReport rep = reports.get(wo).rep;
			final Map<String, String> replacerefs =
					new HashMap<String, String>();
			final Set<Reference> refs = new HashSet<Reference>();
			final List<Reference> provrefs = new LinkedList<Reference>();
			for (final IdReference r: rep.getIdReferences().getIds(WS_ID_TYPE)) {
				final Set<TypeDefName> allowedTypes = new HashSet<TypeDefName>();
				for (final String a: r.getAttributes()) {
					allowedTypes.add(new TypeDefName(a));
				}
				final TypeDefName type = reftypes.get(r.getId()).getType();
				if (!allowedTypes.isEmpty() && !allowedTypes.contains(type)) {
					throw new TypedObjectValidationException(String.format(
							"Object %s: The type %s of reference %s " + 
							"at location %s in this object is not " +
							"allowed for this object's type, %s. " +
							"Allowed types are: %s",
							getObjectErrorId(wo.getObjectIdentifier(),
									reports.get(wo).order),
							reftypes.get(r.getId()).getTypeString(),
							r.getId(),
							r.getLocation(OBJECT_PATH_SEPARATOR),
							rep.getValidationTypeDefId().getTypeString(),
							r.getAttributes()));
				}
				refs.add(newrefs.get(r.getId()));
				replacerefs.put(r.getId(), newrefs.get(r.getId()).toString());
			}
			for (final Provenance.ProvenanceAction action:
					wo.getProvenance().getActions()) {
				for (final String ref: action.getWorkspaceObjects()) {
					provrefs.add(newrefs.get(ref));
				}
			}
			ttlObjSize += rep.setAbsoluteIdRefMapping(replacerefs);
			saveobjs.add(wo.resolve(rep, refs, provrefs));
		}
		objects = null; // don't screw with the input, but release to gc
		reports.clear();
		reftypes.clear();
		newrefs.clear();
		
		objcount = 1;
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
		return db.saveObjects(user, rwsi, saveobjs);
	}

	private void processRef(final Map<String, ObjectIdentifier> refToOid,
			final Map<ObjectIdentifier, TempObjectData> oidToObject,
			final String objerrid, final TempObjectData data, final String ref,
			final boolean provenance)
			throws TypedObjectValidationException {
		try {
			if (!refToOid.containsKey(ref)) {
				final ObjectIdentifier oi = refparse.parse(ref);
				refToOid.put(ref, oi);
				oidToObject.put(oi, data);
			}
		} catch (IllegalArgumentException iae) {
			throw new TypedObjectValidationException(String.format(
					"Object %s has unparseable %sreference %s: %s",
					objerrid, provenance ? "provenance " : "", ref,
							iae.getLocalizedMessage(), iae));
		}
	}

	private TypedObjectValidationException generateInaccessibleObjectException(
			final Map<String, ObjectIdentifier> refToOid,
			final Map<ObjectIdentifier, TempObjectData> oidToObject,
			final Map<String, ObjectIdentifier> provRefToOid,
			final Map<ObjectIdentifier, TempObjectData> provOidToObject,
			final InaccessibleObjectException ioe, final ObjectIdentifier cause) {
		String ref = null; //must be set correctly below
		TempObjectData tod = null;
		String reftype = "";
		for (final String r: refToOid.keySet()) {
			if (refToOid.get(r).equals(cause)) {
				ref = r;
				tod = oidToObject.get(cause);
				break;
			}
		}
		if (ref == null) {
			for (final String r: provRefToOid.keySet()) {
				if (provRefToOid.get(r).equals(cause)) {
					ref = r;
					tod = provOidToObject.get(cause);
					reftype = "provenance ";
					break;
				}
			}
		}
		final String objerrid = getObjectErrorId(
				tod.wo.getObjectIdentifier(), tod.order);
		final TypedObjectValidationException tove =
				new TypedObjectValidationException(String.format(
				"Object %s has inaccessible %sreference %s: %s",
				objerrid, reftype, ref, ioe.getLocalizedMessage(), ioe));
		return tove;
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
		return ret;
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
}
