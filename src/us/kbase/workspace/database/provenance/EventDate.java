package us.kbase.workspace.database.provenance;

import java.util.Objects;
import java.util.regex.Pattern;
import us.kbase.workspace.database.Util;

/**
 * The date of a specified event.
 */
public class EventDate {

	private final String date;
	private final String event;

	/**
	 * VALID_DATE_REGEX ensures that dates are in one of the following formats:
	 * yyyy
	 * yyyy-MM
	 * yyyy-MM-dd
	 *
	 * It also ensures that months are in the range 00-12 and days are in the range
	 * 00-31.
	 */
	public static final Pattern VALID_DATE_REGEX = Pattern.compile(
			"^[12]\\d{3}(-(0[1-9]|1[0-2])(-(0[1-9]|[12]\\d|3[01]))?)?$");

	private EventDate(
			final String date,
			final String event) {
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
	 * Gets the event that occurred on the date in question, for example "updated".
	 *
	 * @return the event.
	 */
	public String getEvent() {
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
	 * Gets a builder for an {@link EventDate}.
	 *
	 * @param date  the date when the event occurred,
	 *              in the format yyyy-MM-dd, yyyy-MM, or yyyy.
	 * @param event the event that occurred on that date
	 * @return the builder.
	 */
	public static Builder getBuilder(final String date, final String event) {
		return new Builder(date, event);
	}

	/** A builder for an {@link EventDate}. */
	public static class Builder {

		private String date;
		private String event;

		private Builder(final String date, final String event) {
			this.event = Util.checkString(event, "event");
			final String protoDate = Util.checkString(date, "date");
			this.date = Common.checkAgainstRegex(protoDate, VALID_DATE_REGEX, "date", false);
		}

		/**
		 * Builds the {@link EventDate}.
		 *
		 * @return the event date.
		 */
		public EventDate build() {
			return new EventDate(date, event);
		}
	}
}
