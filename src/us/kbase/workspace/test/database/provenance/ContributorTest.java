package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import static us.kbase.common.test.TestCommon.ES;

import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_PID_LIST;

import us.kbase.workspace.database.provenance.Organization;

import us.kbase.workspace.database.provenance.Contributor;
import us.kbase.workspace.database.provenance.ContributorRole;
import us.kbase.workspace.database.provenance.Contributor.ContributorType;

public class ContributorTest {

	static final String INCORRECT = "incorrect ";
	static final String EXP_EXC = "expected exception";

	// field names
	static final String NAME = "name";
	static final String CREDIT_NAME = "creditName";
	static final String CONTRIB_ID = "contributorID";

	static final String INCORRECT_NAME = INCORRECT + NAME;
	static final String INCORRECT_CREDIT_NAME = INCORRECT + CREDIT_NAME;
	static final String INCORRECT_CONTRIB_ID = INCORRECT + CONTRIB_ID;
	static final String INCORRECT_CONTRIB_TYPE = "incorrect contributor type";
	static final String INCORRECT_AFFILIATIONS = "incorrect affiliations";
	static final String INCORRECT_CONTRIB_ROLES = "incorrect contributor roles";
	static final String INCORRECT_CONTRIB_ROLE_STRINGS = "incorrect contributor role strings";

	// field values
	static final String NAME_STRING = "Insert Name Here";
	static final String CREDIT_NAME_STRING = "Here, IN";
	static final String CONTRIB_ID_STRING = "contrib:ID";

	// error messages
	static final String CONTRIBUTOR_ERROR = "Errors in Contributor construction:\n";
	static final String TITLE_AT_LEAST_ONE = "at least one title must be provided";
	static final String RESOURCE_TYPE_NON_NULL = "resource type cannot be null";
	static final String IDENTIFIER_NON_NULL = "identifier cannot be null or whitespace only";
	static final String CONTRIBUTOR_TYPE_NON_NULL = "contributorType cannot be null or whitespace only";
	static final String NAME_NON_NULL = "name cannot be null or whitespace only";
	static final String CREDIT_NAME_FOR_PEOPLE_ONLY = "the creditName field is only used with contributorType person";

	static final ContributorType CONTRIB_TYPE = ContributorType.PERSON;

	static final Organization ORG_1 = Organization.getBuilder("Ransome the Clown's Emporium of Wonder").build();
	static final Organization ORG_2 = Organization.getBuilder("Pillowtronics").build();
	static final Organization ORG_3 = Organization.getBuilder("Stupendous Brothers Traveling Circus").build();
	static final Organization ORG_4 = Organization.getBuilder("Safely First Savings").build();
	static final Organization ORG_5 = Organization.getBuilder("Pigeon Sisters Plumbing").build();

	static final List<Organization> AFFILIATIONS = Arrays.asList(ORG_1, ORG_2,
			ORG_3);

	static final List<Organization> SINGLE_AFFILIATION = Arrays.asList(ORG_4);

	static final List<Organization> AFFILIATIONS_WITH_NULL = Arrays.asList(ORG_5, null);

	static final List<ContributorRole> ROLES = Arrays.asList(
			ContributorRole.METHODOLOGY,
			ContributorRole.WORK_PACKAGE_LEADER,
			ContributorRole.PROJECT_MANAGER,
			ContributorRole.FUNDING_ACQUISITION,
			ContributorRole.WRITING_ORIGINAL_DRAFT);

	// as above, but with duplicates and nulls
	static final List<ContributorRole> ROLES_WITH_DUPES_NULLS = Arrays.asList(
		ContributorRole.METHODOLOGY,
		ContributorRole.WORK_PACKAGE_LEADER,
		ContributorRole.PROJECT_MANAGER,
		null,
		ContributorRole.PROJECT_MANAGER,
		ContributorRole.FUNDING_ACQUISITION,
		ContributorRole.PROJECT_MANAGER,
		ContributorRole.WRITING_ORIGINAL_DRAFT,
		null,
		null,
		ContributorRole.WRITING_ORIGINAL_DRAFT);

