package us.kbase.workspace.database.provenance;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import us.kbase.workspace.database.Util;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;

/**
 * The date of a specified event.
 */
public class EventDate {

	private final String date;
	private final Event event;

	private EventDate(
			final String date,
			final Event event) {
		this.date = date;
		this.event = event;
	}

	/**
	 * Gets the date, for example "2022-05-10".
	 * Dates are returned in the format yyyy-MM-dd; if no data is available for the
	 * month or day, the string is truncated to yyyy-MM or yyyy respectively.
	 *
	 * @return the date.
	 */
	public String getDate() {
		return date;
	}

	/**
	 * Gets the event that occurred on the date in question, for example
	 * {@link Event#UPDATED}.
	 *
	 * @return the event.
	 */
	public Event getEvent() {
		return event;
	}

	@Override
	public int hashCode() {
		return Objects.hash(date, event);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EventDate other = (EventDate) obj;
		return Objects.equals(date, other.date) && Objects.equals(event, other.event);
	}

	/**
	 * Ensures that a string can be parsed as a valid date.
	 *
	 * @param protoDate the string that may or may not be a date
	 * @return the valid string
	 * @throws IllegalArgumentException if the string can't be parsed as a date
	 */
	private static String checkDate(final String protoDate) {
		final String[] dateParts = protoDate.split("-");
		if (dateParts.length < 4) {
			try {
				switch (dateParts.length) {
					case 1:
						final Year y = Year.parse(protoDate);
						return y.toString();
					case 2:
						final YearMonth ym = YearMonth.parse(protoDate);
						return ym.toString();
					default:
						final LocalDate ymd = LocalDate.parse(protoDate,
								DateTimeFormatter.ISO_LOCAL_DATE);
						return ymd.toString();
				}
			} catch (DateTimeParseException e) {
				// report the error below as an IllegalArgumentException
			}
		}
		throw new IllegalArgumentException(
				"Invalid date: \"" + protoDate
						+ "\"\ndate must be in the format yyyy, yyyy-MM, or yyyy-MM-dd and be a valid combination of day, month, and year.");
	}

	/**
	 * Builds an {@link EventDate}.
	 *
	 * @param date  the date when the event occurred,
	 *              in the format yyyy-MM-dd, yyyy-MM, or yyyy.
	 * @param event the event that occurred on that date, as an Event
	 * @return the new {@link EventDate}.
	 */
	public static EventDate build(final String date, final Event event) {
		return build(date, event, new ArrayList<>());
	}

	/**
	 * Builds an {@link EventDate}.
	 *
	 * @param date  the date when the event occurred,
	 *              in the format yyyy-MM-dd, yyyy-MM, or yyyy.
	 * @param event the event that occurred on that date, as a string.
	 *              See the {@link Event} class for valid string values.
	 * @return the new {@link EventDate}.
	 */
	public static EventDate build(final String date, final String event) {
		final List<String> errorList = new ArrayList<>();
		Event eventObject = null;
		try {
			eventObject = Event.getEvent(event);
		} catch (Exception e) {
			errorList.add(e.getMessage());
		}
		return build(date, eventObject, errorList);
	}

	/**
	 * Builds an {@link EventDate}.
	 *
	 * @param date      the date when the event occurred,
	 *                  in the format yyyy-MM-dd, yyyy-MM, or yyyy.
	 * @param event     the event that occurred on that date, as an Event
	 * @param errorList list of error strings accumulated during the `build` method
	 *
	 * @return the new {@link EventDate}.
	 */
	private static EventDate build(final String date, final Event event, final List<String> errorList) {
		if (event == null && errorList.isEmpty()) {
			errorList.add("event cannot be null");
		}

		String protoDate = null;
		try {
			final String trimmedDate = Util.checkString(date, "date");
			protoDate = checkDate(trimmedDate);
		} catch (Exception e) {
			errorList.add(e.getMessage());
		}

		if (errorList.isEmpty()) {
			return new EventDate(protoDate, event);
		}
		throw new IllegalArgumentException(
				"Errors in EventDate construction:\n" + String.join("\n", errorList));
	}
}