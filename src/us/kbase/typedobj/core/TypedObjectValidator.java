package us.kbase.typedobj.core;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.*;

/**
 * Interface for validating typed object instances in JSON against typed object definitions
 * registered in a type definition database.  This interface also provides methods for
 * extracting ID reference fields from a typed object instance, relabeling ID references,
 * and extracting the searchable subset of a typed object instance as smaller JSON object.
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
	 * Validate a Json String instance against the specified module and type.  Returns a TypedObjectValidationReport
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
		final JsonSchema schema = typeDefDB.getJsonSchema(mt.getModule(),mt.getName());
		
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

	
	public List<TypedObjectValidationReport> validate(List <JsonNode> instanceRootNodes, TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		final TypeDefName mt = typeDefId.getType();
		final JsonSchema schema = typeDefDB.getJsonSchema(mt.getModule(),mt.getName());
		
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
	
	
	
	
	
	
	

}
