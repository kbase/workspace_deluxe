package us.kbase.typedobj.idref;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores an ID reference
 */
public class IdReference<T> {

	//TODO unit tests
	
	private final IdReferenceType type;
	private final T id;
	private final List<String> attributes;
	
	public IdReference(final IdReferenceType type, final T id,
			List<String> attributes) {
		if (type == null || id == null) {
			throw new NullPointerException(
					"type, id, and location cannot be null");
		}
		this.type = type;
		this.id = id;
		attributes = attributes == null ? new LinkedList<String>() :
			new LinkedList<String>(attributes);
		this.attributes = Collections.unmodifiableList(attributes);
	}
	
	/**
	 * Get the type of the ID.
	 * @return the type of IdReference (e.g. "ws", "shock", "external", "kb")
	 */
	public final IdReferenceType getType() {
		return type;
	}
	
	/**
	 * Get the original ID.
	 * @return the original id (note if the ID has been relabeled in the
	 * typed object instance data, you will still always get the original
	 * ID found when validating)
	 */
	public final T getId() {
		return id;
	}
	
	/**
	 * Get the set of attributes associated with an id.
	 * @return the set of attributes associated with an id.
	 */
	public final List<String> getAttributes() {
		return attributes;
	}
	
	@Override
	public String toString() {
		return "IdReference [type=" + type + ", id=" + id
				+ ", attributes=" + attributes + "]";
	}
	
}
