package us.kbase.typedobj.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class describes schema of id reference substitution.
 * The node represents a portion of the JSON tree that either contains an ID
 * to be remapped or a child that contains such an ID.
 * The location stored is either the key of a map or the index of an array 
 * position as a string. The location is thus relative to the parent IdRefNode.
 * 
 * If the key of the map is an ID to be remapped, this can be marked as such.
 * 
 * Additionally, either a String value that is an ID to be remapped or a set
 * of child nodes may be added.
 * 
 * @author rsutormin
 * @author gaprice@lbl.gov
 */
public class IdRefNode {
	//TODO should the class handle arrays and maps differently?
	private final String location;
	private boolean locationIsID = false;;
	private String valueID = null;
	private Map<String, IdRefNode> children = null;
	
	/** Construct a new IdRefNode.
	 * @param relativeLocation the location of the node relative to the node's
	 * parent.
	 */
	public IdRefNode(String relativeLocation) {
		this.location = relativeLocation;
	}
	
	/** Get the node's location.
	 * @return the location of the node relative to the node's
	 * parent.
	 */
	public String getRelativeLocation() {
		return location;
	}
	
	/** Get the children of this node.
	 * 
	 * Note that the altering the returned map will alter this node's internal
	 * state.
	 * 
	 * @return the set of children of this node, mapped by their location -
	 * either a map key or an array index. Returns null if this node has no
	 * children.
	 */
	public Map<String, IdRefNode> getChildren() {
		return children;
	}
	
	/** Returns true if the node has child nodes.
	 * @return true if the node has child nodes.
	 */
	public boolean hasChildren() {
		return children != null;
	}
	
	/** Add a child node to this node. This method will throw an error if a
	 * value ID has already been added.
	 * @param child - the child to add to this node.
	 */
	public void addChild(IdRefNode child) {
		if (valueID != null) {
			throw new IllegalStateException(
					"Cannot have a value and children");
		}
		if (children == null) 
			children = new LinkedHashMap<String, IdRefNode>();
		children.put(child.getRelativeLocation(), child);
	}
	
	/** Record that the location of this node is an ID. This method should
	 * only be called when the location is the key of a map.
	 */
	public void setLocationIsID() {
		locationIsID = true;
	}
	
	/** Check if the location of this node (e.g. the key of a map) is an ID.
	 * @return true if the node location is an ID.
	 */
	public boolean locationIsID() {
		return locationIsID;
	}
	
	/** Return true if the value at this location is an ID.
	 * @return true if the value at this location is an ID.
	 */
	public boolean hasValueID() {
		return valueID != null;
	}
	
	/** Get the value at this node. 
	 * @return the value at this node. Returns null if the value at this node
	 * is not an ID.
	 */
	public String getValueID() {
		return valueID;
	}
	
	/** Set the value at this node. This implies that the value is an ID.
	 * @param valueID the ID value at this node. This method will throw an
	 * error if one or more children have been added to this node already.
	 */
	public void setIDAtValue(String valueID) {
		if (children != null) {
			throw new IllegalStateException(
					"Cannot have a value and children");
		}
		this.valueID = valueID;
	}

	@Override
	public String toString() {
		return "IdRefNode [location=" + location + ", locationIsID="
				+ locationIsID + ", valueID=" + valueID + ", children="
				+ children + "]";
	}
}
