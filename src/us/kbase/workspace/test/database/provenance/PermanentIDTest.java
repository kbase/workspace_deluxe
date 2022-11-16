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
import us.kbase.workspace.database.provenance.RelationshipType;

public class PermanentIDTest {

	static final String INCORRECT_PID = "incorrect resolvable PID";
	static final String INCORRECT_DESC = "incorrect description";
	static final String INCORRECT_REL_TYPE = "incorrect relationship type";
	static final String EXP_EXC = "expected exception";

	static final String PID_STRING = "some:permanent ID";
	static final String PID_STRING_UNTRIMMED = "  \n\n some:\npermanent ID\r\r";

	static final String DESC_STRING = "some string of stingy stringy strings strung together";
	static final String DESC_STRING_UNTRIMMED = "\n\n    \f  some string of stingy stringy strings strung together \t  \n";

	static final String REL_STRING = "isVersionOf";
	static final String REL_STRING_UNTRIMMED = " \f\f   \t isVersionOf  \n\r\n";
	static final RelationshipType REL_TYPE = RelationshipType.IS_VERSION_OF;

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(PermanentID.class).usingGetClass().verify();
	}

	private void assertTitleStringAndOptionalNulls(final PermanentID pid, final String pidString) {
		assertThat(INCORRECT_PID, pid.getID(), is(pidString));
		assertThat(INCORRECT_DESC, pid.getDescription(), is(ES));
		assertThat(INCORRECT_REL_TYPE, pid.getRelationshipType(), is(ES));
	}

	@Test
	public void buildMinimal() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey()).build();
			assertTitleStringAndOptionalNulls(pid1, mapElement.getValue());
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
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_TYPE)));
		}
	}

	@Test
	public void buildMaximalWithRelationshipType() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
					.withDescription(DESC_STRING)
					.withRelationshipType(REL_TYPE)
					.build();
			assertThat(INCORRECT_PID, pid1.getID(), is(mapElement.getValue()));
			assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_TYPE)));
		}
	}

	@Test
	public void buildMaximalWithDifferentRelTypeStrings() throws Exception {

		final RelationshipType expectedRelType = RelationshipType.IS_VARIANT_FORM_OF;

		final String[] validRelTypeInputs = {
				"  is_variant_form_of\n\n",
				"DATACITE:IS_VARIANT_FORM_OF",
				"\t\tisvariantformof\r",
				"DATACITE:ISVARIANTFORMOF",
		};

		for (final String relType : validRelTypeInputs) {
			final PermanentID pid1 = PermanentID.getBuilder(PID_STRING_UNTRIMMED)
					.withDescription(DESC_STRING)
					.withRelationshipType(relType)
					.build();
			assertThat(INCORRECT_PID, pid1.getID(), is(PID_STRING));
			assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(expectedRelType)));
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
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_TYPE)));
		}
	}

	@Test
	public void buildWithNullOrWhitespaceDescriptionRelationshipType() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
				final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
						.withDescription(nullOrWs)
						.withRelationshipType(nullOrWs).build();
				assertTitleStringAndOptionalNulls(pid1, mapElement.getValue());
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
				assertTitleStringAndOptionalNulls(pid1, mapElement.getValue());
			}
		}
	}

	@Test
	public void buildAndOverwriteDescriptionRelationshipTypeEnum() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
				final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
						.withDescription(DESC_STRING).withDescription(nullOrWs)
						.withRelationshipType(REL_TYPE).withRelationshipType(nullOrWs)
						.build();
				assertTitleStringAndOptionalNulls(pid1, mapElement.getValue());
			}
			final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey())
					.withDescription(DESC_STRING).withDescription(null)
					.withRelationshipType(REL_TYPE).withRelationshipType((RelationshipType) null)
					.build();
			assertTitleStringAndOptionalNulls(pid1, mapElement.getValue());
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
	public void buildFailNullOrEmptyPID() throws Exception {
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

	@Test
	public void buildFailInvalidRelationshipType() throws Exception {
		try {
			PermanentID.getBuilder("ID:permanent").withRelationshipType("unmutualism").build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Invalid relationship type: unmutualism"));
		}
	}
}
