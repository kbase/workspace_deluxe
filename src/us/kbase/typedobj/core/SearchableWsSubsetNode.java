package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class describes schema of filtering user-defined data
 * in order to construct ws-searchable subset.
 * @author rsutormin
 */
public class SearchableWsSubsetNode {
	private boolean needKeys = false;
	private boolean needAll = false;
	private List<String> needValueForMetadata;  // if this is non-empty, then we need the value at this node for metadata
	private List<String> needLengthForMetadata; // if this is non-empty, then we need the length of this node for metadata
	private Map<String, SearchableWsSubsetNode> children = null;
	
	public SearchableWsSubsetNode() {
		needValueForMetadata  = new ArrayList<String>();
		needLengthForMetadata = new ArrayList<String>();
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
	
	public void setNeedValueForMetadata(String metadataName) {
		this.needValueForMetadata.add(metadataName);
	}
	
	public List<String> getNeedValueForMetadata() {
		return needValueForMetadata;
	}
	
	public void addNeedLengthForMetadata(String metadataName) {
		this.needLengthForMetadata.add(metadataName);
	}
	
	public List<String> getNeedLengthForMetadata() {
		return needLengthForMetadata;
	}
	
}
