package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import us.kbase.workspace.database.provenance.EventDate;
import us.kbase.workspace.database.provenance.Event;

@Category(us.kbase.common.test.ProvenanceTests.class)
public class EventDateTest {
	static final String INCORRECT_DATE = "incorrect date";
	static final String INCORRECT_EVENT = "incorrect event";
	static final String EXP_EXC = "expected exception";

	static final String DATE_FORMAT_ERROR = "\ndate must be in the format yyyy, yyyy-MM, or yyyy-MM-dd and be a valid combination of day, month, and year.";
	static final String DATE_NO_NULL_WS = "date cannot be null or whitespace only";
	static final String EVENT_NO_NULL_WS = "event cannot be null or whitespace only";

	static final String DATE_STRING = "2022-12-12";
	static final String EVENT_STRING = "accepted";
	static final String EVENT_STRING_UNTRIMMED = " \n\n\n accepted \r\n  ";
	static final Event SOME_EVENT = Event.ACCEPTED;
	static final Map<String, String> DATE_INPUTS;
	static {
		Map<String, String> dateInputs = new HashMap<>();
		dateInputs.put("2112", "2112");
		dateInputs.put("1989-06", "1989-06");
		dateInputs.put(DATE_STRING, DATE_STRING);
		dateInputs.put("\n\n2112\t ", "2112");
		dateInputs.put("    \f 2022-12\r\n  ", "2022-12");
		dateInputs.put("  \n  2022-12-12\f\n  \n", DATE_STRING);
		DATE_INPUTS = Collections.unmodifiableMap(dateInputs);
	}

	static final String[] invalidDateStrings = {
		"a porcupine with a hat on",
		"22",
		"22.03.31",
		"2022/12/31",
		"2022-00-01",
		"2022-05-00",
		"2022-13-31",
		"2022-00-31",
		"2022-44-55",
		"2022-04-31",
		"20-22-22-04",
		"22-31",
		"987-01-01",
		"2022-1",
		"2022-1-1",
	};

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(EventDate.class).usingGetClass().verify();
	}

	private void buildEventDateFailWithError(final String dateInput, final String eventInput, final String error) {
		try {
			EventDate.build(dateInput, eventInput);
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got,
					new IllegalArgumentException("Errors in EventDate construction:\n" + error));
		}
	}

	private void buildEventDateFailWithError(final String dateInput, final Event eventInput, final String error) {
		try {
			EventDate.build(dateInput, eventInput);
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got,
					new IllegalArgumentException("Errors in EventDate construction:\n" + error));
		}
	}

	@Test
	public void buildEventDateWithEnum() throws Exception {
		for (Map.Entry<String, String> entry : DATE_INPUTS.entrySet()) {
			final EventDate ed1 = EventDate.build(entry.getKey(), SOME_EVENT);
			assertThat(INCORRECT_DATE, ed1.getDate(), is(entry.getValue()));
			assertThat(INCORRECT_EVENT, ed1.getEvent(), is(SOME_EVENT));
		}
	}

	@Test
	public void buildEventDateWithString() throws Exception {
		for (Map.Entry<String, String> entry : DATE_INPUTS.entrySet()) {
			final EventDate ed1 = EventDate.build(entry.getKey(), EVENT_STRING);
			assertThat(INCORRECT_DATE, ed1.getDate(), is(entry.getValue()));
			assertThat(INCORRECT_EVENT, ed1.getEvent(), is(SOME_EVENT));

		}
	}

	@Test
	public void buildEventDateFailDate() throws Exception {
		for (String dateStr : invalidDateStrings) {
			buildEventDateFailWithError(dateStr, SOME_EVENT, "Invalid date: \"" + dateStr
					+ "\"" + DATE_FORMAT_ERROR);
		}
	}

	@Test
	public void buildAndTrimEvent() throws Exception {
		final EventDate ed1 = EventDate.build(DATE_STRING, EVENT_STRING_UNTRIMMED);
		assertThat(INCORRECT_DATE, ed1.getDate(), is(DATE_STRING));
		assertThat(INCORRECT_EVENT, ed1.getEvent(), is(SOME_EVENT));
	}

	@Test
	public void buildEventDateFailEvent() throws Exception {
		buildEventDateFailWithError(
				DATE_STRING, "kookaburra",
				"Invalid event: kookaburra");
	}

	@Test
	public void buildEventDateFailNullEvent() throws Exception {
		buildEventDateFailWithError(DATE_STRING, (Event) null, "event cannot be null");
	}

	@Test
	public void buildFailNullOrWhitespaceDate() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildEventDateFailWithError(nullOrWs, EVENT_STRING, DATE_NO_NULL_WS);
		}
	}

	@Test
	public void buildFailNullOrWhitespaceEvent() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildEventDateFailWithError(DATE_STRING, nullOrWs, EVENT_NO_NULL_WS);
		}
	}

	@Test
	public void buildFailNullOrWhitespaceEventDate() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildEventDateFailWithError(nullOrWs, nullOrWs, EVENT_NO_NULL_WS + "\n" + DATE_NO_NULL_WS);
		}
	}

	@Test
	public void buildEventDateFailDateEvent() throws Exception {
		for (String dateStr : invalidDateStrings) {
			buildEventDateFailWithError(dateStr, "kookaburra",
					"Invalid event: kookaburra\n" +
							"Invalid date: \"" + dateStr
							+ "\"" + DATE_FORMAT_ERROR);
		}
	}
}
