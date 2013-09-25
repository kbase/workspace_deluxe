package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.*;

/**
 * Interface for validating typed object instances in JSON against typed object definitions
 * registered in a type definition database.  This interface also provides methods for
 * extracting ID reference fields from a typed object instance, relabeling ID references,
 * and extracting the searchable subset of a typed object instance as smaller JSON object.
 * 
 * Ex.
 * // validate, which gives you a report
 * TypedObjectValidator tov = ...
 * TypedObjectValidationReport report = tov.validate(instanceRootNode, typeDefId);
 * if(report.isInstanceValid()) {
 *    // get a list of ids
 *    String [] idReferences = report.getIdReferences();
 *      ... validate refs, create map which maps id refs to absolute id refs ...
 *    Map<string,string> absoluteIdMap = ...
 *    
 *    // update the ids in place
 *    report.setAbsoluteIdReferences(absoluteIdMap);
 *    tov.relableToAbsoluteIds(instanceRootNode,report);
 *    
 *    // extract out 
 *    JsonNode subset = tov.extractWsSearchableSubset(instanceRootNode,report);
 *    
 *      ... do what you want with the instance and the subset ...
 *    
 *    
 * } else {
 *   ... handle invalid typed object
 * }
 * 
 * 
 * @author msneddon
 * @author gaprice@lbl.gov
 *
 */
public final class TypedObjectValidator {

	
	/**
	 * This object is used to fetch the typed object Json Schema documents and
	 * JsonSchema objects which are used for validation
	 */
	protected TypeDefinitionDB typeDefDB;
	
	
	/**
	 * Get the type database the validator validates typed object instances against.
	 * @return the database.
	 */
	public TypeDefinitionDB getDB() {
		return typeDefDB;
	}
	
	
	/**
	 * Construct a TypedObjectValidator set to the specified Typed Object Definition DB
	 */
	public TypedObjectValidator(TypeDefinitionDB typeDefDB) {
		this.typeDefDB = typeDefDB;
	}
	
	
	/**
	 * Validate a Json String instance against the specified TypeDefId.  Returns a TypedObjectValidationReport
	 * containing the results of the validation and any other KBase typed object specific information such
	 * as a list of recognized IDs.
	 * @param instance in Json format
	 * @param type the type to process. Missing version information indicates 
	 * use of the most recent version.
	 * @return ProcessingReport containing the result of the validation
	 * @throws InstanceValidationException 
	 * @throws BadJsonSchemaDocumentException 
	 * @throws TypeStorageException 
	 */
	public TypedObjectValidationReport validate(String instance, TypeDefId type)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		// parse the instance document into a JsonNode
		ObjectMapper mapper = new ObjectMapper();
		final JsonNode instanceRootNode;
		try {
			instanceRootNode = mapper.readTree(instance);
		} catch (Exception e) {
			throw new InstanceValidationException("instance was not a valid or readable JSON document",e);
		}
		
		// validate and return the report
		return validate(instanceRootNode, type);
	}
	
	/**
	 * Validate a Json instance loaded to a JsonNode against the specified module and type.  Returns
	 * a ProcessingReport containing the results of the validation and any other KBase typed object
	 * specific information such as a list of recognized IDs.
	 * @param instanceRootNode
	 * @param moduleName
	 * @param typeName
	 * @param version (if set to null, then the latest version is used)
	 * @return
	 * @throws NoSuchTypeException
	 * @throws InstanceValidationException
	 * @throws BadJsonSchemaDocumentException
	 * @throws TypeStorageException
	 */
	public TypedObjectValidationReport validate(JsonNode instanceRootNode, TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		//TODO deal with versions, return AbsoluteTypeID with full information

		final TypeDefName mt = typeDefId.getType();
		final JsonSchema schema = typeDefDB.getJsonSchema(mt);
		
		// Actually perform the validation and return the report
		ProcessingReport report;
		try {
			report = schema.validate(instanceRootNode);
		} catch (ProcessingException e) {
			throw new InstanceValidationException(
					"instance is not a valid '" + mt.getTypeString() + "'",e);
		}
		
		//@todo set the correct AbsoluteTypeDefId
		return new TypedObjectValidationReport(report, new AbsoluteTypeDefId(new TypeDefName(mt.getModule(),mt.getName()),0,0));
	}

	/**
	 * Batch validation of the given Json instances, all against a single TypeDefId.  This method saves some communication
	 * steps with the backend 
	 * @param instanceRootNodes
	 * @param typeDefId
	 * @return
	 * @throws NoSuchTypeException
	 * @throws NoSuchModuleException
	 * @throws InstanceValidationException
	 * @throws BadJsonSchemaDocumentException
	 * @throws TypeStorageException
	 */
	public List<TypedObjectValidationReport> validate(List <JsonNode> instanceRootNodes, TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		final TypeDefName mt = typeDefId.getType();
		final JsonSchema schema = typeDefDB.getJsonSchema(mt);
		
		List <TypedObjectValidationReport> reportList = new ArrayList<TypedObjectValidationReport>(instanceRootNodes.size());
		for(JsonNode j : instanceRootNodes) {
			// Actually perform the validation and return the report
			ProcessingReport report;
			try {
				report = schema.validate(j);
			} catch (ProcessingException e) {
				throw new InstanceValidationException(
				"instance is not a valid '" + mt.getTypeString() + "'",e);
			}
			reportList.add(new TypedObjectValidationReport(report, new AbsoluteTypeDefId(new TypeDefName(mt.getModule(),mt.getName()),0,0)) );
		}
		return reportList;
	}
	
	
	
	/**
	 * Given the original JsonNode instance and a report from a validation, convert all ID references in the JsonNode
	 * to the absolute reference set in the report.
	 * @param instanceRootNode
	 * @param report
	 * @return
	 */
	public void relableToAbsoluteIds(JsonNode instanceRootNode, TypedObjectValidationReport report) {
		
		//@TODO implement
		
		
		return;
	}
	
	
	
	public JsonNode extractWsSearchableSubset(JsonNode instanceRootNode, TypedObjectValidationReport report) {
		
		// current method uses the data stashed by the report
		//@TODO double check that any updates to instanceRootNode get propagated via the report....
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode subset = mapper.createObjectNode();
		Iterator<ProcessingMessage> mssgs = report.getRawProcessingReport().iterator();
		while(mssgs.hasNext()) {
			ProcessingMessage m = mssgs.next();
			if( m.getMessage().compareTo("ws-searchable-fields-subset") == 0 ) {
				JsonNode fieldsSubset = m.asJson().get("value");
				Iterator<String> fieldNames = fieldsSubset.fieldNames();
				while(fieldNames.hasNext()) {
					String fieldName = fieldNames.next();
					subset.put(fieldName, fieldsSubset.findValue(fieldName));
				}
			} else if( m.getMessage().compareTo("ws-searchable-keys-subset") == 0 ) {
				JsonNode fieldsSubset = m.asJson().get("keys_of");
				Iterator<String> fieldNames = fieldsSubset.fieldNames();
				while(fieldNames.hasNext()) {
					String fieldName = fieldNames.next();
					subset.put(fieldName, fieldsSubset.findValue(fieldName));
				}
			}
		}
		return subset;
	}
	
	

}
