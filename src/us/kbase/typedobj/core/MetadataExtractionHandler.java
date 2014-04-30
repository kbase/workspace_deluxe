package us.kbase.typedobj.core;

import java.util.Map.Entry;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A small class used to stash metadata extraction selection, and the metadata that
 * is eventually extracted.
 * @author msneddon
 */
public class MetadataExtractionHandler {

	/** Place to build up the extracted metadata, maps metadata name to metadata value (string to string) **/
	protected ObjectNode extracted;
	
	/** 
	 * Place to store info on what should be extracted, maps a metadata name to an expression
	 * used for extraction of the metadata value.
	 */
	protected ObjectNode selection;
	
	static protected ObjectMapper mapper; 
	static {
		mapper = new ObjectMapper();
	}
	
	public MetadataExtractionHandler(JsonNode selection) {
		this.selection = mapper.createObjectNode();
		extracted      = mapper.createObjectNode();
		addMetadataToExtract(selection);
	}
	
	/**
	 * Selection should be a Json Object node with field values being the 
	 * metadata name, and value being a string giving the expression to extract
	 * the metadata value
	 */
	public void addMetadataToExtract(JsonNode selection) {
		if(selection!=null) {
			Iterator<Entry<String, JsonNode>> fields = selection.fields();
			while(fields.hasNext()) {
				Entry<String,JsonNode> info = fields.next();
				if(info.getValue().isTextual()) {
					this.selection.put(info.getKey(), info.getValue().asText());
				}
			}
		}
	}
	
	public JsonNode getMetadataSelection() {
		return selection;
	}
	
	public void saveMetadata(String name, String value) {
		extracted.put(name, value);
	}
	
	public JsonNode getSavedMetadata() {
		return extracted;
	}
	

	@Override
	public String toString() {
		return "MetadataExtractionHandler [extracted=" + extracted
				+ ", selection=" + selection + "]";
	}
}
