package us.kbase.typedobj.core.validatorconfig;

import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class WsSearchableFieldsValidationBuilder {

	/**
	 * Declare the keyword that will be parsed
	 */
	public static final String keyword = "kb-ws-searchable-fields";
	
	/**
	 * Method for generating a Keyword object which can be added to a JSON
	 * Schema Library object.  The Keyword object is initialized with
	 * classes that perform the validation and syntax checking.
	 * @return Keyword
	 */
	public static Keyword getKeyword() {
		final Keyword kbTypeKeyword = 
				Keyword.newBuilder(WsSearchableFieldsValidationBuilder.keyword)
				.withSyntaxChecker(WsSearchableFieldsSyntaxChecker.getInstance())
				.withDigester(WsSearchableFieldsDigester.getInstance())
				.withValidatorClass(WsSearchableFieldsKeywordValidator.class).freeze();
		return kbTypeKeyword;
	}
	
	/**
	 * Method for generating a MessageSource which can be added to a 
	 * ValidationConfiguration so that proper error reporting can be generated
	 * for the new JSON Schema keyword.
	 * @return MessageSource
	 */
	public static MessageSource getErrorMssgSrc() {
		final String key = "wsSearchableFields";
		final String value = "i hath found error in kb-ws-searchable-fields";
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
	private static final class WsSearchableFieldsDigester extends AbstractDigester {
		
		private static final Digester INSTANCE = new WsSearchableFieldsDigester();

		public static Digester getInstance() {
			return INSTANCE;
		}

		private WsSearchableFieldsDigester() {
			// The Digester must declare the types of nodes that it can operate on.  In this case,
			// the searchable fields can only be marked for a typed object
			super(WsSearchableFieldsValidationBuilder.keyword, NodeType.OBJECT);
		}

		@Override
		public JsonNode digest(final JsonNode schema) {
			// we don't really care about the context in this case, we just want the array
			// containing the list of searchable fields
			return schema.findValue(WsSearchableFieldsValidationBuilder.keyword);
		}
	}
	
	
	/**
	 * This class defines the method that performs the actual validation of the instance.
	 * @author msneddon
	 */
	public static final class WsSearchableFieldsKeywordValidator extends AbstractKeywordValidator {
		
		/**
		 * Store the digested Json Schema node, which has already been digested to include
		 * a list of fields we have to extract
		 */
		private ArrayNode fields;
		
		public WsSearchableFieldsKeywordValidator(final JsonNode digest) {
			super(WsSearchableFieldsValidationBuilder.keyword);
			fields = (ArrayNode) digest;
		}

		/**
		 * the validation has only the single purpose of placing the field information into
		 * the processing message so
		 */
		@Override
		public void validate(
				final Processor<FullData, FullData> processor,
				final ProcessingReport report,
				final MessageBundle bundle,
				final FullData data)
						throws ProcessingException
		{
			// assemble the subset object for return
			report.info(new ProcessingMessage()
								.setMessage("ws-searchable-fields-subset")
								.put("fields", fields)
								);
		}

	    @Override
	    public String toString() {
	        return "WsSearchableFieldsKeywordValidator set to validate:" + fields;
	    }
	}
	
	
	
	/**
	 * This class checks the information in the json schema to make sure it is
	 * correct.  If not, we throw an error.
	 * @author msneddon
	 *
	 */
	private static final class WsSearchableFieldsSyntaxChecker extends AbstractSyntaxChecker {
	
		private static final SyntaxChecker INSTANCE = new WsSearchableFieldsSyntaxChecker();
		
		public static SyntaxChecker getInstance() {
			return INSTANCE;
		}
		
		private WsSearchableFieldsSyntaxChecker()
		{
		    // When constructing, the name for the keyword must be provided along with the allowed type for the value
		    super(WsSearchableFieldsValidationBuilder.keyword, NodeType.ARRAY);
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
			//report.warn(newMsg(tree, bundle, "ha! i detected your kb-type"));
	
			// we got here, so we know that in the schema we have properly set the ids as an array, but
			// we have to confirm that all the nodes are 
			
			
//		    final JsonNode node = getNode(tree);
//			System.err.println(node.asDouble());
//			System.err.println("pointers");
//			Iterator<JsonPointer> i = pointers.iterator();
//			JsonPointer p;
//			while(i.hasNext()) {
//				p = i.next();
//				System.out.println(p);
//			}
//			
			
			//newMsg(tree, bundle, "emptyArray");
			
			
			
		    /*
		     * Using AbstractSyntaxChecker as a base, we know that when we reach
		     * this method, the value has already been validated as being of
		     * the allowed primitive types (only array here).
		     *
		     * But this is not enough for this particular validator: we must
		     * also ensure that all elements of this array are integers. Cycle
		     * through all elements of the array and check each element. If we
		     * encounter a non integer argument, add a message.
		     *
		     * We must also check that there is at lease one element, that the
		     * array contains no duplicates and that all elements are positive
		     * integers and strictly greater than 0.
		     *
		     * The getNode() method grabs the value of this keyword for us, so
		     * use that. Note that we also reuse some messages already defined
		     * in SyntaxMessages.
		    final JsonNode node = getNode(tree);
		
		    final int size = node.size();
		
		    if (size == 0) {
		        report.error(newMsg(tree, bundle, "emptyArray"));
		        return;
		    }
		
		    NodeType type;
		    JsonNode element;
		    boolean uniqueItems = true;
		
		    final Set<JsonNode> set = Sets.newHashSet();
		
		    for (int index = 0; index < size; index++) {
		        element = node.get(index);
		        type = NodeType.getNodeType(element);
		        if (type != NodeType.INTEGER)
		            report.error(newMsg(tree, bundle, "incorrectElementType")
		                .put("expected", NodeType.INTEGER)
		                .put("found", type));
		        else if (element.bigIntegerValue().compareTo(BigInteger.ONE) < 0)
		            report.error(newMsg(tree, bundle, "integerIsNegative")
		                .put("value", element));
		        uniqueItems = set.add(element);
		    }
		
		    if (!uniqueItems)
		        report.error(newMsg(tree, bundle, "elementsNotUnique"));
		    */
		}
	}
	
	
	
}
