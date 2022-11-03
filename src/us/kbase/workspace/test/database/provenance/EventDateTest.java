package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import us.kbase.workspace.database.provenance.EventDate;

public class EventDateTest {
	static final String INCORRECT_DATE = "incorrect date";
	static final String INCORRECT_FORMAT = "incorrect date format";
	static final String INCORRECT_EVENT = "incorrect event";
	static final String EXP_EXC = "expected exception";

	static final String DATE_STRING = "2012-12-12";
	static final String DATE_STRING_UNTRIMMED = "  2012-12-12   ";

	static final String FORMAT_STRING = "YYYY-MM-DD";
	static final String FORMAT_STRING_UNTRIMMED = "  YYYY-MM-DD   ";

	static final String EVENT_STRING = "blah blah blah";
	static final String EVENT_STRING_UNTRIMMED = "  blah blah blah   ";

	static final String[] validDateFormatStrings = {
		"YYYY",
		"YYYY-MM",
		FORMAT_STRING,
		FORMAT_STRING_UNTRIMMED
	};

	static final String[] validDateStrings = {
		"2022",
		"2022-03",
		"2022-12-31",
		"1829-01-01",
		DATE_STRING,
		DATE_STRING_UNTRIMMED
	};


	static final String[] invalidDateFormatStrings = {
		"yyyy",
		"yyyy-dd",
		"YYYY-DD",
		"YY-MM",
		"YY-MM-DD",
		"YYYY-MM-DDTHH:MM:SS.fffZ",
		"YYYY-M-D",
		"YYYY.MM.DD"
	};

	static final String[] invalidDateStrings = {
		"22",
		"22.03.31",
		"2022/12/31",
		"2022-00-01",
		"2022-05-00",
		"2022-13-31",
		"2022-00-31",
		"2022-44-55",
		"22-31",
		"0987-01-01",
	};


	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(EventDate.class).usingGetClass().verify();
	}

	@Test
	public void buildEventDate() throws Exception {
		final EventDate ed1 = EventDate.getBuilder(DATE_STRING, FORMAT_STRING, EVENT_STRING).build();
		assertThat(INCORRECT_DATE, ed1.getDate(), is(DATE_STRING));
		assertThat(INCORRECT_FORMAT, ed1.getDateFormat(), is(FORMAT_STRING));
		assertThat(INCORRECT_EVENT, ed1.getEvent(), is(EVENT_STRING));
	}

	@Test
	public void buildEventDateFailDate() throws Exception {
		for (String dateStr : invalidDateStrings) {
			try {
				EventDate.getBuilder(dateStr, FORMAT_STRING, EVENT_STRING).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Illegal format for date: \"" + dateStr +
					"\"\nIt should match the pattern \"" +  EventDate.VALID_DATE_REGEX.toString() + "\""
				));
			}
		}
	}

	@Test
	public void buildEventDateFailDateFormat() throws Exception {
		for (String dateFormatStr : invalidDateFormatStrings) {
			try {
				EventDate.getBuilder(DATE_STRING, dateFormatStr, EVENT_STRING).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Illegal format for dateFormat: \"" + dateFormatStr +
					"\"\nIt should match the pattern \"" +  EventDate.VALID_DATE_FORMAT_REGEX.toString() + "\""
				));
			}
		}
	}

	@Test
	public void buildAndTrimEventDate() throws Exception {
		final EventDate ed1 = EventDate.getBuilder(DATE_STRING_UNTRIMMED, FORMAT_STRING_UNTRIMMED, EVENT_STRING_UNTRIMMED).build();
		assertThat(INCORRECT_DATE, ed1.getDate(), is(DATE_STRING));
		assertThat(INCORRECT_FORMAT, ed1.getDateFormat(), is(FORMAT_STRING));
		assertThat(INCORRECT_EVENT, ed1.getEvent(), is(EVENT_STRING));
	}

	@Test
	public void buildFailNullOrWhitespaceEventDate() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final String[][] nullOrWhitespaceArgs = {
				{nullOrWs, FORMAT_STRING, EVENT_STRING, "date"},
				{DATE_STRING, nullOrWs, EVENT_STRING, "dateFormat"},
				{DATE_STRING, FORMAT_STRING, nullOrWs, "event"}
			};

			for (String[] arr : nullOrWhitespaceArgs) {
				try {
					EventDate.getBuilder(arr[0], arr[1], arr[2]).build();
					fail(EXP_EXC);
				} catch (Exception got) {
					TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(arr[3] + " cannot be null or whitespace only"));
				}
			}
		}
	}
}
