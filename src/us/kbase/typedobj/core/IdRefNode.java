package us.kbase.typedobj.core;

import java.util.LinkedHashMap;
import java.util.Map;

import us.kbase.typedobj.idref.IdReference;

/**
 * This class describes schema of id reference substitution.
 * @author rsutormin
 */
public class IdRefNode {
	private final String lastPathLocation; 
	private IdReference parentKeyRef = null;
	private IdReference scalarValueRef = null;
	private Map<String, IdRefNode> children = null;
	
	public IdRefNode(String lastPathLocation) {
		this.lastPathLocation = lastPathLocation;
	}
	
	public String getLastPathLocation() {
		return lastPathLocation;
	}
	
	public Map<String, IdRefNode> getChildren() {
		return children;
	}
	
	public void addChild(String key, IdRefNode child) {
		if (children == null) 
			children = new LinkedHashMap<String, IdRefNode>();
		children.put(key, child);
	}
	
	public IdReference getParentKeyRef() {
		return parentKeyRef;
	}
	
	public void setParentKeyRef(IdReference parentKeyRef) {
		this.parentKeyRef = parentKeyRef;
	}
	
	public IdReference getScalarValueRef() {
		return scalarValueRef;
	}
	
	public void setScalarValueRef(IdReference scalarValueRef) {
		this.scalarValueRef = scalarValueRef;
	}
}
