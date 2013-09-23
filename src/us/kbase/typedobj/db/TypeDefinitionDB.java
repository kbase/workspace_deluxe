package us.kbase.typedobj.db;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;

import us.kbase.kidl.KbFuncdef;
import us.kbase.kidl.KbTypedef;
import us.kbase.typedobj.exceptions.*;

/**
 * This abstract base class provides methods/interface for retrieving a JSON Schema document and
 * JSON Schema validators for typed objects.
 * 
 * @author msneddon
 *
 */
public abstract class TypeDefinitionDB {

	
	/**
	 * This is the factory used to create a JsonSchema object from a Json Schema
	 * document stored in the DB.
	 */
	protected JsonSchemaFactory jsonSchemaFactory; 
	
	/**
	 * The Jackson ObjectMapper which can translate a raw Json Schema document to
	 * a JsonTree that can be handled by the validator.
	 */
	protected ObjectMapper mapper;
	
	
	/**
	 * Constructor which initializes this TypeDefinitionDB with a factory for
	 * generating schema objects from schema documents
	 * @param jsonSchemaFactory
	 */
	public TypeDefinitionDB(JsonSchemaFactory jsonSchemaFactory) {
		this.jsonSchemaFactory = jsonSchemaFactory;
		this.mapper            = new ObjectMapper();
	}
	
	/**
	 * Constructor which initializes this TypeDefinitionDB with a factory for
	 * generating schema objects from schema documents, and allows you to specify a
	 * custom Jackson object mapper for mapping the schema documents to JsonTree
	 * document, used before constructing the JsonSchema object.
	 * @param jsonSchemaFactory
	 * @param mapper
	 */
	public TypeDefinitionDB(JsonSchemaFactory jsonSchemaFactory, ObjectMapper mapper) {
		this.jsonSchemaFactory = jsonSchemaFactory;
		this.mapper            = mapper;
	}
	
	/**
	 * @return all names of registered modules
	 */
	public abstract List<String> getAllRegisteredModules() throws TypeStorageException;

	
	public abstract boolean isValidModule(String moduleName) throws TypeStorageException;
	
	public abstract String getModuleSpecDocument(String moduleName) throws NoSuchModuleException, TypeStorageException;
	
	public abstract ModuleInfo getModuleInfo(String moduleName) throws NoSuchModuleException, TypeStorageException;
	
	/**
	 * Given a module and a type name, return true if the type exists, false otherwise
	 * @param moduleName
	 * @param typeName
	 * @return true if valid, false otherwise
	 * @throws TypeStorageException 
	 */
	public boolean isValidType(String moduleName, String typeName) throws TypeStorageException {
		return isValidType(moduleName, typeName, null);
	}
	
	/**
	 * Given a module, a typeName and version, return true if the type exists, false otherwise.
	 * If version parameter is null (no version number is specified) then the latest version is 
	 * used for checking schema.
	 * @param moduleName
	 * @param typeName
	 * @param version
	 * @return true if valid, false otherwise
	 */
	public abstract boolean isValidType(String moduleName, String typeName, String version) throws TypeStorageException;
	
	/**
	 * Given a moduleName and typeName, return the JSON Schema document for the type. No version
	 * number is specified, so the latest version on record is always the schema returned if the
	 * underlying Json Schema database supports versioned typed objects.
	 * @param moduleName
	 * @param typeName
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public String getJsonSchemaDocument(String moduleName, String typeName) throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getJsonSchemaDocument(moduleName, typeName, null);
	}

	/**
	 * Given a moduleName, a typeName and version, return the JSON Schema document for the type. If 
	 * version parameter is null (no version number is specified) then the latest version is used for
	 * the schema returned if the underlying Json Schema database supports versioned typed objects.
	 * @param moduleName
	 * @param typeName
	 * @param version
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public abstract String getJsonSchemaDocument(String moduleName, String typeName, String version) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;

	/**
	 * The default implementation for getting a JsonSchema object that can be used as a validator.  This
	 * method creates a new JsonSchema object on each call.  If we implement caching of the validator
	 * objects for better performance, this is the method we would need to extend.
	 * @param moduleName
	 * @param typeName
	 * @return
	 * @throws NoSuchTypeException
	 */
	public JsonSchema getJsonSchema(String moduleName, String typeName)
			throws NoSuchTypeException, NoSuchModuleException, BadJsonSchemaDocumentException, TypeStorageException
	{
		// first we retrieve the Json Schema document, this can throw a NoSuchTypeException
		String jsonSchemaDocument = getJsonSchemaDocument(moduleName, typeName);
		
		// next we parse the document into a JsonSchema using our jsonSchemaFactory
		// if there are problems, we catch and throw up an error indicating a bad document
		return jsonSchemaFromString(moduleName, typeName, jsonSchemaDocument);
	}

