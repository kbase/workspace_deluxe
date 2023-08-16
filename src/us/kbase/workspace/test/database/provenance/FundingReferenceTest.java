package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import static us.kbase.common.test.TestCommon.optn;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import java.net.URL;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.FundingReference;
import us.kbase.workspace.database.provenance.Organization;

public class FundingReferenceTest {

	static final String INCORRECT = "incorrect ";
	static final String EXP_EXC = "expected exception";

	// field names
	static final String FUNDER = "funder";
	static final String GRANT_ID = "grant ID";
	static final String GRANT_TITLE = "grant title";
	static final String GRANT_URL = "grant URL";

	static final String INCORRECT_FUNDER = INCORRECT + " " + FUNDER;
	static final String INCORRECT_GRANT_ID = INCORRECT + " " + GRANT_ID;
	static final String INCORRECT_GRANT_TITLE = INCORRECT + " " + GRANT_TITLE;
	static final String INCORRECT_GRANT_URL = INCORRECT + " " + GRANT_URL;

	static final String GRANT_ID_STRING = "198170392";
	static final String GRANT_ID_STRING_UNTRIMMED = "\n     " + GRANT_ID_STRING + "    \t";
	static final String GRANT_URL_STRING = "http://example.com/cgi-bin/wtf.cgi";
	static final String GRANT_URL_STRING_UNTRIMMED = "  \t  \r\n " + GRANT_URL_STRING + "                \n";
	static final String GRANT_TITLE_STRING = "The Best Little Toaster";
	static final String GRANT_TITLE_STRING_UNTRIMMED = "\r\n  \f   " + GRANT_TITLE_STRING + "   \t \n\n \t";

	private static final Organization ORG_1 = Organization.getBuilder("Ransome the Clown's Emporium of Wonder").build();
	private static final Organization ORG_2 = Organization.getBuilder("Pillowtronics").build();

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
	 * @param grantURL
	 *                expected grantURL field
	 */
	private void assertFundingReferenceFields(
			final FundingReference fr,
			final Organization funder,
			final String grantId,
			final String grantTitle,
			final URL grantUrl) {
		assertThat(INCORRECT_FUNDER, fr.getFunder(), is(funder));
		assertThat(INCORRECT_GRANT_ID, fr.getGrantID(), is(optn(grantId)));
		assertThat(INCORRECT_GRANT_TITLE, fr.getGrantTitle(), is(optn(grantTitle)));
		assertThat(INCORRECT_GRANT_URL, fr.getGrantURL(), is(optn(grantUrl)));
	}

	@Test
	public void buildMinimal() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(ORG_1).build();
		assertFundingReferenceFields(fr, ORG_1, null, null, null);
	}

	@Test
	public void buildMinimalWithNullURL() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(ORG_2)
			.withGrantURL((URL) null)
			.build();
		assertFundingReferenceFields(fr, ORG_2, null, null, null);
	}

	@Test
	public void buildMaximal() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(ORG_1)
				.withGrantID(GRANT_ID_STRING)
				.withGrantTitle(GRANT_TITLE_STRING)
				.withGrantURL(GRANT_URL_STRING)
				.build();
		assertFundingReferenceFields(fr, ORG_1, GRANT_ID_STRING, GRANT_TITLE_STRING, new URL(GRANT_URL_STRING));
	}

	@Test
	public void buildMaximalWithURL() throws Exception {
		final URL grantURL = new URL(GRANT_URL_STRING);
		final FundingReference fr = FundingReference.getBuilder(ORG_1)
				.withGrantID(GRANT_ID_STRING)
				.withGrantTitle(GRANT_TITLE_STRING)
				.withGrantURL(grantURL)
				.build();
		assertFundingReferenceFields(fr, ORG_1, GRANT_ID_STRING, GRANT_TITLE_STRING, grantURL);
	}

	@Test
	public void buildAndTrimMaximal() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(ORG_2)
				.withGrantID(GRANT_ID_STRING_UNTRIMMED)
				.withGrantTitle(GRANT_TITLE_STRING_UNTRIMMED)
				.withGrantURL(GRANT_URL_STRING_UNTRIMMED)
				.build();
		assertFundingReferenceFields(fr, ORG_2, GRANT_ID_STRING, GRANT_TITLE_STRING, new URL(GRANT_URL_STRING));
	}

	@Test
	public void buildWithNullOrWhitespace() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final FundingReference fr = FundingReference.getBuilder(ORG_1)
					.withGrantID(nullOrWs)
					.withGrantTitle(nullOrWs)
					.withGrantURL(nullOrWs)
					.build();
			assertFundingReferenceFields(fr, ORG_1, null, null, null);
		}
	}

	@Test
	public void buildAndOverwrite() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final FundingReference fr = FundingReference.getBuilder(ORG_1)
					.withGrantID(GRANT_ID_STRING).withGrantID(nullOrWs)
					.withGrantTitle(GRANT_TITLE_STRING).withGrantTitle(nullOrWs)
					.withGrantURL(GRANT_URL_STRING).withGrantURL(nullOrWs)
					.build();
			assertFundingReferenceFields(fr, ORG_1, null, null, null);
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
	public void buildFailInvalidGrantURL() throws Exception {
		buildFundingRefFailWithError(
				FundingReference.getBuilder(ORG_1).withGrantURL("ror:123"),
				"Illegal grantURL url 'ror:123': unknown protocol: ror");

		buildFundingRefFailWithError(
				FundingReference.getBuilder(ORG_1)
						.withGrantURL("a random string with no protocol"),
				"Illegal grantURL url 'a random string with no protocol': no protocol: a random string with no protocol");

		buildFundingRefFailWithError(
				FundingReference.getBuilder(ORG_1).withGrantURL("https://kb^ase.us/"),
				"Illegal grantURL url 'https://kb^ase.us/': Illegal character in authority at " +
						"index 8: https://kb^ase.us/");

		// class input
		buildFundingRefFailWithError(
				FundingReference.getBuilder(ORG_1)
						.withGrantURL(new URL("https://kb^ase.us/")),
				"Illegal grantURL url 'https://kb^ase.us/': Illegal character in authority at " +
						"index 8: https://kb^ase.us/");
	}

	@Test
	public void buildFailNullFunder() throws Exception {
		buildFundingRefFailWithError(
			FundingReference.getBuilder((Organization) null),
			"funder cannot be null");
	}

	@Test
	public void buildFailAllFieldsWithValidation() throws Exception {
		final String notAnURL = "this is not an URL";
		buildFundingRefFailWithError(
				FundingReference.getBuilder((Organization) null)
						.withGrantURL(notAnURL),
						"funder cannot be null\n" +
						"Illegal grantURL url '" + notAnURL + "': no protocol: "
						+ notAnURL);
	}
}
