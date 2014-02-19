package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.report.ListProcessingReport;
import com.github.fge.jsonschema.report.ListReportProvider;
import com.github.fge.jsonschema.report.LogLevel;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.JsonTreeTraversingParser;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.validatorconfig.IdRefValidationBuilder;
import us.kbase.typedobj.core.validatornew.IdRefNode;
import us.kbase.typedobj.core.validatornew.JsonTokenValidationException;
import us.kbase.typedobj.core.validatornew.JsonTokenValidationListener;
import us.kbase.typedobj.core.validatornew.NodeSchema;
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
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException {
		//JsonParser jp = new JsonTreeTraversingParser(instanceRootNode, new ObjectMapper());
		try {
			UObject obj = new UObject(new JsonTokenStream(instanceRootNode), "");
			return validate(obj, typeDefId);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
	
	public TypedObjectValidationReport validate(UObject obj, TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, BadJsonSchemaDocumentException, TypeStorageException {	
		AbsoluteTypeDefId absoluteTypeDefDB = typeDefDB.resolveTypeDefId(typeDefId);
		
		// Actually perform the validation and return the report
		final ListProcessingReport report;
		String schemaText = typeDefDB.getJsonSchemaDocument(absoluteTypeDefDB);
		/*
		System.out.println(typeDefDB.getModuleSpecDocument(absoluteTypeDefDB.getType().getModule()));
		System.out.println("-------------------------------------------------------------");
		System.out.println(schemaText);
		System.out.println("--------------------------------------------------------------");
		*/
		report = new ListProcessingReport(LogLevel.INFO, LogLevel.FATAL);
		IdRefNode idRefTree = new IdRefNode(null);
		try {
			NodeSchema schema = NodeSchema.parseJsonSchema(schemaText);
			schema.checkJsonData(obj.getPlacedStream(), null, new JsonTokenValidationListener() {
				int errorCount = 0;
				@Override
				public void addError(String message) throws JsonTokenValidationException {
					errorCount++;
					if (errorCount <= 10)
						try {
							report.error(new ProcessingMessage().setMessage(message));
						} catch (ProcessingException ex) {
							throw new JsonTokenValidationException(ex.getMessage());
						}
				}
				
				@Override
				public void addIdRefMessage(String id, JsonNode idRefSpecificationData, List<String> path, boolean isField) {
					ProcessingMessage pm = new ProcessingMessage()
						.setMessage(IdRefValidationBuilder.keyword)
						.put("id", id)
						.put("id-spec-info", idRefSpecificationData)
						.put("location", UObject.transformObjectToJackson(path))
						.put("is-field-name", isField ? BooleanNode.TRUE : BooleanNode.FALSE);
					try {
						report.info(pm);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				
				@Override
				public void addSearchableWsSubsetMessage(JsonNode searchData) {
					try {
						report.info(new ProcessingMessage()
							.setMessage("searchable-ws-subset")
							.put("search-data", searchData));
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}, idRefTree);
		} catch (Exception ex) {
			try {
				report.error(new ProcessingMessage().setMessage(ex.getMessage()));
			} catch (ProcessingException ignore) {}
		}
		
		return new TypedObjectValidationReport(report, absoluteTypeDefDB, obj, idRefTree);
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
	
	
	/**
	 * If an exception is thrown during validation, we can catch that exception and instead of
	 * throwing it back up, we package it into a new report, add the message
	 * @param e
	 * @param typeDefId
	 * @return
	 * @throws InstanceValidationException
	 */
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
	}
	
	
}
