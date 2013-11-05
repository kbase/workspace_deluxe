package us.kbase.workspace.workspaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.core.TypedObjectValidator;
import us.kbase.typedobj.db.ModuleDefId;
import us.kbase.typedobj.db.OwnerInfo;
import us.kbase.typedobj.db.TypeChange;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.BadJsonSchemaDocumentException;
import us.kbase.typedobj.exceptions.InstanceValidationException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchPrivilegeException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.RelabelIdReferenceException;
import us.kbase.typedobj.exceptions.SpecParseException;
import us.kbase.typedobj.exceptions.TypeStorageException;
import us.kbase.typedobj.exceptions.TypedObjectValidationException;
import us.kbase.typedobj.idref.WsIdReference;
import us.kbase.workspace.database.ObjectIDNoWSNoVer;
import us.kbase.workspace.database.PermissionSet;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.ReferenceParser;
import us.kbase.workspace.database.TypeAndReference;
import us.kbase.workspace.database.WorkspaceDatabase;
import us.kbase.workspace.database.ObjectIDResolvedWS;
import us.kbase.workspace.database.ObjectIdentifier;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.ObjectInfoUserMeta;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.ResolvedWorkspaceID;
import us.kbase.workspace.database.User;
import us.kbase.workspace.database.WorkspaceIdentifier;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.PreExistingWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.exceptions.WorkspaceAuthorizationException;

public class Workspaces {
	
	//TODO rename workspace / object
	//TODO clone workspace
	//TODO get history of object
	//TODO copy object(s)
	//TODO move objects(s) ? needed?
	//TODO revert object
	//TODO lock workspace, publish workspace
	//TODO list workspaces w/ filters on globalread, user, deleted (ONWER)
	//TODO list objects w/ filters on ws, creator, type, meta, deleted (WRITE), hidden
	//TODO set global read
	//TODO set description
	//TODO get objects by ref chain
	//TODO get provenance by ref chain
	//TODO import shock objects
	//TODO think about listing most recent version of objects - possible to avoid sort if not querying by object id/name?
	//TODO BIG GC garbage collection - make a static thread that calls a gc() method, waits until all reads done - read counting, read methods must register to static object. Set latest object version on version deletion. How delete entire object? have deleted obj collection with 30 day expiration?
	//TODO BIG SHOCK shock acl integration. Needs auth groups. group = workspace.
	//TODO BIG SHOCK shock node pointer objects that return pointer and set ACLS on pointer.
	//TODO BIG SEARCH separate service - search interface, return changes since date, store most recent update to avoid queries
	//TODO BIG SEARCH separate service - get object changes since date (based on type collection and pointers collection
	//TODO BIG SEARCH index typespecs
	//TODO BIG SUBDATA separate service - subdata search interface. Add ability to 'install' queries that certain users can run? Test subdata creation
	//TODO BIG SUBDATA separate service - subdata search - admin can install and remove indexes.
	//TODO performance tests with large / many objects
	
	//TODO 1 lots of tests
	
	//TODO need a way to get all types matching a typedef (which might only include a typename) - already exists?
	
	private final static int MAX_WS_DESCRIPTION = 1000;
	
	private final WorkspaceDatabase db;
	private final TypeDefinitionDB typedb;
	private final ReferenceParser refparse;
	
	public Workspaces(final WorkspaceDatabase db,
			final ReferenceParser refparse) {
		if (db == null) {
			throw new IllegalArgumentException("db cannot be null");
		}
		if (refparse == null) {
			throw new IllegalArgumentException("refparse cannot be null");
		}
		this.db = db;
		typedb = db.getTypeValidator().getDB();
		this.refparse = refparse;
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
	
	private ResolvedWorkspaceID checkPerms(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission perm,
			final String operation)
			throws CorruptWorkspaceDBException, WorkspaceAuthorizationException,
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		return checkPerms(user, wsi, perm, operation, false);
	}
	
