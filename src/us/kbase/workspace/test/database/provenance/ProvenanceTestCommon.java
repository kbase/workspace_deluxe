package us.kbase.workspace.test.database.provenance;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProvenanceTestCommon {
	public static final String NS = null;
	public static final String WHITESPACE = "\n\n    \f     \t\t  \r\n   ";
	public static final String UNICODE_WHITESPACE = "\u2001     \u205F   \u2001";

	public static final List<String> WHITESPACE_STRINGS = Collections.unmodifiableList(Arrays.asList(
			"",
			"   ",
			" \f ",
			"\r",
			"\n",
			" \n \n \n \n \n ",
			UNICODE_WHITESPACE,
			WHITESPACE));

	public static final List<String> WHITESPACE_STRINGS_WITH_NULL = Collections.unmodifiableList(Arrays.asList(
			"",
			"   ",
			NS,
			" \f ",
			"\r",
			"\n",
			" \n \n \n \n \n ",
			WHITESPACE));

}
