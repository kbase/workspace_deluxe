package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import us.kbase.workspace.database.provenance.EventDate;

public class EventDateTest {
	static final String INCORRECT_DATE = "incorrect date";
	static final String INCORRECT_EVENT = "incorrect event";
	static final String EXP_EXC = "expected exception";

	static final String DATE_STRING = "2022-12-12";
	static final String EVENT_STRING = "blah blah blah";
	static final String EVENT_STRING_UNTRIMMED = " \n\n\n blah blah blah \r\n  ";

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
		"22",
		"22.03.31",
		"2022/12/31",
		"2022-00-01",
		"2022-05-00",
		"2022-13-31",
		"2022-00-31",
		"2022-44-55",
		"22-31",
		"987-01-01",
	};


	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(EventDate.class).usingGetClass().verify();
	}

	@Test
	public void buildEventDate() throws Exception {
		for (String key : DATE_INPUTS.keySet()) {
			final EventDate ed1 = EventDate.getBuilder(key, EVENT_STRING).build();
			assertThat(INCORRECT_DATE, ed1.getDate(), is(DATE_INPUTS.get(key)));
			assertThat(INCORRECT_EVENT, ed1.getEvent(), is(EVENT_STRING));

		}
	}

	@Test
	public void buildEventDateFailDate() throws Exception {
		for (String dateStr : invalidDateStrings) {
			try {
				EventDate.getBuilder(dateStr, EVENT_STRING).build();
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
	public void buildAndTrimEvent() throws Exception {
		final EventDate ed1 = EventDate.getBuilder(DATE_STRING, EVENT_STRING_UNTRIMMED).build();
		assertThat(INCORRECT_DATE, ed1.getDate(), is(DATE_STRING));
		assertThat(INCORRECT_EVENT, ed1.getEvent(), is(EVENT_STRING));
	}

	@Test
	public void buildFailNullOrWhitespaceEventDate() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final String[][] nullOrWhitespaceArgs = {
				{nullOrWs, EVENT_STRING, "date"},
				{DATE_STRING, nullOrWs, "event"}
			};

			for (String[] arr : nullOrWhitespaceArgs) {
				try {
					EventDate.getBuilder(arr[0], arr[1]).build();
					fail(EXP_EXC);
				} catch (Exception got) {
					TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(arr[2] + " cannot be null or whitespace only"));
				}
			}
		}
	}
}
