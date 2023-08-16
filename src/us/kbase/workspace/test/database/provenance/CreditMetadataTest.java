package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.ES;
import static us.kbase.common.test.TestCommon.opt;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.ABNORMAL_URL_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_PID_LIST;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_BAD_PROTOCOL_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_NO_USER_INFO;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_USER_INFO_LIST;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_PID_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_URL_LIST;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.Contributor;
import us.kbase.workspace.database.provenance.Contributor.ContributorType;
import us.kbase.workspace.database.provenance.CreditMetadata;
import us.kbase.workspace.database.provenance.CreditMetadata.ResourceType;
import us.kbase.workspace.database.provenance.Event;
import us.kbase.workspace.database.provenance.EventDate;
import us.kbase.workspace.database.provenance.FundingReference;
import us.kbase.workspace.database.provenance.Organization;
import us.kbase.workspace.database.provenance.PermanentId;
import us.kbase.workspace.database.provenance.Title;

public class CreditMetadataTest {

	private static final String INCORRECT = "incorrect ";
	private static final String NO_MUTATION = "no mutation";
	// field names
	private static final String COMMENTS = "comments";
	private static final String ID = "identifier";
	private static final String DESCRIPTION = "description";
	private static final String LICENSE = "license";
	private static final String VERSION = "version";
	private static final String REPO = "repository";
	private static final String TYPE = "resource type";
	private static final String CONTRIBUTORS = "contributors";
	private static final String DATES = "dates";
	private static final String FUNDING = "funding";
	private static final String RELATED_IDS = "related identifiers";
	private static final String TITLES = "titles";

	// test error strings
	private static final String INCORRECT_COMMENTS = INCORRECT + COMMENTS;
	private static final String INCORRECT_ID = INCORRECT + ID;
	private static final String INCORRECT_DESCRIPTION = INCORRECT + DESCRIPTION;
	private static final String INCORRECT_LICENSE = INCORRECT + LICENSE;
	private static final String INCORRECT_TYPE = INCORRECT + TYPE;
	private static final String INCORRECT_REPO = INCORRECT + REPO;
	private static final String INCORRECT_VERSION = INCORRECT + VERSION;
	private static final String INCORRECT_CONTRIBUTORS = INCORRECT + CONTRIBUTORS;
	private static final String INCORRECT_DATES = INCORRECT + DATES;
	private static final String INCORRECT_FUNDING = INCORRECT + FUNDING;
	private static final String INCORRECT_RELATED_IDS = INCORRECT + RELATED_IDS;
	private static final String INCORRECT_TITLES = INCORRECT + TITLES;

	// error messages
	private static final String CONTRIBUTOR_AT_LEAST_ONE = "at least one contributor must be provided";
	private static final String IDENTIFIER_NON_NULL_WS = "identifier cannot be null or whitespace only";
	private static final String REPO_NON_NULL = "repository cannot be null";
	private static final String RESOURCE_TYPE_NON_NULL = "resourceType cannot be null";
	private static final String RESOURCE_TYPE_NON_WS_NULL = "resourceType cannot be null or whitespace only";
	private static final String TITLE_AT_LEAST_ONE = "at least one title must be provided";
	private static final String VERSIONING_DATES_REQUIRED = "must provide either 'version' " +
			"or one or more 'dates', ideally indicating when the resource was " +
			"published or when it was last updated";
	private static final String LICENSE_URL_ILLEGAL = "Illegal license url '";
	private static final String ILLEGAL_ID_BEFORE = "Illegal format for identifier: \"";
	private static final String ILLEGAL_ID_AFTER = "\"\nIt should match the pattern " +
			"\"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\"";

	// field values
	private static final String IDENTIFIER_STRING = "SOME:identifier";
	private static final ResourceType RESOURCE_TYPE = ResourceType.DATASET;
	private static final String RESOURCE_TYPE_STRING = "dataset";

	private static final String DESC_STRING = "World's worst description.";
	private static final String DESC_STRING_UNTRIMMED = "\t\tWorld's worst description.\r\n  \r\n";
	private static final String LICENSE_STRING = "Apache 2.0";
	private static final String LICENSE_STRING_UNTRIMMED = "    \n \n Apache 2.0 \t\t  ";
	private static final String VERSION_STRING = "1.2.3.4.c";
	private static final String VERSION_STRING_UNTRIMMED = "\n\r\n1.2.3.4.c";

