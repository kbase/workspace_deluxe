package us.kbase.typedobj.core.validatorconfig;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
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
 * This class wraps everything required to identify kb-id-reference fields in instances.
 * @author msneddon
 */
public class IdRefValidationBuilder {

	/**
	 * Declare the keyword that will be parsed
	 */
	public static final String keyword = "id-reference";
	
	/**
	 * Method for generating a Keyword object which can be added to a JSON
	 * Schema Library object.  The Keyword object is initialized with
	 * classes that perform the validation and syntax checking.
	 * @return Keyword
	 */
	public static Keyword getKeyword() {
		final Keyword kbTypeKeyword = 
				Keyword.newBuilder(IdRefValidationBuilder.keyword)
					.withSyntaxChecker(IdRefSyntaxChecker.getInstance())
					.withDigester(IdRefDigester.getInstance())
					.withValidatorClass(IdRefKeywordValidator.class).freeze();
		return kbTypeKeyword;
	}
	
	/**
	 * Method for generating a MessageSource which can be added to a 
	 * ValidationConfiguration so that proper error reporting can be generated
	 * for the new JSON Schema keyword.
	 * @return MessageSource
	 */
	public static MessageSource getErrorMssgSrc() {
		final String key = "idReferenceError";
		final String value = "error in id-reference annotation";
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
	private static final class IdRefDigester extends AbstractDigester {
		
		private static final Digester INSTANCE = new IdRefDigester();

		public static Digester getInstance() {
			return INSTANCE;
		}

		private IdRefDigester() {
			// The Digester must declare the types of nodes that it can operate on. In this case,
			// the NodeType of the instance can only be a String or an Object (a kbase mapping)
			super(IdRefValidationBuilder.keyword, NodeType.STRING, NodeType.OBJECT);
		}

		@Override
		public JsonNode digest(final JsonNode schema) {
			// we don't really care about the context in this case, we just want the array
			// containing the data about what type of ID this is.
			System.out.println(schema.findValue(IdRefValidationBuilder.keyword));
			return schema.findValue(IdRefValidationBuilder.keyword);
		}
	}
	
	
	/**
	 * This class defines the method that performs the actual validation of the instance.
	 * @author msneddon
	 */
	public static final class IdRefKeywordValidator extends AbstractKeywordValidator {
		
		/**
		 * Store the digested Json Schema node
		 */
		private JsonNode idRefSpecificationData;
		
		public IdRefKeywordValidator(final JsonNode digest) {
			super(IdRefValidationBuilder.keyword);
			idRefSpecificationData = digest;
		}

		/**
		* Performs the actual validation of the instance, which in this case simply
		* TODO add validation to make sure ID is in the proper format
		*/
		@Override
		public void validate(
				final Processor<FullData, FullData> processor,
				final ProcessingReport report,
				final MessageBundle bundle,
				final FullData data)
						throws ProcessingException
		{
			// get the node we are looking at and the SchemaTree (schema tree no longer necessary)
			JsonNode node = data.getInstance().getNode();
			
			//if the node type is a string, extract out the ID and associate it with
			//the valid typed object names
			if(node.getNodeType() == JsonNodeType.STRING) {
				ProcessingMessage pm = new ProcessingMessage()
										.setMessage(IdRefValidationBuilder.keyword)
										.put("id", node.textValue())
										.put("id-spec-info", idRefSpecificationData)
										.put("location",data.getInstance().getPointer())
										.put("is-field-name",BooleanNode.FALSE) ;
				report.info(pm);
			}
			// if the node is a mapping, then we need to extract the field names (which are
			// the keys of the mapping) and save each one to the report
			else if(node.getNodeType() == JsonNodeType.OBJECT) {
				Iterator<Entry<String, JsonNode>> fields= node.fields();
				while(fields.hasNext()) {
					Entry<String,JsonNode> f = fields.next();
					ProcessingMessage pm = new ProcessingMessage()
											.setMessage(IdRefValidationBuilder.keyword)
											.put("id", f.getKey())
											.put("id-spec-info", idRefSpecificationData)
											.put("location",data.getInstance().getPointer())
											.put("is-field-name",BooleanNode.TRUE);
					report.info(pm);
				}
			}
			
			// for now, this method never fails.
			return;
		}

		@Override
		public String toString() {
			return "IdRefKeywordValidator set to validate ids of type" + idRefSpecificationData.get("type");
		}
	}
	
	
	
	/**
	 * This class checks the information in the json schema to make sure it is
	 * correct.  If not, we throw an error.
	 * @author msneddon
	 *
	 */
	private static final class IdRefSyntaxChecker extends AbstractSyntaxChecker {
	
		private static final SyntaxChecker INSTANCE = new IdRefSyntaxChecker();
		
//		/* this tells us what exceptions to throw if we run into an invalid schema */
//		private static final ExceptionProvider EXCEPTION_PROVIDER
//			= new ExceptionProvider()
//		{
//			@Override
//			public ProcessingException doException(final ProcessingMessage message) {
//				ProcessingMessage newMessage = new ProcessingMessage();
//				newMessage.setLogLevel(message.getLogLevel());
//				newMessage.setMessage(message.getMessage()+" - encountered when parsing annotation "+IdRefValidationBuilder.keyword);
//				return new InvalidSchemaException(message);
//			}
//		};
		
		public static SyntaxChecker getInstance() {
			return INSTANCE;
		}
		
		private IdRefSyntaxChecker()
		{
			// When constructing, the name for the keyword must be provided along with the allowed type for the value
			super(IdRefValidationBuilder.keyword, NodeType.OBJECT);
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
