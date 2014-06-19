package us.kbase.typedobj.idref;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Stores an ID reference
 */
public class IdReference {

	//TODO unit test
	
	private final IDReferenceType type;
	private final String id;
	private final boolean isFieldName;
	private final List<String> attributes;
	//TODO 1 location is a placeholder, string may be a bad idea
	private final List<String> location;
	
	public IdReference(final IDReferenceType type, final String id,
			final boolean isFieldName, List<String> attributes,
			final List<String> location) {
		if (type == null || id == null || location == null) {
			throw new NullPointerException(
					"type, id, and location cannot be null");
		}
		this.type = type;
		this.id = id;
		this.isFieldName = isFieldName;
		attributes = attributes == null ? new LinkedList<String>() :
			attributes;
		this.attributes = Collections.unmodifiableList(attributes);
		//TODO 1 location is a placeholder, string may be a bad idea
		this.location = new LinkedList<String>(location);

	}
	
	/**
	 * Get the type of the ID.
	 * @return the type of IdReference (e.g. "ws", "shock", "external", "kb")
	 */
	public final IDReferenceType getType() {
		return type;
	}
	
	/**
	 * Get the original ID.
	 * @return the original id (note if the ID has been relabeled in the
	 * typed object instance data, you will still always get the original
	 * ID found when validating)
	 */
	public final String getId() {
		return id;
	}
	
	/** 
	 * Determine whether the id is in a field of a map/structure.
	 * @return true if id is a field name in the json document, false otherwise
	 */
	public final boolean isFieldName() {
		return isFieldName;
	}
	
	/**
	 * Get the location of the id in the enclosing object.
	 * @return the location of the id in the enclosing object.
	 */
	public final String getLocation(final char pathsep) {
		return pathsep + StringUtils.join(location, pathsep);
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
		return "IdReference [type=" + type + ", id=" + id + ", isFieldName="
				+ isFieldName + ", attributes=" + attributes + ", location="
				+ location + "]";
	}
	
}
