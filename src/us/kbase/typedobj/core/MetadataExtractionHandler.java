package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author msneddon
 */
public class MetadataExtractionHandler {

	/** Place to build up the extracted metadata, maps metadata name to metadata value **/
	protected Map <String,String> extracted;
	
	/** 
	 * Place to store info on what should be extracted, maps a metadata field to an expression
	 * used for extraction of the metadata value.
	 */
	protected Map <String, String> expressionToMetadataName;
	
	public MetadataExtractionHandler() {
		extracted = new HashMap<String,String>();
		expressionToMetadataName = new HashMap<String,String>();
	}
	
	public MetadataExtractionHandler(JsonNode selection) {
		extracted = new HashMap<String,String>();
		expressionToMetadataName = new HashMap<String,String>();
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
					String metadataName = info.getKey();
					String metadataSelection = info.getValue().asText();
					expressionToMetadataName.put(metadataName,metadataSelection);
				}
			}
		}
	}
	
	public void saveMetadata(String name, String value) {
		extracted.put(name, value);
	}
	
	public Map<String,String> getSavedMetadata() {
		return extracted;
	}
	
	public JsonNode getSavedMetadataAsJsonNode() {
		return null;
	}
	
	
	
}
