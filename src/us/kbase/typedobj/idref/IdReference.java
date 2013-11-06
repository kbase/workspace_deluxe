package us.kbase.typedobj.idref;

import java.util.List;

import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.exceptions.RelabelIdReferenceException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;


/**
 * Stores an ID reference
 */
public class IdReference {

	
	protected String idReference;
	protected List <TypeDefId> validTypes;
	protected String absoluteIdReference;
	
	private final String type;
	private final String id;
	private final boolean isFieldName;
	private final int depth;
	
	protected final ArrayNode location;
	protected final ObjectNode info;
	
	private String replacementId;
	private boolean isReplacementIdSet;
	
	protected IdReference(String type, String id, ArrayNode location, ObjectNode info, boolean isFieldName) {
		this.type = type;
		this.id = id;
		this.location = location;
		this.isFieldName = isFieldName;
		this.depth = location.size()-1;
		this.replacementId="";
		this.isReplacementIdSet=false;
		this.info = info;
	}
	
	/**
	 * return the type of IdReference (e.g. "ws", "shock", "external", "kb")
	 */
	public final String getType() {
		return type;
	}
	
	/**
	 * return the original id set at the specified location (note if the ID has been relabeled in the
	 * typed object instance data, you will still always get the original Id found when validating)
	 */
	public final String getId() {
		return id;
	}
	
	/**
	 * return the location of the ID in the original instance data; location is given as a sequence of
	 * either field names or array positions that will get you to the specified id field.
	 */
	public final ArrayNode getLocation() { 
		return location;
	}
	
	/**
	 * true if id is a field name in the json document, false otherwise
	 */
	public final boolean isFieldName() {
		return isFieldName;
	}
	
	/**
	 * the depth of the id in the json instance
	 */
	public final int getDepth() {
		return depth;
	}
	
	
	/**
	 * set the replacement id
	 */
	public final void setReplacementId(String replacementId) {
		this.replacementId = replacementId;
		this.isReplacementIdSet = true;
	}
	
	
	/**
	 * relabel the id in the json instance to the replacement id
	 */
	public final void relabel(JsonNode instanceRoot) throws RelabelIdReferenceException {
		if(!isReplacementIdSet) return;
		//System.out.println("renaming: "+this);
		if(isFieldName) {
			JsonNode mapping = instanceRoot;
			for(int depth=0; depth<location.size(); depth++) {
				if(mapping==null) {
					throw new RelabelIdReferenceException("relabeling '"+id+"' to '"+replacementId+"' failed because location in the JsonNode instance was not found at "+locationAsString());
				}
				if(mapping.isArray()) {
					mapping = mapping.get(location.get(depth).asInt());
				} else if(mapping.isObject()) {
					mapping = mapping.get(location.get(depth).asText());
				}
			}
			if(mapping==null) {
				throw new RelabelIdReferenceException("relabeling '"+id+"' to '"+replacementId+"' failed because location in the JsonNode instance was not found  at "+locationAsString());
			}
			// the mapping object we find MUST be an object
			if(mapping.isObject()) {
				ObjectNode mappingObj = (ObjectNode) mapping;
				// do the swap
				JsonNode value = mappingObj.remove(id);
				if(mappingObj.has(replacementId)) {
					// if the key was already added, then we gots a problem- the user was very likely trying to change two different
					// id references to the same newString, which can't work because in a mapping keys must be unique.  Overwriting
					// here would result in loss of data, so we must abort.
					throw new RelabelIdReferenceException("relabeling '"+id+"' to '"+replacementId+"' failed because the field name already exists at "+locationAsString());
				}
				mappingObj.put(replacementId, value);
			} else {
				throw new RelabelIdReferenceException("relabeling '"+id+"' to '"+replacementId+"' failed because location in the JsonNode instance was not found  at "+locationAsString());
			}
		} else {
			//traverse to the parent of the field we want to change
			JsonNode parent = instanceRoot;
			for(int depth=0; depth<location.size()-1; depth++) {
				if(parent==null) {
					throw new RelabelIdReferenceException("relabeling '"+id+"' to '"+replacementId+"' failed because location in the JsonNode instance was not found  at "+locationAsString());
				}
				if(parent.isArray()) {
					parent = parent.get(location.get(depth).asInt());
				} else if(parent.isObject()) {
					parent = parent.get(location.get(depth).asText());
				}
			}
			
			if(parent==null) {
				throw new RelabelIdReferenceException("relabeling '"+id+"' to '"+replacementId+"' failed because location in the JsonNode instance was not found at "+locationAsString());
			}
			
			// figure out whether the TextNode target is in an array or an object, and set
			// the new value accordingly
			if(parent.isArray()) {
				ArrayNode parentArray = (ArrayNode) parent;
				parentArray.set(location.get(location.size()-1).asInt(), new TextNode(replacementId));
			} else if(parent.isObject()) {
				ObjectNode parentObject = (ObjectNode) parent;
				parent = parentObject.set(location.get(location.size()-1).asText(), new TextNode(replacementId));
			} else {
				throw new RelabelIdReferenceException("relabeling '"+id+"' to '"+replacementId+"' failed because location in the JsonNode instance was not found at "+locationAsString());
			}
		}
		
	}
	
	protected boolean isReplacementIdSet() {
		return isReplacementIdSet;
	}
	protected String getReplacementId() {
		return replacementId;
	}
	
	protected String locationAsString() {
		StringBuilder sb = new StringBuilder();
		for(int d=0; d<location.size(); d++) {
			sb.append("/"+location.get(d).asText());
		}
		return sb.toString();
	}
	
	
	@Override
	public String toString() {
		String s;
		if(isReplacementIdSet)
			s=replacementId;
		else
			s="-none-";
		return "IdReference: [id:'"+id+"', type='"+type+"', loc="+location+", isField?="+isFieldName+", replacementId='"+s+"']";
	}
	
}
