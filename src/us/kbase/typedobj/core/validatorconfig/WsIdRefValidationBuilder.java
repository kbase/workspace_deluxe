package us.kbase.typedobj.core.validatorconfig;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.github.fge.jackson.NodeType;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.exceptions.ExceptionProvider;
import com.github.fge.jsonschema.exceptions.InvalidSchemaException;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.keyword.digest.AbstractDigester;
import com.github.fge.jsonschema.keyword.digest.Digester;
import com.github.fge.jsonschema.keyword.validator.AbstractKeywordValidator;
import com.github.fge.jsonschema.library.Keyword;
import com.github.fge.jsonschema.processing.Processor;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.syntax.checkers.AbstractSyntaxChecker;
import com.github.fge.jsonschema.syntax.checkers.SyntaxChecker;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.source.MapMessageSource;
import com.github.fge.msgsimple.source.MessageSource;


/**
 * This class wraps everything required to identify kb-id-reference fields.
 * @author msneddon
 */
public class WsIdRefValidationBuilder {

	/**
	 * Declare the keyword that will be parsed
	 */
	public static final String keyword = "kb-id-reference";
	
	/**
	 * Method for generating a Keyword object which can be added to a JSON
	 * Schema Library object.  The Keyword object is initialized with
	 * classes that perform the validation and syntax checking.
	 * @return Keyword
	 */
	public static Keyword getKeyword() {
		final Keyword kbTypeKeyword = 
				Keyword.newBuilder(WsIdRefValidationBuilder.keyword)
					.withSyntaxChecker(WsIdRefSyntaxChecker.getInstance())
					.withDigester(WsIdRefDigester.getInstance())
					.withValidatorClass(WsIdRefKeywordValidator.class).freeze();
		return kbTypeKeyword;
	}
	
	/**
	 * Method for generating a MessageSource which can be added to a 
	 * ValidationConfiguration so that proper error reporting can be generated
	 * for the new JSON Schema keyword.
	 * @return MessageSource
	 */
	public static MessageSource getErrorMssgSrc() {
		final String key = "idRefError";
		final String value = "i hath found error in kb-id-reference";
		final MessageSource source = MapMessageSource.newBuilder()
				.put(key, value).build();
		return source;
	}
	
	
	
	/**
	 * Define the Digester, which can process the JSON schema context where the
	 * keyword was defined.  It consumes a JsonNode object containing the schema
	 * document at the node where the keyword is identified, and returns a
	 * simplified JsonNode object that can make validation later easier if
	 * needed.  In this case, we extract out the array containing the provided
	 * list of typed objects for which this ID may map to. This list is returned
	 * as part of the final ProcessingReport.
	 * @author msneddon
	 */
	private static final class WsIdRefDigester extends AbstractDigester {
		
		private static final Digester INSTANCE = new WsIdRefDigester();

		public static Digester getInstance() {
			return INSTANCE;
		}

		private WsIdRefDigester() {
			// The Digester must declare the types of nodes that it can operate on. In this case,
			// the NodeType of the instance can only be a String or an Object (a kbase mapping)
			super(WsIdRefValidationBuilder.keyword, NodeType.STRING, NodeType.OBJECT);
		}

		@Override
		public JsonNode digest(final JsonNode schema) {
			// we don't really care about the context in this case, we just want the array
			// containing the list of possible typed objects that this ID can map to.
			return schema.findValue(WsIdRefValidationBuilder.keyword);
		}
	}
	
	
	/**
	 * This class defines the method that performs the actual validation of the instance.
	 * @author msneddon
	 */
	public static final class WsIdRefKeywordValidator extends AbstractKeywordValidator {
		
		/**
		 * Store the digested Json Schema node, which in this case was digested to
		 * already contain the list of valid typed objects that the ID can point to
		 */
		private JsonNode validTypedObjectNames;
		
	    public WsIdRefKeywordValidator(final JsonNode digest) {
	        super(WsIdRefValidationBuilder.keyword);
	        validTypedObjectNames = digest;
	    }

	    /**
	     * Performs the actual validation of the instance, which in this case simply
	     * @todo add validation to make sure ID is in the proper format
	     */
	    @Override
	    public void validate(
	    		final Processor<FullData, FullData> processor,
	    		final ProcessingReport report,
	    		final MessageBundle bundle,
	    		final FullData data)
	    				throws ProcessingException
	    {
	    	// get the node we are looking at and the SchemaTree 
	    	JsonNode node = data.getInstance().getNode();
	    	SchemaTree schemaLocation = data.getSchema();
	    	
	    	//if the node type is a string, extract out the ID and associate it with
	    	//the valid typed object names
	    	if(node.getNodeType() == JsonNodeType.STRING) {
	    		ProcessingMessage pm = new ProcessingMessage()
										.setMessage(WsIdRefValidationBuilder.keyword)
										.put("id", node.textValue())
										.put("type", validTypedObjectNames)
		    							.put("location",schemaLocation.getPointer());
	    		report.info(pm);
	    	}
	    	// if the node is a mapping, then we need to extract the field names (which are
	    	// the keys of the mapping) and save each one to the report
	    	else if(node.getNodeType() == JsonNodeType.OBJECT) {
	    		Iterator<Entry<String, JsonNode>> fields= node.fields();
	    		while(fields.hasNext()) {
	    			Entry<String,JsonNode> f = fields.next();
			    	ProcessingMessage pm = new ProcessingMessage()
											.setMessage(WsIdRefValidationBuilder.keyword)
											.put("id", f.getKey())
											.put("type", validTypedObjectNames)
			    							.put("location",schemaLocation.getPointer());
					report.info(pm);
	    		}
	    	}
	    	
	    	// for now, this method never fails.
	    	return;
	    }

	    @Override
	    public String toString() {
	        return "WsIdRefKeywordValidator set to validate:" + validTypedObjectNames;
	    }
	}
	
	
	
	/**
	 * This class checks the information in the json schema to make sure it is
	 * correct.  If not, we throw an error.
	 * @author msneddon
	 *
	 */
	private static final class WsIdRefSyntaxChecker extends AbstractSyntaxChecker {
	
		private static final SyntaxChecker INSTANCE = new WsIdRefSyntaxChecker();
		
		/* this tells us what exceptions to throw if we run into an invalid schema */
		private static final ExceptionProvider EXCEPTION_PROVIDER
			= new ExceptionProvider()
		{
			@Override
			public ProcessingException doException(final ProcessingMessage message) {
				return new InvalidSchemaException(message);
			}
		};
		
		public static SyntaxChecker getInstance() {
			return INSTANCE;
		}
		
		private WsIdRefSyntaxChecker()
		{
			// When constructing, the name for the keyword must be provided along with the allowed type for the value
			super(WsIdRefValidationBuilder.keyword, NodeType.ARRAY);
			//System.err.println("creating a syntax checker");
		}
		
		@Override
		protected void checkValue(
				final Collection<JsonPointer> pointers,
				final MessageBundle bundle,
				final ProcessingReport report,
				final SchemaTree tree)
						throws ProcessingException
		{
			// this checks the values in the JSON Schema, which at this point should not
			// be required because all registered JSON Schemas have already been validated when they
			// are first registered
		}
	}
	
	
	
}