	static final List<String> ROLES_AS_STRINGS = Arrays.asList(
			"CRediT:methodology",
			"DataCite:WorkPackageLeader",
			"DataCite:ProjectManager",
			"CRediT:funding-acquisition",
			"CRediT:writing-original-draft");

	static final List<String> ROLE_INPUT_STRINGS = Arrays.asList(
			"methodology",
			"datacite:work_package_leader",
			"projectmanager",
			"credit:funding_acquisition",
			"writingoriginaldraft");

	static final List<String> ROLE_INPUT_STRINGS_DUPES_NULLS = Arrays.asList(
	"methodology",
			null,
			"datacite:work_package_leader",
			"\r\n\r\n",
			"   projectmanager\n\n",
			"credit:funding_acquisition",
			"",
			"CREDIT:METHODOLOGY",
			"writingoriginaldraft",
			"\n\n",
			"WorkPackageLeader",
			"methodology",
			null);

	static final List<ContributorRole> ALT_ROLES = Arrays.asList(
		ContributorRole.REGISTRATION_AUTHORITY,
		ContributorRole.FUNDING_ACQUISITION
	);

	static final List<String> ALT_ROLES_AS_STRINGS = Arrays.asList(
		"DataCite:RegistrationAuthority",
		"CRediT:funding-acquisition"
	);

	static final List<ContributorRole> ALT_ROLES_WITH_DUPES_NULLS = Arrays.asList(
			ContributorRole.REGISTRATION_AUTHORITY,
			null,
			ContributorRole.REGISTRATION_AUTHORITY,
			ContributorRole.REGISTRATION_AUTHORITY,
			ContributorRole.FUNDING_ACQUISITION,
			ContributorRole.REGISTRATION_AUTHORITY,
			null,
			ContributorRole.FUNDING_ACQUISITION
	);

	static final List<String> ALT_ROLE_INPUT_STRINGS_DUPES_NULLS = Arrays.asList(
			"RegistrationAuthority",
			"\n\n\n\n\n    CRediT:funding-acquisition",
			"    RegistrationAuthority\n\n",
			null,
			"\tREGISTRATION_AUTHORITY\r",
			"",
			"datacite:REGISTRATIONAUTHORITY",
			"  CREDIT:FUNDING_ACQUISITION\n\n",
			"\r\n\r\n",
			"FUNDING_ACQUISITION",
			"funding-acquisition",
			"FundingAcquisition",
			"\t",
			null,
			"Credit:FundingAcquisition"
	);

	// invalid contributor role strings
	static final List<String> INVALID_ROLES = Arrays.asList(
		"magical fairy princess",
		"ContributorRole:FUNDING_ACQUISITION",
		"credit:",
		"datecite:software",
		"credit:workpackageleader"
	);

	static final List<String> INVALID_ROLE_ERRORS = INVALID_ROLES.stream().map(r -> "Invalid contributorRole: " + r)
			.collect(Collectors.toList());


	static final List<String> INVALID_ROLES_WITH_WS_NULL = Arrays.asList(
				"magical fairy princess",
			"    ",
			"ContributorRole:FUNDING_ACQUISITION",
				"credit:",
			"\n\n\n",
			"datecite:software",
				"",
			null,
			"credit:workpackageleader",
			"datecite:software"
	);

	static final Map<String, String> NAME_MAP = ImmutableMap.of(NAME, NAME_STRING);

	static final Map<String, String> NAME_ID_MAP = ImmutableMap.of(NAME, NAME_STRING, CONTRIB_ID,
			CONTRIB_ID_STRING);

	static final Map<String, String> NAME_CREDIT_MAP = ImmutableMap.of(
			NAME, NAME_STRING,
			CREDIT_NAME, CREDIT_NAME_STRING);

