package us.kbase.typedobj.idref;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores an ID reference
 */
public class IdReference<T> {

	//TODO TEST unit tests
	
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((attributes == null) ? 0 : attributes.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IdReference<?> other = (IdReference<?>) obj;
		if (attributes == null) {
			if (other.attributes != null)
				return false;
		} else if (!attributes.equals(other.attributes))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "IdReference [type=" + type + ", id=" + id
				+ ", attributes=" + attributes + "]";
	}
	
}
