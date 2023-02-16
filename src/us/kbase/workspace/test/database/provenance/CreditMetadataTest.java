package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.opt;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import static us.kbase.common.test.TestCommon.ES;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Collections;

import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_PID_LIST;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_PID_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_URL_LIST;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_BAD_PROTOCOL_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_USER_INFO_LIST;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_URL_NO_USER_INFO;

import us.kbase.workspace.database.provenance.CreditMetadata;
import us.kbase.workspace.database.provenance.CreditMetadata.ResourceType;
import us.kbase.workspace.database.provenance.Contributor;
import us.kbase.workspace.database.provenance.Event;
import us.kbase.workspace.database.provenance.EventDate;
import us.kbase.workspace.database.provenance.FundingReference;
import us.kbase.workspace.database.provenance.PermanentID;
import us.kbase.workspace.database.provenance.Title;
import us.kbase.workspace.database.provenance.Contributor.ContributorType;

public class CreditMetadataTest {

	private static final String INCORRECT = "incorrect ";
	// field names
	private static final String COMMENTS = "comments";
	private static final String ID = "identifier";
	private static final String LICENSE = "license";
	private static final String VERSION = "version";
	private static final String TYPE = "resource type";
	private static final String CONTRIBUTORS = "contributors";
	private static final String DATES = "dates";
	private static final String FUNDING = "funding";
	private static final String RELATED_IDS = "related identifiers";
	private static final String TITLES = "titles";

	// field values
	private static final String LICENSE_STRING = "Apache 2.0";
	private static final String LICENSE_STRING_UNTRIMMED = "    \n \n Apache 2.0 \t\t  ";
	private static final String LICENSE_URL_STRING = "https://jgi.doe.gov/user-programs/pmo-overview/policies/";
	private static final String LICENSE_URL_STRING_UNTRIMMED = "\n\n\n   HTTPS://jgi.doe.gov/user-programs/pmo-overview/policies/\t\r\n";
	private static final String VERSION_STRING = "1.2.3.4.c";
	private static final String VERSION_STRING_UNTRIMMED = "\n\r\n1.2.3.4.c";

	// test error strings
	private static final String INCORRECT_COMMENTS = INCORRECT + COMMENTS;
	private static final String INCORRECT_ID = INCORRECT + ID;
	private static final String INCORRECT_LICENSE = INCORRECT + LICENSE;
	private static final String INCORRECT_TYPE = INCORRECT + TYPE;
	private static final String INCORRECT_VERSION = INCORRECT + VERSION;
	private static final String INCORRECT_CONTRIBUTORS = INCORRECT + CONTRIBUTORS;
	private static final String INCORRECT_DATES = INCORRECT + DATES;
	private static final String INCORRECT_FUNDING = INCORRECT + FUNDING;
	private static final String INCORRECT_RELATED_IDS = INCORRECT + RELATED_IDS;
	private static final String INCORRECT_TITLES = INCORRECT + TITLES;

	// error messages
	private static final String CONTRIBUTOR_AT_LEAST_ONE = "at least one contributor must be provided";
	private static final String IDENTIFIER_NON_NULL_WS = "identifier cannot be null or whitespace only";
	private static final String RESOURCE_TYPE_NON_NULL = "resourceType cannot be null";
	private static final String RESOURCE_TYPE_NON_WS_NULL = "resourceType cannot be null or whitespace only";
	private static final String TITLE_AT_LEAST_ONE = "at least one title must be provided";
	private static final String VERSIONING_DATES_REQUIRED = "must provide either 'version' " +
			"or one or more 'dates', ideally indicating when the resource was " +
			"published or when it was last updated";

	private static final String IDENTIFIER_STRING = "SOME:identifier";
	private static final Map<String, String> VERSION_MAP = ImmutableMap.of(VERSION,
			VERSION_STRING);
	private static final Map<String, String> LICENSE_VERSION_MAP = ImmutableMap.of(VERSION,
			VERSION_STRING, LICENSE, LICENSE_STRING);
	private static final Map<String, String> LICENSE_URL_VERSION_MAP = ImmutableMap.of(VERSION,
			VERSION_STRING, LICENSE, LICENSE_URL_STRING);

