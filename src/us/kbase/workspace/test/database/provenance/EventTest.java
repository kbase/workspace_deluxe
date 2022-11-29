package us.kbase.workspace.test.database.provenance;

import org.junit.Test;

import us.kbase.workspace.database.provenance.Event;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import us.kbase.common.test.TestCommon;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

public class EventTest {

	@Test
	public void testGetEventName() throws Exception {
		final Event wre = Event.ISSUED;
		assertThat("incorrect eventName",
				wre.getEventName(),
				is("issued"));
	}

	@Test
	public void testGetEvent() throws Exception {
		final String[] validEvents = {
				"collected",
				"COLLECTED",
				"    \r\r\nCOLLECTED\n\n",
				"Collected\n"
		};

		for (final String validEvent : validEvents) {
			assertThat("incorrect event",
					Event.getEvent(validEvent),
					is(Event.COLLECTED));
		}
	}

	private void getEventFail(final String input, final String error) {
		try {
			Event.getEvent(input);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(error));
		}
	}

	@Test
	public void testGetEventFail() throws Exception {
		final String[] invalidEvents = {
				"catastrophic loss of life",
				"Event:ISSUED",
				"    Event:ISSUED\r",
		};

		for (final String invalidEvent : invalidEvents) {
			getEventFail(invalidEvent, "Invalid event: " + invalidEvent);
		}
	}

	@Test
	public void testGetEventNullOrWs() throws Exception {
		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			getEventFail(nullOrWs, "event cannot be null or whitespace only");
		}
	}
}
