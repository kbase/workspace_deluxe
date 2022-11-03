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
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_PID_LIST;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_PID_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import us.kbase.workspace.database.provenance.PermanentID;

public class PermanentIDTest {

	static final String INCORRECT_PID = "incorrect resolvable PID";
	static final String INCORRECT_DESC = "incorrect description";
	static final String INCORRECT_REL_TYPE = "incorrect relationship type";
	static final String EXP_EXC = "expected exception";

	static final String DESC_STRING = "some string of stingy stringy strings strung together";
	static final String DESC_STRING_UNTRIMMED = "\n\n    \f  some string of stingy stringy strings strung together \t  \n";

	static final String REL_STRING = "isVersionOf";
	static final String REL_STRING_UNTRIMMED = " \f\f   \t isVersionOf  \n\r\n";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(PermanentID.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey()).build();
			assertThat(INCORRECT_PID, pid1.getID(), is(mapElement.getValue()));
			assertThat(INCORRECT_DESC, pid1.getDescription(), is(ES));
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(ES));
		}
	}

	@Test
	public void buildMaximal() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
					.withDescription(DESC_STRING)
					.withRelationshipType(REL_STRING)
					.build();
			assertThat(INCORRECT_PID, pid1.getID(), is(mapElement.getValue()));
			assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_STRING)));
		}
	}

	@Test
	public void buildTrimAllFields() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
					.withDescription(DESC_STRING_UNTRIMMED)
					.withRelationshipType(REL_STRING_UNTRIMMED)
					.build();
			assertThat(INCORRECT_PID, pid1.getID(), is(mapElement.getValue()));
			assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_STRING)));
		}
	}

	@Test
	public void buildWithNullOrWhitespaceDescriptionRelationshipType() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
				final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
						.withDescription(nullOrWs)
						.withRelationshipType(nullOrWs).build();
				assertThat(INCORRECT_PID, pid1.getID(), is(mapElement.getValue()));
				assertThat(INCORRECT_DESC, pid1.getDescription(), is(ES));
				assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(ES));
			}
		}
	}

	@Test
	public void buildAndOverwriteDescriptionRelationshipType() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
				final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
						.withDescription(DESC_STRING).withDescription(nullOrWs)
						.withRelationshipType(REL_STRING).withRelationshipType(nullOrWs)
						.build();
				assertThat(INCORRECT_PID, pid1.getID(), is(mapElement.getValue()));
				assertThat(INCORRECT_DESC, pid1.getDescription(), is(ES));
				assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(ES));
			}
		}
	}

	@Test
	public void buildFailInvalidPID() throws Exception {
		for (String invalidPid : INVALID_PID_LIST) {
			try {
				PermanentID.getBuilder(invalidPid).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"Illegal format for id: \"" + invalidPid + "\"\n" +
								"It should match the pattern "
								+ "\"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\""));
			}
		}
	}

	@Test
	public void buildFailNullOrEmptyID() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			try {
				PermanentID.getBuilder(nullOrWs).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"id cannot be null or whitespace only"));
			}
		}
	}
}
