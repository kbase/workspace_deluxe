package us.kbase.typedobj.core;

import java.util.List;

import com.fasterxml.jackson.databind.node.ArrayNode;


/**
 * Stores an ID reference
 */
public class IdReference {

	protected ArrayNode location;
	protected String idReference;
	protected List <TypeDefId> validTypes;
	protected String absoluteIdReference;
	protected boolean isMappingKey;
	
	
	
	public IdReference(ArrayNode location, String idReference, List <TypeDefId>validTypes,boolean isMappingKey) {
		this.location=location;
		this.idReference=idReference;
		this.validTypes=validTypes;
		this.absoluteIdReference=null;
		this.isMappingKey=isMappingKey;
	}
	
	/**
	 * Get the ID 
	 */
	public String getIdReference() { return idReference; }
	
	public ArrayNode getLocation() { return location; }
	
	public boolean isMappingKey() { return isMappingKey; }
	
	/**
	 * For now, we only permit checking of TypeDefName is in the list, which means we
	 * cannot yet verify type def versions match...
	 */
	public boolean isValidType(TypeDefName typeDefName) {
		for(TypeDefId t: validTypes) {
			if(t.getType().equals(typeDefName)) return true;
		}
		return false;
	}
	
	
	/**
	 * Set the absolute ID 
	 */
	public void setAbsoluteId(String absoluteIdReference) {
		this.absoluteIdReference = absoluteIdReference;
	}
	
	public String getAbsoluteId() {
		return this.absoluteIdReference;
	}
	
	@Override
	public String toString() {
		String validTypeStr = "";
		if(validTypes.isEmpty()) { validTypeStr="*"; }
		else {
			validTypeStr += "[ ";
			for(TypeDefId type : validTypes) {
				validTypeStr += type.getType().getModule() + "." + type.getType().getName() + " ";
			}
			validTypeStr += "]";
		}
			
		return "ID: "+idReference+" (type:"+validTypeStr+",loc:"+location+",key?="+isMappingKey+")";
	}
	
}
