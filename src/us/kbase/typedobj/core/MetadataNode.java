package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class describes nodes in the tree representing the structure of the objects that need
 * to be extracted as part of the WS searchable subdata or metadata.
 * @author rsutormin
 * @author gaprice
 */
public class MetadataNode {
	// if this is non-empty, then we need the value at this node for metadata
	private List<String> needValueForMetadata;
	// if this is non-empty, then we need the length of this node for metadata
	private List<String> needLengthForMetadata;
	private Map<String, MetadataNode> children =
			new LinkedHashMap<String, MetadataNode>();
	
	public MetadataNode() {
		needValueForMetadata  = new ArrayList<String>();
		needLengthForMetadata = new ArrayList<String>();
	}
	
	public Map<String, MetadataNode> getChildren() {
		return children;
	}
	
	public void addChild(String key, MetadataNode child) {
		children.put(key, child);
	}

	public boolean hasChildren() {
		return children.size() > 0;
	}
	
	public MetadataNode getChild(String name) {
		return children.get(name);
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
	
	/*
	public void printTree(String offset) {
		System.out.println(offset+";mdvalues:"+needValueForMetadata.size()+";mdlenghts:"+needLengthForMetadata.size());
		if(children!=null) {
			for (Map.Entry<String, SubsetAndMetadataNode> entry : children.entrySet()) {
				System.out.println(offset+"==="+entry.getKey());
				entry.getValue().printTree(offset+"   ");
			}
		}
	}*/
	
}
