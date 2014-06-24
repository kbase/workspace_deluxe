package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.*;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceSet;

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
 *    String [] idReferences = report.getListOfIdReferences();
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
 * @author rsutormin
 */
public final class TypedObjectValidator {
	
	private static final boolean VERBOSE_EXCEPTIONS = false;
	
	private static final Map<String, String> ERROR_MAP =
			new HashMap<String, String>();
	static {
		final String nullErr = "Keys in maps/structures may not be null";
		ERROR_MAP.put("Null key for a Map not allowed in JSON (use a converting NullKeySerializer?)",
				nullErr); //when Map w/ null keys converted to JsonNode
	}
	
	private static final int maxErrorCount = 10;
	
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
	 * @throws TypeStorageException 
	 * @throws TypedObjectValidationException 
	 * @throws TypedObjectSchemaException 
	 */
	public TypedObjectValidationReport validate(final String instance,
			final TypeDefId type)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectValidationException,
			TypedObjectSchemaException {
		// parse the instance document into a JsonNode
		ObjectMapper mapper = new ObjectMapper();
		final JsonNode instanceRootNode;
		try {
			instanceRootNode = mapper.readTree(instance);
		} catch (Exception e) {
			throw new TypedObjectValidationException(
					"instance was not a valid or readable JSON document",e);
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
	 * @throws TypeStorageException
	 * @throws TypedObjectSchemaException 
	 */
	public TypedObjectValidationReport validate(final JsonNode instanceRootNode,
			final TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectSchemaException {
		final UObject obj;
		try {
			obj = new UObject(new JsonTokenStream(instanceRootNode), null);
		} catch (IOException ex) {
			//This should never happen since the data is already parsed into
			// a JsonNode
			throw new IllegalStateException("Something is very broken; " + 
					"already parsed JSON in memory caused a parsing " +
					"exception: " + ex.getMessage(), ex);
		}
		return validate(obj, typeDefId);
	}
	
	public TypedObjectValidationReport validate(final UObject obj,
			final TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectSchemaException {
		AbsoluteTypeDefId absoluteTypeDefDB = typeDefDB.resolveTypeDefId(typeDefId);
		
		// Actually perform the validation and return the report
		final List<String> errors = new ArrayList<String>();
		String schemaText = typeDefDB.getJsonSchemaDocument(absoluteTypeDefDB);
		final IdReferenceSet ids = new IdReferenceSet();
		final JsonNode[] searchDataWrap = new JsonNode[] {null};
		final JsonTokenValidationSchema schema =
				JsonTokenValidationSchema.parseJsonSchema(schemaText);
		try {
			if (!schema.getOriginalType().equals("kidl-structure"))
				throw new IllegalStateException(
						"Data of type other than structure couldn't be stored in workspace");
			JsonTokenStream jts = obj.getPlacedStream();
			try {
				schema.checkJsonData(jts, new JsonTokenValidationListener() {
					int errorCount = 0;
					@Override
					public void addError(String message) throws JsonTokenValidationException {
						errorCount++;
						if (errorCount < maxErrorCount) {
							mapErrors(errors, message);
//							errors.add(message);
						} else {
							throw new JsonTokenValidationException(message);
						}
					}

					@Override
					public void addIdRefMessage(IdReference ref) {
						//TODO 1 limit ID count to 100K IDs in memory
						ids.addId(ref);
					}

					@Override
					public void addSearchableWsSubsetMessage(JsonNode searchData) {
						searchDataWrap[0] = searchData;
					}
				});
			} finally {
				try { jts.close(); } catch (Exception ignore) {}
			}
		} catch (Exception ex) {
			if (VERBOSE_EXCEPTIONS) {
				ex.printStackTrace();
			}
			mapErrors(errors, ex.getMessage());
//			errors.add(ex.getMessage());
		}
		return new TypedObjectValidationReport(errors, searchDataWrap[0],
				absoluteTypeDefDB, obj, schema, ids.lock());
	}
	
	private void mapErrors(final List<String> errors, final String err) {
		if (ERROR_MAP.containsKey(err)) {
			errors.add(ERROR_MAP.get(err));
		} else {
			errors.add(err);
		}
	}

	/*
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
	public List<TypedObjectValidationReport> validate(List <JsonNode> instanceRootNodes, TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException
	{
		AbsoluteTypeDefId absoluteTypeDefDB = typeDefDB.resolveTypeDefId(typeDefId);
		final JsonSchema schema = typeDefDB.getJsonSchema(absoluteTypeDefDB);
		
		List <TypedObjectValidationReport> reportList = new ArrayList<TypedObjectValidationReport>(instanceRootNodes.size());
		for(JsonNode node : instanceRootNodes) {
			// Actually perform the validation and return the report
			ProcessingReport report;
			try {
				report = schema.validate(node);
			} catch (ProcessingException e) {
				report = repackageProcessingExceptionIntoReport(e,typeDefId);
			}
			reportList.add(new TypedObjectValidationReport(report, absoluteTypeDefDB,node));
		}
		return reportList;
	}*/
	
	
	/*
	 * If an exception is thrown during validation, we can catch that exception and instead of
	 * throwing it back up, we package it into a new report, add the message
	 * @param e
	 * @param typeDefId
	 * @return
	 * @throws InstanceValidationException
	protected ProcessingReport repackageProcessingExceptionIntoReport(ProcessingException e, TypeDefId typeDefId) 
			throws InstanceValidationException {
		ProcessingMessage m = e.getProcessingMessage();
		//System.out.println(m);
		ProcessingReport report = new ListReportProvider(LogLevel.DEBUG,LogLevel.NONE).newReport();
		try {
			if(m.getLogLevel().equals(LogLevel.FATAL)) {
				report.fatal(m);
			} else { //(m.getLogLevel().equals(LogLevel.ERROR))
				m.setLogLevel(LogLevel.ERROR); // we always set this as an error, because we threw an exception
				report.error(m);
			}
		} catch (ProcessingException e2) {
			throw new InstanceValidationException(
				"instance is not a valid '" + typeDefId.getTypeString() + "'",e2);
		}
		return report;
	}*/
	
	
}
