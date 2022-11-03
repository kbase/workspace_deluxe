package us.kbase.workspace.database.provenance;

import java.util.Objects;
import java.util.regex.Pattern;

import us.kbase.workspace.database.Util;

/**
 * The date of a specified event.
 */
public class EventDate {

	private final String date;
	private final String dateFormat;
	private final String event;

	public static final Pattern VALID_DATE_FORMAT_REGEX = Pattern.compile("^YYYY(-MM(-DD)?)?$");

	public static final Pattern VALID_DATE_REGEX = Pattern.compile("^[12]\\d{3}(-(0[1-9]|1[0-2])(-(0[1-9]|[12]\\d|3[01]))?)?$");

	private EventDate(
		final String date,
		final String dateFormat,
		final String event) {
		this.date = date;
		this.dateFormat = dateFormat;
		this.event = event;
	}

	/** Get the date, for example "2022-05-10".
	 * @return the date.
	 */
	public String getDate() {
		return date;
	}

	/** Get the format of the date, for example "YYYY-MM-DD".
	 * @return the date format.
	 */
	public String getDateFormat() {
		return dateFormat;
	}

	/** Get the event that occurred on the date in question, for example "updated".
	 * @return the event.
	 */
	public String getEvent() {
		return event;
	}

	@Override
	public int hashCode() {
		return Objects.hash(date, dateFormat, event);
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
		return Objects.equals(date, other.date) && Objects.equals(dateFormat, other.dateFormat)
				&& Objects.equals(event, other.event);
	}

	/** Get a builder for an {@link EventDate}.
	 * @return the builder.
	 */
	public static Builder getBuilder(final String date, final String dateFormat, final String event) {
		return new Builder(date, dateFormat, event);
	}

	/** A builder for an {@link EventDate}. */
	public static class Builder {

		private String date;
		private String dateFormat;
		private String event;

		private Builder(final String date, final String dateFormat, final String event) {
			this.date = Common.checkAgainstRegex(date, VALID_DATE_REGEX, "date", false);
			this.dateFormat = Common.checkAgainstRegex(dateFormat, VALID_DATE_FORMAT_REGEX, "dateFormat", false);
			this.event = Util.checkString(event, "event");
		}

		/** Build the {@link EventDate}.
		 * @return the external data.
		 */
		public EventDate build() {
			return new EventDate(date, dateFormat, event);
		}
	}
}
