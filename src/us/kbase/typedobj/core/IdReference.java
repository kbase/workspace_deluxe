package us.kbase.typedobj.core;

import java.util.List;


/**
 * Stores an ID reference
 */
public class IdReference {

	protected String location;
	protected String idReference;
	protected List <TypeDefId> validTypes;
	protected String absoluteIdReference;
	
	
	
	public IdReference(String location, String idReference, List <TypeDefId>validTypes) {
		this.location=location;
		this.idReference=idReference;
		this.validTypes=validTypes;
		this.absoluteIdReference=null;
	}
	
	/**
	 * Get the ID 
	 */
	public String getIdReference() { return idReference; }
	
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
			
		return "ID: "+idReference+" (type:"+validTypeStr+",loc:"+location+")";
	}
	
}
