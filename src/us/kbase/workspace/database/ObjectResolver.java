package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.nonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import us.kbase.workspace.database.exceptions.CorruptWorkspaceDBException;
import us.kbase.workspace.database.exceptions.InaccessibleObjectException;
import us.kbase.workspace.database.exceptions.NoSuchObjectException;
import us.kbase.workspace.database.exceptions.NoSuchReferenceException;
import us.kbase.workspace.database.exceptions.NoSuchWorkspaceException;
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.refsearch.ReferenceGraphSearch;
import us.kbase.workspace.database.refsearch.ReferenceGraphTopologyProvider;
import us.kbase.workspace.database.refsearch.ReferenceProviderException;
import us.kbase.workspace.database.refsearch.ReferenceSearchFailedException;
import us.kbase.workspace.database.refsearch.ReferenceSearchMaximumSizeExceededException;

public class ObjectResolver {
	
	//TODO NOW JAVADOC
	//TODO NOW TEST
	
	public final static int MAX_OBJECT_SEARCH_COUNT_DEFAULT = 10000;
	
	private final WorkspaceDatabase db;
	private final WorkspaceUser user;
	private final List<ObjectIdentifier> objects;
	private final boolean nullIfInaccessible;
	private final int maximumObjectSearchCount;
	private final Map<ObjectIdentifier, ObjectIDResolvedWS> nopath;
	private final Map<ObjectIdentifier, ObjectIDResolvedWS> withpath;
	private final Map<ObjectIdentifier, List<Reference>> withpathRefPath;
	
	public ObjectResolver(
			final WorkspaceDatabase db,
			final WorkspaceUser user,
			final List<ObjectIdentifier> objects,
			final boolean nullIfInaccessible,
			final int maxSearch)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchReferenceException,
				ReferenceSearchMaximumSizeExceededException {
		this.db = db;
		this.user = user;
		this.objects = Collections.unmodifiableList(objects);
		this.nullIfInaccessible = nullIfInaccessible;
		this.maximumObjectSearchCount = maxSearch;
		final ResolvedRefPaths paths = resolve();
		this.nopath = Collections.unmodifiableMap(paths.nopath);
		this.withpath = Collections.unmodifiableMap(paths.withpath);
		this.withpathRefPath = Collections.unmodifiableMap(paths.withpathRefPath);
	}
	
	public enum ObjectResolution {
		
		NO_PATH,
		
		PATH,
		
		INACCESSIBLE;
	}
	
	public List<ObjectIdentifier> getObjects() {
		return objects;
	}
	
	public ObjectResolution getObjectResolution(final ObjectIdentifier objID) {
		nonNull(objID, "objID");
		if (nopath.containsKey(objID)) {
			return ObjectResolution.NO_PATH;
		} else if (withpath.containsKey(objID)) {
			return ObjectResolution.PATH;
		} else {
			return ObjectResolution.INACCESSIBLE;
		}
	}
	
	public ObjectIDResolvedWS getResolvedObject(final ObjectIdentifier objID) {
		if (nopath.containsKey(objID)) {
			return nopath.get(objID);
		} else if (withpath.containsKey(objID)) {
			return withpath.get(objID);
		} else {
			throw new IllegalArgumentException("Object is inaccessible");
		}
	}
	
	public Set<ObjectIDResolvedWS> getResolvedObjects(final boolean withPath) {
		if (withPath) {
			return new HashSet<>(withpath.values());
		} else {
			return new HashSet<>(nopath.values());
		}
	}
	
	public List<Reference> getReferencePath(final ObjectIdentifier objID) {
		if (withpath.containsKey(objID)) {
			return new ArrayList<>(withpathRefPath.get(objID));
		} else {
			throw new IllegalArgumentException(
					"Direct access to objID is available, no path was needed");
		}
	}
	
	public Map<ObjectIdentifier, ObjectIDResolvedWS> getNoPaths() { //TODO NOW temporary. Remove this later in the refactoring cycle.
		return nopath;
	}

	public Map<ObjectIdentifier, ObjectIDResolvedWS> getWithPaths() { //TODO NOW temporary. Remove this later in the refactoring cycle.
		return withpath;
	}
	
