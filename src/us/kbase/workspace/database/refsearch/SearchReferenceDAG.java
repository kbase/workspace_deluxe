package us.kbase.workspace.database.refsearch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.Reference;

/** Searches a reference graph from a set of target references to find references in a provided
 * set of workspaces, and returns the path from each found object to its respective target object.
 * The graph is assumed to be directed and acyclic (e.g. a DAG).
 * @author gaprice@lbl.gov
 *
 */
public class SearchReferenceDAG {
	
	//TODO TEST Unit tests

	private final int maximumReferenceSearchCount;
	private final Map<Reference, List<Reference>> paths = new HashMap<>();
	private final ReferenceDAGTopologyProvider refProvider;
	private final boolean throwExceptionOnFail;
	
	/** An exception thrown when a failure occurs in a reference provider.
	 * @author gaprice@lbl.gov
	 *
	 */
	@SuppressWarnings("serial")
	public static class ReferenceProviderException extends Exception {
		
		/** Construct the exception.
		 * @param message an exception message.
		 * @cause the cause of the exception.
		 */
		public ReferenceProviderException(final String message, final Throwable cause) {
			super(message, cause);
		}
	}
	
	/** An exception thrown when a search from a particular reference is exhausted without meeting
	 * the search criteria.
	 * @author gaprice@lbl.gov
	 *
	 */
	@SuppressWarnings("serial")
	public static class ReferenceSearchFailedException extends Exception {
		
		private final Reference failedRef;
		
		/** Construct the exception.
		 * @param failedOn the reference for which the search failed.
		 */
		public ReferenceSearchFailedException(final Reference failedOn) {
			super();
			failedRef = failedOn;
		}
		
		/** Get the reference for which the search failed.
		 * @return the reference for which the search failed.
		 */
		public Reference getFailedReference() {
			return failedRef;
		}
	}
	
	/** An exception thrown when a search has traversed the maximum number of references allowed.
	 * @author gaprice@lbl.gov
	 *
	 */
	@SuppressWarnings("serial")
	public static class ReferenceSearchMaximumSizeExceededException extends Exception {
		
		/** Construct the exception.
		 * @param message an exception message.
		 */
		public ReferenceSearchMaximumSizeExceededException(final String message) {
			super(message);
		}
	}
	
	/** Construct and perform a search in a directed, acyclic reference graph from a set of target
	 * references to objects that a) exist in one of a set of workspaces and b) are at the
	 * head of a reference path leading to the target objects.
	 * @param workspaceIDs the workspaces that terminate the search. When a reference is found in
	 * one of these workspaces, the search ends for the target reference for that particular
	 * search.
	 * @param startingRefs the references from where the search starts. The search will proceed
	 * through the reference DAG until a reference is found in one of the provided workspaces or
	 * the search is exhausted.
	 * @param refProvider provides access to the reference DAG topology.
	 * @param maximumSearchSize the maximum number of references to search through. If the search
	 * exceeds this size an exception is thrown or the search immediately ends.
	 * @param throwExceptionOnFail if a) a search ends without finding a reference in one of the
	 * provided workspaces or b) the maximum search size is exceeded, immediately clear all data
	 * and throw an exception containing the reference for which the search failed. Otherwise in
	 * case a) the search proceeds as normal for the remainder of the starting references and in
	 * case b) the searches that have not yet completed are terminated. The paths for any completed
	 * searches will still be accessible. 
	 * @throws ReferenceSearchMaximumSizeExceededException 
	 * @throws ReferenceSearchFailedException 
	 * @throws ReferenceProviderException 
	 */
	public SearchReferenceDAG(
			final Set<Long> workspaceIDs,
			final Set<Reference> startingRefs,
			final ReferenceDAGTopologyProvider refProvider,
			final int maximumSearchSize,
			final boolean throwExceptionOnFail)
			throws ReferenceSearchFailedException, ReferenceSearchMaximumSizeExceededException,
				ReferenceProviderException {
		if (workspaceIDs == null || workspaceIDs.isEmpty()) {
			throw new IllegalArgumentException("workspaceIDs cannot be null or empty");
		}
		if (startingRefs == null || startingRefs.isEmpty()) {
			throw new IllegalArgumentException("startingRefs cannot be null or empty");
		}
		if (refProvider == null) {
			throw new NullPointerException("refProvider");
		}
		if (maximumSearchSize < 1) {
			throw new IllegalArgumentException("maximumSearchSize must be > 0");
		}
		this.refProvider = refProvider;
		this.throwExceptionOnFail = throwExceptionOnFail;
		maximumReferenceSearchCount = maximumSearchSize;
		searchObjectDAG(workspaceIDs, startingRefs);
	}
	
