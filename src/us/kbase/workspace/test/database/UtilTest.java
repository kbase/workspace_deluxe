package us.kbase.workspace.test.database;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.junit.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import us.kbase.common.test.TestCommon;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.NS;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import us.kbase.workspace.database.Util;

public class UtilTest {

	private static final String EXP_EXC = "expected exception";
	private static final String INCORRECT_NULL_WHITESPACE = "incorrect null or empty";
	private static final String INCORRECT_CHECKSTRING = "incorrect checkString";
	private static final String TYPE_NAME = "some type";

	private static final String FUN_UNICODE_STRING = "❌❉ ⨍∪ℕ ᏬᏁᎥς๏๔є sтя⌽ηg ❉❓❗";


	private static final Long NL = null;
	private static final long VALID_LONG = 1234567890;

	public static final String STRING = "some string of stingy stringy strings strung together";
	public static final String STRING_WITH_WHITESPACE = "\n\n    \f  some string of stingy stringy strings strung together \t  \n";
	public static final String STRING2 = "A Series of Unfortunate Elephants";
	public static final String STRING2_WITH_WHITESPACE = "\n\t   \t  A Series of Unfortunate Elephants\n\n ";

	private static final List<String> NON_WHITESPACE_STRINGS = Arrays.asList(
			STRING,
			STRING2,
			"ab",
			"\n5\n6\n7\n8\n",
			STRING_WITH_WHITESPACE,
			STRING2_WITH_WHITESPACE,
			FUN_UNICODE_STRING);

	private static final List<String> NON_WHITESPACE_STRINGS_WITH_NULL = Arrays.asList(
			STRING,
			STRING2,
			"ab",
			"\n5\n6\n7\n8\n",
			NS,
			STRING_WITH_WHITESPACE,
			STRING2_WITH_WHITESPACE,
			FUN_UNICODE_STRING);

	@Test
	public void xorNameIdPass() throws Exception {
		Util.xorNameId(NS, VALID_LONG, TYPE_NAME);
		Util.xorNameId(STRING, NL, TYPE_NAME);
	}

	@Test
	public void xorNameIdFail() throws Exception {
		try {
			Util.xorNameId(NS, NL, TYPE_NAME);
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Must provide one and only one of some type name (was: null) or id (was: null)"));
		}

		try {
			Util.xorNameId(STRING, VALID_LONG, "a different type");
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Must provide one and only one of a different type name (was: some string of stingy stringy strings strung together) or id (was: 1234567890)"));
		}
	}

	@Test
	public void isNullOrWhitespacePass() throws Exception {
		for (String empty : WHITESPACE_STRINGS_WITH_NULL) {
			assertThat(INCORRECT_NULL_WHITESPACE, Util.isNullOrWhitespace(empty), is(true));
		}
	}

	@Test
	public void isNullOrWhitespaceFail() throws Exception {
		for (String nullOrEmpty : NON_WHITESPACE_STRINGS) {
			assertThat(INCORRECT_NULL_WHITESPACE, Util.isNullOrWhitespace(nullOrEmpty), is(false));
		}
	}

	@Test
	public void nonNullPass() throws Exception {
		final List<String> testList = new ArrayList<>();
		testList.addAll(WHITESPACE_STRINGS);
		testList.addAll(NON_WHITESPACE_STRINGS);

		for (String testString : testList) {
			Util.nonNull(testString, "some message");
		}
	}