	private ResolvedWorkspaceID checkPerms(final WorkspaceUser user,
			final WorkspaceIdentifier wsi, final Permission perm,
			final String operation, boolean allowDeletedWorkspace)
			throws CorruptWorkspaceDBException, WorkspaceAuthorizationException,
			NoSuchWorkspaceException, WorkspaceCommunicationException {
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi,
				allowDeletedWorkspace);
		comparePermission(user, perm, db.getPermission(user, wsid),
				wsi, operation);
		return wsid;
	}
	
	private Map<ObjectIdentifier, ObjectIDResolvedWS> checkPerms(
			final WorkspaceUser user, final List<ObjectIdentifier> loi,
			final Permission perm, final String operation) throws
			WorkspaceCommunicationException, InaccessibleObjectException,
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
				rwsis = db.resolveWorkspaces(wsis.keySet());
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
			final ResolvedWorkspaceID r = rwsis.get(o.getWorkspaceIdentifier());
			try {
				comparePermission(user, perm, perms.getPermission(r), o,
						operation);
			} catch (WorkspaceAuthorizationException wae) {
				throw new InaccessibleObjectException(String.format(
						"Object %s cannot be accessed: %s",
						o.getIdentifierString(), wae.getLocalizedMessage()),
						o, wae);
			}
			ret.put(o, o.resolveWorkspace(r));
		}
		return ret;
	}
	
	public WorkspaceInformation createWorkspace(final WorkspaceUser user, 
			final String wsname, boolean globalread, String description)
			throws PreExistingWorkspaceException,
			WorkspaceCommunicationException, CorruptWorkspaceDBException {
		new WorkspaceIdentifier(wsname, user); //check for errors
		if(description != null && description.length() > MAX_WS_DESCRIPTION) {
			description = description.substring(0, MAX_WS_DESCRIPTION);
		}
		return db.createWorkspace(user, wsname, globalread, description);
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
			final Permission permission) throws CorruptWorkspaceDBException,
			NoSuchWorkspaceException, WorkspaceAuthorizationException,
			WorkspaceCommunicationException {
		if (Permission.OWNER.compareTo(permission) <= 0) {
			throw new IllegalArgumentException("Cannot set owner permission");
		}
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.ADMIN,
				"set permissions on");
		db.setPermissions(wsid, users, permission);
	}

	//TODO refactor to use permission sets
	public Map<User, Permission> getPermissions(final WorkspaceUser user,
				final WorkspaceIdentifier wsi) throws NoSuchWorkspaceException,
				WorkspaceCommunicationException, CorruptWorkspaceDBException {
		if (user == null) {
			throw new IllegalArgumentException("User cannot be null");
		}
		final ResolvedWorkspaceID wsid = db.resolveWorkspace(wsi);
		final PermissionSet perms = db.getPermissions(user, wsid);
		if (Permission.ADMIN.compareTo(perms.getPermission(wsid)) > 0) {
			final Map<User, Permission> ret = new HashMap<User, Permission>();
			ret.put(perms.getUser(), perms.getUserPermission(wsid));
			if (perms.isWorldReadable(wsid)) {
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
			NoSuchWorkspaceException, NoSuchTypeException,
			NoSuchModuleException, TypeStorageException,
			TypedObjectValidationException,
			BadJsonSchemaDocumentException, InstanceValidationException { //TODO get rid of these when possible
		//TODO this method is a teensy bit long
		if (objects.isEmpty()) {
			throw new IllegalArgumentException("No data provided");
		}
		final ResolvedWorkspaceID rwsi = checkPerms(user, wsi, Permission.WRITE,
				"write to");
		final TypedObjectValidator val = db.getTypeValidator();
		//this method must maintain the order of the objects
		//TODO tests for validation of objects
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
			}
			if (!rep.isInstanceValid()) {
				final String[] e = rep.getErrorMessages();
				final String err = StringUtils.join(e, "\n");
				throw new TypedObjectValidationException(String.format(
						"Object %s failed type checking:\n", objerrid) + err);
			}
			final TempObjectData data = new TempObjectData(wo, rep, objcount);
			for (final WsIdReference ref: rep.getWsIdReferences()) {
				//WsIdReference doesn't implement equals or hashcode so use string
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
		for (WorkspaceSaveObject wo: objects) {
			final TypedObjectValidationReport rep = reports.get(wo).rep;
			final int objnum = reports.get(wo).order;
			final Map<String, String> replacerefs =
					new HashMap<String, String>();
			final Set<Reference> refs = new HashSet<Reference>();
			final List<Reference> provrefs = new LinkedList<Reference>();
			for (final WsIdReference r: rep.getWsIdReferences()) {
				if (!r.isValidInstanceType(reftypes.get(r.getId()))) {
					throw new TypedObjectValidationException(String.format(
							"Object %s: The type %s of reference %s " + 
							"contained in this object is not " +
							"allowed for this object's type, %s",
							getObjectErrorId(wo.getObjectIdentifier(), objnum),
							reftypes.get(r.getId()).getTypeString(),
							r.getId(),
							rep.getValidationTypeDefId().getTypeString()));
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
			try {
				//modifies data in place
				rep.relabelWsIdReferences(replacerefs);
			} catch (RelabelIdReferenceException ref) {
				/* this occurs when two references in the same hash resolve
				 * to the same reference, so one value would be lost
				 */
				throw new TypedObjectValidationException(String.format(
						"Object %s: Two references in a single hash are identical when resolved, resulting in a loss of data: ",
						getObjectErrorId(wo.getObjectIdentifier(), objnum))
						+ ref.getLocalizedMessage(), ref);
			}
			saveobjs.add(wo.resolve(rep, refs, provrefs));
		}
		objects = null; // don't screw with the input, but release to gc
		reports.clear();
		reftypes.clear();
		newrefs.clear();
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
	
	public List<WorkspaceInformation> prealphaListWorkspaces(
			final WorkspaceUser user)
			throws WorkspaceCommunicationException,
			CorruptWorkspaceDBException {
		final PermissionSet perms =
				db.getWorkspacesWithPermission(user, Permission.READ);
		return db.getWorkspaceInformation(perms);
	}
	
	public List<ObjectInformation> prealphaListObjects(final WorkspaceUser user,
			final WorkspaceIdentifier wsi)
			throws CorruptWorkspaceDBException, NoSuchWorkspaceException,
			WorkspaceCommunicationException, WorkspaceAuthorizationException {
		final ResolvedWorkspaceID wsid = checkPerms(user, wsi, Permission.READ,
				"read");
		return db.getObjectInformation(wsid);
	}
	
	public List<WorkspaceObjectData> getObjects(final WorkspaceUser user,
			final List<ObjectIdentifier> loi) throws
			CorruptWorkspaceDBException, WorkspaceCommunicationException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, WorkspaceObjectData> data = 
				db.getObjects(new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<WorkspaceObjectData> ret =
				new ArrayList<WorkspaceObjectData>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(data.get(ws.get(o)));
		}
		return ret;
	}
	
	public List<ObjectInfoUserMeta> getObjectInformation(
			final WorkspaceUser user, final List<ObjectIdentifier> loi)
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException,
			InaccessibleObjectException {
		final Map<ObjectIdentifier, ObjectIDResolvedWS> ws = 
				checkPerms(user, loi, Permission.READ, "read");
		final Map<ObjectIDResolvedWS, ObjectInfoUserMeta> meta = 
				db.getObjectInformation(new HashSet<ObjectIDResolvedWS>(ws.values()));
		final List<ObjectInfoUserMeta> ret =
				new ArrayList<ObjectInfoUserMeta>();
		
		for (final ObjectIdentifier o: loi) {
			ret.add(meta.get(ws.get(o)));
		}
		return ret;
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
				(delete ? "" : "un") + "delete", !delete);
		db.setWorkspaceDeleted(wsid, delete);
	}

	//TODO tests for module registration/compile/list/update etc.
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
			return typedb.getNewModuleRegistrationRequests(null);
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
				typedb.approveModuleRegistrationRequest(null, module);
			} else {
				typedb.refuseModuleRegistrationRequest(null, module);
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
				user.getUser(), dryRun, moduleVers, previousVer);
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
		return typedb.releaseModule(module, user.getUser());
	}
	
	public String getJsonSchema(final TypeDefId type) throws
			NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return typedb.getJsonSchemaDocument(type);
	}
	
	public List<String> listModules(WorkspaceUser user)
			throws TypeStorageException {
		if (user == null) {
			return typedb.getAllRegisteredModules();
		}
		return typedb.getModulesByOwner(user.getUser());
	}
	
	public ModuleInfo getModuleInfo(final ModuleDefId module)
			throws NoSuchModuleException, TypeStorageException {
		final us.kbase.typedobj.db.ModuleInfo moduleInfo =
				typedb.getModuleInfo(module);
		return new ModuleInfo(typedb.getModuleSpecDocument(module),
				typedb.getModuleOwners(module.getModuleName()),
				moduleInfo.getVersionTime(),  moduleInfo.getDescription(),
				typedb.getJsonSchemasForAllTypes(module), 
				moduleInfo.getIncludedModuleNameToVersion(), moduleInfo.getMd5hash());
	}
	
	public List<Long> getModuleVersions(final String module, WorkspaceUser user)
			throws NoSuchModuleException, TypeStorageException, NoSuchPrivilegeException {
		if (user != null && typedb.isOwnerOfModule(module, user.getUser()))
			return typedb.getAllModuleVersionsWithUnreleased(module, user.getUser());
		return typedb.getAllModuleVersions(module);
	}
	
	public List<Long> getModuleVersions(final TypeDefId type) 
			throws NoSuchModuleException, TypeStorageException,
			NoSuchTypeException {
		final List<ModuleDefId> mods =
				typedb.findModuleVersionsByTypeVersion(type);
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

	public Map<String,String> translateToMd5Types(List<String> semanticTypeList) 
			throws TypeStorageException, NoSuchTypeException, NoSuchModuleException {
		HashMap<String, String> ret = new LinkedHashMap<String, String>();
		for (String semantString : semanticTypeList) {
			TypeDefId semantTypeDef = TypeDefId.fromTypeString(semantString);
			ret.put(semantString, typedb.getTypeMd5Version(semantTypeDef).getTypeString());
		}
		return ret;
	}
	
	public long compileTypeSpecCopy(String moduleName, String specDocument, Set<String> extTypeSet,
			String userId, Map<String, String> includesToMd5, Map<String, Long> extIncludedSpecVersions) 
					throws NoSuchModuleException, TypeStorageException, SpecParseException, NoSuchPrivilegeException {
		long lastLocalVer = typedb.getLatestModuleVersionWithUnreleased(moduleName, userId);
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
		return typedb.getLatestModuleVersionWithUnreleased(moduleName, userId);
	}
}
