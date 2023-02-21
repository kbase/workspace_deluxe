package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.net.URL;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;

import static us.kbase.common.test.TestCommon.opt;
import static us.kbase.common.test.TestCommon.optn;
import static us.kbase.common.test.TestCommon.ES;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_PID_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_PID_LIST;

import us.kbase.workspace.database.provenance.FundingReference;

@Category(us.kbase.common.test.ProvenanceTests.class)
public class FundingReferenceTest {

	static final String INCORRECT = "incorrect ";
	static final String EXP_EXC = "expected exception";

	// field names
	static final String FUNDER_NAME = "funder name";
	static final String FUNDER_ID = "funder ID";
	static final String AWARD_ID = "award ID";
	static final String AWARD_TITLE = "award title";
	static final String AWARD_URL = "award URL";

	static final String INCORRECT_FUNDER_NAME = INCORRECT + " " + FUNDER_NAME;
	static final String INCORRECT_FUNDER_ID = INCORRECT + " " + FUNDER_ID;
	static final String INCORRECT_AWARD_ID = INCORRECT + " " + AWARD_ID;
	static final String INCORRECT_AWARD_TITLE = INCORRECT + " " + AWARD_TITLE;
	static final String INCORRECT_AWARD_URL = INCORRECT + " " + AWARD_URL;

	static final String FUNDER_NAME_STRING = "World's Richest Funding Body";
	static final String FUNDER_NAME_STRING_UNTRIMMED = "\t \f \n \r" + FUNDER_NAME_STRING + "\t \f \r \n";
	static final String FUNDER_ID_STRING = "ROR:04xm1d337";
	static final String FUNDER_ID_STRING_UNTRIMMED = "\t\t\t   " + FUNDER_ID_STRING + "  \f  \r \n\n";
	static final String AWARD_ID_STRING = "198170392";
	static final String AWARD_ID_STRING_UNTRIMMED = "\n     " + AWARD_ID_STRING + "    \t";
	static final String AWARD_URL_STRING = "http://example.com/cgi-bin/wtf.cgi";
	static final String AWARD_URL_STRING_UNTRIMMED = "  \t  \r\n " + AWARD_URL_STRING + "                \n";
	static final String AWARD_TITLE_STRING = "The Best Little Toaster";
	static final String AWARD_TITLE_STRING_UNTRIMMED = "\r\n  \f   " + AWARD_TITLE_STRING + "   \t \n\n \t";

	static final Map<String, String> MINIMAL_MAP = ImmutableMap.of(
			FUNDER_NAME, FUNDER_NAME_STRING);

