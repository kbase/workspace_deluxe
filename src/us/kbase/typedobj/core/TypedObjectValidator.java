package us.kbase.typedobj.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.exceptions.*;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.IdReferenceHandlerSet;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdParseException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.IdReferenceHandlerException;
import us.kbase.typedobj.idref.IdReferenceHandlerSet.TooManyIdsException;

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
public class TypedObjectValidator {
	
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
	protected TypeProvider typeProvider;
	
	
	/**
	 * Get the type provider the validator validates typed object instances against.
	 * @return the provider.
	 */
	public TypeProvider getTypeProvider() {
		return typeProvider;
	}
	
	
	/**
	 * Construct a TypedObjectValidator set to the specified Typed Provider
	 */
	public TypedObjectValidator(TypeProvider typeProvider) {
		this.typeProvider = typeProvider;
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
	 * @throws IdReferenceHandlerException 
	 * @throws TooManyIdsException 
	 * @throws IOException 
	 * @throws JsonParseException 
	 */
	public ValidatedTypedObject validate(final String instance,
			final TypeDefId type, final IdReferenceHandlerSet<?> handlers)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectValidationException,
			TypedObjectSchemaException,
			TooManyIdsException, IdReferenceHandlerException,
			JsonParseException, IOException {
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
		return validate(instanceRootNode, type, handlers);
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
	 * @throws IdReferenceHandlerException 
	 * @throws TooManyIdsException 
	 * @throws IOException 
	 * @throws JsonParseException 
	 */
	public ValidatedTypedObject validate(final JsonNode instanceRootNode,
			final TypeDefId typeDefId, final IdReferenceHandlerSet<?> handlers)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectSchemaException,
			TooManyIdsException, IdReferenceHandlerException,
			JsonParseException, IOException {
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
		return validate(obj, typeDefId, handlers);
	}
	
	public ValidatedTypedObject validate(final UObject obj,
			final TypeDefId typeDefId, final IdReferenceHandlerSet<?> handlers)
			throws NoSuchTypeException, NoSuchModuleException,
			TypeStorageException, TypedObjectSchemaException,
			TooManyIdsException, JsonParseException, IOException {
		AbsoluteTypeDefId absoluteTypeDefId = typeProvider.resolveTypeDef(
				typeDefId);
		
		// Actually perform the validation and return the report
		final List<String> errors = new ArrayList<String>();
		String schemaText = typeProvider.getTypeJsonSchema(absoluteTypeDefId);
		final JsonTokenValidationSchema schema =
				JsonTokenValidationSchema.parseJsonSchema(schemaText);
		
		// these must be arrays to get the inner class def override to work
		final JsonNode [] metadataSelection = new JsonNode[] {null};
		try {
			if (!schema.getOriginalType().equals("kidl-structure"))
				throw new JsonTokenValidationException(
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
						} else {
							throw new JsonTokenValidationException(message);
						}
					}

					@Override
					public void addStringIdRefMessage(
							final IdReference<String> ref,
							final JsonDocumentLocation loc)
							throws TooManyIdsException,
							JsonTokenValidationException {
						if (handlers.hasHandler(ref.getType())) {
							try {
								handlers.addStringId(ref);
							} catch (IdParseException e) {
								addError(String.format(
										"Unparseable id %s of type %s: %s at %s",
										e.getId(),
										e.getIdType().getType(),
										e.getMessage(),
										loc.getFullLocationAsString()));
							} catch (IdReferenceException e) {
								addError(String.format(
										"Invalid id %s of type %s: %s at %s",
										e.getId(),
										e.getIdType().getType(),
										e.getMessage(),
										loc.getFullLocationAsString()));
							} catch (IdReferenceHandlerException e) {
								addError(String.format(
										"Id handling error for id type %s: %s at %s",
										e.getIdType().getType(),
										e.getMessage(),
										loc.getFullLocationAsString()));
							}
						}
					}

//					@Override
//					public void addLongIdRefMessage(IdReference<Long> ref)
//							throws TooManyIdsException,
//							IdReferenceHandlerException {
//						if (handlers.hasHandler(ref.getType())) {
//							handlers.addLongId(ref);
//						}
//					}

					@Override
					public void addMetadataWsMessage(JsonNode selection) {
						metadataSelection[0] = selection;
					}
				});
			} finally {
				try { jts.close(); } catch (Exception ignore) {}
			}
		} catch (JsonTokenValidationException ex) {
			if (VERBOSE_EXCEPTIONS) {
				ex.printStackTrace();
			}
			mapErrors(errors, ex.getMessage());
		} catch (IllegalArgumentException iae) {
			if (iae.getCause() instanceof JsonGenerationException) {
				//thrown if there's a null map key by Jackson
				if (VERBOSE_EXCEPTIONS) {
					iae.printStackTrace();
				}
				mapErrors(errors, iae.getMessage());
			} else {
				throw iae;
			}
		}

		return new ValidatedTypedObject(
									obj,
									absoluteTypeDefId,
									errors, 
									metadataSelection[0],
									schema,
									handlers);
	}
	
	private void mapErrors(final List<String> errors, final String err) {
		if (ERROR_MAP.containsKey(err)) {
			errors.add(ERROR_MAP.get(err));
		} else {
			errors.add(err);
		}
	}
	
}
