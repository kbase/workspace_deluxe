package us.kbase.typedobj.core.validatornew;

import java.util.LinkedHashMap;
import java.util.Map;

public class WsSubsetNode {
	private boolean needKeys = false;
	private boolean needAll = false;
	private Map<String, WsSubsetNode> children = null;
	
	public WsSubsetNode() {
	}
	
	public Map<String, WsSubsetNode> getChildren() {
		return children;
	}
	
	public void addChild(String key, WsSubsetNode child) {
		if (children == null) 
			children = new LinkedHashMap<String, WsSubsetNode>();
		children.put(key, child);
	}

	public boolean hasChildren() {
		return children != null && children.size() > 0;
	}
	
	public boolean isNeedKeys() {
		return needKeys;
	}
	
	public void setNeedKeys(boolean needKeys) {
		this.needKeys = needKeys;
	}
	
	public boolean isNeedAll() {
		return needAll;
	}
	
	public void setNeedAll(boolean needAll) {
		this.needAll = needAll;
	}
}
