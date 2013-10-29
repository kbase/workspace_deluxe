package us.kbase.typedobj.core.validatorconfig;

import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * This class wraps everything required to identify and process the 'searchable' class of annotations.
 * @author msneddon
 */
public class SearchableWsSubsetValidationBuilder {

	/**
	 * Declare the keyword that will be parsed
	 */
	public static final String keyword = "searchable-ws-subset";
	
	/**
	 * Method for generating a Keyword object which can be added to a JSON
	 * Schema Library object.  The Keyword object is initialized with
	 * classes that perform the validation and syntax checking.
	 * @return Keyword
	 */
	public static Keyword getKeyword() {
		final Keyword kbTypeKeyword = 
				Keyword.newBuilder(SearchableWsSubsetValidationBuilder.keyword)
				.withSyntaxChecker(SearchableWsSubsetSyntaxChecker.getInstance())
				.withDigester(SearchableWsSubsetDigester.getInstance())
				.withValidatorClass(SearchableWsSubsetKeywordValidator.class).freeze();
		return kbTypeKeyword;
	}
	
	/**
	 * Method for generating a MessageSource which can be added to a 
	 * ValidationConfiguration so that proper error reporting can be generated
	 * for the new JSON Schema keyword.
	 * @return MessageSource
	 */
	public static MessageSource getErrorMssgSrc() {
		final String key = "searchableError";
		final String value = "error encountered in processing searchable keyword";
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
	private static final class SearchableWsSubsetDigester extends AbstractDigester {
		
		private static final Digester INSTANCE = new SearchableWsSubsetDigester();

		public static Digester getInstance() {
			return INSTANCE;
		}

		private SearchableWsSubsetDigester() {
			// The Digester must declare the types of nodes that it can operate on.  In this case,
			// the searchable fields can only be marked for a typed object
			super(SearchableWsSubsetValidationBuilder.keyword, NodeType.OBJECT);
		}

		@Override
		public JsonNode digest(final JsonNode schema) {
			// we don't really care about the context in this case, we just want the array
			// containing the path to the searchable fields
			return schema.findValue(SearchableWsSubsetValidationBuilder.keyword);
		}
	}
	
	
	/**
	 * This class defines the method that performs the actual validation of the instance.
	 * @author msneddon
	 */
	public static final class SearchableWsSubsetKeywordValidator extends AbstractKeywordValidator {
		
		/**
		 * Store the digested Json Schema node, which has already been digested to include
		 * a list of fields we have to extract
		 */
		private JsonNode searchData;
		
		public SearchableWsSubsetKeywordValidator(final JsonNode digest) {
			super(SearchableWsSubsetValidationBuilder.keyword);
			searchData = digest;
		}

		/**
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
							.setMessage("searchable-ws-subset")
							.put("search-data", searchData)
							);
		}

		@Override
		public String toString() {
			return "SearchableFieldsKeywordValidator";
		}
	}
	
	
	
	/**
	 * This class checks the information in the Json Schema to make sure it is
	 * correct.  It does very little because we assume the the Json Schema is valid
	 * @author msneddon
	 *
	 */
	private static final class SearchableWsSubsetSyntaxChecker extends AbstractSyntaxChecker {
	
		private static final SyntaxChecker INSTANCE = new SearchableWsSubsetSyntaxChecker();
		
		public static SyntaxChecker getInstance() {
			return INSTANCE;
		}
		
		private SearchableWsSubsetSyntaxChecker() {
			// the schema must contain a list of values
			super(SearchableWsSubsetValidationBuilder.keyword, NodeType.OBJECT);
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