	private void searchObjectDAG(
			final Set<Long> wsids,
			final Set<Reference> startingRefs)
			throws ReferenceSearchFailedException, ReferenceSearchMaximumSizeExceededException,
				ReferenceProviderException {
		int refcount = startingRefs.size();
		if (refCountExceeded(refcount, throwExceptionOnFail)) {
			return;
		}
		final Map<Reference, Set<Reference>> searchrefs = new HashMap<>();
		for (final Reference r: startingRefs) {
			searchrefs.put(r, Sets.newHashSet(r));
		}
		while (!searchrefs.isEmpty()) {
			final Iterator<Reference> refiter = searchrefs.keySet().iterator();
			Set<Reference> query = new HashSet<Reference>();
			while (refiter.hasNext()) {
				final Reference r = refiter.next();
				final Set<Reference> refs = searchrefs.get(r);
				if (refs.isEmpty()) {
					if (throwExceptionOnFail) {
						throw new ReferenceSearchFailedException(r);
					} else {
						refiter.remove();
					}
				} else { // could just always do this, has no effect, but since checking anyway...
					query.addAll(refs);
				}
			}
			Map<Reference, ObjectReferenceSet> res = refProvider.getAssociatedReferences(query);
			query = null;
			
			for (final Reference oi: searchrefs.keySet()) {
				final Set<Reference> newrefs = getNewRefsFromOldRefs(searchrefs.get(oi), res);
				refcount += newrefs.size();
				searchrefs.put(oi, newrefs);
			}
			if (refCountExceeded(refcount, throwExceptionOnFail)) {
				return;
			}
			res = null;
			findReadableReferences(wsids, searchrefs);
		}
	}

	private boolean refCountExceeded(final int refcount, final boolean throwException)
			throws ReferenceSearchMaximumSizeExceededException {
		if (refcount > maximumReferenceSearchCount) {
			if (throwException) {
				throw new ReferenceSearchMaximumSizeExceededException(
						"Reached reference search limit");
			} else {
				return true;
			}
		} else {
			return false;
		}
	}
	
	private Set<Reference> getNewRefsFromOldRefs(
			final Set<Reference> increfs,
			final Map<Reference, ObjectReferenceSet> res) {
		final Set<Reference> newrefs = new HashSet<Reference>();
		for (final Reference r: increfs) {
			newrefs.addAll(res.get(r).getReferenceSet());
		}
		return newrefs;
	}
	
	/* Modifies searchrefs in place to remove objects with valid ref paths.
	 * Modifies paths in place to add found paths.
	 */
	private void findReadableReferences(
			final Set<Long> wsids,
			final Map<Reference, Set<Reference>> searchrefs)
			throws ReferenceProviderException {
		Set<Reference> readable = new HashSet<>();
		for (final Reference startRef: searchrefs.keySet()) {
			for (final Reference r: searchrefs.get(startRef)) {
				if (wsids.contains(r.getWorkspaceID())) {
					readable.add(r);
				}
			}
		}
		if (readable.isEmpty()) {
			return;
		}
		final Map<Reference, Boolean> exists = refProvider.getReferenceExists(readable);
		readable = null;
		final Iterator<Reference> refiter = searchrefs.keySet().iterator();
		while (refiter.hasNext()) {
			final Reference startRef = refiter.next();
			for (final Reference r: searchrefs.get(startRef)) {
				if (exists.containsKey(r) && exists.get(r)) { //search over
					//TODO REF LOOKUP need to handle path here
					// is absolute at this point, made these absolute earlier
					paths.put(startRef, Collections.unmodifiableList(Arrays.asList(startRef)));  //TODO REF LOOKUP this is wrong!
					refiter.remove();
					break;
				}
			}
		}
	}
	
	/** Determine if a search succeeded for a particular source reference. If throwExceptionOnFail
	 * is set to true in the constructor, this method always returns true.
	 * @param ref the ref to check for success.
	 * @return true if the search for the provided ref succeeded, false otherwise.
	 */
	public boolean isPathFound(final Reference ref) {
		return paths.containsKey(ref);
	}
	
	/** Get the path found for a particular source reference.
	 * @param ref the reference for which the path should be returned.
	 * @return the path from a reference in one of the supplied workspaces to the source reference,
	 * inclusive.
	 * @throws IllegalStateException if no path was found for the reference or the reference was
	 * not provided in the source reference set.
	 */
	public List<Reference> getPath(final Reference ref) {
		if (!paths.containsKey(ref)) {
			throw new IllegalStateException("No path for ref " + ref);
		}
		return paths.get(ref);
	}
}