	private static final ResourceType RESOURCE_TYPE = ResourceType.DATASET;
	private static final String RESOURCE_TYPE_STRING = "dataset";

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

	private static final String TITLE_STRING = "A Series of Unfortunate Elephants";

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

	private static final List<Contributor> CONTRIBUTOR_LIST = Arrays.asList(
			Contributor.getBuilder(ContributorType.PERSON, "Mr Blobby").build(),
			Contributor.getBuilder(ContributorType.ORGANIZATION, "Cereal Convention")
					.build());

	private static final List<EventDate> DATE_LIST = Arrays.asList(
			EventDate.build("0000-12-15", Event.CREATED),
			EventDate.build("2112", Event.WITHDRAWN));

	private static final List<FundingReference> FUNDING_LIST = Arrays.asList(
			FundingReference.getBuilder("New World Order LLC").build());

	private static final List<PermanentID> RELATED_ID_LIST = Arrays.asList(
			PermanentID.getBuilder("this:ID").build(),
			PermanentID.getBuilder("that:ID").build(),
			PermanentID.getBuilder("the:other ID").build());

	private static final List<Title> TITLE_LIST = Arrays.asList(
			Title.getBuilder(TITLE_STRING).build());

	private static final Map<String, String> EM = Collections.emptyMap();
	private static final List<String> ELS = Collections.emptyList();
	private static final List<Contributor> ELC = Collections.emptyList();
	private static final List<EventDate> ELD = Collections.emptyList();
	private static final List<FundingReference> ELF = Collections.emptyList();
	private static final List<PermanentID> ELRI = Collections.emptyList();
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
	 * @param creditMetadata
	 *                instance to check
	 * @param identifier
	 *                expected resource ID
	 * @param rt
	 *                expected ResourceType
	 * @param contributors
	 *                list of Contributor objects expected to be returned by
	 *                `getContributors`;
	 * @param titles
	 *                list of Title objects expected to be returned by
	 *                `getTitles`;
	 * @param expectedMap
	 *                key/value pairs are the field names and values for the string
	 *                fields that are expected not to be null
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
	 *                list of PermanentIDs expected to be returned by
	 *                `getRelatedIdentifiers`;
	 *                if null, should return an empty list
	 */
	private void assertCreditMetadataFields(final CreditMetadata creditMetadata,
			final String identifier,
			final ResourceType rt,
			final List<Contributor> contributors,
			final List<Title> titles,
			final Map<String, String> expectedMap,
			final List<String> comments,
			final List<EventDate> dates,
			final List<FundingReference> funding,
			final List<PermanentID> relatedIdentifiers) {

		assertThat(INCORRECT_ID, creditMetadata.getIdentifier(), is(identifier));
		assertThat(INCORRECT_TYPE, creditMetadata.getResourceType(), is(rt));

		assertThat(INCORRECT_COMMENTS, creditMetadata.getComments(),
				is(comments));
		assertThat(INCORRECT_CONTRIBUTORS, creditMetadata.getContributors(),
				is(contributors));
		assertThat(INCORRECT_DATES, creditMetadata.getDates(),
				is(dates));
		assertThat(INCORRECT_FUNDING, creditMetadata.getFunding(),
				is(funding));
		assertThat(INCORRECT_RELATED_IDS, creditMetadata.getRelatedIdentifiers(),
				is(relatedIdentifiers));
		assertThat(INCORRECT_TITLES, creditMetadata.getTitles(),
				is(titles));

		assertThat(INCORRECT_LICENSE, creditMetadata.getLicense(),
				is(expectedMap.containsKey(LICENSE) ? opt(expectedMap.get(LICENSE))
						: ES));
		assertThat(INCORRECT_VERSION, creditMetadata.getVersion(),
				is(expectedMap.containsKey(VERSION) ? opt(expectedMap.get(VERSION))
						: ES));
	}

	private void assertCreditMetadataFields(
			final CreditMetadata creditMetadata,
			final String identifier,
			final ResourceType rt,
			final List<Contributor> contributors,
			final List<Title> titles,
			final Map<String, String> expectedMap) {
		assertCreditMetadataFields(creditMetadata, identifier, rt, contributors,
				titles, expectedMap, ELS, ELD, ELF, ELRI);
	}