	public Map<ObjectIdentifier, List<Reference>> getRefPaths() { //TODO NOW temporary. Remove this later in the refactoring cycle.
		return withpathRefPath;
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
	
	/* used to resolve object IDs that might contain reference chains */
	private ResolvedRefPaths resolve()
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchReferenceException,
				ReferenceSearchMaximumSizeExceededException {
		final PermissionsCheckerFactory permfac = new PermissionsCheckerFactory(db, user);
		Set<ObjectIdentifier> lookup = new HashSet<>();
		List<ObjectIdentifier> nolookup = new LinkedList<>();
		for (final ObjectIdentifier o: objects) {
			if (o instanceof ObjectIDWithRefPath && ((ObjectIDWithRefPath) o).isLookupRequired()) {
				lookup.add(o);
			} else {
				nolookup.add(o);
			}
		}

		//handle the faster cases first, fail before the searches
		Map<ObjectIdentifier, ObjectIDResolvedWS> ws = new HashMap<>();
		if (!nolookup.isEmpty()) {
			ws = permfac.getObjectChecker(nolookup, Permission.READ)
					.withSuppressErrors(nullIfInaccessible).check();
		}
		nolookup = null; //gc
		
		final List<ObjectIDWithRefPath> refpaths = new LinkedList<>();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> heads = new HashMap<>();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> std = new HashMap<>();
		for (final ObjectIdentifier o: objects) {
			if (lookup.contains(o)) { // need to do a lookup on this one, skip
				refpaths.add(null); //maintain count for error reporting
			} else if (!ws.containsKey(o)) { // skip, workspace wasn't resolved
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
				permfac, refpaths, heads, nullIfInaccessible).withStandardObjects(std);
		
		return resolvedPaths.merge(searchObjectDAG(permfac.getUser(), lookup, nullIfInaccessible));
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
			return db.resolveWorkspaces(wsis, true);
		} catch (NoSuchWorkspaceException e) {
			throw new RuntimeException("Threw exception when explicitly told not to", e);
		}
	}
	
	private ResolvedRefPaths resolveReferencePaths(
			final PermissionsCheckerFactory permfac,
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
				permfac.getObjectChecker(allRefPathEntries, Permission.NONE)
						.withIncludeDeletedWorkspaces()
						.check();
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

	public static Builder getBuilder(final WorkspaceDatabase db, final WorkspaceUser user) {
		return new Builder(db, user);
	}
	
	public static class Builder {
		
		private final WorkspaceDatabase db;
		private final WorkspaceUser user;
		private final List<ObjectIdentifier> objects = new LinkedList<>();
		private boolean nullIfInaccessible = false;
		private int maxSearch = MAX_OBJECT_SEARCH_COUNT_DEFAULT;
		
		private Builder(final WorkspaceDatabase db, final WorkspaceUser user) {
			nonNull(db, "db");
			this.db = db;
			this.user = user;
		}
		
		public ObjectResolver resolve()
				throws WorkspaceCommunicationException, InaccessibleObjectException,
					CorruptWorkspaceDBException, NoSuchReferenceException,
					ReferenceSearchMaximumSizeExceededException {
			if (objects.isEmpty()) {
				throw new IllegalArgumentException("No object identifiers provided");
			}
			return new ObjectResolver(db, user, objects, nullIfInaccessible, maxSearch);
		}
		
		public ObjectResolver buildEmpty() {

			try {
				return new ObjectResolver(db, user, Collections.emptyList(), nullIfInaccessible,
						maxSearch);
			} catch (WorkspaceCommunicationException | InaccessibleObjectException |
					CorruptWorkspaceDBException | NoSuchReferenceException |
					ReferenceSearchMaximumSizeExceededException e) {
				throw new RuntimeException(
						"Exceptions should be impossible with an empty input set", e);
			}
		}
		
		public Builder withNullIfInaccessible(final boolean nullIfInaccessible) {
			this.nullIfInaccessible = nullIfInaccessible;
			return this;
		}
		
		public Builder withMaximumObjectsSearched(final int count) {
			if (count < 1) {
				throw new IllegalArgumentException("count must be > 0");
			}
			maxSearch = count;
			return this;
		}
		
		public Builder withObject(final ObjectIdentifier object) {
			nonNull(object, "object");
			objects.add(object);
			return this;
		}
	}

}
