package us.kbase.typedobj.idref;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypeDefName;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class WsIdReference extends IdReference {

	public static final String typestring = "ws";
	
	/** storage of valid typedefnames; we expect this list to be small, but if it is large a Map might be more efficient */
	private String [] validTypeDefNames;
	
	
	public WsIdReference(String id, ArrayNode location, ObjectNode info, boolean isFieldName) {
		super(typestring,id,location,info,isFieldName);
		ArrayNode validNames = (ArrayNode) info.get("valid-typedef-names");
		if(validNames==null) throw new RuntimeException("cannot create WsIdReference; invalid IdReference info; 'valid-typedef-names' field is required");
		validTypeDefNames = new String[validNames.size()];
		for(int i=0; i<validNames.size(); i++) {
			validTypeDefNames[i] = validNames.get(i).asText();
		}
	}
	
	/**
	 * return true if the given TypeDefName is on the list of valid TypeDefNames specified for this WsIdReference
	 */
	public boolean isValidInstanceType(TypeDefName typeDefName) {
		// if nothing is on the list, then everything is valid
		if(validTypeDefNames.length==0) return true;
		// if something is on the list, then we better find it
		for(int i=0; i<validTypeDefNames.length; i++) {
			if( validTypeDefNames[i].equals(typeDefName.getTypeString()) )
				return true;
		}
		return false;
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
		String s;
		if(isReplacementIdSet())
			s=getReplacementId();
		else
			s="-none-";
		return "WsIdReference [id:'"+getId()+"', loc="+location+", isField?="+isFieldName()+", replacement='"+s+"']";
	}
	
}
