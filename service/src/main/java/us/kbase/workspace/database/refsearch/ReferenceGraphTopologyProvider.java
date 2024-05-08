package us.kbase.workspace.database.refsearch;

import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.Reference;

/** Provides information necessary for searching the reference graph. For a set of references, 
 * provides the references adjacent (incoming or outgoing, depending on the search direction) to
 * those references in the reference graph. Furthermore, for each adjacent reference, provides
 * whether said reference should terminate the search.
 * @author gaprice@lbl.gov
 *
 */
public interface ReferenceGraphTopologyProvider {

	/** Given a set of references, returns the set of references in the graph associated with those
	 * references. The references may be the incoming or outgoing references to
	 * or from the target references, depending on which way the search should proceed. Each
	 * reference is further associated with a boolean which indicates whether the reference should
	 * terminate the search for any search trees in which it exists.
	 * @param sourceRefs the references for which associated references should be found.
	 * @return a mapping from each source reference to the references associated with the source
	 * reference. Each of the referring references are mapped to a boolean indicating the search
	 * should terminate when reaching that reference.
	 */
	public Map<Reference, Map<Reference, Boolean>> getAssociatedReferences(
			Set<Reference> sourceRefs)
					throws ReferenceProviderException;
}
