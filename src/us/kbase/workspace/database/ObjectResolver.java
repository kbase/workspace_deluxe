package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.nonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
	private final PermissionsCheckerFactory permissionsFactory;
	private final boolean nullIfInaccessible;
	private final int maximumObjectSearchCount;
	
	/* only the below are accessible via the api. The variables above are only needed during the
	 * object resolution process. 
	 * 
	 * Could move the resolution process into the builder to get rid of them, but they're
	 * cheap memory-wise, that'd means an extra layer of indentation, but most importantly I'm
	 * lazy.
	 */
	private final List<ObjectIdentifier> objects;
	private final Map<ObjectIdentifier, ObjectIDResolvedWS> nopath = new HashMap<>();
	private final Map<ObjectIdentifier, ObjectIDResolvedWS> withpath = new HashMap<>();
	private final Map<ObjectIdentifier, List<Reference>> withpathRefPath = new HashMap<>();
	
	private ObjectResolver(
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
		this.permissionsFactory = new PermissionsCheckerFactory(db, user);
		this.objects = Collections.unmodifiableList(objects);
		this.nullIfInaccessible = nullIfInaccessible;
		this.maximumObjectSearchCount = maxSearch;
		resolve();
	}
	
	public enum ObjectResolution {
		
		NO_PATH,
		
		PATH,
		
		INACCESSIBLE;
	}
	
	public List<ObjectIdentifier> getObjects() {
		return objects;
	}
	
	public Set<ObjectIdentifier> getObjects(final boolean withPath) {
		if (withPath) {
			return new HashSet<>(withpath.keySet());
		} else {
			return new HashSet<>(nopath.keySet());
		}
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
	
	/* used to resolve object IDs that might contain reference chains */
	private void resolve()
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchReferenceException,
				ReferenceSearchMaximumSizeExceededException {
		final Set<ObjectIdentifier> lookup = new HashSet<>();
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
			ws = permissionsFactory.getObjectChecker(nolookup, Permission.READ)
					.withSuppressErrors(nullIfInaccessible).check();
		}
		nolookup = null; //gc
		
		final List<ObjectIDWithRefPath> refpaths = new LinkedList<>();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> heads = new HashMap<>();
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
				nopath.put(o, ws.get(o));
			}
		}
		ws = null; //GC

		// this should exclude any heads that are deleted, even if nullIfInaccessible is true
		// do this before starting the search, fail early before the expensive part
		resolveReferencePaths(refpaths, heads);
		
		searchObjectDAG(lookup);
	}
	
	//TODO REF LOOKUP positive and negative caches (?)

	/* Modifies lookup in place to remove objects that don't need lookup.
	 * Note the reference path returned for looked up objects is currently incorrect. 
	 */
	private void searchObjectDAG(final Set<ObjectIdentifier> lookup)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, ReferenceSearchMaximumSizeExceededException {
		if (lookup.isEmpty()) {
			return;
		}
		Map<WorkspaceIdentifier, ResolvedWorkspaceID> rwsis = resolveWorkspaces(lookup);
		final Set<Long> readableWorkspaceIDs = getReadableWorkspaces();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> searchRequired = new HashMap<>();
		final Iterator<ObjectIdentifier> oiter = lookup.iterator();
		while (oiter.hasNext()) {
			final ObjectIdentifier o = oiter.next();
			final ResolvedWorkspaceID rwsi = rwsis.get(o.getWorkspaceIdentifier());
			if (rwsi != null) {
				final ObjectIDResolvedWS oid = o.resolveWorkspace(rwsi);
				if (readableWorkspaceIDs.contains(rwsi.getID())) {
					//TODO NOW BUG what if the object is deleted?
					nopath.put(o, oid);
					oiter.remove();
				} else {
					searchRequired.put(o, oid);
				}
			}
		}
		if (lookup.isEmpty()) { // all objects were directly accessible
			return;
		}
		rwsis = null;
		if (readableWorkspaceIDs.isEmpty()) { // some objects were in missing workspaces
			if (nullIfInaccessible) {
				return;
			} else {
				throw generateInaccessibleObjectException(lookup.iterator().next());
			}
		}
		searchObjectDAG(readableWorkspaceIDs, lookup, searchRequired);
	}
	
	private Set<Long> getReadableWorkspaces()
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		//could make a method to just get IDs of workspace with specific permission to save mem
		final PermissionSet pset = db.getPermissions(user, Permission.READ, false);
		return pset.getWorkspaces().stream()
				.filter(ws -> !ws.isDeleted())
				.map(ws -> ws.getID())
				.collect(Collectors.toSet());
	}

	private void searchObjectDAG(
			final Set<Long> wsIDs,
			final Set<ObjectIdentifier> lookup,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs)
			throws WorkspaceCommunicationException, ReferenceSearchMaximumSizeExceededException,
				InaccessibleObjectException {
		
		final Map<ObjectIDResolvedWS, Reference> objrefs = db.getObjectReference(
				new HashSet<>(resobjs.values()));
		try {
			// will throw an exception if can't find a ref for any object in lookup
			final Set<Reference> startingRefs = searchObjectDAGGetStartingRefs(
					lookup, resobjs, objrefs);
			//so starting refs can't be empty unless nullIfInaccessible is true
			if (nullIfInaccessible && startingRefs.isEmpty()) {
				return;
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
			searchObjectDAGBuildResolvedObjectPaths(resobjs, objrefs, search);
		} catch (final ReferenceSearchFailedException |
				ObjectDAGSearchFromObjectIDFailedException e) {
			final ObjectIdentifier failedOn = searchObjectDAGGetSearchFailedTarget(
					e, objrefs, resobjs);
			// ensure exceptions are thrown from the same place so users can't probe arbitrary
			// workspaces. Returning the stack trace for errors just might have been a bad idea.
			throw generateInaccessibleObjectException(failedOn);
		} catch (final ReferenceProviderException e) {
			throw (WorkspaceCommunicationException) e.getCause();
		}
	}

	private Set<Reference> searchObjectDAGGetStartingRefs(
			final Set<ObjectIdentifier> lookup,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs,
			final Map<ObjectIDResolvedWS, Reference> objrefs)
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

	private void searchObjectDAGBuildResolvedObjectPaths(
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs,
			final Map<ObjectIDResolvedWS, Reference> objrefs,
			final ReferenceGraphSearch paths) {
		
		for (final Entry<ObjectIdentifier, ObjectIDResolvedWS> e: resobjs.entrySet()) {
			final Reference r = objrefs.get(e.getValue());
			if (paths.isPathFound(r)) { // objid was valid and path was found
				//absolutize the ObjectIDResolvedWS
				withpath.put(e.getKey(), new ObjectIDResolvedWS(
						e.getValue().getWorkspaceIdentifier(), r.getObjectID(), r.getVersion()));
				withpathRefPath.put(e.getKey(), paths.getPath(r));
			}
		}
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
	
	private Map<WorkspaceIdentifier, ResolvedWorkspaceID> resolveWorkspaces(
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
	
	private void resolveReferencePaths(
			final List<ObjectIDWithRefPath> objsWithRefpaths,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> heads)
			throws WorkspaceCommunicationException,
				InaccessibleObjectException, CorruptWorkspaceDBException,
				NoSuchReferenceException {
		
		if (!hasItems(objsWithRefpaths)) {
			return;
		}
		//TODO CODE should probably have a limit on total path size per call, like 10000 or so
		final List<ObjectIdentifier> allRefPathEntries = new LinkedList<>();
		for (final ObjectIDWithRefPath oc: objsWithRefpaths) {
			if (oc != null) {
				/* allow nulls in list to maintain object count in the case
				 * calling method input includes objectIDs with and without
				 * paths
				 */
				allRefPathEntries.addAll(oc.getRefPath());
			}
		}
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> headrefs =
				getObjectOutgoingReferences(heads, nullIfInaccessible, false);
		/* ignore all errors when getting chain objects until actually getting
		 * to the point where we need the data. Otherwise an attacker can
		 * explore what objects exist in arbitrary workspaces.
		 */
		final Map<ObjectIdentifier, ObjectIDResolvedWS> resolvedRefPathObjs =
				permissionsFactory.getObjectChecker(allRefPathEntries, Permission.NONE)
						.withIncludeDeletedWorkspaces().check();
		final Map<ObjectIDResolvedWS, ObjectReferenceSet> outrefs =
				getObjectOutgoingReferences(resolvedRefPathObjs, true, true);
		
		int chnum = 1;
		for (final ObjectIDWithRefPath owrp: objsWithRefpaths) {
			if (owrp != null) {
				final ObjectReferenceSet refs = headrefs.get(heads.get(owrp));
				if (refs != null) {
					final List<Reference> resRefPath = getResolvedRefPath(owrp, refs,
							resolvedRefPathObjs, outrefs, chnum);
					if (resRefPath != null) {
						final Reference ref = resRefPath.get(resRefPath.size() - 1);
						final ObjectIDResolvedWS end = resolvedRefPathObjs.get(owrp.getLast());
						final ObjectIDResolvedWS res = new ObjectIDResolvedWS(
								end.getWorkspaceIdentifier(), ref.getObjectID(), ref.getVersion());
						withpath.put(owrp, res);
						withpathRefPath.put(owrp, resRefPath);
					}
				}
			}
			chnum++;
		}
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
			final ObjectIDWithRefPath head,
			final ObjectReferenceSet headrefs,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resRefPathObjs,
			final Map<ObjectIDResolvedWS, ObjectReferenceSet> outgoingRefs,
			final int objectNumber)
			throws NoSuchReferenceException {
		final List<Reference> resolvedRefPath = new LinkedList<>();
		ObjectIdentifier currentPos = head;
		ObjectReferenceSet currentRefs = headrefs;
		resolvedRefPath.add(currentRefs.getObjectReference());
		int posnum = 1;
		for (final ObjectIdentifier nextPos: head.getRefPath()) {
			/* refs are guaranteed to exist, so if the db didn't find
			 * it the user specified it incorrectly
			 */
			/* only throw the exception from one
			 * place, otherwise an attacker can tell if the object in the
			 * ref chain is in the DB or not
			 */
			final ObjectIDResolvedWS nextResolved = resRefPathObjs.get(nextPos);
			final ObjectReferenceSet nextRefSet = nextResolved == null ?
					null : outgoingRefs.get(nextResolved);
			if (nextRefSet == null || !currentRefs.contains(nextRefSet.getObjectReference())) {
				if (nullIfInaccessible) {
					return null;
				}
				throw new NoSuchReferenceException(
						null, objectNumber, posnum, head, currentPos, nextPos);
			}
			resolvedRefPath.add(nextRefSet.getObjectReference());
			currentPos = nextPos;
			currentRefs = nextRefSet;
			posnum++;
		}
		return resolvedRefPath;
	}

	private boolean hasItems(final Collection<?> c) {
		if (c.isEmpty()) {
			return false;
		}
		for (final Object item: c) {
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
