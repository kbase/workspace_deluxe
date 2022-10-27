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
import us.kbase.workspace.database.Util;

public class UtilTest {

    public static final String EXP_EXC = "expected exception";
    public static final String UNEXP_EXC = "unexpected exception";
    public static final String INCORRECT_NULL_EMPTY = "incorrect null or empty";
    public static final String NULL_STRING = null;
    public static final String OK_STRING = "some string of stingy stringy strings strung together";
    public static final String STRING_WITH_SPACES = "\n\n\n   string \t  \n";
    public static final String FUN_UNICODE_STRING = "❌❉ ⨍∪ℕ ᏬᏁᎥς๏๔є sтя⌽ηg ❉❓❗";

    public static final String WHITESPACE = "\n\n         \t\t  \r\n   ";
    public static final Long NULL_LONG = null;
    public static final Long OK_LONG = (long) 123456;

    public static final String TYPE_NAME = "some type";

    private static final List<String> EMPTY_STRINGS = Arrays.asList(
            "",
            "   ",
            "\n",
            WHITESPACE);

    private static final List<String> NON_EMPTY_STRINGS = Arrays.asList(
            OK_STRING,
            "ab",
            "\n5\n6\n7\n8\n",
            STRING_WITH_SPACES,
            FUN_UNICODE_STRING);

    private static final List<String> EMPTY_STRINGS_WITH_NULL = Arrays.asList(
            "",
            "   ",
            NULL_STRING,
            "\n",
            WHITESPACE);

    private static final List<String> NON_EMPTY_STRINGS_WITH_NULL = Arrays.asList(
            OK_STRING,
            "ab",
            "\n5\n6\n7\n8\n",
            NULL_STRING,
            STRING_WITH_SPACES,
            FUN_UNICODE_STRING);

    @Test
    public void xorNameIdPass() throws Exception {
        Util.xorNameId(NULL_STRING, OK_LONG, TYPE_NAME);
        Util.xorNameId(OK_STRING, NULL_LONG, TYPE_NAME);
    }

    @Test
    public void xorNameIdFail() throws Exception {
        try {
            Util.xorNameId(NULL_STRING, NULL_LONG, TYPE_NAME);
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                    "Must provide one and only one of some type name (was: null) or id (was: null)"));
        }

        try {
            Util.xorNameId(OK_STRING, OK_LONG, "a different type");
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
            Util.nonNull(empty, OK_STRING);
        }

        for (String nonEmpty : NON_EMPTY_STRINGS) {
            Util.nonNull(nonEmpty, OK_STRING);
        }
    }

    @Test
    public void nonNullFail() throws Exception {
        try {
            Util.nonNull(null, OK_STRING);
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new NullPointerException(OK_STRING));
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
                Util.noNulls(testListItem, OK_STRING);
                fail(EXP_EXC);
            } catch (Exception got) {
                TestCommon.assertExceptionCorrect(got, new NullPointerException(OK_STRING));
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
        final String INCORRECT_CHECKSTRING = "incorrect checkString";
        final Map<String, String> trimmedNonEmptyStrings = new HashMap<>();
        trimmedNonEmptyStrings.put(OK_STRING, OK_STRING);
        trimmedNonEmptyStrings.put("\n5\n6\n7\n8\n", "5\n6\n7\n8");
        trimmedNonEmptyStrings.put(STRING_WITH_SPACES, "string");

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

        // FUN_UNICODE_STRING should be 25 codePoints long.
        final int codePointCount = 25;
        assertThat(
                INCORRECT_CHECKSTRING,
                Util.checkString(FUN_UNICODE_STRING, TYPE_NAME, codePointCount),
                is(FUN_UNICODE_STRING));
        // force failure by adding a character
        try {
            Util.checkString(FUN_UNICODE_STRING + "⛔", TYPE_NAME, codePointCount);
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                    "some type size greater than limit 25"));
        }
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