	static final Map<String, String> ALL_MAP = ImmutableMap.of(
			CREDIT_NAME, CREDIT_NAME_STRING,
			NAME, NAME_STRING,
			CONTRIB_ID, CONTRIB_ID_STRING);

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(Contributor.class).usingGetClass().verify();
	}

	/**
	 * Check the contributor has the expected fields
	 *
	 * @param contributor
	 *                instance to check
	 * @param ct
	 *                expected contributor type
	 * @param expectedMap
	 *                key/value pairs are the field names and values for the
	 *                string fields that are expected not to be null
	 * @param orgs
	 *                list of Organization objects expected to be returned by
	 *                `getAffiliations`;
	 *                if null, `getAffiliations` should return an empty list
	 * @param roles
	 *                list of ContributorRoles expected to be returned by
	 *                `getContributorRoles`;
	 *                if null, `getContributorRoles` should return an empty list
	 */
	private void assertContributorFields(final Contributor contributor, final ContributorType ct,
			final Map<String, String> expectedMap, final List<Organization> orgs,
			final List<ContributorRole> roles, final List<String> roleStrings) {
		assertThat(INCORRECT_CONTRIB_TYPE, contributor.getContributorType(), is(ct));
		assertThat(INCORRECT_NAME, contributor.getName(), is(expectedMap.get(NAME)));
		assertThat(INCORRECT_CREDIT_NAME, contributor.getCreditName(),
				is(expectedMap.containsKey(CREDIT_NAME) ? opt(expectedMap.get(CREDIT_NAME)) : ES));
		assertThat(INCORRECT_CONTRIB_ID, contributor.getContributorID(),
				is(expectedMap.containsKey(CONTRIB_ID) ? opt(expectedMap.get(CONTRIB_ID)) : ES));

		assertThat(INCORRECT_AFFILIATIONS, contributor.getAffiliations(),
				is(orgs == null ? Collections.emptyList() : orgs));
		assertThat(INCORRECT_CONTRIB_ROLES, contributor.getContributorRoles(),
				is(roles == null ? Collections.emptyList() : roles));
		assertThat(INCORRECT_CONTRIB_ROLE_STRINGS, contributor.getContributorRoleStrings(),
				is(roleStrings == null ? Collections.emptyList() : roleStrings));
	}

	private void assertContributorFields(final Contributor contributor, final ContributorType ct,
			final Map<String, String> expectedMap) {
		assertContributorFields(contributor, ct, expectedMap, null, null, null);
	}

	@Test
	public void buildMinimalWithName() throws Exception {
		for (final ContributorType ct : ContributorType.values()) {
			final Contributor contributor = Contributor.getBuilder(ct, NAME_STRING)
					.build();
			assertContributorFields(contributor, ct, NAME_MAP);
		}
	}

	@Test
	public void buildMinimalWithCreditName() throws Exception {
		final Contributor contributor = Contributor.getBuilder(ContributorType.PERSON, NAME_STRING)
				.withCreditName(CREDIT_NAME_STRING)
				.build();
		assertContributorFields(contributor, ContributorType.PERSON, NAME_CREDIT_MAP);
	}

	@Test
	public void buildMinimalWithNameContribID() throws Exception {
		for (final ContributorType ct : ContributorType.values()) {
			final Contributor contributor = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorID(CONTRIB_ID_STRING)
					.build();
			assertContributorFields(contributor, ct, NAME_ID_MAP);
		}
	}

	@Test
	public void buildMinimalWithNameContributorTypeString() throws Exception {
		for (final ContributorType ct : ContributorType.values()) {
			final String cts = ct.name().toLowerCase();
			final Contributor contributor = Contributor.getBuilder(cts, NAME_STRING)
					.build();
			assertContributorFields(contributor, ct, NAME_MAP);
		}

		final Contributor contributor = Contributor.getBuilder("Organisation", NAME_STRING)
				.build();
		assertContributorFields(contributor, ContributorType.ORGANIZATION, NAME_MAP);
	}

	@Test
	public void buildMinimalWithNameAffiliationNullsContribRoleEmptyList() throws Exception {
		// build the List fields with either an empty list or null
		for (final ContributorType ct : ContributorType.values()) {
			final Contributor contributor1 = Contributor.getBuilder(ct, NAME_STRING)
					.withAffiliations(Collections.emptyList())
					.withContributorRoles(Collections.emptyList())
					.build();
			assertContributorFields(contributor1, ct, NAME_MAP);

			final Contributor contributor2 = Contributor.getBuilder(ct, NAME_STRING)
					.withAffiliations(null)
					.withContributorRoles(null)
					.build();
			assertContributorFields(contributor2, ct, NAME_MAP);

			// using contributorRoleStrings
			final Contributor contributor3 = Contributor.getBuilder(ct, NAME_STRING)
					.withAffiliations(null)
					.withContributorRoleStrings(null)
					.build();
			assertContributorFields(contributor3, ct, NAME_MAP);

			final Contributor contributor4 = Contributor.getBuilder(ct, NAME_STRING)
					// .withName(NAME_STRING)
					.withAffiliations(null)
					.withContributorRoleStrings(Collections.emptyList())
					.build();
			assertContributorFields(contributor4, ct, NAME_MAP);
		}
	}

	@Test
	public void buildMaximal1AffiliationManyRoles() throws Exception {
		final Contributor contributor = Contributor.getBuilder(CONTRIB_TYPE, NAME_STRING)
				.withCreditName(CREDIT_NAME_STRING)
				.withContributorID(CONTRIB_ID_STRING)
				.withAffiliations(SINGLE_AFFILIATION)
				.withContributorRoles(ROLES)
				.build();

		assertContributorFields(contributor, CONTRIB_TYPE, ALL_MAP, SINGLE_AFFILIATION, ROLES, ROLES_AS_STRINGS);
	}

	@Test
	public void buildMaximal3AffiliationsManyRoles() throws Exception {
		final Contributor contributor = Contributor.getBuilder(CONTRIB_TYPE, NAME_STRING)
				.withCreditName(CREDIT_NAME_STRING)
				.withContributorID(CONTRIB_ID_STRING)
				.withAffiliations(AFFILIATIONS)
				.withContributorRoles(ROLES)
				.build();
		assertContributorFields(contributor, CONTRIB_TYPE, ALL_MAP, AFFILIATIONS, ROLES, ROLES_AS_STRINGS);
	}

	@Test
	public void buildMaximalContributorRoleStrings() throws Exception {
		final Contributor contributor = Contributor.getBuilder(CONTRIB_TYPE, NAME_STRING)
				.withCreditName(CREDIT_NAME_STRING)
				.withContributorID(CONTRIB_ID_STRING)
				.withAffiliations(AFFILIATIONS)
				.withContributorRoleStrings(ROLE_INPUT_STRINGS)
				.build();
		assertContributorFields(contributor, CONTRIB_TYPE, ALL_MAP, AFFILIATIONS, ROLES, ROLES_AS_STRINGS);
	}

	@Test
	public void buildAndOverwriteSimpleFields() throws Exception {
		for (final ContributorType ct : ContributorType.values()) {
			for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
				final Contributor contributor = Contributor.getBuilder(ct, NAME_STRING)
						.withCreditName(CREDIT_NAME_STRING)
						.withCreditName(nullOrWs)
						.withContributorID(CONTRIB_ID_STRING)
						.withContributorID(nullOrWs)
						.build();
				assertContributorFields(contributor, ct, NAME_MAP);
			}
		}
	}


	@Test
	public void buildAffiliationsPruneDupesNulls() throws Exception {
		final Contributor contributor = Contributor.getBuilder(ContributorType.ORGANIZATION, NAME_STRING)
				.withAffiliations(
						Arrays.asList(
								ORG_1,
								null,
								ORG_2,
								ORG_1,
								ORG_3,
								ORG_3,
								null,
								ORG_3))
				.build();
		assertContributorFields(contributor, ContributorType.ORGANIZATION, NAME_MAP, AFFILIATIONS, null, null);

		final Contributor contributor2 = Contributor.getBuilder(ContributorType.ORGANIZATION, NAME_STRING)
				.withAffiliations(
						Arrays.asList(
								null,
								null,
								null,
								null,
								null))
				.build();
		assertContributorFields(contributor2, ContributorType.ORGANIZATION, NAME_MAP, null, null, null);
	}



	@Test
	public void buildOverwriteAffiliations() throws Exception {
		for (final ContributorType ct : ContributorType.values()) {
			// affiliations, overwrite with empty list
			final Contributor contributor1 = Contributor.getBuilder(ct, NAME_STRING)
					.withAffiliations(AFFILIATIONS)
					.withAffiliations(Collections.emptyList())
					.build();
			assertContributorFields(contributor1, ct, NAME_MAP);

			// affiliations, overwrite with null
			final Contributor contributor2 = Contributor.getBuilder(ct, NAME_STRING)
					.withAffiliations(AFFILIATIONS)
					.withAffiliations(null)
					.build();
			assertContributorFields(contributor2, ct, NAME_MAP);

			// overwrite list with empty list
			final Contributor contributor3 = Contributor.getBuilder(ct, NAME_STRING)
					.withAffiliations(AFFILIATIONS)
					.withAffiliations(Arrays.asList((Organization) null, null))
					.build();
			assertContributorFields(contributor3, ct, NAME_MAP, null, null, null);

			// overwrite list o' nulls
			final Contributor contributor4 = Contributor.getBuilder(ct, NAME_STRING)
					.withAffiliations(Arrays.asList(null, null))
					.withAffiliations(AFFILIATIONS)
					.build();
			assertContributorFields(contributor4, ct, NAME_MAP, AFFILIATIONS, null, null);
		}
	}

	@Test
	public void buildContributorRolesPruneDupesNulls() throws Exception {
		for (final ContributorType ct : ContributorType.values()) {
			final Contributor contributor1 = Contributor.getBuilder(ct, NAME_STRING)
				.withContributorRoles(ROLES_WITH_DUPES_NULLS)
				.build();
			assertContributorFields(contributor1, ct, NAME_MAP, null, ROLES, ROLES_AS_STRINGS);

			final Contributor contributor2 = Contributor.getBuilder(ct, NAME_STRING)
			.withContributorRoleStrings(ROLE_INPUT_STRINGS_DUPES_NULLS)
			.build();
		assertContributorFields(contributor2, ct, NAME_MAP, null, ROLES, ROLES_AS_STRINGS);

			final Contributor contributor3 = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(WHITESPACE_STRINGS_WITH_NULL)
					.build();
			assertContributorFields(contributor3, ct, NAME_MAP, null, null, null);

		}


	}

	@Test
	public void buildOverwriteAndPruneContributorRoles() throws Exception {
		for (final ContributorType ct : ContributorType.values()) {
			// contrib role strings, empty list
			final Contributor contributor1 = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoleStrings(Collections.emptyList())
					.build();
			assertContributorFields(contributor1, ct, NAME_MAP);

			// contrib role strings, null
			final Contributor contributor2 = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoleStrings(null)
					.build();
			assertContributorFields(contributor2, ct, NAME_MAP);

			// contrib role strings, all are null or ws
			final Contributor contributor3 = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoleStrings(WHITESPACE_STRINGS_WITH_NULL)
					.build();
			assertContributorFields(contributor3, ct, NAME_MAP);

			// contrib roles, empty list
			final Contributor contributor4 = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES)
					.withContributorRoles(Collections.emptyList())
					.build();
			assertContributorFields(contributor4, ct, NAME_MAP);

			// contrib roles, null
			final Contributor contributor5 = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES)
					.withContributorRoles(null)
					.build();
			assertContributorFields(contributor5, ct, NAME_MAP);

			// contrib roles, list of null
			final Contributor contributor6 = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES)
					.withContributorRoles(Arrays.asList((ContributorRole) null, null))
					.build();
			assertContributorFields(contributor6, ct, NAME_MAP);

			// mix and match contributor role and contributor role strings
			// empty list
			final Contributor contributor1m = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES)
					.withContributorRoleStrings(Collections.emptyList())
					.build();
			assertContributorFields(contributor1m, ct, NAME_MAP);

			// null
			final Contributor contributor2m = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES)
					.withContributorRoleStrings(null)
					.build();
			assertContributorFields(contributor2m, ct, NAME_MAP);

			// all are null or ws
			final Contributor contributor3m = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES)
					.withContributorRoleStrings(WHITESPACE_STRINGS_WITH_NULL)
					.build();
			assertContributorFields(contributor3m, ct, NAME_MAP);

			// empty list
			final Contributor contributor4m = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoles(Collections.emptyList())
					.build();
			assertContributorFields(contributor4m, ct, NAME_MAP);

			// null
			final Contributor contributor5m = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoles(null)
					.build();
			assertContributorFields(contributor5m, ct, NAME_MAP);

			// list of nulls
			final Contributor contributor6m = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoles(Arrays.asList((ContributorRole) null, null))
					.build();
			assertContributorFields(contributor6m, ct, NAME_MAP);

			// overwrite with values
			// overwrite role strings with roles
			final Contributor contributor1o = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoles(ALT_ROLES_WITH_DUPES_NULLS)
					.build();
			assertContributorFields(contributor1o, ct, NAME_MAP, null, ALT_ROLES, ALT_ROLES_AS_STRINGS);

			// strings overwrite strings
			final Contributor contributor2o = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoleStrings(ROLE_INPUT_STRINGS)
					.withContributorRoleStrings(ALT_ROLE_INPUT_STRINGS_DUPES_NULLS)
					.build();
			assertContributorFields(contributor2o, ct, NAME_MAP, null, ALT_ROLES, ALT_ROLES_AS_STRINGS);

			// strings overwrite roles
			final Contributor contributor3o = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES_WITH_DUPES_NULLS)
					.withContributorRoleStrings(ALT_ROLE_INPUT_STRINGS_DUPES_NULLS)
					.build();
			assertContributorFields(contributor3o, ct, NAME_MAP, null, ALT_ROLES, ALT_ROLES_AS_STRINGS);

			// roles overwrite roles
			final Contributor contributor4o = Contributor.getBuilder(ct, NAME_STRING)
					.withContributorRoles(ROLES)
					.withContributorRoles(ALT_ROLES_WITH_DUPES_NULLS)
					.build();
			assertContributorFields(contributor4o, ct, NAME_MAP, null, ALT_ROLES, ALT_ROLES_AS_STRINGS);
		}
	}

	/**
	 * Checks the error thrown when a Contributor is built with invalid arguments
	 *
	 * @param builder
	 *                contributor builder, complete with args
	 * @param errorStrings
	 *                list of expected errors
	 */
	private void buildContributorFailWithError(final Contributor.Builder builder, final List<String> errorStrings) {
		try {
			builder.build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					CONTRIBUTOR_ERROR + String.join("\n", errorStrings)));
		}
	}

	/**
	 * Checks the error thrown when a Contributor is built with invalid arguments
	 *
	 * @param builder
	 *                contributor builder, complete with args
	 * @param errorString
	 *                expected error string
	 */
	private void buildContributorFailWithError(final Contributor.Builder builder, final String errorString) {
		try {
			builder.build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					CONTRIBUTOR_ERROR + errorString));
		}
	}

	@Test
	public void buildFailNeedName() throws Exception {
		final List<String> errorStrings = Arrays.asList(NAME_NON_NULL);
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			for (final ContributorType ct : ContributorType.values()) {
				buildContributorFailWithError(
					Contributor.getBuilder(ct, nullOrWs),
					errorStrings);
			}
		}
	}

	@Test
	public void buildFailOrgWithCreditName() throws Exception {
		final List<String> errorStrings = Arrays.asList(CREDIT_NAME_FOR_PEOPLE_ONLY);

		buildContributorFailWithError(
				Contributor.getBuilder(ContributorType.ORGANIZATION, NAME_STRING)
						.withCreditName(CREDIT_NAME_STRING),
				errorStrings);

		buildContributorFailWithError(
				Contributor.getBuilder("ORGANISATION", NAME_STRING)
						.withCreditName(CREDIT_NAME_STRING),
				errorStrings);
	}

	@Test
	public void buildFailInvalidContributorId() {
		for (final String invalidPid : INVALID_PID_LIST) {
			final List<String> errorStrings = Arrays.asList(
					"Illegal format for contributorID: \"" + invalidPid + "\"",
					"It should match the pattern "
							+ "\"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\"");
			for (final ContributorType ct : ContributorType.values()) {
				buildContributorFailWithError(
						Contributor.getBuilder(ct, NAME_STRING)
								.withContributorID(invalidPid),
						errorStrings);
			}
		}
	}

	@Test
	public void buildFailInvalidContributorRoleStrings() {

		for (final ContributorType ct : ContributorType.values()) {
			buildContributorFailWithError(
					Contributor.getBuilder(ct, NAME_STRING)
							.withContributorRoleStrings(INVALID_ROLES),
					INVALID_ROLE_ERRORS);

			// same thing plus whitespace and nulls
			buildContributorFailWithError(
					Contributor.getBuilder(ct, NAME_STRING)
							.withContributorRoleStrings(INVALID_ROLES_WITH_WS_NULL),
					INVALID_ROLE_ERRORS);
		}
	}

	@Test
	public void buildFailErrorCombinations() throws Exception {

		for (final String invalidPid : INVALID_PID_LIST) {
			// all the following entries have an invalid name and contributorID,
			// plus additional extra errors as indicated.
			final List<String> errorStrings = Arrays.asList(
					// no valid name supplied
					NAME_NON_NULL,
					// invalid contributorID error
					"Illegal format for contributorID: \"" + invalidPid + "\"",
					"It should match the pattern "
							+ "\"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\""
					);

			final String errorString = String.join("\n", errorStrings);


			final String[] invalidTypes = {
					"Organisification",
					"personal",
					"Creature from the Black Lagoon"
			};

			// invalid contributorTypes
			for (final String invalidType : invalidTypes) {
				buildContributorFailWithError(
						Contributor.getBuilder(invalidType, null)
								.withContributorID(invalidPid),
						"Invalid contributorType: " + invalidType + "\n" + errorString);
			}

			// invalid contributorTypes: string input, whitespace or null
			for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
				buildContributorFailWithError(
						Contributor.getBuilder(nullOrWs, "\n\n\n\n")
								.withContributorID(invalidPid),
						CONTRIBUTOR_TYPE_NON_NULL + "\n" + errorString);
			}

			// ContributorType null
			buildContributorFailWithError(
					Contributor.getBuilder((ContributorType) null, "")
							.withContributorID(invalidPid),
					"contributorType cannot be null" + "\n" + errorString);

			final String errorWithInvalidRoles = errorString + "\n" + String.join("\n", INVALID_ROLE_ERRORS);
			for (final ContributorType ct : ContributorType.values()) {
				// valid contributorType, invalid contributor role strings
				buildContributorFailWithError(
						Contributor.getBuilder(ct, "")
								.withContributorID(invalidPid)
								.withContributorRoleStrings(INVALID_ROLES),
								errorWithInvalidRoles);

				// same thing plus whitespace and nulls
				buildContributorFailWithError(
						Contributor.getBuilder(ct, null)
							.withContributorID(invalidPid)
							.withContributorRoleStrings(INVALID_ROLES_WITH_WS_NULL),
								errorWithInvalidRoles);
			}
		}
	}
}
