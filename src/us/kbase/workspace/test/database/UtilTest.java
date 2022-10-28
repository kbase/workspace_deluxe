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
import static us.kbase.common.test.TestCommon.L;
import static us.kbase.common.test.TestCommon.NL;
import static us.kbase.common.test.TestCommon.NS;
import static us.kbase.common.test.TestCommon.WHITESPACE;
import static us.kbase.common.test.TestCommon.STRING;
import static us.kbase.common.test.TestCommon.STRING_WITH_WHITESPACE;
import us.kbase.workspace.database.Util;

public class UtilTest {

    private static final String EXP_EXC = "expected exception";
    private static final String INCORRECT_NULL_EMPTY = "incorrect null or empty";
    private static final String INCORRECT_CHECKSTRING = "incorrect checkString";
    private static final String TYPE_NAME = "some type";

    private static final String FUN_UNICODE_STRING = "❌❉ ⨍∪ℕ ᏬᏁᎥς๏๔є sтя⌽ηg ❉❓❗";

    private static final List<String> EMPTY_STRINGS = Arrays.asList(
            "",
            "   ",
            "\n",
            WHITESPACE);

    private static final List<String> NON_EMPTY_STRINGS = Arrays.asList(
            STRING,
            "ab",
            "\n5\n6\n7\n8\n",
            STRING_WITH_WHITESPACE,
            FUN_UNICODE_STRING);

    private static final List<String> EMPTY_STRINGS_WITH_NULL = Arrays.asList(
            "",
            "   ",
            NS,
            "\n",
            WHITESPACE);

    private static final List<String> NON_EMPTY_STRINGS_WITH_NULL = Arrays.asList(
            STRING,
            "ab",
            "\n5\n6\n7\n8\n",
            NS,
            STRING_WITH_WHITESPACE,
            FUN_UNICODE_STRING);

    @Test
    public void xorNameIdPass() throws Exception {
        Util.xorNameId(NS, L, TYPE_NAME);
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
            Util.xorNameId(STRING, L, "a different type");
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                    "Must provide one and only one of a different type name (was: some string of stingy stringy strings strung together) or id (was: 123456)"));
        }
    }

    @Test
    public void isNullOrEmptyPass() throws Exception {
        for (String empty : EMPTY_STRINGS_WITH_NULL) {
            assertThat(INCORRECT_NULL_EMPTY, Util.isNullOrEmpty(empty), is(true));
        }
    }

    @Test
    public void isNullOrEmptyFail() throws Exception {
        for (String nullOrEmpty : NON_EMPTY_STRINGS) {
            assertThat(INCORRECT_NULL_EMPTY, Util.isNullOrEmpty(nullOrEmpty), is(false));
        }
    }

    @Test
    public void nonNullPass() throws Exception {
        for (String empty : EMPTY_STRINGS) {
            Util.nonNull(empty, STRING);
        }

        for (String nonEmpty : NON_EMPTY_STRINGS) {
            Util.nonNull(nonEmpty, STRING);
        }
    }

    @Test
    public void nonNullFail() throws Exception {
        try {
            Util.nonNull(null, STRING);
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new NullPointerException(STRING));
        }
    }

    @Test
    public void noNullsPass() throws Exception {
        Util.noNulls(NON_EMPTY_STRINGS, TYPE_NAME);
        Util.noNulls(EMPTY_STRINGS, TYPE_NAME);
    }

    @Test
    public void noNullsFail() throws Exception {
        final List<List<String>> testList = new ArrayList<>();
        testList.add(EMPTY_STRINGS_WITH_NULL);
        testList.add(NON_EMPTY_STRINGS_WITH_NULL);

        for (List<String> testListItem : testList) {
            try {
                Util.noNulls(testListItem, STRING);
                fail(EXP_EXC);
            } catch (Exception got) {
                TestCommon.assertExceptionCorrect(got, new NullPointerException(STRING));
            }
        }
    }

    @Test
    public void checkNoNullsOrEmptiesPass() throws Exception {
        Util.checkNoNullsOrEmpties(NON_EMPTY_STRINGS, TYPE_NAME);
    }

    @Test
    public void checkNoNullsOrEmptiesFail() throws Exception {
        final List<List<String>> testList = new ArrayList<>();
        testList.add(EMPTY_STRINGS);
        testList.add(EMPTY_STRINGS_WITH_NULL);
        testList.add(NON_EMPTY_STRINGS_WITH_NULL);

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
    public void checkStringFailNullWhitespace() throws Exception {
        for (String emptyNullString : EMPTY_STRINGS_WITH_NULL) {
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
    public void checkStringFailTooLong() throws Exception {
        final int max = 1;
        for (String nonEmptyString : NON_EMPTY_STRINGS) {
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
