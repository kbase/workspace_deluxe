package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class describes nodes in the tree representing the structure of the objects that need
 * to be extracted as part of the WS searchable subdata or metadata.
 * @author rsutormin
 */
public class SearchableWsSubsetAndMetadataNode {
	private boolean needKeys = false;
	private boolean needAll = false;
	private boolean needSubsetInChildren = false;
	private List<String> needValueForMetadata;  // if this is non-empty, then we need the value at this node for metadata
	private List<String> needLengthForMetadata; // if this is non-empty, then we need the length of this node for metadata
	private Map<String, SearchableWsSubsetAndMetadataNode> children = null;
	
	public SearchableWsSubsetAndMetadataNode() {
		needValueForMetadata  = new ArrayList<String>();
		needLengthForMetadata = new ArrayList<String>();
	}
	
	public Map<String, SearchableWsSubsetAndMetadataNode> getChildren() {
		return children;
	}
	
	public void addChild(String key, SearchableWsSubsetAndMetadataNode child) {
		if (children == null) 
			children = new LinkedHashMap<String, SearchableWsSubsetAndMetadataNode>();
		children.put(key, child);
	}

	public boolean hasChildren() {
		return children != null && children.size() > 0;
	}
	
	public boolean hasChildByName(String name) {
		if(children == null) return false;
		return children.containsKey(name);
	}
	
	public SearchableWsSubsetAndMetadataNode getChild(String name) {
		if(children == null) return null;
		return children.get(name);
	}
	
	public boolean isNeedSubsetInChildren() {
		return needSubsetInChildren;
	}
	
	public void setNeedSubsetInChildren(boolean needSubsetInChildren) {
		this.needSubsetInChildren = needSubsetInChildren;
	}
	
	public boolean isNeedAll() {
		return needAll;
	}
	
	public void setNeedAll(boolean needAll) {
		this.needAll = needAll;
	}
	
	public boolean isNeedKeys() {
		return needKeys;
	}
	
	public void setNeedKeys(boolean needKeys) {
		this.needKeys = needKeys;
	}
	
	public void addNeedValueForMetadata(String metadataName) {
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
	
	public boolean needMetadata() {
		if(needLengthForMetadata.size()>0 || needValueForMetadata.size()>0) {
			return true;
		}
		return false;
	}
	
	public void printTree(String offset) {
		System.out.println(offset+"needKeys="+needKeys+";needAll="+needAll+";mdvalues:"+needValueForMetadata.size()+";mdlenghts:"+needLengthForMetadata.size() +
				";needBelow="+needSubsetInChildren);
		if(children!=null) {
			for (Map.Entry<String, SearchableWsSubsetAndMetadataNode> entry : children.entrySet()) {
				System.out.println(offset+"==="+entry.getKey());
				entry.getValue().printTree(offset+"   ");
			}
		}
	}
	
}
