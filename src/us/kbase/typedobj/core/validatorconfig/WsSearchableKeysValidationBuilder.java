package us.kbase.typedobj.core.validatorconfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * This class wraps everything required to identify report ws-searchable fields.
 * @author msneddon
 */
public class WsSearchableKeysValidationBuilder {

	/**
	 * Declare the keyword that will be parsed
	 */
	public static final String keyword = "kb-ws-searchable-keys";
	
	/**
	 * Method for generating a Keyword object which can be added to a JSON
	 * Schema Library object.  The Keyword object is initialized with
	 * classes that perform the validation and syntax checking.
	 * @return Keyword
	 */
	public static Keyword getKeyword() {
		final Keyword kbTypeKeyword = 
				Keyword.newBuilder(WsSearchableKeysValidationBuilder.keyword)
				.withSyntaxChecker(WsSearchableKeysSyntaxChecker.getInstance())
				.withDigester(WsSearchableKeysDigester.getInstance())
				.withValidatorClass(WsSearchableKeysKeywordValidator.class).freeze();
		return kbTypeKeyword;
	}
	
	/**
	 * Method for generating a MessageSource which can be added to a 
	 * ValidationConfiguration so that proper error reporting can be generated
	 * for the new JSON Schema keyword.
	 * @return MessageSource
	 */
	public static MessageSource getErrorMssgSrc() {
		final String key = "wsSearchableKeys";
		final String value = "i hath found error in kb-ws-searchable-keys";
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
	private static final class WsSearchableKeysDigester extends AbstractDigester {
		
		private static final Digester INSTANCE = new WsSearchableKeysDigester();

		public static Digester getInstance() {
			return INSTANCE;
		}

		private WsSearchableKeysDigester() {
			// The Digester must declare the types of nodes that it can operate on.  In this case,
			// the searchable fields can only be marked for a typed object
	        super(WsSearchableKeysValidationBuilder.keyword, NodeType.OBJECT);
	    }

	    @Override
	    public JsonNode digest(final JsonNode schema) {
	    	// we don't really care about the context in this case, we just want the array
	        // containing the list of searchable fields
	        return schema.findValue(WsSearchableKeysValidationBuilder.keyword);
	    }
	}
	
	
	/**
	 * This class defines the method that performs the actual validation of the instance.
	 * @author msneddon
	 */
	public static final class WsSearchableKeysKeywordValidator extends AbstractKeywordValidator {
		
		/**
		 * Store the digested Json Schema node, which has already been digested to include
		 * a list of fields we have to extract
		 */
		private ArrayList<String> fieldList;
		
		public WsSearchableKeysKeywordValidator(final JsonNode digest) {
			super(WsSearchableKeysValidationBuilder.keyword);
			fieldList = new ArrayList<String>(digest.size());
			Iterator <JsonNode> iter = digest.elements();
			while(iter.hasNext()) {
				fieldList.add(iter.next().asText());
			}
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
			
			// extract out everything specified as a field
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode keys_of = mapper.createObjectNode();
			
			for(int k=0; k<fieldList.size(); k++) {
				String field_name = fieldList.get(k);
				
				// here is where a field name may be split to reference  keys_of nested fields
				// but for now we don't support this
				//field_name.split(".");
				
				JsonNode kbase_mapping = node.findValue(field_name);
				Iterator <String> keys = kbase_mapping.fieldNames();
				ArrayNode keys_list = mapper.createArrayNode();
				while(keys.hasNext()) {
					String key = keys.next();
					keys_list.add(key);
				}
				keys_of.put(field_name, keys_list);
			}
			
			// assemble the subset object for return
			ProcessingMessage pm = new ProcessingMessage()
											.setMessage("ws-searchable-keys-subset")
											.put("keys_of", keys_of);
			report.info(pm);
			
			return;
		}

		@Override
		public String toString() {
			return "WsSearchableFieldsKeywordValidator set to validate:" + fieldList;
		}
	}
	
	
	
	/**
	 * This class checks the information in the Json Schema to make sure it is
	 * correct.  It does very little because we assume the the Json Schema is valid
	 * @author msneddon
	 *
	 */
	private static final class WsSearchableKeysSyntaxChecker extends AbstractSyntaxChecker {
	
		private static final SyntaxChecker INSTANCE = new WsSearchableKeysSyntaxChecker();
		
		public static SyntaxChecker getInstance() {
			return INSTANCE;
		}
		
		private WsSearchableKeysSyntaxChecker() {
			// the schema must contain a list of values
			super(WsSearchableKeysValidationBuilder.keyword, NodeType.ARRAY);
		}
		
		@Override
		protected void checkValue(
				final Collection<JsonPointer> pointers,
				final MessageBundle bundle,
				final ProcessingReport report,
				final SchemaTree tree)
						throws ProcessingException
		{
			// we assume that the Json Schema has already been validated
		}
	}
	
	
	
}
