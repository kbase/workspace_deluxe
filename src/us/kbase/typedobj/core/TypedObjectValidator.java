package us.kbase.typedobj.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.*;

/**
 * The primary class you should use for validating
 * @author msneddon
 *
 */
public final class TypedObjectValidator {

	
	/**
	 * This object is used to fetch the typed object Json Schema documents and
	 * JsonSchema objects which are used for validation
	 */
	protected TypeDefinitionDB typeDefDB;
	
	/**
	 * Get the type database the validator validates types against.
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
	 * Validate a Json String instance against the specified module and type.  Returns a ProcessingReport
	 * containing the results of the validation and any other KBase typed object specific information such
	 * as a list of recognized IDs.
	 * @param instance in Json format
	 * @param moduleName
	 * @param typeName
	 * @return ProcessingReport containing the result of the validation
	 * @throws InstanceValidationException 
	 * @throws BadJsonSchemaDocumentException 
	 * @throws TypeStorageException 
	 */
	public ProcessingReport validate(String instance, String moduleName, String typeName)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		String version = null;
		return validate(instance,moduleName,typeName,version);
	}
	
	
	/**
	 * Validate a Json String instance against the specified module and type.  Returns a ProcessingReport
	 * containing the results of the validation and any other KBase typed object specific information such
	 * as a list of recognized IDs.
	 * @param instance in Json format
	 * @param moduleName
	 * @param typeName
	 * @param version (if set to null, then the latest version is used)
	 * @return ProcessingReport containing the result of the validation
	 * @throws InstanceValidationException 
	 * @throws BadJsonSchemaDocumentException 
	 * @throws TypeStorageException 
	 */
	public ProcessingReport validate(String instance, String moduleName, String typeName, String version)
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
        return validate(instanceRootNode, moduleName, typeName, version);
	}
	
	

	/**
	 * Validate a Json instance loaded to a JsonNode against the specified module and type.  Returns
	 * a ProcessingReport containing the results of the validation and any other KBase typed object
	 * specific information such as a list of recognized IDs.
	 * @param instanceRootNode
	 * @param moduleName
	 * @param typeName
	 * @return
	 * @throws NoSuchTypeException
	 * @throws InstanceValidationException
	 * @throws BadJsonSchemaDocumentException
	 * @throws TypeStorageException 
	 */
	public ProcessingReport validate(JsonNode instanceRootNode, String moduleName, String typeName)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		//TODO make input for all validate methods a TypeId and return an AbsoluteTypeId
		String version = null;
        return validate(instanceRootNode, moduleName, typeName, version);
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
	public ProcessingReport validate(JsonNode instanceRootNode, String moduleName, String typeName, String version)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		if(version != null) {
			throw new BadJsonSchemaDocumentException("Versioning of typed objects not supported yet, pass 'null' for now as the version String.");
		}
		// Retrieve the JsonSchema object; this will throw an error if the type or schema aren't valid
		final JsonSchema schema = typeDefDB.getJsonSchema(moduleName, typeName);
		
		// Actually perform the validation and return the report
        ProcessingReport report;
		try {
			report = schema.validate(instanceRootNode);
		} catch (ProcessingException e) {
			throw new InstanceValidationException("instance is not a valid '"+moduleName+"."+typeName+"'",e);
		}
		return report;
	}
	
	
	/**
	 * 
	 * @param instance
	 * @param typeName
	 * @return
	 */
	public String extractSearchableSubset(String instance, String typeName) {
		
		return "{}";
	}
}
