package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

import us.kbase.kidl.KbStruct;
import us.kbase.kidl.KbType;
import us.kbase.kidl.KidlParseException;
import us.kbase.typedobj.exceptions.TypedObjectException;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * quick hack at subset extraction by a set of string paths, should probably be
 * refactored at some point
 * @author msneddon
 *
 */
public class TypedObjectExtractor {

	
	/**
	 * side effect: your list of paths will get sorted
	 */
	static public JsonNode extract(final List<String> paths, final JsonNode data) throws TypedObjectExtractionException {
		
		// sort the list so that if we get elements in an array, ordering is preserved
		java.util.Collections.sort(paths);
		
		// data should always be an object!
		if(!data.isObject()) { 
			throw new TypedObjectExtractionException("Can only extract data from an JsonNode of type object, not scalars or lists.");
		}
		
		//setup the return object
		ObjectMapper mapper = new ObjectMapper();
		JsonNode extraction = mapper.createObjectNode();
		
		for(String p : paths) {
			System.out.println(p);
			String [] parsedPath = parsePath(p);
			
			// if we selected nothing, we return nothing
			if(parsedPath.length==0) { continue; }
			// if we selected one level down, perform a couple basic checks
			if(parsedPath.length==1) {
				// if we selected everything, we return everything
				if(parsedPath[0].equals("*")) { return data; }
				// if we selected everything in a list, that is bad because root objects are not lists!!
				if(parsedPath[0].equals("[*]")) { throw new TypedObjectExtractionException("Cannot select elements of a list at the root of the object."); }
			}
			
			addFromPath(parsedPath,0,data,extraction);
			
			
			//for(int k=0;k<p.length; k++) {
			//	System.out.println(" - "+p[k]);
			//}
			
			
			
			
			
			//return getAtPath(s,data);
			
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
		return extraction;
	}
	
	// remove trailing '*' and '[*]', because these select everything
	static protected String [] parsePath(String path) {
		String [] pathToken = path.split("/");
		int end = pathToken.length;
		for(int k=pathToken.length-1; k>0; k--) {
			if(pathToken[k].equals("*") || pathToken[k].equals("[*]")) {
				// but we do not remove the first one, because this means we select everything...
				if(k==0) {break;} 
				end--;
			} else {break;}
		}
		String[] parsedPath = new String[end];
		for(int k=0; k<end; k++) {
			parsedPath[k] = pathToken[k];
		}
		return parsedPath;
	}
	
	
	static protected void addFromPath(String [] path, int pathPos, JsonNode data, JsonNode extract) throws TypedObjectExtractionException {
		
		System.out.println(pathPos+" "+path.length);
		
		// TODO pull this out of every call!
		ObjectMapper mapper = new ObjectMapper();
		
		// first handle the end of recursion condition, which is that we are
		// at the last element, and so we add the entire contents as specified
		if(pathPos == path.length-1) {
			if(data.isArray()) {
				// if it is an array, then we try to get the element at the given position
				try {
					((ArrayNode)extract).add(data.get(Integer.parseInt(path[pathPos])));
				} catch (NumberFormatException e) {
					throw new TypedObjectExtractionException("Malformed selection string, given '"+path[pathPos]+"', but object at this location is an array");
				}
			} else if(data.isObject()) {
				JsonNode value = data.get(path[pathPos]);
				if(value==null) {
					throw new TypedObjectExtractionException("Malformed selection string, cannot get '"+path[pathPos]+"'");
				}
				((ObjectNode)extract).set(path[pathPos], value);
			} else {
				throw new TypedObjectExtractionException("Malformed selection string, cannot get '"+path[pathPos]+"' from a scalar element");
			}
			return;
		}
		
		// next we decide what to do next
		if(data.isArray()) {
			if(path[pathPos].equals("[*]")) {
				// we must get all the elements and recurse on each one
				for(int k=0; k<data.size(); k++) {
					JsonNode elementdata = data.get(k);
					JsonNode elementextract = extract.get(k);
					if(elementextract == null) {
						if(elementdata.isArray()) {
							elementextract = ((ArrayNode)extract).addArray();
						} else {
							elementextract = ((ArrayNode)extract).addObject();
						}
					}
					addFromPath(path,pathPos+1,elementdata,elementextract);
				}
			} else {
				try {
					JsonNode subelement = data.get(Integer.parseInt(path[pathPos]));
					if(subelement==null) {
						throw new TypedObjectExtractionException("No element at position '"+path[pathPos]+"'");
					}
					JsonNode elementextract = extract.get(Integer.parseInt(path[pathPos]));
					if(elementextract == null) {
						if(subelement.isArray()) {
							elementextract = ((ArrayNode)extract).addArray();
						} else {
							elementextract = ((ArrayNode)extract).addObject();
						}
					}
					addFromPath(path,pathPos+1,subelement,elementextract);
				} catch (NumberFormatException e) {
					throw new TypedObjectExtractionException("Malformed index into array '"+path[pathPos]+"'");
				}
			}
		} else if(data.isObject()) {
			if(path[pathPos].equals("*")) {
				// we get all the fields and recurse on each one
				Iterator<String> fields = data.fieldNames();
				while(fields.hasNext()) {
					String f = fields.next();
					if(!extract.has(f)) {
						if(data.get(f).isArray()) {
							((ObjectNode)extract).putArray(f);
						} else {
							((ObjectNode)extract).putObject(f);
						}
					}
					addFromPath(path,pathPos+1,data.get(f),extract.get(f));
				}
			} else {
				JsonNode subdata = data.get(path[pathPos]);
				if(subdata==null) {
					throw new TypedObjectExtractionException("Malformed selection string, cannot get '"+path[pathPos]+"'");
				}
				if(!extract.has(path[pathPos])) {
					if(subdata.isArray()) {
						((ObjectNode)extract).putArray(path[pathPos]);
					} else {
						((ObjectNode)extract).putObject(path[pathPos]);
					}
				}
				addFromPath(path,pathPos+1,subdata,extract.get(path[pathPos]));
			}
		} else {
			throw new TypedObjectExtractionException("Malformed selection string, cannot get '"+path[pathPos]+"' from a scalar element");
		}
	}
	
	
}