	@Test
	public void nonNullFail() throws Exception {
		try {
			Util.nonNull(null, "error message");
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("error message"));
		}
	}

	@Test
	public void noNullsPass() throws Exception {
		Util.noNulls(NON_WHITESPACE_STRINGS, TYPE_NAME);
		Util.noNulls(WHITESPACE_STRINGS, TYPE_NAME);
	}

	@Test
	public void noNullsFail() throws Exception {
		final List<List<String>> testList = new ArrayList<>();
		testList.add(WHITESPACE_STRINGS_WITH_NULL);
		testList.add(NON_WHITESPACE_STRINGS_WITH_NULL);

		for (List<String> testListItem : testList) {
			try {
				Util.noNulls(testListItem, "oh no!");
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new NullPointerException("oh no!"));
			}
		}
	}

	@Test
	public void checkNoNullsOrEmptiesPass() throws Exception {
		Util.checkNoNullsOrEmpties(NON_WHITESPACE_STRINGS, TYPE_NAME);
	}

	@Test
	public void checkNoNullsOrEmptiesFail() throws Exception {
		final List<List<String>> testList = new ArrayList<>();
		testList.add(WHITESPACE_STRINGS);
		testList.add(WHITESPACE_STRINGS_WITH_NULL);
		testList.add(NON_WHITESPACE_STRINGS_WITH_NULL);

		for (List<String> testListItem : testList) {
			try {
				Util.checkNoNullsOrEmpties(testListItem, TYPE_NAME);
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"Null or whitespace only string in collection some type"));
			}
		}
	}

	@Test
	public void checkStringPass() throws Exception {
		final Map<String, String> trimmedNonEmptyStrings = new HashMap<>();
		trimmedNonEmptyStrings.put(STRING, STRING);
		trimmedNonEmptyStrings.put("\n5\n6\n7\n8\n", "5\n6\n7\n8");
		trimmedNonEmptyStrings.put(STRING_WITH_WHITESPACE, STRING);

		for (Map.Entry<String, String> mapElement : trimmedNonEmptyStrings.entrySet()) {
			assertThat(
				INCORRECT_CHECKSTRING,
				Util.checkString(mapElement.getKey(), TYPE_NAME),
				is(mapElement.getValue()));
		}

		final int max = 53;
		for (Map.Entry<String, String> mapElement : trimmedNonEmptyStrings.entrySet()) {
			assertThat(
				INCORRECT_CHECKSTRING,
				Util.checkString(mapElement.getKey(), TYPE_NAME, max),
				is(mapElement.getValue()));
			// max is only taken into account when larger than zero
			assertThat(
				INCORRECT_CHECKSTRING,
				Util.checkString(mapElement.getKey(), TYPE_NAME, 0),
				is(mapElement.getValue()));
			assertThat(
				INCORRECT_CHECKSTRING,
				Util.checkString(mapElement.getKey(), TYPE_NAME, -20),
				is(mapElement.getValue()));
		}

	}

	@Test
	public void checkStringFancyUnicode() throws Exception {

		// FUN_UNICODE_STRING should be 25 codePoints long.
		final int codePointCount = 25;
		assertThat(
			INCORRECT_CHECKSTRING,
			Util.checkString(FUN_UNICODE_STRING, TYPE_NAME, codePointCount),
			is(FUN_UNICODE_STRING));

		// 𝔊 is two characters in length but only one code point
		final String amendedFunString = FUN_UNICODE_STRING + "𝔊";

		assertThat(
			"incorrect length vs codePointCount",
			amendedFunString.length(),
			is(amendedFunString.codePointCount(0, amendedFunString.length()) + 1));

		// the amended string will now fail
		try {
			Util.checkString(amendedFunString, TYPE_NAME, codePointCount);
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"some type size greater than limit 25"));
		}

		// but will pass with size 26
		assertThat(
			INCORRECT_CHECKSTRING,
			Util.checkString(amendedFunString, TYPE_NAME, codePointCount + 1),
			is(amendedFunString));
	}

	@Test
	public void checkStringFailNullOrEmpty() throws Exception {
		for (String emptyNullString : WHITESPACE_STRINGS_WITH_NULL) {
			try {
				Util.checkString(emptyNullString, TYPE_NAME);
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"some type cannot be null or whitespace only"));
			}
		}
	}

	@Test
	public void checkStringPassNullOrEmpty() throws Exception {
		for (String emptyNullString : WHITESPACE_STRINGS_WITH_NULL) {
			assertThat(
				INCORRECT_CHECKSTRING,
				Util.checkString(emptyNullString, TYPE_NAME, true),
				is(NS));
		}
	}

	@Test
	public void checkStringFailTooLong() throws Exception {
		final int max = 1;
		for (String nonEmptyString : NON_WHITESPACE_STRINGS) {
			try {
				Util.checkString(nonEmptyString, TYPE_NAME, max);
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"some type size greater than limit 1"));
			}
		}
	}
}
