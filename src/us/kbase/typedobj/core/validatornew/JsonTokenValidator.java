package us.kbase.typedobj.core.validatornew;

import java.io.IOException;
import java.io.InputStream;

import us.kbase.common.service.JsonTreeTraversingParser;
import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.TypedObjectValidationReport;
import us.kbase.typedobj.db.TypeDefinitionDB;
import us.kbase.typedobj.exceptions.BadJsonSchemaDocumentException;
import us.kbase.typedobj.exceptions.InstanceValidationException;
import us.kbase.typedobj.exceptions.NoSuchModuleException;
import us.kbase.typedobj.exceptions.NoSuchTypeException;
import us.kbase.typedobj.exceptions.TypeStorageException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.report.ListProcessingReport;
import com.github.fge.jsonschema.report.LogLevel;
import com.github.fge.jsonschema.report.ProcessingMessage;

public class JsonTokenValidator {
	private static final int MAX_ERROR_COUNT = 10;
	
	protected TypeDefinitionDB typeDefDB;
	
	/**
	 * Get the type database the validator validates typed object instances against.
	 * @return the database.
	 */
	public TypeDefinitionDB getDB() {
		return typeDefDB;
	}
	
	/**
	 * Construct a validator set to the specified Typed Object Definition DB
	 */
	public JsonTokenValidator(TypeDefinitionDB typeDefDB) {
		this.typeDefDB = typeDefDB;
	}
	
	public TypedObjectValidationReport validate(JsonTokenStream jp, TypeDefId typeDefId)
			throws NoSuchTypeException, NoSuchModuleException, InstanceValidationException, 
			BadJsonSchemaDocumentException, TypeStorageException, JsonParseException, 
			JsonMappingException, IOException, JsonTokenValidationException {
		//JsonParser jp = new JsonTreeTraversingParser(instanceRootNode, new ObjectMapper());
		AbsoluteTypeDefId absoluteTypeDefDB = typeDefDB.resolveTypeDefId(typeDefId);
		String schemaText = typeDefDB.getJsonSchemaDocument(absoluteTypeDefDB);
		NodeSchema schema = NodeSchema.parseJsonSchema(schemaText);
		final ListProcessingReport report = new ListProcessingReport(LogLevel.ERROR, LogLevel.FATAL);
		schema.checkJsonData(jp, null, null); /*new JsonTokenValidationListener() {
			int errorCount = 0;
			@Override
			public void addError(String message) throws JsonTokenValidationException {
				errorCount++;
				if (errorCount <= MAX_ERROR_COUNT)
					try {
						report.error(new ProcessingMessage().setMessage(message));
					} catch (ProcessingException ex) {
						throw new JsonTokenValidationException(ex.getMessage());
					}
			}
		});*/
		return new TypedObjectValidationReport(report, absoluteTypeDefDB, null);
	}

}
