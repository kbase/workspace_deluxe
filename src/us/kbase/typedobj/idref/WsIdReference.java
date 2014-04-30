package us.kbase.typedobj.idref;

import java.util.HashSet;
import java.util.Set;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;

public class WsIdReference extends IdReference {

	public static final String typestring = "ws";
	
	/** storage of valid typedefnames; we expect this list to be small, but if it is large a Map might be more efficient */
	private Set<TypeDefName> validTypeDefNames;
	
	
	public WsIdReference(String id, Set<TypeDefName> validTypeDefNames, boolean isFieldName) {
		super(typestring, id, isFieldName);
		this.validTypeDefNames = new HashSet<TypeDefName>();
		if (validTypeDefNames != null) {
			this.validTypeDefNames.addAll(validTypeDefNames);
		}
	}
	
	/**
	 * return true if the given TypeDefName is on the list of valid TypeDefNames specified for this WsIdReference
	 */
	public boolean isValidInstanceType(TypeDefName type) {
		return validTypeDefNames.isEmpty() || validTypeDefNames.contains(type);
	}
	
	/**
	 * return true if the given TypeDefName is on the list of valid TypeDefNames specified for this WsIdReference
	 * NOTE: type def version information is ignored because it is not currently accepted at the schema level.
	 */
	public boolean isValidInstanceType(TypeDefId typeDefId) {
		return isValidInstanceType(typeDefId.getType());
	}

	@Override
	public String toString() {
		return "WsIdReference [id=" + getId() + " isFieldName=" + isFieldName()
				+ "validTypeDefNames=" + validTypeDefNames + "]";
	}
}
