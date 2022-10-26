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
    public static final String OK_STRING = "some string of strings strung together";
    public static final Long NULL_LONG = null;
    public static final Long OK_LONG = (long) 123456;

    public static final String TYPE_NAME = "some type";

    protected final List<String> emptyStrings = Arrays.asList(
            "",
            "   ",
            "\n",
            "\n\n         \t\t  \r\n   ");

    protected final List<String> nonEmptyStrings = Arrays.asList(
            OK_STRING,
            "\n5\n6\n7\n8\n",
            "  string ",
            "\n\n\n   string \t  \n");

    protected final List<String> emptyStringsWithNull = Arrays.asList(
            "",
            "   ",
            NULL_STRING,
            "\n",
            "\n\n         \t\t  \r\n   ");

    protected final List<String> nonEmptyStringsWithNull = Arrays.asList(
            OK_STRING,
            "\n5\n",
            NULL_STRING,
            "  string ",
            "\n\n\n   string \t  \n");

    @Test
    public void passXorNameId() throws Exception {
        Util.xorNameId(NULL_STRING, OK_LONG, TYPE_NAME);
        Util.xorNameId(OK_STRING, NULL_LONG, TYPE_NAME);
    }

    @Test
    public void failXorNameId() throws Exception {
        try {
            Util.xorNameId(NULL_STRING, NULL_LONG, TYPE_NAME);
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(String.format(
                    "Must provide one and only one of %s name (was: %s) or id (was: %s)",
                    TYPE_NAME, NULL_STRING, NULL_LONG)));
        }

        try {
            Util.xorNameId(OK_STRING, OK_LONG, TYPE_NAME);
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(String.format(
                    "Must provide one and only one of %s name (was: %s) or id (was: %s)",
                    TYPE_NAME, OK_STRING, OK_LONG)));
        }
    }

    @Test
    public void passIsNullOrEmpty() throws Exception {
        for (String empty : emptyStrings) {
            assertThat(INCORRECT_NULL_EMPTY, Util.isNullOrEmpty(empty), is(true));
        }
        assertThat(INCORRECT_NULL_EMPTY, Util.isNullOrEmpty(NULL_STRING), is(true));
    }

    @Test
    public void failIsNullOrEmpty() throws Exception {
        for (String nullOrEmpty : nonEmptyStrings) {
            assertThat(INCORRECT_NULL_EMPTY, Util.isNullOrEmpty(nullOrEmpty), is(false));
        }
    }

    @Test
    public void passNonNull() throws Exception {
        for (String empty : emptyStrings) {
            Util.nonNull(empty, OK_STRING);
        }

        for (String nonEmpty : nonEmptyStrings) {
            Util.nonNull(nonEmpty, OK_STRING);
        }
    }

    @Test
    public void failNonNull() throws Exception {
        try {
            Util.nonNull(null, OK_STRING);
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new NullPointerException(OK_STRING));
        }
    }

    @Test
    public void passNoNulls() throws Exception {
        Util.noNulls(nonEmptyStrings, TYPE_NAME);
        Util.noNulls(emptyStrings, TYPE_NAME);
    }

    @Test
    public void failNoNulls() throws Exception {
        final List<List<String>> testList = new ArrayList<>();
        testList.add(emptyStringsWithNull);
        testList.add(nonEmptyStringsWithNull);

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
    public void passCheckNoNullsOrEmpties() throws Exception {
        Util.checkNoNullsOrEmpties(nonEmptyStrings, TYPE_NAME);
    }

    @Test
    public void failCheckNoNullsOrEmpties() throws Exception {
        final List<List<String>> testList = new ArrayList<>();
        testList.add(emptyStrings);
        testList.add(emptyStringsWithNull);
        testList.add(nonEmptyStringsWithNull);

        for (List<String> testListItem : testList) {
            try {
                Util.checkNoNullsOrEmpties(testListItem, TYPE_NAME);
                fail(EXP_EXC);
            } catch (Exception got) {
                TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                        "Null or whitespace only string in collection " + TYPE_NAME));
            }
        }
    }

    @Test
    public void passCheckString() throws Exception {
        final String INCORRECT_CHECKSTRING = "incorrect checkString";
        final Map<String, String> trimmedNonEmptyStrings = new HashMap<>();
        for (String nonEmpty : nonEmptyStrings) {
            trimmedNonEmptyStrings.put(nonEmpty, nonEmpty.trim());
        }

        for (Map.Entry<String, String> mapElement : trimmedNonEmptyStrings.entrySet()) {
            assertThat(
                    INCORRECT_CHECKSTRING,
                    Util.checkString(mapElement.getKey(), TYPE_NAME),
                    is(mapElement.getValue()));
        }

        final int max = 100;
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
    public void failCheckStringNullWhitespace() throws Exception {
        for (String emptyNullString : emptyStringsWithNull) {
            try {
                Util.checkString(emptyNullString, TYPE_NAME);
                fail(EXP_EXC);
            } catch (Exception got) {
                TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                        TYPE_NAME + " cannot be null or whitespace only"));
            }
        }
    }

    @Test
    public void failCheckStringTooLong() throws Exception {
        final int max = 1;
        for (String nonEmptyString : nonEmptyStrings) {
            try {
                Util.checkString(nonEmptyString, TYPE_NAME, max);
                fail(EXP_EXC);
            } catch (Exception got) {
                TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                        TYPE_NAME + " size greater than limit " + max));
            }
        }
    }
}
