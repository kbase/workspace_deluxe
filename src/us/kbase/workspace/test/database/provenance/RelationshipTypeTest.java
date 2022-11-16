package us.kbase.workspace.test.database.provenance;

import org.junit.Test;

import us.kbase.workspace.database.provenance.RelationshipType;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import us.kbase.common.test.TestCommon;

import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

public class RelationshipTypeTest {

	@Test
	public void testGetPid() throws Exception {
		final RelationshipType rt1 = RelationshipType.OBSOLETES;
		assertThat("incorrect PID",
				rt1.getPid(),
				is("DataCite:Obsoletes"));
		final RelationshipType rt2 = RelationshipType.IS_PREVIOUS_VERSION_OF;
		assertThat("incorrect PID",
				rt2.getPid(),
				is("DataCite:IsPreviousVersionOf"));
	}

	@Test
	public void testGetRelationshipType() throws Exception {
		final String[] testDateciteInputs = {
				"DataCite:isNewVersionOf",
				"    DATACITE:ISNEWVERSIONOF\n\n",
				"\tis_new_version_of\r",
				"IS_NEW_VERSION_OF",
				"isnewversionof",
				"datacite:isnewversionof",
				"  DataCite:is_new_version_of",
			};

		for (final String testInput : testDateciteInputs) {
			assertThat("incorrect role",
					RelationshipType.getRelationshipType(testInput),
					is(RelationshipType.IS_NEW_VERSION_OF));
		}
	}

	@Test
	public void testGetRelationshipTypeFail() throws Exception {

		final String[] invalidTypes = {
				"magical fairy princess",
				"RelationshipType:IS_REVIEWED_BY",
		};

		for (final String invalidType : invalidTypes) {
			try {
				RelationshipType.getRelationshipType(invalidType);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"Invalid relationship type: " + invalidType));
			}
		}

		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			try {
				RelationshipType.getRelationshipType(nullOrWs);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"input cannot be null or whitespace only"));
			}
		}
	}
}