	@Test
	public void buildMinimal() throws Exception {
		for (final ResourceType rt : ResourceType.values()) {
			for (Map.Entry<String, String> entry : VALID_PID_MAP.entrySet()) {
				final CreditMetadata creditMetadata = CreditMetadata
						.getBuilder(entry.getKey(), rt,
								CONTRIBUTOR_LIST, TITLE_LIST)
						.withVersion(VERSION_STRING)
						.build();
				assertCreditMetadataFields(creditMetadata, entry.getValue(), rt,
						CONTRIBUTOR_LIST, TITLE_LIST, VERSION_MAP);
			}
		}
	}

	@Test
	public void buildMinimalResourceTypeStrings() throws Exception {
		for (Map.Entry<String, ResourceType> rtes : VALID_RESOURCE_TYPE_MAP.entrySet()) {
			for (Map.Entry<String, String> entry : VALID_PID_MAP.entrySet()) {
				final CreditMetadata creditMetadata = CreditMetadata
						.getBuilder(entry.getKey(), rtes.getKey(),
								CONTRIBUTOR_LIST, TITLE_LIST)
						.withVersion(VERSION_STRING)
						.build();
				assertCreditMetadataFields(creditMetadata, entry.getValue(),
						rtes.getValue(), CONTRIBUTOR_LIST, TITLE_LIST,
						VERSION_MAP);
			}
		}
	}


	@Test
	public void buildCleanAndTrimComments() throws Exception {
		final CreditMetadata creditMetadata = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withComments(COMMENTS_LIST_UNTRIMMED)
				.withVersion(VERSION_STRING_UNTRIMMED)
				.build();

		assertCreditMetadataFields(creditMetadata, IDENTIFIER_STRING, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, VERSION_MAP,
				COMMENTS_LIST, ELD, ELF, ELRI);
	}

	@Test
	public void buildCleanURLTypeLicense() throws Exception {
		for (final String licenseURL : VALID_URL_LIST) {
			final CreditMetadata creditMetadata2 = CreditMetadata
					.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withLicense(licenseURL)
					.withDates(DATE_LIST)
					.build();
			assertCreditMetadataFields(creditMetadata2, IDENTIFIER_STRING, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST,
					ImmutableMap.of(LICENSE, licenseURL),
					ELS, DATE_LIST, ELF, ELRI);
		}

		// trim and tidy the URL
		final CreditMetadata creditMetadata = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withLicense(LICENSE_URL_STRING_UNTRIMMED)
				.withVersion(VERSION_STRING_UNTRIMMED)
				.build();

		assertCreditMetadataFields(creditMetadata, IDENTIFIER_STRING, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, LICENSE_URL_VERSION_MAP);
	}

	@Test
	public void buildMinimalNullEmptyLists() throws Exception {
		// empty list inputs
		final CreditMetadata creditMetadata1 = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withVersion(VERSION_STRING)
				.withComments(ELS)
				.withDates(ELD)
				.withFunding(ELF)
				.withRelatedIdentifiers(ELRI)
				.build();
		assertCreditMetadataFields(creditMetadata1, IDENTIFIER_STRING, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, VERSION_MAP);

		// list of nulls
		final CreditMetadata creditMetadata2 = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withVersion(VERSION_STRING)
				.withComments(Arrays.asList(null, null, null, null, null))
				.withDates(Arrays.asList(null, null, null, null, null))
				.withFunding(Arrays.asList(null, null, null, null, null))
				.withRelatedIdentifiers(
						Arrays.asList(null, null, null, null, null))
				.build();
		assertCreditMetadataFields(creditMetadata2, IDENTIFIER_STRING, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, VERSION_MAP);

		// plain nulls
		final CreditMetadata creditMetadata3 = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withVersion(VERSION_STRING)
				.withComments(null)
				.withDates(null)
				.withFunding(null)
				.withRelatedIdentifiers(null)
				.build();
		assertCreditMetadataFields(creditMetadata3, IDENTIFIER_STRING, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, VERSION_MAP);
	}

