package us.kbase.typedobj.core.validatorconfig;

import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.keyword.digest.AbstractDigester;
import com.github.fge.jsonschema.keyword.digest.Digester;
import com.github.fge.jsonschema.keyword.validator.AbstractKeywordValidator;
import com.github.fge.jsonschema.library.Keyword;
import com.github.fge.jsonschema.processing.Processor;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.syntax.checkers.AbstractSyntaxChecker;
import com.github.fge.jsonschema.syntax.checkers.SyntaxChecker;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.source.MapMessageSource;
import com.github.fge.msgsimple.source.MessageSource;


/**
 * Example code to add additional custom keywords to a JSON Schema document.
 * @author msneddon
 */
public class KBTypeValidationBuilder {

	/**
	 * Declare the keyword that will be parsed
	 */
	public static final String keyword = "kb-type";
	
	/**
	 * Method for generating a Keyword object which can be added to a JSON
	 * Schema Library object.  The Keyword object is initialized with
	 * classes that perform the validation and syntax checking.
	 * @return Keyword
	 */
	public static Keyword getKeyword() {
		final Keyword kbTypeKeyword = 
				Keyword.newBuilder(KBTypeValidationBuilder.keyword)
	            	.withSyntaxChecker(KBTypeSyntaxChecker.getInstance())
	            	.withDigester(KBTypeDigester.getInstance())
	            	.withValidatorClass(KBTypeKeywordValidator.class).freeze();
		return kbTypeKeyword;
	}
	
	/**
	 * Method for generating a MessageBundle which can be added to a 
	 * ValidationConfiguration so that proper error reporting can be generated
	 * for the new JSON Schema keyword.
	 * @return MessageSource
	 */
	public static MessageSource getErrorMssgSrc() {
		final String key = "kbTypeError";
        final String value = "i hath found error in kb-type";
        final MessageSource source = MapMessageSource.newBuilder()
            .put(key, value).build();
        return source;
	}
	
	
	
	
	
	
	
	
	/**
	 * Define the Digester, which can process the JSON schema context where the
	 * keyword was defined.  It consumes a JsonNode object containing the schema
	 * document at the node where the keyword is identified, and returns a
	 * simplified JsonNode object that can make validation later easier if
	 * needed.  In this example, we do nothing.
	 * @author msneddon
	 */
	private static final class KBTypeDigester extends AbstractDigester {
		
		private static final Digester INSTANCE = new KBTypeDigester();

	    public static Digester getInstance() {
	        return INSTANCE;
	    }

	    private KBTypeDigester() {
	    	// The Digester must declare the types of nodes that it can operate on.  In this case, any
	    	// node type can be labeled with the kb-type flag
	        super(KBTypeValidationBuilder.keyword, NodeType.OBJECT, NodeType.ARRAY, NodeType.BOOLEAN, NodeType.INTEGER, NodeType.NUMBER, NodeType.STRING);
	    }

	    @Override
	    public JsonNode digest(final JsonNode schema) {
	    	//System.err.println("I just digested something, yo!:"+schema);
	        return schema;
	    }
	}
	
	
	/**
	 * This class defines the 
	 * @author msneddon
	 */
	public static final class KBTypeKeywordValidator extends AbstractKeywordValidator {
		
	    public KBTypeKeywordValidator(final JsonNode digest) {
	        super(KBTypeValidationBuilder.keyword);
	    }

	    @Override
	    public void validate(
	    		final Processor<FullData, FullData> processor,
	    		final ProcessingReport report,
	    		final MessageBundle bundle,
	    		final FullData data)
	    				throws ProcessingException {

	    	//System.err.println("calling the keyword validator");
	    	
	    	//report.warn(newMsg(data, bundle, "i be validat'n"));
	    	return;
	    }

	    @Override
	    public String toString() {
	        return "i am a kb-type validator, but you prob wanted more than that";
	    }
	}
	
	
	
	/**
	 * Finally, this class performs the actual value checking.
	 * @author msneddon
	 *
	 */
	private static final class KBTypeSyntaxChecker extends AbstractSyntaxChecker {
	
		private static final SyntaxChecker INSTANCE = new KBTypeSyntaxChecker();
		
		public static SyntaxChecker getInstance() {
			return INSTANCE;
		}
		
		private KBTypeSyntaxChecker()
		{
		    // When constructing, the name for the keyword must be provided along with the allowed type for the value
		    super(KBTypeValidationBuilder.keyword, NodeType.OBJECT, NodeType.ARRAY, NodeType.BOOLEAN, NodeType.INTEGER, NodeType.NUMBER, NodeType.STRING);
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
	
		    /*final JsonNode node = getNode(tree);
			System.err.println(node.asDouble());
			System.err.println("pointers");
			Iterator<JsonPointer> i = pointers.iterator();
			JsonPointer p;
			while(i.hasNext()) {
				p = i.next();
				System.out.println(p);
			}
			newMsg(tree, bundle, "emptyArray");
			*/
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
