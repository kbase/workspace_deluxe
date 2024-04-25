package us.kbase.typedobj.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class describes schema of subdata extraction.
 * @author rsutormin
 */
public class SubdataExtractionNode {
	private boolean needAll = false;
	private Map<String, SubdataExtractionNode> children = null;
	
	public SubdataExtractionNode() {
	}
	
	public Map<String, SubdataExtractionNode> getChildren() {
		return children;
	}
	
	public void addChild(String key, SubdataExtractionNode child) {
		if (children == null) 
			children = new LinkedHashMap<String, SubdataExtractionNode>();
		children.put(key, child);
	}

	public boolean hasChildren() {
		return children != null && children.size() > 0;
	}

	public boolean isNeedAll() {
		return needAll;
	}
	
	public void setNeedAll(boolean needAll) {
		this.needAll = needAll;
	}
	
	public void addPath(String[] path) {
		if (path.length == 0 || path[0].isEmpty()) {
			needAll = true;
		} else {
			addPath(path, 0);
		}
	}
	
	private void addPath(String[] path, int pos) {
		if (pos >= path.length) {
			needAll = true;
		} else {
			String key = path[pos];
			SubdataExtractionNode child = null;
			if (getChildren() == null || !getChildren().containsKey(key)) {
				child = new SubdataExtractionNode();
				addChild(key, child);
			} else {
				child = getChildren().get(key);
			}
			child.addPath(path, pos + 1);
		}
	}
}
