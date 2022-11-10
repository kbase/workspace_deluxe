package us.kbase.workspace.database.provenance;

import java.util.HashMap;
import java.util.Map;
import us.kbase.workspace.database.Util;

/**
 * A class representing resource lifecycle events
 */

public enum Event {

	ACCEPTED("accepted"),
	AVAILABLE("available"),
	COPYRIGHTED("copyrighted"),
	COLLECTED("collected"),
	CREATED("created"),
	ISSUED("issued"),
	SUBMITTED("submitted"),
	UPDATED("updated"),
	VALID("valid"),
	WITHDRAWN("withdrawn"),
	OTHER("other");

	private static final Map<String, Event> STRING_TO_EVENT_MAP = new HashMap<>();
	static {
		for (final Event e : Event.values()) {
			STRING_TO_EVENT_MAP.put(e.getEventName(), e);
		}
	}

	private final String eventName;

	private Event(final String eventName) {
		this.eventName = eventName;
	}

	/**
	 * Get the event name.
	 *
	 * @return the eventName.
	 */
	public String getEventName() {
		return eventName;
	}

	/**
	 * Get an event based on a supplied string.
	 *
	 * @param input a string representing an event.
	 * @return an Event.
	 * @throws IllegalArgumentException if there is no event corresponding to the
	 *                                  input string.
	 */
	public static Event getEvent(final String input) {
		final String lowercaseInput = Util.checkString(input, "event").toLowerCase();
		if (!STRING_TO_EVENT_MAP.containsKey(lowercaseInput)) {
			throw new IllegalArgumentException("Invalid event: " + input);
		}
		return STRING_TO_EVENT_MAP.get(lowercaseInput);
	}
}