	private static final Map<String, ResourceType> VALID_RESOURCE_TYPE_MAP = ImmutableMap.of(
			"Dataset", ResourceType.DATASET,
			"DATA_SET", ResourceType.DATASET,
			"\t\rdAtA SeT \n\n", ResourceType.DATASET
	);

	private static final String[] INVALID_RESOURCE_TYPE_STRINGS = {
			"data sets",
			TYPE,
			"DATAZET"
	};

	private static final List<String> COMMENTS_LIST = Arrays.asList(
			"An Airport For Aliens Currently Run By Dogs",
			"Space Warlord Organ Trading Simulator",
			"El Paso Elsewhere");

	private static final List<String> COMMENTS_LIST_UNTRIMMED = Arrays.asList(
			"\n\n\n\t\t",
			"\n\nAn Airport For Aliens Currently Run By Dogs\n\n\n\t\f",
			null,
			"\n\n",
			"Space Warlord Organ Trading Simulator\t\t\r\f\n\r",
			"An Airport For Aliens Currently Run By Dogs",
			"\t\tEl Paso Elsewhere\n\n   ");

	private static final Contributor C1 = Contributor.getBuilder(
			ContributorType.PERSON, "Mr Blobby")
			.build();

	private static final Contributor C2 = Contributor.getBuilder(
			ContributorType.ORGANIZATION, "Cereal Convention")
			.build();

	private static final Contributor C3 = Contributor.getBuilder(
			ContributorType.PERSON, "Ransome the Clown")
			.build();

	private static final List<Contributor> CONTRIBUTOR_LIST = Arrays.asList(C1, C2);
	private static final List<Contributor> CONTRIBUTOR_LIST_DUPES_NULLS = Arrays.asList(
			C1, null, null, C2, C2, C2);

	private static final EventDate ED1 = EventDate.build("0000-12-15", Event.CREATED);
	private static final EventDate ED2 = EventDate.build("2112", Event.WITHDRAWN);
	private static final EventDate ED3 = EventDate.build("2000-01-01", Event.AVAILABLE);

	private static final List<EventDate> DATE_LIST = Arrays.asList(ED1, ED2);
	private static final List<EventDate> DATE_LIST_DUPES_NULLS = Arrays.asList(
			ED1, ED2, null, ED1, ED2, null, ED1, ED2, null);

	private static final Organization ORG_1 = Organization.getBuilder("Ransome the Clown's Emporium of Wonder").build();
	private static final Organization ORG_2 = Organization.getBuilder("Pillowtronics").build();

	private static final FundingReference F1 = FundingReference
			.getBuilder(ORG_1).build();
	private static final FundingReference F2 = FundingReference
			.getBuilder(ORG_2).build();

	private static final List<FundingReference> FUNDING_LIST = Arrays.asList(F1);
	private static final List<FundingReference> FUNDING_LIST_DUPES_NULLS = Arrays.asList(
			F1, F1, F1, F1, F1, F1, F1, null);

	private static final PermanentId PID1 = PermanentId.getBuilder("this:ID").build();
	private static final PermanentId PID2 = PermanentId.getBuilder("that:ID").build();
	private static final PermanentId PID3 = PermanentId.getBuilder("the:other ID").build();
	private static final List<PermanentId> RELATED_ID_LIST = Arrays.asList(PID1, PID2);
	private static final List<PermanentId> RELATED_ID_LIST_DUPES_NULLS = Arrays.asList(
			PID1, PID2, PID1, PID2, null, PID2, PID2, PID1);

	// titles
	private static final Title T1 = Title.getBuilder(
			"\t\t\f\t\n\rA Series of Unfortunate Elephants\n\n\n   ")
			.build();
	private static final Title T2 = Title.getBuilder(
			"Eine Reihe unglücklicher Elefanten")
			.withTitleLanguage("de")
			.withTitleType("translated_title")
			.build();

