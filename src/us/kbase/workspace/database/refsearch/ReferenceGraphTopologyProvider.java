package us.kbase.workspace.database.refsearch;

import java.util.Map;
import java.util.Set;

import us.kbase.workspace.database.ObjectReferenceSet;
import us.kbase.workspace.database.Reference;
import us.kbase.workspace.database.refsearch.ReferenceProviderException;

/** Provides information necessary for searching the reference graph about one or more
 * references:
 * - For a set of references, provides the references adjacent (either incoming or outgoing,
 * but not both) to those references in the reference DAG.
 * - For a set of references, provides whether the references exist.
 * @author gaprice@lbl.gov
 *
 */
public interface ReferenceGraphTopologyProvider {

	/** Given a set of references, returns the set of references associated with the target
	 * references in the graph. The references may be the incoming or outgoing references to
	 * or from the target references, depending on which way the search should proceed. *Only*
	 * the incoming or outgoing references should be provided for one search, never a mix of
	 * both.
	 * @param sourceRefs the references for which associated references should be found.
	 * @return a mapping from each source reference to the references that refer to the source
	 * reference.
	 */
	public Map<Reference, ObjectReferenceSet> getAssociatedReferences(
			Set<Reference> sourceRefs)
					throws ReferenceProviderException;

	/** Determines whether a reference a) exists and b) is not in the deleted state.
	 * @param refs the references to check.
	 * @return a mapping from each reference to a boolean that is true if the reference exists
	 * and is not in the deleted state.
	 */
	public Map<Reference, Boolean> getReferenceExists(Set<Reference> refs)
			throws ReferenceProviderException;
}
