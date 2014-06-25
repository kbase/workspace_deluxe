package us.kbase.typedobj.idref;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A set of ID references contained in an object.
 * @author gaprice@lbl.gov
 *
 */
public class IdReferenceSet {
	
	//TODO unit tests
	//TODO 1 test extraction of other id types
	
	private final Map<IdReferenceType, List<IdReference>> ids =
			new HashMap<IdReferenceType, List<IdReference>>();
	private boolean locked = false;
	private int size = 0;
	
	public IdReferenceSet() {} //nothing to construct
	
	public IdReferenceSet addId(final IdReference id) {
		if (locked) {
			throw new IllegalStateException(
					"This ID set is locked. No more IDs may be added.");
		}
		if (!ids.containsKey(id.getType())) {
			ids.put(id.getType(), new LinkedList<IdReference>());
		}
		ids.get(id.getType()).add(id);
		size++;
		return this;
	}
	
	public List<IdReference> getIds(final IdReferenceType type) {
		if (!ids.containsKey(type)) {
			return new LinkedList<IdReference>();
		}
		return new LinkedList<IdReference>(ids.get(type));
	}
	
	public Set<IdReferenceType> getIdTypes() {
		return ids.keySet(); 
	}
	
	public int size() {
		return size;
	}
	
	public boolean isEmpty() {
		return size == 0;
	}
	
	public IdReferenceSet lock() {
		locked = true;
		return this;
	}

	public boolean isLocked() {
		return locked;
	}
}