	static final Map<String, String> ALL_MAP = ImmutableMap.of(
			FUNDER_NAME, FUNDER_NAME_STRING,
			FUNDER_ID, FUNDER_ID_STRING,
			AWARD_ID, AWARD_ID_STRING,
			AWARD_TITLE, AWARD_TITLE_STRING);

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(FundingReference.class).usingGetClass().verify();
	}

	/**
	 * Check the funding ref has the expected fields
	 *
	 * @param fr
	 *                instance to check
	 * @param expectedMap
	 *                key/value pairs are the field names and values for the
	 *                string fields that are expected not to be null
	 * @param awardURL
	 *                expected awardURL field
	 */
	private void assertFundingReferenceFields(
			final FundingReference fr,
			final Map<String, String> expectedMap,
			final URL awardUrl) {
		assertThat(INCORRECT_FUNDER_NAME, fr.getFunderName(), is(expectedMap.get(FUNDER_NAME)));
		assertThat(INCORRECT_FUNDER_ID, fr.getFunderID(),
				is(expectedMap.containsKey(FUNDER_ID) ? opt(expectedMap.get(FUNDER_ID)) : ES));
		assertThat(INCORRECT_AWARD_ID, fr.getAwardID(),
				is(expectedMap.containsKey(AWARD_ID) ? opt(expectedMap.get(AWARD_ID)) : ES));
		assertThat(INCORRECT_AWARD_TITLE, fr.getAwardTitle(),
				is(expectedMap.containsKey(AWARD_TITLE) ? opt(expectedMap.get(AWARD_TITLE)) : ES));
		assertThat(INCORRECT_AWARD_URL, fr.getAwardURL(), is(optn(awardUrl)));
	}

	@Test
	public void buildMinimal() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_STRING).build();
		assertFundingReferenceFields(fr, MINIMAL_MAP, null);
	}

	@Test
	public void buildMinimalWithNullURL() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_STRING)
			.withAwardURL((URL) null)
			.build();
		assertFundingReferenceFields(fr, MINIMAL_MAP, null);
	}

	@Test
	public void buildMaximal() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_STRING)
				.withFunderID(FUNDER_ID_STRING)
				.withAwardID(AWARD_ID_STRING)
				.withAwardTitle(AWARD_TITLE_STRING)
				.withAwardURL(AWARD_URL_STRING)
				.build();
		assertFundingReferenceFields(fr, ALL_MAP, new URL(AWARD_URL_STRING));
	}

	@Test
	public void buildMaximalWithURL() throws Exception {
		final URL awardURL = new URL(AWARD_URL_STRING);
		final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_STRING)
				.withFunderID(FUNDER_ID_STRING)
				.withAwardID(AWARD_ID_STRING)
				.withAwardTitle(AWARD_TITLE_STRING)
				.withAwardURL(awardURL)
				.build();
		assertFundingReferenceFields(fr, ALL_MAP, awardURL);
	}

	@Test
	public void buildAndTrimMaximal() throws Exception {
		for (Map.Entry<String, String> entry : VALID_PID_MAP.entrySet()) {
			final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_STRING_UNTRIMMED)
					.withFunderID(entry.getKey())
					.withAwardID(AWARD_ID_STRING_UNTRIMMED)
					.withAwardTitle(AWARD_TITLE_STRING_UNTRIMMED)
					.withAwardURL(AWARD_URL_STRING_UNTRIMMED)
					.build();
			final Map<String, String> expectedMap = ImmutableMap.of(
				FUNDER_NAME, FUNDER_NAME_STRING,
				FUNDER_ID, entry.getValue(),
				AWARD_ID, AWARD_ID_STRING,
				AWARD_TITLE, AWARD_TITLE_STRING);
			assertFundingReferenceFields(fr, expectedMap, new URL(AWARD_URL_STRING));
		}
	}

	@Test
	public void buildWithNullOrWhitespace() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_STRING)
					.withFunderID(nullOrWs)
					.withAwardID(nullOrWs)
					.withAwardTitle(nullOrWs)
					.withAwardURL(nullOrWs)
					.build();
			assertFundingReferenceFields(fr, MINIMAL_MAP, null);
		}
	}

	@Test
	public void buildAndOverwrite() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_STRING)
					.withFunderID(FUNDER_ID_STRING).withFunderID(nullOrWs)
					.withAwardID(AWARD_ID_STRING).withAwardID(nullOrWs)
					.withAwardTitle(AWARD_TITLE_STRING).withAwardTitle(nullOrWs)
					.withAwardURL(AWARD_URL_STRING).withAwardURL(nullOrWs)
					.build();
			assertFundingReferenceFields(fr, MINIMAL_MAP, null);
		}
	}

	/**
	 * Checks the error thrown when a FundingReference is built with invalid
	 * arguments
	 *
	 * @param builder
	 *                funding reference builder, complete with args
	 * @param error
	 *                the expected error
	 */
	private void buildFundingRefFailWithError(final FundingReference.Builder builder, final String errorString) {
		try {
			builder.build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Errors in FundingReference construction:\n" + errorString));
		}
	}

	@Test
	public void buildFailInvalidPIDs() throws Exception {
		for (String invalidPid : INVALID_PID_LIST) {
			buildFundingRefFailWithError(
					FundingReference.getBuilder(FUNDER_NAME_STRING).withFunderID(invalidPid),
					"Illegal format for funderID: \"" + invalidPid + "\"\n" +
							"It should match the pattern \"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\"");
		}
	}

	@Test
	public void buildFailInvalidAwardURL() throws Exception {
		buildFundingRefFailWithError(
				FundingReference.getBuilder(FUNDER_NAME_STRING).withAwardURL(FUNDER_ID_STRING),
				"Illegal awardURL url '" + FUNDER_ID_STRING + "': unknown protocol: ror");

		buildFundingRefFailWithError(
				FundingReference.getBuilder(FUNDER_NAME_STRING)
						.withAwardURL("a random string with no protocol"),
				"Illegal awardURL url 'a random string with no protocol': no protocol: a random string with no protocol");

		buildFundingRefFailWithError(
				FundingReference.getBuilder(FUNDER_NAME_STRING).withAwardURL("https://kb^ase.us/"),
				"Illegal awardURL url 'https://kb^ase.us/': Illegal character in authority at " +
						"index 8: https://kb^ase.us/");

		// class input
		buildFundingRefFailWithError(
				FundingReference.getBuilder(FUNDER_NAME_STRING)
						.withAwardURL(new URL("https://kb^ase.us/")),
				"Illegal awardURL url 'https://kb^ase.us/': Illegal character in authority at " +
						"index 8: https://kb^ase.us/");
	}

	@Test
	public void buildFailNullOrWhitespaceFunderName() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildFundingRefFailWithError(
					FundingReference.getBuilder(nullOrWs),
					"funderName cannot be null or whitespace only");
		}
	}

	@Test
	public void buildFailAllFieldsWithValidation() throws Exception {
		final String notAnURL = "this is not an URL";
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			for (String invalidPid : INVALID_PID_LIST) {
				buildFundingRefFailWithError(
						FundingReference.getBuilder(nullOrWs).withFunderID(invalidPid)
								.withAwardURL(notAnURL),
						"funderName cannot be null or whitespace only\n" +
								"Illegal format for funderID: \"" + invalidPid + "\"\n"
								+
								"It should match the pattern \"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\"\n"
								+
								"Illegal awardURL url '" + notAnURL + "': no protocol: "
								+ notAnURL);
			}
		}
	}
}
