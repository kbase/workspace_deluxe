package us.kbase.workspace.test.database.provenance;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import us.kbase.workspace.database.provenance.ContributorRole;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import us.kbase.common.test.TestCommon;

import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

@Category(us.kbase.common.test.ProvenanceTests.class)
public class ContributorRoleTest {

	@Test
	public void testGetPid() throws Exception {
		final ContributorRole wpl = ContributorRole.WORK_PACKAGE_LEADER;
		assertThat("incorrect PID",
				wpl.getPid(),
			is("DataCite:WorkPackageLeader"));

		final ContributorRole pa = ContributorRole.PROJECT_ADMINISTRATION;
		assertThat("incorrect PID",
				pa.getPid(),
			is("CRediT:project-administration"));
	}

	@Test
	public void testgetContributorRole() throws Exception {
		final String[] testDateciteInputs = {
				"RegistrationAuthority",
				"    RegistrationAuthority\n\n",
				"\tREGISTRATION_AUTHORITY\r",
				"datacite:REGISTRATIONAUTHORITY",
		};

		for (final String testInput : testDateciteInputs) {
			assertThat("incorrect role",
					ContributorRole.getContributorRole(testInput),
					is(ContributorRole.REGISTRATION_AUTHORITY));
		}

		final String[] testCreditInputs = {
				"CRediT:funding-acquisition",
				"  CREDIT:FUNDING_ACQUISITION\n\n",
				"FUNDING_ACQUISITION",
				"funding-acquisition",
				"FundingAcquisition",
				"Credit:FundingAcquisition",
		};

		for (final String testInput : testCreditInputs) {
			assertThat("incorrect role",
					ContributorRole.getContributorRole(testInput),
					is(ContributorRole.FUNDING_ACQUISITION));
		}
	}

	private void getContributorRoleFail(final String input, final String error) {
		try {
			ContributorRole.getContributorRole(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(error));
		}
	}

	@Test
	public void testgetContributorRoleFail() throws Exception {

		final String[] invalidRoles = {
				"magical fairy princess",
				"ContributorRole:FUNDING_ACQUISITION",
				"credit:",
				"datecite:software",
				"credit:workpackageleader",
		};

		for (final String invalidRole : invalidRoles) {
			getContributorRoleFail(invalidRole, "Invalid contributorRole: " + invalidRole);
		}
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			getContributorRoleFail(nullOrWs, "contributorRole cannot be null or whitespace only");
		}
	}
}
