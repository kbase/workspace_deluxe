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
			"http://1337.net",
			"http://a.b-c.de",
			"http://223.255.255.254"));


	public static final String INVALID_URL_NO_USER_INFO = "URLs must not contain user and/or password information";

	public static final List<String> INVALID_URL_USER_INFO_LIST = Collections.unmodifiableList(
			Arrays.asList(
					"http://userid:password@example.com:8080",
					"http://userid:password@example.com:8080/",
					"http://userid@example.com",
					"http://userid@example.com/",
					"http://userid@example.com:8080",
					"http://userid@example.com:8080/",
					"http://userid:password@example.com",
					"http://userid:password@example.com/",
					"http://-.~_!$&'()*+,;=:%40:80%2f::::::@example.com"));

	// URLs with no protocol or invalid protocol
	public static final Map<String, String> INVALID_URL_BAD_PROTOCOL_MAP;
	static {
		Map<String, String> invalidUrlMap = new HashMap<>();
		invalidUrlMap.put("h://test.com", "unknown protocol: h");
		invalidUrlMap.put("htpp://test.com", "unknown protocol: htpp");
		invalidUrlMap.put("sftp://test.com", "unknown protocol: sftp");
		invalidUrlMap.put("rdar://1234.com", "unknown protocol: rdar");
		INVALID_URL_BAD_PROTOCOL_MAP = Collections.unmodifiableMap(invalidUrlMap);
	}

	public static final Map<String, String> INVALID_URL_MAP;
	static {
		Map<String, String> invalidUrlMap = new HashMap<>();

		invalidUrlMap.put("HTTPPS://FOO.BAR/", "unknown protocol: httpps");
		invalidUrlMap.put("http:// shouldfail.com",
				"Illegal character in authority at index 7: http:// shouldfail.com");
		invalidUrlMap.put("http://foo.bar?q=Spaces should be encoded",
				"Illegal character in query at index 23: http://foo.bar?q=Spaces should be encoded");
		invalidUrlMap.put("http://foo.bar/foo(bar)baz quux",
				"Illegal character in path at index 26: http://foo.bar/foo(bar)baz quux");
		invalidUrlMap.put("https://kb^ase.us/",
				"Illegal character in authority at index 8: https://kb^ase.us/");
		INVALID_URL_MAP = Collections.unmodifiableMap(invalidUrlMap);
	}

	public static final Map<String, String> ABNORMAL_URL_MAP;
	static {
		Map<String, String> abnormalUrlMap = new HashMap<>();
		abnormalUrlMap.put(
			"\n\n\n   HTTPS://jgi.doe.gov/user-programs/pmo-overview/policies/\t\r\n",
			"https://jgi.doe.gov/user-programs/pmo-overview/policies/");
		abnormalUrlMap.put(
			"ftp://www.example.com/public/images/../../private/illegal_torrents/",
			"ftp://www.example.com/private/illegal_torrents/");

		abnormalUrlMap.put(
				"http://this.is.home/./././com",
				"http://this.is.home/com"
		);

		ABNORMAL_URL_MAP = Collections.unmodifiableMap(abnormalUrlMap);
	}
}
