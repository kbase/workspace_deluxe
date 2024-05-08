package us.kbase.workspace.database.refsearch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Reference;

/** Searches a reference graph from a set of target references to find references that meet search
 * termination criteria, and returns the path from each found object to its respective target
 * object.
 * @author gaprice@lbl.gov
 *
 */
public class ReferenceGraphSearch {
	
	private final int maximumReferenceSearchCount;
	private final Map<Reference, List<Reference>> paths = new HashMap<>();
	private final ReferenceGraphTopologyProvider refProvider;
	private final boolean throwExceptionOnFail;
	
	/** Construct and perform a search in a reference graph from a set of target
	 * references to references that meet the search termination criteria as provided by the
	 * reference graph topology provider.
	 * @param startingRefs the references from where the search starts. The search will proceed
	 * through the reference graph until a reference is found that meets the termination criteria
	 * or the search is exhausted.
	 * @param refProvider provides access to the reference graph topology and termination criteria.
	 * @param maximumSearchSize the maximum number of references to search through. If the search
	 * exceeds this size an exception is thrown or the search immediately ends.
	 * @param throwExceptionOnFail if a) a search ends without finding a reference that terminates
	 * the search or b) the maximum search size is exceeded, immediately clear all data
	 * and throw an exception containing the reference for which the search failed. Otherwise in
	 * case a) the search proceeds as normal for the remainder of the starting references and in
	 * case b) the searches that have not yet completed are terminated. The paths for any completed
	 * searches will still be accessible. 
	 * @throws ReferenceSearchMaximumSizeExceededException if the maximum search size is reached.
	 * @throws ReferenceSearchFailedException if the reference search completed without meeting its
	 * termination criteria.
	 * @throws ReferenceProviderException if the reference provider threw and exception. 
	 */
	public ReferenceGraphSearch(
			final Set<Reference> startingRefs,
			final ReferenceGraphTopologyProvider refProvider,
			final int maximumSearchSize,
			final boolean throwExceptionOnFail)
			throws ReferenceSearchFailedException, ReferenceSearchMaximumSizeExceededException,
				ReferenceProviderException {
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
		searchObjectDAG(startingRefs);
	}
	
	private void searchObjectDAG(
			final Set<Reference> startingRefs)
			throws ReferenceSearchFailedException, ReferenceSearchMaximumSizeExceededException,
				ReferenceProviderException {
		int refcount = startingRefs.size();
		if (refCountExceeded(refcount)) {
			return;
		}
		Set<Reference> query = new HashSet<>();
		final List<ReferenceSearchTree> trees = new LinkedList<>();
		for (final Reference r: startingRefs) {
			trees.add(new ReferenceSearchTree(r));
			query.add(r);
		}
		while (!trees.isEmpty()) {
			final Map<Reference, Map<Reference, Boolean>> res =
					refProvider.getAssociatedReferences(query);
			query = null;
			for (final Map<Reference, Boolean> r: res.values()) {
				refcount += r.size();
			}
			if (refCountExceeded(refcount)) {
				return;
			}
			query = new HashSet<>();
			final Iterator<ReferenceSearchTree> treeiter = trees.iterator();
			while (treeiter.hasNext()) {
				final ReferenceSearchTree tree = treeiter.next();
				query.addAll(tree.updateTree(res));
				if (tree.isComplete()) {
					treeiter.remove();
					if (tree.isPathFound()) {
						paths.put(tree.getRoot(), tree.getPath());
					} else if (throwExceptionOnFail) { // otherwise do nothing
						throw new ReferenceSearchFailedException(tree.getRoot());
					}
				}
			}
		}
	}

	private boolean refCountExceeded(final int refcount)
			throws ReferenceSearchMaximumSizeExceededException {
		if (refcount > maximumReferenceSearchCount) {
			if (throwExceptionOnFail) {
				throw new ReferenceSearchMaximumSizeExceededException(
						"Reached reference search limit");
			} else {
				return true;
			}
		} else {
			return false;
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
	 * @return the path from a search terminating reference to the source reference, inclusive.
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