	@Test
	public void buildMinimalNullEmptyString() throws Exception {
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final CreditMetadata creditMetadata = CreditMetadata
					.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withLicense(nullOrWs)
					.withVersion(nullOrWs)
					.withDates(DATE_LIST)
					.build();
			assertCreditMetadataFields(creditMetadata, IDENTIFIER_STRING, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST, EM,
					ELS, DATE_LIST, ELF, ELRI);
		}
	}

	@Test
	public void buildMaximal() throws Exception {
		for (Map.Entry<String, String> entry : VALID_PID_MAP.entrySet()) {
			final CreditMetadata creditMetadata = CreditMetadata
					.getBuilder(entry.getKey(), RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withLicense(LICENSE_STRING_UNTRIMMED)
					.withVersion(VERSION_STRING_UNTRIMMED)
					.withComments(COMMENTS_LIST_UNTRIMMED)
					.withDates(DATE_LIST)
					.withFunding(FUNDING_LIST)
					.withRelatedIdentifiers(RELATED_ID_LIST)
					.build();
			assertCreditMetadataFields(creditMetadata, entry.getValue(), RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST, LICENSE_VERSION_MAP,
					COMMENTS_LIST, DATE_LIST, FUNDING_LIST,
					RELATED_ID_LIST);
		}
	}

	@Test
	public void buildMinimalOverwriteNullEmptyString() throws Exception {
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final CreditMetadata creditMetadata = CreditMetadata
					.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, TITLE_LIST)
					.withLicense(LICENSE_STRING_UNTRIMMED)
					.withLicense(nullOrWs)
					.withVersion(VERSION_STRING_UNTRIMMED)
					.withVersion(nullOrWs)
					.withDates(DATE_LIST)
					.build();
			assertCreditMetadataFields(creditMetadata, IDENTIFIER_STRING, RESOURCE_TYPE,
					CONTRIBUTOR_LIST, TITLE_LIST, EM,
					ELS, DATE_LIST, ELF, ELRI);
		}
	}


	@Test
	public void buildMaximalOverwriteNullEmptyLists() throws Exception {
		final CreditMetadata creditMetadata = CreditMetadata
				.getBuilder(IDENTIFIER_STRING, RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST, TITLE_LIST)
				.withDates(DATE_LIST)
				.withDates(Arrays.asList(null, null, null, null))
				.withFunding(FUNDING_LIST)
				.withFunding(null)
				.withRelatedIdentifiers(RELATED_ID_LIST)
				.withRelatedIdentifiers(ELRI)
				.withVersion(VERSION_STRING_UNTRIMMED)
				.build();
		assertCreditMetadataFields(creditMetadata, IDENTIFIER_STRING, RESOURCE_TYPE,
				CONTRIBUTOR_LIST, TITLE_LIST, VERSION_MAP);
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
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					"Illegal format for identifier: \"" + invalidPid
							+ "\"\n" +
							"It should match the pattern "
							+ "\"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\"");
		}
	}

	@Test
	public void buildFailInvalidResourceType() throws Exception {
		for (final String rt : INVALID_RESOURCE_TYPE_STRINGS) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
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
							wsOrNull,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					RESOURCE_TYPE_NON_WS_NULL);
		}

		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
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
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withDates(DATE_LIST)
							.withLicense(entry.getKey()),
					"Illegal license url '" + entry.getKey() + "': "
							+ entry.getValue());
		}

		// these contain ://
		for (Map.Entry<String, String> entry : INVALID_URL_BAD_PROTOCOL_MAP
				.entrySet()) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withDates(DATE_LIST)
							.withLicense(entry.getKey()),
					"Illegal license url '" + entry.getKey() + "': "
							+ entry.getValue());
		}

		for (final String url : INVALID_URL_USER_INFO_LIST) {
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withDates(DATE_LIST)
							.withLicense(url),
					"Illegal license url '" + url + "': "
							+ INVALID_URL_NO_USER_INFO);
		}


	}

	@Test
	public void buildFailMissingVersionOrDates() throws Exception {
		// no version or dates at all
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST),				VERSIONING_DATES_REQUIRED);

		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			// version is null or whitespace
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST,
							TITLE_LIST)
							.withVersion(nullOrWs),
					VERSIONING_DATES_REQUIRED);

			// overwrite valid version with null or whitespace
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
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
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(null),
				VERSIONING_DATES_REQUIRED);

		// empty date list
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
						RESOURCE_TYPE_STRING,
						CONTRIBUTOR_LIST,
						TITLE_LIST)
						.withDates(ELD),
				VERSIONING_DATES_REQUIRED);
		// list of nulls
		buildCreditMetadataFailWithError(
				CreditMetadata.getBuilder(
						IDENTIFIER_STRING,
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
							RESOURCE_TYPE_STRING,
							null,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					CONTRIBUTOR_AT_LEAST_ONE);

			// empty contributor list
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							RESOURCE_TYPE_STRING,
							ELC,
							TITLE_LIST)
							.withVersion(VERSION_STRING),
					CONTRIBUTOR_AT_LEAST_ONE);
			// list of nulls
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
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
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, null)
							.withVersion(VERSION_STRING),
					TITLE_AT_LEAST_ONE);

			// empty title list
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
							RESOURCE_TYPE_STRING,
							CONTRIBUTOR_LIST, ELT)
							.withVersion(VERSION_STRING),
					TITLE_AT_LEAST_ONE);
			// list of nulls
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							IDENTIFIER_STRING,
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
				IDENTIFIER_NON_NULL_WS,
				CONTRIBUTOR_AT_LEAST_ONE,
				TITLE_AT_LEAST_ONE,
				VERSIONING_DATES_REQUIRED
		};
		final String errorString = String.join("\n", errs);

		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			// null contributor and title lists
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(nullOrWs, nullOrWs, null, null),
					errorString);

			// empty contributor and title lists
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(nullOrWs, nullOrWs, ELC, ELT),
					errorString);

			// list of nulls
			buildCreditMetadataFailWithError(
					CreditMetadata.getBuilder(
							nullOrWs,
							nullOrWs,
							Arrays.asList(null, null, null, null),
							Arrays.asList(null, null, null, null)),
					errorString);
		}

		for (String invalidPid : INVALID_PID_LIST) {
			final String[] errs2 = {
					RESOURCE_TYPE_NON_WS_NULL,
					"Illegal format for identifier: \"" + invalidPid + "\"\n" +
							"It should match the pattern "
							+ "\"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\"",
					CONTRIBUTOR_AT_LEAST_ONE,
					TITLE_AT_LEAST_ONE,
					VERSIONING_DATES_REQUIRED

			};
			final String errorStringWithRegex = String.join("\n", errs2);
			for (String wsOrNull : WHITESPACE_STRINGS_WITH_NULL) {
				// null lists
				buildCreditMetadataFailWithError(
						CreditMetadata.getBuilder(
							invalidPid, wsOrNull, null, null),
						errorStringWithRegex);

				// empty lists
				buildCreditMetadataFailWithError(
						CreditMetadata.getBuilder(invalidPid, wsOrNull, ELC, ELT),
						errorStringWithRegex);

				// list of nulls
				buildCreditMetadataFailWithError(
						CreditMetadata.getBuilder(
								invalidPid,
								wsOrNull,
								Arrays.asList(null, null, null),
								Arrays.asList(null, null, null)),
						errorStringWithRegex);
			}
		}
	}

	@Test
	public void buildMaximalFailAllFields() throws Exception {

		for (String invalidPid : INVALID_PID_LIST) {
			final String invalidPidError = "Illegal format for identifier: \"" + invalidPid + "\"\n" +
			"It should match the pattern "
			+ "\"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\"";
			for (Map.Entry<String, String> entry : INVALID_URL_MAP.entrySet()) {
				final String invalidLicenseUrlError = "Illegal license url '"
				+ entry.getKey() + "': " + entry.getValue();

				final String[] errs = {
					RESOURCE_TYPE_NON_WS_NULL,
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
							CreditMetadata.getBuilder(invalidPid, wsOrNull, null, null)
									.withLicense(entry.getKey()),
							errorStringWithRegex);

					// empty lists
					buildCreditMetadataFailWithError(
							CreditMetadata.getBuilder(invalidPid, wsOrNull, ELC, ELT)
									.withLicense(entry.getKey()),
							errorStringWithRegex);

					// list of nulls
					buildCreditMetadataFailWithError(
							CreditMetadata.getBuilder(invalidPid, wsOrNull,
									Arrays.asList(null, null, null),
									Arrays.asList(null, null, null))
									.withLicense(entry.getKey()),
							errorStringWithRegex);
				}
			}
		}
	}
}
