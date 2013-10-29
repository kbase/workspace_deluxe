package us.kbase.typedobj.core.validatorconfig;

import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jackson.jsonpointer.JsonPointer;
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
 * Builder to validate the 'original-type' keyword.  The 'original-type' indicates what the original
 * type in the source IDL was that maps to a particular node in the Json Schema.  This is useful, for
 * instance, if generating Json Schemas from the type compiler to distinguish between 'structure' types
 * and 'mapping' types, which at the json level appear identical.
 * @author msneddon
 */
public class OriginalTypeValidationBuilder {

	/**
	 * Declare the keyword that will be parsed
	 */
	public static final String keyword = "original-type";
	
	/**
	 * Method for generating a Keyword object which can be added to a JSON
	 * Schema Library object.  The Keyword object is initialized with
	 * classes that perform the validation and syntax checking.
	 * @return Keyword
	 */
	public static Keyword getKeyword() {
		final Keyword kbTypeKeyword = 
				Keyword.newBuilder(OriginalTypeValidationBuilder.keyword)
				.withSyntaxChecker(OriginalTypeSyntaxChecker.getInstance())
				.withDigester(OriginalTypeDigester.getInstance())
				.withValidatorClass(OriginalTypeKeywordValidator.class).freeze();
		return kbTypeKeyword;
	}
	
	/**
	 * Method for generating a MessageSource which can be added to a 
	 * ValidationConfiguration so that proper error reporting can be generated
	 * for the new JSON Schema keyword.
	 * @return MessageSource
	 */
	public static MessageSource getErrorMssgSrc() {
		final String key = "originalTypeKeywordError";
		final String value = "error encountered in processing original-type keyword";
		final MessageSource source = MapMessageSource.newBuilder()
				.put(key, value).build();
		return source;
	}
	
	
	
	/**
	 * Define the Digester, which processes the JSON schema context where the
	 * keyword was defined.  It consumes a JsonNode object containing the schema
	 * document at the node where the keyword is identified, and returns a
	 * simplified JsonNode object that can make validation later easier if
	 * needed.  In this example, we do nothing.
	 */
	private static final class OriginalTypeDigester extends AbstractDigester {
		
		private static final Digester INSTANCE = new OriginalTypeDigester();

		public static Digester getInstance() {
			return INSTANCE;
		}

		private OriginalTypeDigester() {
			// The Digester must declare the types of nodes that it can operate on.  In this case, any
			// node type can be labeled with the original-type flag
			super(OriginalTypeValidationBuilder.keyword, NodeType.OBJECT, NodeType.ARRAY, NodeType.BOOLEAN, NodeType.INTEGER, NodeType.NUMBER, NodeType.STRING);
		}

		@Override
		public JsonNode digest(final JsonNode schema) {
			return schema;
		}
	}
	
	
	/**
	 * This class defines the 
	 * @author msneddon
	 */
	public static final class OriginalTypeKeywordValidator extends AbstractKeywordValidator {
		
		/**
		 * we need to mark kidl mappings at the validation level, because later on we can't distinguish
		 * between structures and mappings in raw json without this.
		 */
		//private boolean isKidlMapping;
		
		public OriginalTypeKeywordValidator(final JsonNode digest) {
			super(OriginalTypeValidationBuilder.keyword);
			
			//JsonNode originalType = digest.get(OriginalTypeValidationBuilder.keyword);

			// this code would allow us to mark a type as "mapping"- it is not currently needed, so this is commented out
			/*if(originalType!=null) {
				if(originalType.asText().equals("kidl-mapping")) {
					isKidlMapping = true;
				} else {
					isKidlMapping = false;
				}
			}*/
		}

		@Override
		public void validate(
				final Processor<FullData, FullData> processor,
				final ProcessingReport report,
				final MessageBundle bundle,
				final FullData data)
						throws ProcessingException {
			// this code would allow us to mark a type as "mapping"- it is not currently needed, so this is commented out
			/*if(isKidlMapping) {
				JsonNode node = data.getInstance().getNode();
				report.info(new ProcessingMessage()
								.setMessage("kidl-mapping-marker")
								.put("location",data.getInstance().getPointer())
								);
			}*/
			return;
		}

		@Override
		public String toString() {
			return "OriginalKeyWordValidator instance";
		}
	}
	
	
	
	/**
	 *
	 */
	private static final class OriginalTypeSyntaxChecker extends AbstractSyntaxChecker {
	
		private static final SyntaxChecker INSTANCE = new OriginalTypeSyntaxChecker();
		
		
		public static SyntaxChecker getInstance() {
			return INSTANCE;
		}
		
		private OriginalTypeSyntaxChecker() {
			// When constructing, the name for the keyword must be provided along with the allowed type for the value
			super(OriginalTypeValidationBuilder.keyword, NodeType.OBJECT, NodeType.ARRAY, NodeType.BOOLEAN, NodeType.INTEGER, NodeType.NUMBER, NodeType.STRING);
		}
		
		@Override
		protected void checkValue(
				final Collection<JsonPointer> pointers,
				final MessageBundle bundle,
				final ProcessingReport report,
				final SchemaTree tree)
						throws ProcessingException
		{
		}
	}
	
	
	
}
