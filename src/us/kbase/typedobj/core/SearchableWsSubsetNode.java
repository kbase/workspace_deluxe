package us.kbase.typedobj.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class describes schema of filtering user-defined data
 * in order to construct ws-searchable subset.
 * @author rsutormin
 */
public class SearchableWsSubsetNode {
	private boolean needKeys = false;
	private boolean needAll = false;
	private Map<String, SearchableWsSubsetNode> children = null;
	
	public SearchableWsSubsetNode() {
	}
	
	public Map<String, SearchableWsSubsetNode> getChildren() {
		return children;
	}
	
	public void addChild(String key, SearchableWsSubsetNode child) {
		if (children == null) 
			children = new LinkedHashMap<String, SearchableWsSubsetNode>();
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