	private static final Title T3 = Title.getBuilder("Elephants Gone Wild!")
			.withTitleType("subtitle")
			.build();

	private static final List<Title> TITLE_LIST = Arrays.asList(T1, T2);
	private static final List<Title> TITLE_LIST_DUPES_NULLS = Arrays.asList(
			null, T1, null, null, null, T1, T2);

	private static final List<String> ELS = Collections.emptyList();
	private static final List<Contributor> ELC = Collections.emptyList();
	private static final List<EventDate> ELD = Collections.emptyList();
	private static final List<FundingReference> ELF = Collections.emptyList();
	private static final List<PermanentId> ELRI = Collections.emptyList();
	private static final List<Title> ELT = Collections.emptyList();

	private static final String EXP_EXC = "expected exception";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(CreditMetadata.class).usingGetClass().verify();
	}

	@Test
	public void getResourceTypePass() throws Exception {
		for (Map.Entry<String, ResourceType> entry : VALID_RESOURCE_TYPE_MAP.entrySet()) {
			assertThat(INCORRECT_TYPE, ResourceType.getResourceType(entry.getKey()),
					is(entry.getValue()));
		}
	}

	@Test
	public void getResourceTypeFail() throws Exception {
		for (final String rt : INVALID_RESOURCE_TYPE_STRINGS) {
			try {
				ResourceType.getResourceType(rt);
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"Invalid resourceType: " + rt));
			}
		}
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			try {
				ResourceType.getResourceType(nullOrWs);
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						RESOURCE_TYPE_NON_WS_NULL));
			}
		}
	}

	/**
	 * Check the CreditMetadata has the expected fields
	 *
	 * @param cm
	 *                instance to check
	 * @param identifier
	 *                expected resource ID
	 * @param repository
	 *                expected repository
	 * @param rt
	 *                expected ResourceType
	 * @param contributors
	 *                list of Contributor objects expected to be returned by
	 *                `getContributors`;
	 * @param titles
	 *                list of Title objects expected to be returned by
	 *                `getTitles`;
	 * @param description
	 *                optional string returned by `getDescription`
	 * @param license
	 *                optional string returned by `getLicense`
	 * @param version
	 *                optional string returned by `getVersion`
	 * @param comments
	 *                list of strings expected to be returned by `getComments`;
	 * @param dates
	 *                list of EventDates expected to be returned by
	 *                `getDates`;
	 *                if null, should return an empty list
	 * @param funding
	 *                list of FundingReferences expected to be returned by
	 *                `getFunding`;
	 *                if null, should return an empty list
	 * @param relatedIdentifiers
	 *                list of PermanentIds expected to be returned by
	 *                `getRelatedIdentifiers`;
	 *                if null, should return an empty list
	 */
	private void assertCreditMetadataFields(final CreditMetadata cm,
			final String identifier,
			final Organization repository,
			final ResourceType rt,
			final List<Contributor> contributors,
			final List<Title> titles,
			final Optional<String> description,
			final Optional<String> license,
			final Optional<String> version,
			final List<String> comments,
			final List<EventDate> dates,
			final List<FundingReference> funding,
			final List<PermanentId> relatedIdentifiers) {

		assertThat(INCORRECT_ID, cm.getIdentifier(), is(identifier));
		assertThat(INCORRECT_REPO, cm.getRepository(), is(repository));
		assertThat(INCORRECT_TYPE, cm.getResourceType(), is(rt));
		assertThat(INCORRECT_COMMENTS, cm.getComments(),
				is(comments));
		assertThat(INCORRECT_CONTRIBUTORS, cm.getContributors(),
				is(contributors));
		assertThat(INCORRECT_DATES, cm.getDates(),
				is(dates));
		assertThat(INCORRECT_FUNDING, cm.getFunding(),
				is(funding));
		assertThat(INCORRECT_RELATED_IDS, cm.getRelatedIdentifiers(),
				is(relatedIdentifiers));
		assertThat(INCORRECT_TITLES, cm.getTitles(),
				is(titles));
		assertThat(INCORRECT_DESCRIPTION, cm.getDescription(), is(description));
		assertThat(INCORRECT_LICENSE, cm.getLicense(), is(license));
		assertThat(INCORRECT_VERSION, cm.getVersion(), is(version));
	}

	private void assertCreditMetadataFields(
			final CreditMetadata cm,
			final String identifier,
			final Organization repository,
			final ResourceType rt,
			final List<Contributor> contributors,
			final List<Title> titles,
			final Optional<String> description,
			final Optional<String> license,
			final Optional<String> version) {
		assertCreditMetadataFields(cm, identifier, repository, rt, contributors, titles,
				description, license, version, ELS, ELD, ELF, ELRI);
	}

	@Test
	public void buildMinimal() throws Exception {
		for (final ResourceType rt : ResourceType.values()) {
			for (Map.Entry<String, String> entry : VALID_PID_MAP.entrySet()) {
				final CreditMetadata cm = CreditMetadata
						.getBuilder(entry.getKey(), ORG_1, rt,
								CONTRIBUTOR_LIST, TITLE_LIST)
						.withVersion(VERSION_STRING)
						.build();
				assertCreditMetadataFields(cm, entry.getValue(), ORG_1, rt,
						CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, opt(VERSION_STRING));
			}
		}
	}

	@Test
	public void buildMinimalResourceTypeStrings() throws Exception {
		for (Map.Entry<String, ResourceType> rtes : VALID_RESOURCE_TYPE_MAP.entrySet()) {
			for (Map.Entry<String, String> entry : VALID_PID_MAP.entrySet()) {
				final CreditMetadata cm = CreditMetadata
						.getBuilder(entry.getKey(), ORG_1, rtes.getKey(),
								CONTRIBUTOR_LIST, TITLE_LIST)
						.withVersion(VERSION_STRING)
						.build();
				assertCreditMetadataFields(cm, entry.getValue(), ORG_1,
						rtes.getValue(), CONTRIBUTOR_LIST, TITLE_LIST,
						ES, ES, opt(VERSION_STRING));
			}
		}
	}


	@Test
	public void buildCleanAndTrimComments() throws Exception {
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withComments(COMMENTS_LIST_UNTRIMMED)
				.withVersion(VERSION_STRING_UNTRIMMED)
				.build();

		assertCreditMetadataFields(cm, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, opt(VERSION_STRING),
				COMMENTS_LIST, ELD, ELF, ELRI);
	}

	@Test
	public void buildCleanURLTypeLicense() throws Exception {
		for (final String licenseURL : VALID_URL_LIST) {
			final CreditMetadata cm2 = CreditMetadata
					.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withLicense(licenseURL)
					.withDates(DATE_LIST)
					.build();
			assertCreditMetadataFields(cm2, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST,
					ES, opt(licenseURL), ES,
					ELS, DATE_LIST, ELF, ELRI);
		}

		// trim, tidy, and normalise the URL
		for (Map.Entry<String, String> entry : ABNORMAL_URL_MAP.entrySet()) {
			final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withLicense(entry.getKey())
				.withDates(DATE_LIST)
				.build();

			assertCreditMetadataFields(cm, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST,
					ES, opt(entry.getValue()), ES,
					ELS, DATE_LIST, ELF, ELRI);
		}
	}

	@Test
	public void buildMinimalNullEmptyLists() throws Exception {
		// empty list inputs
		final CreditMetadata cm1 = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withVersion(VERSION_STRING)
				.withComments(ELS)
				.withDates(ELD)
				.withFunding(ELF)
				.withRelatedIdentifiers(ELRI)
				.build();
		assertCreditMetadataFields(cm1, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, opt(VERSION_STRING));

		// list of nulls
		final CreditMetadata cm2 = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withVersion(VERSION_STRING)
				.withComments(Arrays.asList(null, null, null, null, null))
				.withDates(Arrays.asList(null, null, null, null, null))
				.withFunding(Arrays.asList(null, null, null, null, null))
				.withRelatedIdentifiers(
						Arrays.asList(null, null, null, null, null))
				.build();
		assertCreditMetadataFields(cm2, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, opt(VERSION_STRING));

		// plain nulls
		final CreditMetadata cm3 = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withVersion(VERSION_STRING)
				.withComments(null)
				.withDates(null)
				.withFunding(null)
				.withRelatedIdentifiers(null)
				.build();
		assertCreditMetadataFields(cm3, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, opt(VERSION_STRING));
	}

	@Test
	public void buildMinimalNullEmptyString() throws Exception {
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final CreditMetadata cm = CreditMetadata
					.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withDescription(nullOrWs)
					.withLicense(nullOrWs)
					.withVersion(nullOrWs)
					.withDates(DATE_LIST)
					.build();
			assertCreditMetadataFields(cm, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, ES,
					ELS, DATE_LIST, ELF, ELRI);
		}
	}

	@Test
	public void buildMaximal() throws Exception {
		for (Map.Entry<String, String> entry : VALID_PID_MAP.entrySet()) {
			final CreditMetadata cm = CreditMetadata
					.getBuilder(entry.getKey(), ORG_1, RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withDescription(DESC_STRING_UNTRIMMED)
					.withLicense(LICENSE_STRING_UNTRIMMED)
					.withVersion(VERSION_STRING_UNTRIMMED)
					.withComments(COMMENTS_LIST_UNTRIMMED)
					.withDates(DATE_LIST)
					.withFunding(FUNDING_LIST)
					.withRelatedIdentifiers(RELATED_ID_LIST)
					.build();
			assertCreditMetadataFields(cm, entry.getValue(), ORG_1, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST,
					opt(DESC_STRING), opt(LICENSE_STRING), opt(VERSION_STRING),
					COMMENTS_LIST, DATE_LIST, FUNDING_LIST,
					RELATED_ID_LIST);
		}
	}

	@Test
	public void buildMinimalOverwriteNullEmptyString() throws Exception {
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final CreditMetadata cm = CreditMetadata
					.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withDescription(DESC_STRING_UNTRIMMED)
					.withDescription(nullOrWs)
					.withLicense(LICENSE_STRING_UNTRIMMED)
					.withLicense(nullOrWs)
					.withVersion(VERSION_STRING_UNTRIMMED)
					.withVersion(nullOrWs)
					.withDates(DATE_LIST)
					.build();
			assertCreditMetadataFields(cm, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, ES,
					ELS, DATE_LIST, ELF, ELRI);
		}
	}

	@Test
	public void buildMaximalOverwriteNullEmptyLists() throws Exception {
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withDates(DATE_LIST)
				.withDates(Arrays.asList(null, null, null, null))
				.withFunding(FUNDING_LIST)
				.withFunding(null)
				.withRelatedIdentifiers(RELATED_ID_LIST)
				.withRelatedIdentifiers(ELRI)
				.withVersion(VERSION_STRING_UNTRIMMED)
				.build();
		assertCreditMetadataFields(cm, IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, opt(VERSION_STRING));
	}

	@Test
	public void buildMaximalDedupeLists() throws Exception {
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST_DUPES_NULLS,
						TITLE_LIST_DUPES_NULLS)
				.withComments(COMMENTS_LIST_UNTRIMMED)
				.withDates(DATE_LIST_DUPES_NULLS)
				.withFunding(FUNDING_LIST_DUPES_NULLS)
				.withRelatedIdentifiers(RELATED_ID_LIST_DUPES_NULLS)
				.build();
				assertCreditMetadataFields(cm, IDENTIFIER_STRING, ORG_1,
				RESOURCE_TYPE, CONTRIBUTOR_LIST, TITLE_LIST, ES, ES, ES,
				COMMENTS_LIST, DATE_LIST, FUNDING_LIST,
				RELATED_ID_LIST);
	}

	@Test
	public void assertContributorsImmutable() throws Exception {
		// same as CONTRIBUTOR_LIST
		final List<Contributor> contributorList = new ArrayList<>(Arrays.asList(C1, C2));
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						contributorList, TITLE_LIST)
				.withDates(DATE_LIST)
				.build();

		// mutating the input list should not affect the contributor list
		contributorList.add(C3);
		assertThat(NO_MUTATION, contributorList, is(Arrays.asList(C1, C2, C3)));
		assertThat(INCORRECT_CONTRIBUTORS, cm.getContributors(), is(CONTRIBUTOR_LIST));

		try {
			cm.getContributors().add(C3);
			fail(EXP_EXC);
		} catch (UnsupportedOperationException e) {
			// hurrah, no mutations here!
		}
	}

	@Test
	public void assertCommentsImmutable() throws Exception {
		final List<String> commentList = new ArrayList<>(Arrays.asList(
				"What a load of nonsense",
				"Prime codswallop"
		));
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withDates(DATE_LIST)
				.withComments(commentList)
				.build();

		// mutating the input list should not affect the comments
		commentList.add("blah blah blah");
		assertThat(NO_MUTATION, commentList, is(Arrays.asList(
				"What a load of nonsense",
				"Prime codswallop",
				"blah blah blah"
		)));
		assertThat(INCORRECT_COMMENTS, cm.getComments(), is(Arrays.asList(
				"What a load of nonsense",
				"Prime codswallop"
		)));

		try {
			cm.getComments().add("horse puckey!");
			fail(EXP_EXC);
		} catch (UnsupportedOperationException e) {
			// test passed
		}
	}

	@Test
	public void assertDatesImmutable() throws Exception {
		// same as DATE_LIST
		final List<EventDate> dateList = new ArrayList<>(Arrays.asList(ED1, ED2));
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withDates(dateList)
				.build();

		// mutating the input list should not affect the date list
		dateList.add(ED3);
		assertThat(NO_MUTATION, dateList, is(Arrays.asList(ED1, ED2, ED3)));
		assertThat(INCORRECT_DATES, cm.getDates(), is(DATE_LIST));

		try {
			cm.getDates().add(ED3);
			fail(EXP_EXC);
		} catch (UnsupportedOperationException e) {
			// hurrah, no mutations here!
		}
	}

	@Test
	public void assertFundingImmutable() throws Exception {
		// same as CONTRIBUTOR_LIST
		final List<FundingReference> fundingList = new ArrayList<>(Arrays.asList(F1));
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withDates(DATE_LIST)
				.withFunding(fundingList)
				.build();

		// mutating the input list should not affect the funding list
		fundingList.add(F2);
		assertThat(NO_MUTATION, fundingList, is(Arrays.asList(F1, F2)));
		assertThat(INCORRECT_FUNDING, cm.getFunding(), is(FUNDING_LIST));

		try {
			cm.getFunding().add(F2);
			fail(EXP_EXC);
		} catch (UnsupportedOperationException e) {
			// hurrah, no mutations here!
		}
	}

	@Test
	public void assertRelatedIdentifiersImmutable() throws Exception {
		// same as RELATED_ID_LIST
		final List<PermanentId> pidList = new ArrayList<>(Arrays.asList(PID1, PID2));
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withDates(DATE_LIST)
				.withRelatedIdentifiers(pidList)
				.build();
		// mutating the input list should not affect the related ID list
		pidList.add(PID3);
		assertThat(NO_MUTATION, pidList, is(Arrays.asList(PID1, PID2, PID3)));
		assertThat(INCORRECT_RELATED_IDS, cm.getRelatedIdentifiers(), is(RELATED_ID_LIST));

		try {
			cm.getRelatedIdentifiers().add(PID3);
			fail(EXP_EXC);
		} catch (UnsupportedOperationException e) {
			// hurrah, no mutations here!
		}
	}

	@Test
	public void assertTitlesImmutable() throws Exception {
		// same as TITLE_LIST
		final List<Title> titleList = new ArrayList<>(Arrays.asList(T1, T2));
		final CreditMetadata cm = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, ORG_1, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, titleList)
				.withDates(DATE_LIST)
				.build();
		// mutating the input list should not affect the title list
		titleList.add(T3);
		assertThat(NO_MUTATION, titleList, is(Arrays.asList(T1, T2, T3)));
		assertThat(INCORRECT_TITLES, cm.getTitles(), is(TITLE_LIST));

		try {
			cm.getTitles().add(T3);
			fail(EXP_EXC);
		} catch (UnsupportedOperationException e) {
			// hurrah, no mutations here!
		}
	}

	/**
	 * Checks the error thrown when a CreditMetadata is built with invalid arguments
	 *
	 * @param builder
	 *                CreditMetadata builder, complete with args
	 * @param error
	 *                the expected error
	 */
	private void buildCreditMetadataFailWithError(final CreditMetadata.Builder builder,
			final String errorString) {
		try {
			builder.build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Errors in CreditMetadata construction:\n" + errorString));
		}
	}

	@Test
	public void buildFailInvalidPID() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							nullOrWs,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					IDENTIFIER_NON_NULL_WS);
		}

		for (String invalidPid : INVALID_PID_LIST) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							invalidPid,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					ILLEGAL_ID_BEFORE + invalidPid + ILLEGAL_ID_AFTER);
		}
	}

	@Test
	public void buildFailInvalidRepository() throws Exception {
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						null,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withVersion(VERSION_STRING),
				REPO_NON_NULL);
	}

	@Test
	public void buildFailInvalidResourceType() throws Exception {
		for (final String rt : INVALID_RESOURCE_TYPE_STRINGS) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							rt,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					"Invalid resourceType: " + rt);
		}

		for (final String wsOrNull : WHITESPACE_STRINGS_WITH_NULL) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							wsOrNull,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					RESOURCE_TYPE_NON_WS_NULL);
		}

		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						(ResourceType) null,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withVersion(VERSION_STRING),
				RESOURCE_TYPE_NON_NULL);
	}

	@Test
	public void buildFailInvalidLicenseURLs() throws Exception {
		// these all start with http
		for (Map.Entry<String, String> entry : INVALID_URL_MAP.entrySet()) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withDates(DATE_LIST)
							.withLicense(entry.getKey()),
					LICENSE_URL_ILLEGAL + entry.getKey() + "': "
							+ entry.getValue());
		}

		// these contain ://
		for (Map.Entry<String, String> entry : INVALID_URL_BAD_PROTOCOL_MAP
				.entrySet()) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withDates(DATE_LIST)
							.withLicense(entry.getKey()),
					LICENSE_URL_ILLEGAL + entry.getKey() + "': "
							+ entry.getValue());
		}

		for (final String url : INVALID_URL_USER_INFO_LIST) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withDates(DATE_LIST)
							.withLicense(url),
					LICENSE_URL_ILLEGAL + url + "': "
							+ INVALID_URL_NO_USER_INFO);
		}
	}

	@Test
	public void buildFailMissingVersionOrDates() throws Exception {
		// no version or dates at all
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST),
				VERSIONING_DATES_REQUIRED);

		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			// version is null or whitespace
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(nullOrWs),
					VERSIONING_DATES_REQUIRED);

			// overwrite valid version with null or whitespace
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(VERSION_STRING)
							.withVersion(nullOrWs),
					VERSIONING_DATES_REQUIRED);
		}

		// null date list
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(null),
				VERSIONING_DATES_REQUIRED);

		// empty date list
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(ELD),
				VERSIONING_DATES_REQUIRED);
		// list of nulls
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(Arrays.asList(null, null, null)),
				VERSIONING_DATES_REQUIRED);

		// overwrite valid dates
		// null date list
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(DATE_LIST)
						.withDates(null),
				VERSIONING_DATES_REQUIRED);

		// empty date list
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(DATE_LIST)
						.withDates(ELD),
				VERSIONING_DATES_REQUIRED);
		// list of nulls
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						ORG_1,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(DATE_LIST)
						.withDates(Arrays.asList(null, null, null)),
				VERSIONING_DATES_REQUIRED);
	}

	@Test
	public void buildFailNullOrEmptyContributorList() throws Exception {
			// null contributor list
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							null,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					CONTRIBUTOR_AT_LEAST_ONE);

			// empty contributor list
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							ELC,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					CONTRIBUTOR_AT_LEAST_ONE);
			// list of nulls
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							Arrays.asList(null, null, null, null),
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					CONTRIBUTOR_AT_LEAST_ONE);
	}

	@Test
	public void buildFailNullOrEmptyTitleList() throws Exception {
			// null title list
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, null)
							.withVersion(VERSION_STRING),
					TITLE_AT_LEAST_ONE);

			// empty title list
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, ELT)
							.withVersion(VERSION_STRING),
					TITLE_AT_LEAST_ONE);
			// list of nulls
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							ORG_1,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							Arrays.asList(null, null, null, null))
							.withVersion(VERSION_STRING),
					TITLE_AT_LEAST_ONE);
	}

	@Test
	public void buildMinimalFailAllFields() throws Exception {
		final String[] errs = {
				RESOURCE_TYPE_NON_WS_NULL,
				REPO_NON_NULL,
				IDENTIFIER_NON_NULL_WS,
				CONTRIBUTOR_AT_LEAST_ONE,
				TITLE_AT_LEAST_ONE,
				VERSIONING_DATES_REQUIRED
		};
		final String errorString = String.join("\n", errs);

		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			// null contributor and title lists
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(nullOrWs, null, nullOrWs, null, null),
					errorString);

			// empty contributor and title lists
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(nullOrWs, null, nullOrWs, ELC, ELT),
					errorString);

			// list of nulls
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							nullOrWs,
							null,
							nullOrWs,
							Arrays.asList(null, null, null, null),
							Arrays.asList(null, null, null, null)),
					errorString);
		}

		for (String invalidPid : INVALID_PID_LIST) {
			final String[] errs2 = {
					RESOURCE_TYPE_NON_WS_NULL,
					REPO_NON_NULL,
					ILLEGAL_ID_BEFORE + invalidPid + ILLEGAL_ID_AFTER,
					CONTRIBUTOR_AT_LEAST_ONE,
					TITLE_AT_LEAST_ONE,
					VERSIONING_DATES_REQUIRED

			};
			final String errorStringWithRegex = String.join("\n", errs2);
			for (String wsOrNull : WHITESPACE_STRINGS_WITH_NULL) {
				// null lists
				buildCreditMetadataFailWithError(
						CreditMetadata.getBuilder(
								invalidPid,
								null,
								wsOrNull,
								null,
								null),
						errorStringWithRegex);

				// empty lists
				buildCreditMetadataFailWithError(
						CreditMetadata.getBuilder(
								invalidPid,
								null,
								wsOrNull,
								ELC,
								ELT),
						errorStringWithRegex);

				// list of nulls
				buildCreditMetadataFailWithError(
						CreditMetadata.getBuilder(
								invalidPid,
								null,
								wsOrNull,
								Arrays.asList(null, null, null),
								Arrays.asList(null, null, null)),
						errorStringWithRegex);
			}
		}
	}

	@Test
	public void buildFailManyFields() throws Exception {

		for (String invalidPid : INVALID_PID_LIST) {
			final String invalidPidError = ILLEGAL_ID_BEFORE + invalidPid
					+ ILLEGAL_ID_AFTER;
			for (Map.Entry<String, String> entry : INVALID_URL_MAP.entrySet()) {
				final String invalidLicenseUrlError = LICENSE_URL_ILLEGAL
				+ entry.getKey() + "': " + entry.getValue();

				final String[] errs = {
					RESOURCE_TYPE_NON_WS_NULL,
					REPO_NON_NULL,
					invalidPidError,
					CONTRIBUTOR_AT_LEAST_ONE,
					TITLE_AT_LEAST_ONE,
					VERSIONING_DATES_REQUIRED,
					invalidLicenseUrlError
				};

				final String errorStringWithRegex = String.join("\n", errs);
				for (String wsOrNull : WHITESPACE_STRINGS_WITH_NULL) {
					// null lists
					buildCreditMetadataFailWithError(
							CreditMetadata.getBuilder(invalidPid, null, wsOrNull, null, null)
									.withLicense(entry.getKey()),
							errorStringWithRegex);

					// empty lists
					buildCreditMetadataFailWithError(
							CreditMetadata.getBuilder(invalidPid,  null, wsOrNull, ELC, ELT)
									.withLicense(entry.getKey()),
							errorStringWithRegex);

					// list of nulls
					buildCreditMetadataFailWithError(
							CreditMetadata.getBuilder(invalidPid, null, wsOrNull,
									Arrays.asList(null, null, null),
									Arrays.asList(null, null, null))
									.withLicense(entry.getKey()),
							errorStringWithRegex);
				}
			}
		}
	}
}
