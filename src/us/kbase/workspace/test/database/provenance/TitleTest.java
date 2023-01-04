package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;

import org.junit.Test;
import java.util.Optional;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import static us.kbase.common.test.TestCommon.ES;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import us.kbase.workspace.database.provenance.Title;
import us.kbase.workspace.database.provenance.Title.TitleType;

public class TitleTest {
	static final String INCORRECT_TITLE = "incorrect title string";
	static final String INCORRECT_TYPE = "incorrect title type";
	static final String INCORRECT_LANG = "incorrect title language";
	static final String EXP_EXC = "expected exception";

	static final String TITLE_STRING = "A Series of Unfortunate Elephants";
	static final String TITLE_STRING_UNTRIMMED = "\t\t\f\t\n\rA Series of Unfortunate Elephants\n\n\n   ";

	static final String TYPE_STRING = "translated_title";
	static final String TYPE_STRING_UNTRIMMED = "  \n translated_title   \t\n  ";

	static final String LANG_STRING = "de";
	static final String LANG_STRING_UNTRIMMED = "\nde\n";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(Title.class).usingGetClass().verify();
	}

	private void assertTitleFields(final Title title, TitleType titleType, Optional<String> titleLanguage) {
		assertThat(INCORRECT_TITLE, title.getTitleString(), is(TITLE_STRING));
		assertThat(INCORRECT_TYPE, title.getTitleType(), is(titleType));
		assertThat(INCORRECT_LANG, title.getTitleLanguage(), is(titleLanguage));
	}

	private void assertTitleFields(final Title title) {
		assertTitleFields(title, TitleType.TITLE, ES);
	}

	@Test
	public void buildMinimal() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING).build();
		assertTitleFields(title);
	}

	@Test
	public void buildMaximal() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING)
				.withTitleType(TYPE_STRING)
				.withTitleLanguage(LANG_STRING).build();
		assertTitleFields(title, TitleType.TRANSLATED_TITLE, opt(LANG_STRING));
	}

	@Test
	public void buildMaximalWithTitleTypeClass() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING)
				.withTitleType(TitleType.OTHER)
				.withTitleLanguage(LANG_STRING).build();
		assertTitleFields(title, TitleType.OTHER, opt(LANG_STRING));
	}

	@Test
	public void buildTrimAllFields() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING_UNTRIMMED)
				.withTitleType(TYPE_STRING_UNTRIMMED)
				.withTitleLanguage(LANG_STRING_UNTRIMMED).build();
		assertTitleFields(title, TitleType.TRANSLATED_TITLE, opt(LANG_STRING));
	}

	@Test
	public void buildTransformTitleLanguage() throws Exception {
		// these tags are valid BCP-47 syntax, even if the values themselves are
		// nonsense.
		final String[][] langPairs = {
				{ "FR", "fr" },
				{ "en-Latn-US", "en-Latn-US" },
				{ "en_Latn_US_X_PRIVATE_TAG", "en-Latn-US-x-private-tag" },
				{ "eng", "eng" },
				{ "CN", "cn" },
				{ "ENGLISH", "english" },
				{ "ZZ-NONSENSE", "zz-NONSENSE" },
				{ "WTF_h0gWA5H", "wtf-h0gWA5H" },
				{ "zh_hant_cn", "zh-Hant-CN" },
				{ "ZH_HANT_CN", "zh-Hant-CN" },
				{ "en_Mole_US", "en-Mole-US" },
				{ "mn-cyrl-be", "mn-Cyrl-BE" },
				{ "mn-CYRL", "mn-Cyrl" },
				{ "de-de-1996", "de-DE-1996" },
				{ "pt-BR-abl1943", "pt-BR-abl1943" },
				{ "x-NOT_A_LANGUAGE", "x-not-a-language" },
				{ "en-GB-oed", "en-GB-x-oed" },
				{ "hell_on_earth", "hell-ON-earth" },
				{ "NOT-this-ol-x-crap-again", "not-This-OL-x-crap-again" },
				{ "en-BRITISH_ENGLISH", "en-BRITISH-ENGLISH" },
		};

		for (String[] lp : langPairs) {
			final Title title = Title.getBuilder(TITLE_STRING_UNTRIMMED)
					.withTitleType(TYPE_STRING_UNTRIMMED)
					.withTitleLanguage(lp[0])
					.build();
			assertTitleFields(title, TitleType.TRANSLATED_TITLE, opt(lp[1]));
		}
	}

	@Test
	public void buildWithNullOrWhitespaceTitleTypeTitleLanguage() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Title title = Title.getBuilder(TITLE_STRING)
					.withTitleType(nullOrWs)
					.withTitleLanguage(nullOrWs)
					.build();
			assertTitleFields(title);
		}
	}

	@Test
	public void buildAndOverwriteTitleTypeTitleLanguage() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Title title = Title.getBuilder(TITLE_STRING)
					.withTitleType(TYPE_STRING).withTitleType(nullOrWs)
					.withTitleLanguage(LANG_STRING).withTitleLanguage(nullOrWs)
					.build();
			assertTitleFields(title);
		}
	}

	@Test
	public void buildAndOverwriteTitleTypeClass() throws Exception {
		// using whitespace or null as the second TitleType
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Title title = Title.getBuilder(TITLE_STRING)
					.withTitleType(TitleType.ALTERNATIVE_TITLE).withTitleType(nullOrWs)
					.build();
			assertTitleFields(title);
		}
		// using a null TitleType as the second TitleType
		final Title title2 = Title.getBuilder(TITLE_STRING)
				.withTitleType(TitleType.ALTERNATIVE_TITLE).withTitleType((TitleType) null)
				.build();
		assertTitleFields(title2);
	}

	/**
	 * Checks the error thrown when a Title is built with invalid arguments
	 *
	 * @param builder
	 *                title builder, complete with args
	 * @param error
	 *                the expected error
	 */
	private void buildTitleFailWithError(final Title.Builder builder, final String errorString) {
		try {
			builder.build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Errors in Title construction:\n" + errorString));
		}
	}

	@Test
	public void buildFailNullOrWhitespaceTitle() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildTitleFailWithError(
					Title.getBuilder(nullOrWs),
					"titleString cannot be null or whitespace only");
		}
	}

	@Test
	public void buildFailInvalidTitleType() throws Exception {
		buildTitleFailWithError(
				Title.getBuilder(TITLE_STRING).withTitleType("unsanctioned"),
				"Invalid titleType: unsanctioned");
	}

	@Test
	public void buildFailInvalidTitleLanguage() throws Exception {
		final String TLE = "titleLanguage error: ";
		final String[][] invalidLanguageList = {
				{ TITLE_STRING, TLE
						+ "Invalid subtag: A Series of Unfortunate Elephants [at index 0]" },
				{ "i-english", TLE + "Invalid subtag: i [at index 0]" },
				{ "2-pac", TLE + "Invalid subtag: 2 [at index 0]" },
				// wrong ordering
				{ "en-US-GRRR", TLE + "Invalid subtag: GRRR [at index 6]" },
				{ "en_US_Gulp_", TLE + "Invalid subtag: Gulp [at index 6]" },
				// empty trailing subtag
				{ "en_Blob_US_", TLE + "Empty subtag [at index 11]" },
				// back is invalid
				{ "to-hell_an-back", TLE + "Invalid subtag: back [at index 11]" },
				{ "en_BRITISH_ENGLISH_FTW", TLE + "Invalid subtag: FTW [at index 19]", },
		};

		for (String[] langPair : invalidLanguageList) {
			buildTitleFailWithError(
					Title.getBuilder(TITLE_STRING).withTitleLanguage(langPair[0]),
					langPair[1]);
		}
	}

	@Test
	public void buildFailNullOrWsTitleInvalidTitleType() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildTitleFailWithError(
					Title.getBuilder(nullOrWs)
							.withTitleType("unholy and daemonic"),
					"titleString cannot be null or whitespace only\n" +
							"Invalid titleType: unholy and daemonic");
		}
	}
}
