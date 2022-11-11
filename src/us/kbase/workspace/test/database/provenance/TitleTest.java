package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;

import org.junit.Test;

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

	private void assertTitleStringAndOptionalNulls(final Title title) {
		assertThat(INCORRECT_TITLE, title.getTitleString(), is(TITLE_STRING));
		assertThat(INCORRECT_TYPE, title.getTitleType(), is(ES));
		assertThat(INCORRECT_LANG, title.getTitleLanguage(), is(ES));
	}

	@Test
	public void buildMinimal() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING).build();
		assertTitleStringAndOptionalNulls(title);
	}

	@Test
	public void buildMaximal() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING)
				.withTitleType(TYPE_STRING)
				.withTitleLanguage(LANG_STRING).build();
		assertThat(INCORRECT_TITLE, title.getTitleString(), is(TITLE_STRING));
		assertThat(INCORRECT_TYPE, title.getTitleType(), is(opt(TitleType.TRANSLATED_TITLE)));
		assertThat(INCORRECT_LANG, title.getTitleLanguage(), is(opt(LANG_STRING)));
	}

	@Test
	public void buildMaximalWithTitleTypeClass() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING)
				.withTitleType(TitleType.OTHER)
				.withTitleLanguage(LANG_STRING).build();
		assertThat(INCORRECT_TITLE, title.getTitleString(), is(TITLE_STRING));
		assertThat(INCORRECT_TYPE, title.getTitleType(), is(opt(TitleType.OTHER)));
		assertThat(INCORRECT_LANG, title.getTitleLanguage(), is(opt(LANG_STRING)));
	}

	@Test
	public void buildTrimAllFields() throws Exception {
		final Title title = Title.getBuilder(TITLE_STRING_UNTRIMMED)
				.withTitleType(TYPE_STRING_UNTRIMMED)
				.withTitleLanguage(LANG_STRING_UNTRIMMED).build();
		assertThat(INCORRECT_TITLE, title.getTitleString(), is(TITLE_STRING));
		assertThat(INCORRECT_TYPE, title.getTitleType(), is(opt(TitleType.TRANSLATED_TITLE)));
		assertThat(INCORRECT_LANG, title.getTitleLanguage(), is(opt(LANG_STRING)));
	}

	@Test
	public void buildWithNullOrWhitespaceTitleTypeTitleLanguage() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Title title = Title.getBuilder(TITLE_STRING)
					.withTitleType(nullOrWs)
					.withTitleLanguage(nullOrWs)
					.build();
			assertTitleStringAndOptionalNulls(title);
		}
	}

	@Test
	public void buildAndOverwriteTitleTypeTitleLanguage() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Title title = Title.getBuilder(TITLE_STRING)
					.withTitleType(TYPE_STRING).withTitleType(nullOrWs)
					.withTitleLanguage(LANG_STRING).withTitleLanguage(nullOrWs)
					.build();
			assertTitleStringAndOptionalNulls(title);
		}
	}

	@Test
	public void buildAndOverwriteTitleTypeClass() throws Exception {
		// using whitespace or null as the second TitleType
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Title title = Title.getBuilder(TITLE_STRING)
					.withTitleType(TitleType.ALTERNATIVE_TITLE).withTitleType(nullOrWs)
					.build();
			assertTitleStringAndOptionalNulls(title);
		}
		// using a null TitleType as the second TitleType
		final Title title2 = Title.getBuilder(TITLE_STRING)
				.withTitleType(TitleType.ALTERNATIVE_TITLE).withTitleType((TitleType) null)
				.build();
		assertTitleStringAndOptionalNulls(title2);
	}

	@Test
	public void buildFailNullOrWhitespaceTitle() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			try {
				Title.getBuilder(nullOrWs).build();
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"titleString cannot be null or whitespace only"));
			}
		}
	}

	@Test
	public void buildFailInvalidTitleType() throws Exception {
		try {
			Title.getBuilder(TITLE_STRING).withTitleType("unsanctioned").build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got,
					new IllegalArgumentException("Invalid titleType: unsanctioned"));
		}
	}
}
