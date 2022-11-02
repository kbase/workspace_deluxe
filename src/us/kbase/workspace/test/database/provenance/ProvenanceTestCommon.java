package us.kbase.workspace.test.database.provenance;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProvenanceTestCommon {
	public static final String NS = null;
	public static final String WHITESPACE = "\n\n    \f     \t\t  \r\n   ";

	public static final List<String> WHITESPACE_STRINGS = Collections.unmodifiableList(Arrays.asList(
		"",
		"   ",
		" \f ",
		"\r",
		"\n",
		WHITESPACE));

	public static final List<String> WHITESPACE_STRINGS_WITH_NULL = Collections.unmodifiableList(Arrays.asList(
		"",
		"   ",
		NS,
		" \f ",
		"\r",
		"\n",
		WHITESPACE));

	public static final List<String> INVALID_PID_LIST = Collections.unmodifiableList(Arrays.asList(
		// spaces in prefix
		"Too long: didn't read",
		"t\tl:d\tr",
		// no prefix
		":didn't read",
		"\f  :didn't read",
		"\t\t\t      :D\n  \t  ",
		// invalid prefix
		"too-long:didn'tread",
		"'too.long:didnotread'",
		".toolong:didnotread",
		"too\blong:didn't.read",
		// no suffix
		"too long:",
		"too long:\r\n   \f     \n"));

	public static final Map<String, String> VALID_PID_MAP;
	static {
		final String tldr = "TL:DR";
		Map<String, String> validPids = new HashMap<>();
		validPids.put(tldr, tldr);
		validPids.put("\r\n  TL:DR    \n\n\t ", tldr);
		validPids.put("Too.Long:Didn't.Read", "Too.Long:Didn't.Read");
		validPids.put("  toolong : didntread  ", "toolong:didntread");
		validPids.put("\n2.long: didn't read\n", "2.long:didn't read");
		validPids.put("too.long:\n\ndidn't.read", "too.long:didn't.read");
		validPids.put("tl\n\n  :d\tr\n\n  ", "tl:d\tr");
		validPids.put("tl: (┛ಠДಠ)┛彡┻━┻ ", "tl:(┛ಠДಠ)┛彡┻━┻");
		validPids.put(" https://this.is.the.url/ ", "https://this.is.the.url/");
		VALID_PID_MAP = Collections.unmodifiableMap(validPids);
	}
}
