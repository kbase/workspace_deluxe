package us.kbase.workspace.test.database.provenance;

import org.junit.Test;

import us.kbase.workspace.database.provenance.Event;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import us.kbase.common.test.TestCommon;

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

	@Test
	public void testGetEventFail() throws Exception {

		final String[] invalidEvents = {
				"catastrophic loss of life",
				"Event:ISSUED",
		};

		for (final String invalidEvent : invalidEvents) {
			try {
				Event.getEvent(invalidEvent);
				fail("expected exception");
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"Invalid event: " + invalidEvent));
			}
		}
	}
}
