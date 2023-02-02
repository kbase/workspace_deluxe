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
		" \n \n \n \n \n ",
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

	public static final List<String> VALID_URL_LIST = Collections.unmodifiableList(Arrays.asList(
		"http://foo.com/blah_blah",
		"http://foo.com/blah_blah/",
		"http://foo.com/blah_blah_(wikipedia)",
		"http://foo.com/blah_blah_(wikipedia)_(again)",
		"http://www.example.com/wpstyle/?p=364",
		"https://www.example.com/foo/?bar=baz&inga=42&quux",
		"http://✪df.ws/123",
		"http://userid:password@example.com:8080",
		"http://userid:password@example.com:8080/",
		"http://userid@example.com",
		"http://userid@example.com/",
		"http://userid@example.com:8080",
		"http://userid@example.com:8080/",
		"http://userid:password@example.com",
		"http://userid:password@example.com/",
		"http://➡.ws/䨹",
		"http://⌘.ws",
		"http://⌘.ws/",
		"http://foo.com/blah_(wikipedia)#cite-1",
		"http://foo.com/blah_(wikipedia)_blah#cite-1",
		"http://foo.com/unicode_(✪)_in_parens",
		"http://foo.com/(something)?after=parens",
		"http://☺.damowmow.com/",
		"http://code.google.com/events/#&product=browser",
		"http://j.mp",
		"ftp://foo.bar/baz",
		"http://foo.bar/?q=Test%20URL-encoded%20stuff",
		"http://مثال.إختبار",
		"http://例子.测试",
		"http://-.~_!$&'()*+,;=:%40:80%2f::::::@example.com",
		"http://1337.net",
		"http://a.b-c.de",
		"http://223.255.255.254"
	));

	public static final Map<String, String> INVALID_URL_MAP;
	static {
		Map<String, String> invalidUrlMap = new HashMap<>();

// Characters can be unsafe for a number of reasons.  The space
// character is unsafe because significant spaces may disappear and
// insignificant spaces may be introduced when URLs are transcribed or
// typeset or subjected to the treatment of word-processing programs.
// The characters "<" and ">" are unsafe because they are used as the
// delimiters around URLs in free text; the quote mark (""") is used to
// delimit URLs in some systems.  The character "#" is unsafe and should
// always be encoded because it is used in World Wide Web and in other
// systems to delimit a URL from a fragment/anchor identifier that might
// follow it.  The character "%" is unsafe because it is used for
// encodings of other characters.  Other characters are unsafe because
// gateways and other transport agents are known to sometimes modify
// such characters. These characters are "{", "}", "|", "\", "^", "~",
// "[", "]", and "`".
		// no protocol / invalid protocol
		// invalidUrlMap.put(":// should fail", "no protocol: :// should fai");
		// invalidUrlMap.put("//", "no protocol: //");
		// invalidUrlMap.put("///", "no protocol: ///");
		// invalidUrlMap.put("///a", "no protocol: ///a");
		// invalidUrlMap.put("//a", "no protocol: //a");
		// invalidUrlMap.put("foo.com", "no protocol: foo.com");
		// invalidUrlMap.put("h://test", "unknown protocol: h");
		// invalidUrlMap.put("rdar://1234", "unknown protocol: rdar");


		invalidUrlMap.put("HTTPPS://FOO.BAR/", "unknown protocol: httpps");
		invalidUrlMap.put("http:// shouldfail.com", "Illegal character in authority at index 7: http:// shouldfail.com");
		invalidUrlMap.put("http://foo.bar?q=Spaces should be encoded", "Illegal character in query at index 23: http://foo.bar?q=Spaces should be encoded");
		invalidUrlMap.put("http://foo.bar/foo(bar)baz quux", "Illegal character in path at index 26: http://foo.bar/foo(bar)baz quux");

		// generated error message is wrong
		// invalidUrlMap.put("http://", "Expected scheme-specific part at index 5: http:");
		// invalidUrlMap.put("http://#", "Expected scheme-specific part at index 5: http:#");
		// invalidUrlMap.put("http://##", "Expected scheme-specific part at index 5: http:##");
		// invalidUrlMap.put("http://##/", "Expected scheme-specific part at index 5: http:##/");

		// these should be invalid but Java thinks they are OK:
		// invalidUrlMap.put("http://??/", "");
		// invalidUrlMap.put("http://-a.b.co", "");
		// invalidUrlMap.put("http://-error-.invalid/", "");
		// invalidUrlMap.put("http://?", "");
		// invalidUrlMap.put("http://??", "");
		// invalidUrlMap.put("http://.", "");
		// invalidUrlMap.put("http://..", "");
		// invalidUrlMap.put("http://../", "");
		// invalidUrlMap.put("http://.www.foo.bar./", "");
		// invalidUrlMap.put("http://.www.foo.bar/", "");
		// invalidUrlMap.put("http:///a", "");
		// invalidUrlMap.put("http://0.0.0.0", "");
		// invalidUrlMap.put("http://3628126748", "");
		// invalidUrlMap.put("http://a.b-.co", "");
		// invalidUrlMap.put("http://www.foo.bar./", "");
		INVALID_URL_MAP = Collections.unmodifiableMap(invalidUrlMap);
	}
}