	protected JsonSchema jsonSchemaFromString(String moduleName,
			String typeName, String jsonSchemaDocument)
			throws BadJsonSchemaDocumentException, TypeStorageException {
		try {
			JsonNode schemaRootNode = mapper.readTree(jsonSchemaDocument);
			return jsonSchemaFactory.getJsonSchema(schemaRootNode);
		} catch (Exception e) {
			throw new BadJsonSchemaDocumentException("schema for typed object '"+moduleName+"."+typeName+"' was not a valid or readable JSON document",e);
		}
	}
	
	/**
	 * Given a moduleName and typeName, return the SPEC parsing document for the type. No version
	 * number is specified, so the latest version of document will be returned.
	 * @param moduleName
	 * @param typeName
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public KbTypedef getTypeParsingDocument(String moduleName, String typeName) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException {
		return getTypeParsingDocument(moduleName, typeName, null);
	}

	/**
	 * Given a moduleName, a typeName and version, return the JSON Schema document for the type. If 
	 * version parameter is null (no version number is specified) then the latest version of document will be returned.
	 * @param moduleName
	 * @param typeName
	 * @param version
	 * @return JSON Schema document as a String
	 * @throws NoSuchTypeException
	 */
	public abstract KbTypedef getTypeParsingDocument(String moduleName, String typeName, String version) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;

	/**
	 * Return latest version of specified type. Version has two level structure of integers divided by dot like &lt;major&gt;.&lt;minor&gt;
	 * @param moduleName
	 * @param typeName
	 * @return latest version of specified type
	 */
	public abstract String getLatestTypeVersion(String moduleName, String typeName) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;
		
	/**
	 * @param moduleName
	 * @return all names of registered types belonging to specified module
	 */
	public abstract List<String> getAllRegisteredTypes(String moduleName) 
			throws NoSuchModuleException, TypeStorageException;

	public abstract void requestModuleRegistration(String moduleName, String ownerUserId) throws TypeStorageException;

	public abstract void registerModule(String specDocument, List<String> registeredTypes, 
			String userId) throws SpecParseException, TypeStorageException;
	
	public abstract void updateModule(String specDocument, List<String> changedTypes,
			List<String> backwardIncompatibleTypes, List<String> changedFuncs,
			List<String> backwardIncompatibleFuncs, String userId) 
					throws SpecParseException, TypeStorageException;
	
	/**
	 * Change major version from 0 to 1.
	 * @param moduleName
	 * @param typeName
	 * @return new version
	 * @throws NoSuchTypeException when current major version isn't 0
	 */
	public abstract String releaseType(String moduleName, String typeName, String userId) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;
		
	/**
	 * Return latest version of specified type. Version has two level structure of integers divided by dot like &lt;major&gt;.&lt;minor&gt;
	 * @param moduleName
	 * @param typeName
	 * @return latest version of specified type
	 */
	public abstract String getLatestFuncVersion(String moduleName, String funcName) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException;

	/**
	 * @param moduleName
	 * @return all names of registered functions belonging to specified module
	 */
	public abstract List<String> getAllRegisteredFuncs(String moduleName) 
			throws NoSuchModuleException, TypeStorageException;

	public KbFuncdef getFuncParsingDocument(String moduleName, String funcName) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException {
		return getFuncParsingDocument(moduleName, funcName, null);
	}

	public abstract KbFuncdef getFuncParsingDocument(String moduleName, String funcName, 
			String version) throws NoSuchFuncException, NoSuchModuleException, TypeStorageException;

	/**
	 * Change major version from 0 to 1.
	 * @param moduleName
	 * @param typeName
	 * @return new version
	 * @throws NoSuchTypeException when current major version isn't 0
	 */
	public abstract String releaseFunc(String moduleName, String funcName, String userId) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException;

	public abstract void removeTypeVersion(String moduleName, String typeName, String version, String userId) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;

	public abstract void stopTypeSupport(String moduleName, String typeName, String userId) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;	
	
	public abstract void removeTypeForAllVersions(String moduleName, String typeName, String userId) 
			throws NoSuchTypeException, NoSuchModuleException, TypeStorageException;

	public abstract void stopFuncSupport(String moduleName, String funcName, String userId) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException;	

	public abstract void removeFuncForAllVersions(String moduleName, String funcName, String userId) 
			throws NoSuchFuncException, NoSuchModuleException, TypeStorageException;

	public abstract void removeModule(String moduleName, String userId) 
			throws NoSuchModuleException, TypeStorageException;
	
	public abstract void removeAllRefs(String userId) throws TypeStorageException;
	
	public abstract Set<RefInfo> getTypeRefsByDep(String depModule, String depType, String version)
			throws TypeStorageException;

	public abstract Set<RefInfo> getTypeRefsByRef(String refModule, String refType, String version)
			throws TypeStorageException;

	public abstract Set<RefInfo> getFuncRefsByDep(String depModule, String depFunc, String version)
			throws TypeStorageException;

	public abstract Set<RefInfo> getFuncRefsByRef(String refModule, String refType, String version)
			throws TypeStorageException;

}
