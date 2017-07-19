package us.kbase.workspace.database;

import static us.kbase.workspace.database.Util.nonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import us.kbase.workspace.database.exceptions.WorkspaceCommunicationException;
import us.kbase.workspace.database.refsearch.ReferenceGraphSearch;
import us.kbase.workspace.database.refsearch.ReferenceGraphTopologyProvider;
import us.kbase.workspace.database.refsearch.ReferenceProviderException;
import us.kbase.workspace.database.refsearch.ReferenceSearchFailedException;
import us.kbase.workspace.database.refsearch.ReferenceSearchMaximumSizeExceededException;

/** Resolves incoming ObjectIdentities (OIs) to either a) ObjectIDWithResolvedWS with unresolved
 * object information in the case of OIs without a reference path and not specified as requiring
 * a permissions lookup, or b) completely resolved, including the version, ObjectIDWithResolvedWS
 * as well as a reference path from an accessible object to the target object. These latter objects
 * are guaranteed to exist, while the former are not. 
 * @author gaprice@lbl.gov
 *
 */
public class ObjectResolver {
	
	//TODO TEST 100% coverage, make sure object is immutable post resolution
	
	public final static int MAX_OBJECT_SEARCH_COUNT_DEFAULT = 10000;
	
	private final WorkspaceDatabase db;
	private final WorkspaceUser user;
	private final PermissionsCheckerFactory permissionsFactory;
	private final boolean nullIfInaccessible;
	private final boolean asAdmin;
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
			final boolean asAdmin,
			final int maxSearch)
			throws WorkspaceCommunicationException, InaccessibleObjectException,
				CorruptWorkspaceDBException, NoSuchReferenceException,
				ReferenceSearchMaximumSizeExceededException {
		this.db = db;
		this.user = user;
		this.permissionsFactory = new PermissionsCheckerFactory(db, user);
		this.objects = Collections.unmodifiableList(objects);
		this.nullIfInaccessible = nullIfInaccessible;
		this.asAdmin = asAdmin;
		this.maximumObjectSearchCount = maxSearch;
		resolve();
	}
	
	/** The resolution for the object.
	 * @author gaprice@lbl.gov
	 *
	 */
	public enum ObjectResolution {
		/** The object was provided with no reference path and permissions lookup was not
		 * specified. The workspace was resolved, but no further resolution was performed. The
		 * object may not exist.
		 */
		NO_PATH,
		/** The object was fully resolved, and a reference path from an accessible object to the
		 * target object is available. The workspace, object, and version are all resolved. The
		 * object must exist.
		 */
		PATH,
		/** The object was not accessible. It either does not exist or is deleted,
		 * or the reference path, if provided, was invalid, or if a permissions search was
		 * specified there was no reference path from an accessible object that could be found
		 * within the limits of the search.
		 */
		INACCESSIBLE;
	}
	
	/** Get the input objects in the order they were added to the builder.
	 * @return the input objects.
	 */
	public List<ObjectIdentifier> getObjects() {
		return objects;
	}
	
	/** Get the input objects based on their resolution.
	 * @param withPath true to return objects with the {@link ObjectResolution#PATH} resolution
	 * or false to return objects with the {@link ObjectResolution#NO_PATH} resolution.
	 * @return the input objects.
	 */
	public Set<ObjectIdentifier> getObjects(final boolean withPath) {
		if (withPath) {
			return new HashSet<>(withpath.keySet());
		} else {
			return new HashSet<>(nopath.keySet());
		}
	}
	
	/** Get the resolution for an object.
	 * @param objID the object.
	 * @return the object's resolution.
	 */
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
	
	/** Get the resolved object given an incoming object. The resolved object state will depend
	 * on the resolution. If a reference path was provided, the resolved object will be the object
	 * at the end of the path, and the input object the object at the head of the path. The input
	 * object in the latter case contains the input path within it.
	 * @param objID the input object.
	 * @return the corresponding resolved object.
	 */
	public ObjectIDResolvedWS getResolvedObject(final ObjectIdentifier objID) {
		if (nopath.containsKey(objID)) {
			return nopath.get(objID);
		} else if (withpath.containsKey(objID)) {
			return withpath.get(objID);
		} else {
			throw new IllegalArgumentException("Object is inaccessible");
		}
	}
	
	/** Get the resolved objects based on their resolution.
	 * @param withPath true to return objects with the {@link ObjectResolution#PATH} resolution
	 * or false to return objects with the {@link ObjectResolution#NO_PATH} resolution.
	 * @return the resolved objects.
	 */
	public Set<ObjectIDResolvedWS> getResolvedObjects(final boolean withPath) {
		if (withPath) {
			return new HashSet<>(withpath.values());
		} else {
			return new HashSet<>(nopath.values());
		}
	}
	
	/** Get a path from an accessible object to the target object for an input object.
	 * @param objID the input object.
	 * @return A reference path to the object.
	 */
	public List<Reference> getReferencePath(final ObjectIdentifier objID) {
		if (withpath.containsKey(objID)) {
			return new ArrayList<>(withpathRefPath.get(objID));
		} else {
			throw new IllegalArgumentException("No reference path is available");
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
			ws = permissionsFactory.getObjectChecker(
					nolookup, asAdmin ? Permission.NONE : Permission.READ)
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
	

	private Set<Long> getReadableWorkspaces()
			throws WorkspaceCommunicationException, CorruptWorkspaceDBException {
		//could make a method to just get IDs of workspace with specific permission to save mem
		final PermissionSet pset = db.getPermissions(user, Permission.READ, false);
		return pset.getWorkspaces().stream()
				.filter(ws -> !ws.isDeleted())
				.map(ws -> ws.getID())
				.collect(Collectors.toSet());
	}
	
	private class TopoProvider implements ReferenceGraphTopologyProvider {
			
		private final Set<Long> readableWorkspaceIDs;
		
		private TopoProvider(final Set<Long> readableWorkspaceIDs) {
			this.readableWorkspaceIDs = readableWorkspaceIDs;
		}

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
						if (asAdmin || readableWorkspaceIDs.contains(r.getWorkspaceID())) {
							readable.add(r);
						}
					}
				}
				final Map<Reference, Boolean> exists = readable.isEmpty() ?
						Collections.emptyMap() : db.getObjectExistsRef(readable);
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
	}

	//TODO REF LOOKUP positive and negative caches (?)
	private void searchObjectDAG(final Set<ObjectIdentifier> lookup)
			throws WorkspaceCommunicationException, ReferenceSearchMaximumSizeExceededException,
				InaccessibleObjectException, CorruptWorkspaceDBException {
		if (lookup.isEmpty()) {
			return;
		}
		final Set<Long> readableWorkspaceIDs = asAdmin? new HashSet<>() : getReadableWorkspaces();
		final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs = permissionsFactory
				.getObjectChecker(lookup, Permission.NONE)
				.withIncludeDeletedWorkspaces().check();
		final Map<ObjectIDResolvedWS, Reference> objrefs = db.getObjectReference(
				new HashSet<>(resobjs.values()));
		try {
			if (!asAdmin && readableWorkspaceIDs.isEmpty()) {
				if (nullIfInaccessible) {
					return;
				} else {
					throw new ObjectDAGSearchFromObjectIDFailedException(lookup.iterator().next());
				}
			}
		
			// will throw an exception if can't find a ref for any object in lookup
			final Set<Reference> startingRefs = searchObjectDAGGetStartingRefs(
					readableWorkspaceIDs, lookup, resobjs, objrefs);
			if (startingRefs.isEmpty()) {
				return;
			}
			final ReferenceGraphSearch search = new ReferenceGraphSearch(
					startingRefs, new TopoProvider(readableWorkspaceIDs),
					maximumObjectSearchCount, !nullIfInaccessible);
			searchObjectDAGBuildResolvedObjectPaths(resobjs, objrefs, search);
		} catch (final ReferenceSearchFailedException |
				ObjectDAGSearchFromObjectIDFailedException e) {
//			e.printStackTrace();
//			System.out.println(e.getMessage());
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
			final Set<Long> readableWorkspaceIDs,
			final Set<ObjectIdentifier> lookup,
			final Map<ObjectIdentifier, ObjectIDResolvedWS> resobjs,
			final Map<ObjectIDResolvedWS, Reference> objrefs)
			throws ObjectDAGSearchFromObjectIDFailedException, WorkspaceCommunicationException {

		final Map<Reference, Boolean> exists = db.getObjectExistsRef(
				new HashSet<>(objrefs.values()));
		
		final Set<Reference> startingRefs = new HashSet<>();
		for (final ObjectIdentifier o: lookup) {
			final ObjectIDResolvedWS res = resobjs.get(o);
			final Reference ref = objrefs.get(res);
			if (ref == null) { // invalid objectidentifier
				if (!nullIfInaccessible) {
					throw new ObjectDAGSearchFromObjectIDFailedException(o);
				}
			} else {
				if (exists.get(ref) &&
						(asAdmin || readableWorkspaceIDs.contains(ref.getWorkspaceID()))) {
					withpath.put(o, new ObjectIDResolvedWS(
							res.getWorkspaceIdentifier(), ref.getObjectID(), ref.getVersion()));
					withpathRefPath.put(o, Arrays.asList(ref));
				} else {
					startingRefs.add(ref);
				}
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

	/** Get a builder for an ObjectResolver.
	 * @param db the database containing workspace information.
	 * @param user the user for whom objects will be resolved.
	 * @return a new builder.
	 */
	public static Builder getBuilder(final WorkspaceDatabase db, final WorkspaceUser user) {
		return new Builder(db, user);
	}
	
	/** A builder for an ObjectResolver.
	 * @author gaprice@lbl.gov
	 *
	 */
	public static class Builder {
		
		private final WorkspaceDatabase db;
		private final WorkspaceUser user;
		private final List<ObjectIdentifier> objects = new LinkedList<>();
		private boolean nullIfInaccessible = false;
		private boolean asAdmin = false;
		private int maxSearch = MAX_OBJECT_SEARCH_COUNT_DEFAULT;
		
		private Builder(final WorkspaceDatabase db, final WorkspaceUser user) {
			nonNull(db, "db");
			this.db = db;
			this.user = user;
		}
		
		/** Resolve the objects.
		 * @return the object resolver containing the resolved objects.
		 * @throws WorkspaceCommunicationException if a communication error with the storage
		 * system occurs.
		 * @throws InaccessibleObjectException if an object was inaccessible.
		 * @throws CorruptWorkspaceDBException if corrupt data was found in the storage system.
		 * @throws NoSuchReferenceException if a reference path is invalid.
		 * @throws ReferenceSearchMaximumSizeExceededException if the reference search traversed
		 * too many objects before finding a result.
		 */
		public ObjectResolver resolve()
				throws WorkspaceCommunicationException, InaccessibleObjectException,
					CorruptWorkspaceDBException, NoSuchReferenceException,
					ReferenceSearchMaximumSizeExceededException {
			if (objects.isEmpty()) {
				throw new IllegalArgumentException("No object identifiers provided");
			}
			return new ObjectResolver(db, user, objects, nullIfInaccessible, asAdmin, maxSearch);
		}
		
		/** Build an empty ObjectResolver containing no objects. Ignores any objects added to the
		 * builder thus far.
		 * @return the object resolver containing no objects.
		 */
		public ObjectResolver buildEmpty() {

			try {
				return new ObjectResolver(db, user, Collections.emptyList(), nullIfInaccessible,
						asAdmin, maxSearch);
			} catch (WorkspaceCommunicationException | InaccessibleObjectException |
					CorruptWorkspaceDBException | NoSuchReferenceException |
					ReferenceSearchMaximumSizeExceededException e) {
				throw new RuntimeException(
						"Exceptions should be impossible with an empty input set", e);
			}
		}
		
		/** Rather than throwing an exception, leave inaccessible results out of the result set.
		 * These input objects with have a resolution of {@link ObjectResolution#INACCESSIBLE}. 
		 * @param ignoreInaccessible true to leave inaccessible results out of the result set.
		 * @return this builder.
		 */
		public Builder withIgnoreInaccessible(final boolean ignoreInaccessible) {
			this.nullIfInaccessible = ignoreInaccessible;
			return this;
		}
		
		/** Set the maximum number of objects that can be traversed before a search halts,
		 * throwing an exception if inaccessible objects are not set to be ignored.
		 * @param count the maximum number of objects to traverse.
		 * @return this builder.
		 */
		public Builder withMaximumObjectsSearched(final int count) {
			if (count < 1) {
				throw new IllegalArgumentException("count must be > 0");
			}
			maxSearch = count;
			return this;
		}
		
		/** Run the resolution as an admin - e.g. all workspaces are accessible.
		 * @param asAdmin
		 * @return
		 */
		public Builder withAsAdmin(final boolean asAdmin) {
			this.asAdmin = asAdmin;
			return this;
		}
		
		/** Add an object to be resolved.
		 * @param object the object.
		 * @return this builder.
		 */
		public Builder withObject(final ObjectIdentifier object) {
			nonNull(object, "object");
			objects.add(object);
			return this;
		}
	}

}
