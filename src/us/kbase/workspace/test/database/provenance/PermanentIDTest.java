package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Map;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import static us.kbase.common.test.TestCommon.opt;
import static us.kbase.common.test.TestCommon.ES;
import static us.kbase.common.test.TestCommon.WHITESPACE;
import static us.kbase.common.test.TestCommon.PID_STRING;
import static us.kbase.common.test.TestCommon.PID_STRING_WITH_WHITESPACE;
import static us.kbase.common.test.TestCommon.INVALID_PID_LIST;
import static us.kbase.common.test.TestCommon.VALID_PID_MAP;
import us.kbase.workspace.database.provenance.PermanentID;

public class PermanentIDTest {

    static final String INCORRECT_PID = "incorrect resolvable PID";
    static final String INCORRECT_DESC = "incorrect description";
    static final String INCORRECT_REL_TYPE = "incorrect relationship type";
    static final String EXP_EXC = "expected exception";

    static final String DESC_STRING = TestCommon.STRING;
    static final String DESC_STRING_UNTRIMMED = TestCommon.STRING_WITH_WHITESPACE;

    static final String REL_STRING = TestCommon.STRING;
    static final String REL_STRING_UNTRIMMED = TestCommon.STRING_WITH_WHITESPACE;

    @Test
    public void equals() throws Exception {
        EqualsVerifier.forClass(PermanentID.class).usingGetClass().verify();
    }

    @Test
    public void buildMinimal() throws Exception {
        for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
            final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey()).build();
            assertThat(INCORRECT_PID, pid1.getId(), is(mapElement.getValue()));
            assertThat(INCORRECT_DESC, pid1.getDescription(), is(ES));
            assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(ES));
        }
    }

    @Test
    public void buildMaximal() throws Exception {
        final PermanentID pid1 = PermanentID.getBuilder(PID_STRING).withDescription(DESC_STRING)
                .withRelationshipType(REL_STRING).build();
        assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
        assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
        assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_STRING)));
    }

    @Test
    public void buildTrimAllFields() throws Exception {
        final PermanentID pid1 = PermanentID.getBuilder(PID_STRING_WITH_WHITESPACE)
                .withDescription(DESC_STRING_UNTRIMMED).withRelationshipType(REL_STRING_UNTRIMMED)
                .build();
        assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
        assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
        assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_STRING)));
    }

    @Test
    public void buildWithWhitespaceDescriptionRelationshipType() throws Exception {
        final PermanentID pid1 = PermanentID.getBuilder(PID_STRING_WITH_WHITESPACE).withDescription(WHITESPACE)
                .withRelationshipType(WHITESPACE).build();
        assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
        assertThat(INCORRECT_DESC, pid1.getDescription(), is(ES));
        assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(ES));
    }

    @Test
    public void buildWithNullDescriptionRelationshipType() throws Exception {
        final PermanentID pid1 = PermanentID.getBuilder(PID_STRING).withDescription(null)
                .withRelationshipType(null).build();
        assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
        assertThat(INCORRECT_DESC, pid1.getDescription(), is(ES));
        assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(ES));
    }

    @Test
    public void buildAndOverwriteDescriptionRelationshipType() throws Exception {
        final PermanentID pid1 = PermanentID.getBuilder(PID_STRING)
                .withDescription(DESC_STRING).withDescription(WHITESPACE)
                .withRelationshipType(REL_STRING).withRelationshipType(WHITESPACE)
                .build();
        assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
        assertThat(INCORRECT_DESC, pid1.getDescription(), is(ES));
        assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(ES));

        final PermanentID pid2 = PermanentID.getBuilder(PID_STRING)
                .withDescription(DESC_STRING).withDescription(null)
                .withRelationshipType(REL_STRING).withRelationshipType(null)
                .build();
        assertThat(INCORRECT_PID, pid2.getId(), is(PID_STRING));
        assertThat(INCORRECT_DESC, pid2.getDescription(), is(ES));
        assertThat(INCORRECT_REL_TYPE, pid2.getRelationshipType(), is(ES));
    }

    @Test
    public void buildFailInvalidPID() throws Exception {
        for (String invalidPid : INVALID_PID_LIST) {
            try {
                PermanentID.getBuilder(invalidPid).build();
                fail(EXP_EXC);
            } catch (Exception got) {
                TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                        "Illegal ID format for id: \"" + invalidPid + "\"\n" +
                        "Permanent IDs should match the pattern \"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\""));
            }
        }
    }

    @Test
    public void buildFailWhitespacePID() throws Exception {
        try {
            PermanentID.getBuilder(WHITESPACE).build();
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                    "id cannot be null or whitespace only"));
        }
    }

    @Test
    public void buildFailNullPID() throws Exception {
        try {
            PermanentID.getBuilder(null).build();
            fail(EXP_EXC);
        } catch (Exception got) {
            TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
                    "id cannot be null or whitespace only"));
        }
    }
}
