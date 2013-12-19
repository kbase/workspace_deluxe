package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import us.kbase.kidl.KbStruct;
import us.kbase.kidl.KbType;
import us.kbase.kidl.KidlParseException;
import us.kbase.typedobj.exceptions.TypedObjectException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
/**
 * quick hack at subset extraction by a set of string paths, should probably be
 * refactored at some point
 * @author msneddon
 *
 */
public class TypedObjectExtractor {

	
	/*
	 * 
	 * each path given is divided into a starting path and a selector
	 * path :: selector
	 * 
	 * path is of the form field/field/field ...
	 * selector is of the form (field.*.[*].field, ... )
	 * 
	 */
	static public JsonNode extract(final List<String> paths, final JsonNode data) throws TypedObjectExtractionException {
		
		List <JsonNode>extract = new ArrayList<JsonNode> (paths.size());
		
		for(String s : paths) {
			
			return getAtPath(s,data);
			
//			return 
//			String [] tokens = s.split("::");
//			if(tokens.length == 1) {
//				// only a path set, which is great!  we can just split and return 
//				System.out.println();
//				extract.add(getAtPath(tokens[0],data));
//			} else if(tokens.length ==2) {
//				JsonNode startPos = getAtPath(tokens[0],data);
//				extract.add(getSelection(tokens[1],startPos));
//			} else {
//				throw new TypedObjectExtractionException("Malformed selection string, expecting only one occurence of '::'");
//			}
		}
		ObjectMapper mapper = new ObjectMapper();
		return mapper.createObjectNode();
	}
	
	/**
	 * given a path in the form: field/field/field/... where field is either a field name
	 * or array/tuple position, get the json node at that position
	 */
	static protected JsonNode getAtPath(String path, JsonNode data) throws TypedObjectExtractionException {
		String [] pathTokens = path.split("/");
		JsonNode currentPos = data;
		for(int k=0;k<pathTokens.length;k++) {
			JsonNode newPos = null;
			if(currentPos.isArray()) {
				try {
					newPos = currentPos.get(Integer.parseInt(pathTokens[k]));
				} catch (NumberFormatException e) {
					throw new TypedObjectExtractionException("Malformed selection string, given '"+pathTokens[k]+"', but object at this location is an array");
				}
			} else {
				newPos = currentPos.get(pathTokens[k]);
			}
			if(newPos==null) {
				throw new TypedObjectExtractionException("Malformed selection string, cannot get '"+pathTokens[k]+"'");
			}
			currentPos = newPos;
		}
		return currentPos;
	}
	
	
	
	
	
	static protected JsonNode getSelection(String selection, JsonNode data) {
		if(selection.isEmpty()) {
			return data;
		}

		
		return null;
	}
	
	
}
