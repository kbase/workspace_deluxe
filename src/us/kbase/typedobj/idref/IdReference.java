package us.kbase.typedobj.idref;

/**
 * Stores an ID reference
 */
public class IdReference {

	
	private final String type;
	private final String id;
	private final boolean isFieldName;
	
	public IdReference(String type, String id, boolean isFieldName) {
		this.type = type;
		this.id = id;
		this.isFieldName = isFieldName;
	}
	
	/**
	 * return the type of IdReference (e.g. "ws", "shock", "external", "kb")
	 */
	public final String getType() {
		return type;
	}
	
	/**
	 * return the original id (note if the ID has been relabeled in the
	 * typed object instance data, you will still always get the original Id found when validating)
	 */
	public final String getId() {
		return id;
	}
	
	/**
	 * true if id is a field name in the json document, false otherwise
	 */
	public final boolean isFieldName() {
		return isFieldName;
	}
	
	
	@Override
	public String toString() {
		return "IdReference [type=" + type + ", id=" + id + ", isFieldName="
				+ isFieldName + "]";
	}
	
}
