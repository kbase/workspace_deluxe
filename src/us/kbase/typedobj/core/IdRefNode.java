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
	private String parentKeyRef = null;
	private String scalarValueRef = null;
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
	
	public String getParentKeyRef() {
		return parentKeyRef;
	}
	
	public void setParentKeyRef(String parentKeyRef) {
		this.parentKeyRef = parentKeyRef;
	}
	
	public String getScalarValueRef() {
		return scalarValueRef;
	}
	
	public void setScalarValueRef(String scalarValueRef) {
		this.scalarValueRef = scalarValueRef;
	}
}
