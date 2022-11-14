package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Map;
import java.net.URL;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;

import static us.kbase.common.test.TestCommon.opt;
import static us.kbase.common.test.TestCommon.ES;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_PID_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_PID_LIST;

import us.kbase.workspace.database.provenance.FundingReference;

public class FundingReferenceTest {

	static final String INCORRECT_FUNDER_NAME = "incorrect funder name";
	static final String INCORRECT_FUNDER_ID = "incorrect funder id";
	static final String INCORRECT_AWARD_ID = "incorrect award id";
	static final String INCORRECT_AWARD_TITLE = "incorrect award title";
	static final String INCORRECT_AWARD_URL = "incorrect award URL";

	static final String EXP_EXC = "expected exception";

	static final String FUNDER_NAME = "World's Richest Funding Body";
	static final String FUNDER_NAME_UNTRIMMED = "\t \f \n \r" + FUNDER_NAME + "\t \f \r \n";
	static final String FUNDER_ID = "ROR:04xm1d337";
	static final String FUNDER_ID_UNTRIMMED = "\t\t\t   "  + FUNDER_ID + "  \f  \r \n\n";
	static final String AWARD_ID = "198170392";
	static final String AWARD_ID_UNTRIMMED = "" + AWARD_ID + "";
	static final String AWARD_URL = "http://example.com/cgi-bin/wtf.cgi";
	static final String AWARD_URL_UNTRIMMED = "  \t  \r\n " + AWARD_URL + "                \n";
	static final String AWARD_TITLE = "The Best Little Toaster";
	static final String AWARD_TITLE_UNTRIMMED = "\r\n  \f   " + AWARD_TITLE + "   \t \n\n \t";


	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(FundingReference.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME).build();
		assertThat(INCORRECT_FUNDER_NAME, fr.getFunderName(), is(FUNDER_NAME));
		assertThat(INCORRECT_FUNDER_ID, fr.getFunderID(), is(ES));
		assertThat(INCORRECT_AWARD_ID, fr.getAwardID(), is(ES));
		assertThat(INCORRECT_AWARD_TITLE, fr.getAwardTitle(), is(ES));
		assertThat(INCORRECT_AWARD_URL, fr.getAwardURL(), is(ES));
	}

	@Test
	public void buildMaximal() throws Exception {
		final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME)
			.withFunderID(FUNDER_ID)
			.withAwardID(AWARD_ID)
			.withAwardTitle(AWARD_TITLE)
			.withAwardURL(AWARD_URL)
			.build();
		assertThat(INCORRECT_FUNDER_NAME, fr.getFunderName(), is(FUNDER_NAME));
		assertThat(INCORRECT_FUNDER_ID, fr.getFunderID(), is(opt(FUNDER_ID)));
		assertThat(INCORRECT_AWARD_ID, fr.getAwardID(), is(opt(AWARD_ID)));
		assertThat(INCORRECT_AWARD_TITLE, fr.getAwardTitle(), is(opt(AWARD_TITLE)));
		assertThat(INCORRECT_AWARD_URL, fr.getAwardURL(), is(opt(new URL(AWARD_URL))));
	}

	@Test
	public void buildMaximalWithURL() throws Exception {
		final URL awardURL = new URL(AWARD_URL);
		final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME)
			.withFunderID(FUNDER_ID)
			.withAwardID(AWARD_ID)
			.withAwardTitle(AWARD_TITLE)
			.withAwardURL(awardURL)
			.build();
		assertThat(INCORRECT_FUNDER_NAME, fr.getFunderName(), is(FUNDER_NAME));
		assertThat(INCORRECT_FUNDER_ID, fr.getFunderID(), is(opt(FUNDER_ID)));
		assertThat(INCORRECT_AWARD_ID, fr.getAwardID(), is(opt(AWARD_ID)));
		assertThat(INCORRECT_AWARD_TITLE, fr.getAwardTitle(), is(opt(AWARD_TITLE)));
		assertThat(INCORRECT_AWARD_URL, fr.getAwardURL(), is(opt(awardURL)));
	}


	@Test
	public void buildAndTrimMaximal() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME_UNTRIMMED)
				.withFunderID(mapElement.getKey())
				.withAwardID(AWARD_ID_UNTRIMMED)
				.withAwardTitle(AWARD_TITLE_UNTRIMMED)
				.withAwardURL(AWARD_URL_UNTRIMMED)
				.build();
			assertThat(INCORRECT_FUNDER_NAME, fr.getFunderName(), is(FUNDER_NAME));
			assertThat(INCORRECT_FUNDER_ID, fr.getFunderID(), is(opt(mapElement.getValue())));
			assertThat(INCORRECT_AWARD_ID, fr.getAwardID(), is(opt(AWARD_ID)));
			assertThat(INCORRECT_AWARD_TITLE, fr.getAwardTitle(), is(opt(AWARD_TITLE)));
			assertThat(INCORRECT_AWARD_URL, fr.getAwardURL(), is(opt(new URL(AWARD_URL))));
		}
	}


	@Test
	public void buildWithNullOrWhitespace() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME)
				.withFunderID(nullOrWs)
				.withAwardID(nullOrWs)
				.withAwardTitle(nullOrWs)
				.withAwardURL(nullOrWs)
				.build();
			assertThat(INCORRECT_FUNDER_NAME, fr.getFunderName(), is(FUNDER_NAME));
			assertThat(INCORRECT_FUNDER_ID, fr.getFunderID(), is(ES));
			assertThat(INCORRECT_AWARD_ID, fr.getAwardID(), is(ES));
			assertThat(INCORRECT_AWARD_TITLE, fr.getAwardTitle(), is(ES));
			assertThat(INCORRECT_AWARD_URL, fr.getAwardURL(), is(ES));
		}
	}

	@Test
	public void buildAndOverwrite() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final FundingReference fr = FundingReference.getBuilder(FUNDER_NAME)
				.withFunderID(FUNDER_ID).withFunderID(nullOrWs)
				.withAwardID(AWARD_ID).withAwardID(nullOrWs)
				.withAwardTitle(AWARD_TITLE).withAwardTitle(nullOrWs)
				.withAwardURL(AWARD_URL).withAwardURL(nullOrWs)
				.build();
			assertThat(INCORRECT_FUNDER_NAME, fr.getFunderName(), is(FUNDER_NAME));
			assertThat(INCORRECT_FUNDER_ID, fr.getFunderID(), is(ES));
			assertThat(INCORRECT_AWARD_ID, fr.getAwardID(), is(ES));
			assertThat(INCORRECT_AWARD_TITLE, fr.getAwardTitle(), is(ES));
			assertThat(INCORRECT_AWARD_URL, fr.getAwardURL(), is(ES));
		}
	}

	@Test
	public void buildFailInvalidPIDs() throws Exception {
		for (String invalidPid : INVALID_PID_LIST) {
			try {
				FundingReference.getBuilder(FUNDER_NAME).withFunderID(invalidPid).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Illegal format for funderID: \"" + invalidPid + "\"\n" +
					"It should match the pattern \"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\""));
			}
		}
	}

	@Test
	public void buildFailInvalidAwardURL() throws Exception {
		try {
			FundingReference.getBuilder(FUNDER_NAME).withAwardURL(FUNDER_ID).build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
				"Illegal awardURL url '" + FUNDER_ID + "': unknown protocol: ror"));
		}
	}


	@Test
	public void buildFailNullOrWhitespaceFunderName() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			try {
				FundingReference.getBuilder(nullOrWs).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"funderName cannot be null or whitespace only"));
			}
		}

	}

}
