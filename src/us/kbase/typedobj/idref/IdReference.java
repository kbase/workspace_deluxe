package us.kbase.typedobj.idref;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import us.kbase.typedobj.core.JsonDocumentLocation;

/**
 * Stores an ID reference
 */
public class IdReference {

	//TODO unit tests
	
	private final IdReferenceType type;
	private final String id;
	private final List<String> attributes;
	//TODO 1 location is a placeholder, string may be a bad idea
	private final JsonDocumentLocation location;
	
	public IdReference(final IdReferenceType type, final String id,
			List<String> attributes,
			final JsonDocumentLocation location) {
		if (type == null || id == null || location == null) {
			throw new NullPointerException(
					"type, id, and location cannot be null");
		}
		this.type = type;
		this.id = id;
		attributes = attributes == null ? new LinkedList<String>() :
			new LinkedList<String>(attributes);
		this.attributes = Collections.unmodifiableList(attributes);
		//TODO 1 location is a placeholder, string may be a bad idea
		this.location = new JsonDocumentLocation(location);

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
	public final String getId() {
		return id;
	}
	
	/**
	 * Get the location of the id in the enclosing object.
	 * @return the location of the id in the enclosing object.
	 */
	public final String getLocation(final char pathsep) {
		return location.getFullLocationAsString();
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
				+ ", attributes=" + attributes + ", location="
				+ location + "]";
	}
	
}
