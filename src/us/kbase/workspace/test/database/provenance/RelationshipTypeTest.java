package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.RelationshipType;

public class RelationshipTypeTest {

	private static final String INCORRECT_PID = "incorrect PID";

	@Test
	public void testGetPid() throws Exception {
		final RelationshipType rt1 = RelationshipType.OBSOLETES;
		assertThat(INCORRECT_PID,
				rt1.getPid(),
				is("DataCite:Obsoletes"));
		final RelationshipType rt2 = RelationshipType.IS_PREVIOUS_VERSION_OF;
		assertThat(INCORRECT_PID,
				rt2.getPid(),
				is("DataCite:IsPreviousVersionOf"));
		final RelationshipType rt3 = RelationshipType.IS_MANIFESTATION_OF;
		assertThat(INCORRECT_PID,
				rt3.getPid(),
				is("Crossref:IsManifestationOf"));
		final RelationshipType rt4 = RelationshipType.HAS_TRANSLATION;
		assertThat(INCORRECT_PID,
				rt4.getPid(),
				is("Crossref:HasTranslation"));
	}

	@Test
	public void testGetRelationshipType() throws Exception {
		final String[] testDateciteInputs = {
				"DataCite:isOriginalFormOf",
				"    DATACITE:ISOriginalFormOF\n\n",
				"\tis_Original_Form_of\r",
				"IS_Original_Form_OF",
				"isOriginalFormof",
				"datacite:isOriginalFormof",
				"  DataCite:is_Original_Form_of",
				"crossref:isoriginalformof",
				"\t\tCROSSREF:IS_ORIGINAL_FORM_OF\n\n"
		};

		for (final String testInput : testDateciteInputs) {
			assertThat("incorrect role",
					RelationshipType.getRelationshipType(testInput),
					is(RelationshipType.IS_ORIGINAL_FORM_OF));
		}

		final String[] testCrossrefInputs = {
			"hasmanifestation",
			"   \n  has_manifestation   \t\t",
			"CROSSREF:HASMANIFESTATION",
			"  crossref:has_manifestation  "
		};

		for (final String testInput : testCrossrefInputs) {
			assertThat("incorrect role",
					RelationshipType.getRelationshipType(testInput),
					is(RelationshipType.HAS_MANIFESTATION));
		}

	}

	private void getRelationshipTypeFail(final String input, final String error) {
		try {
			RelationshipType.getRelationshipType(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(error));
		}
	}

	@Test
	public void testGetRelationshipTypeFail() throws Exception {

		final String[] invalidTypes = {
				"magical fairy princess",
				"RelationshipType:IS_REVIEWED_BY",
				"crossref:obsoletes",
				"datacite:ismanifestationof"
		};

		for (final String invalidType : invalidTypes) {
			getRelationshipTypeFail(invalidType, "Invalid relationshipType: " + invalidType);
		}

		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			getRelationshipTypeFail(nullOrWs, "relationshipType cannot be null or whitespace only");
		}
	}
}
