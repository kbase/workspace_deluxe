package us.kbase.workspace.database.provenance;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import us.kbase.workspace.database.Util;

/**
 * Represents a title or name of a resource.
 */
public class Title {

	private final String titleString;
	private final TitleType titleType;
	private final String titleLanguage;

	/**
	 * An enum representing types of title.
	 */
	public static enum TitleType {
		SUBTITLE,
		ALTERNATIVE_TITLE,
		TRANSLATED_TITLE,
		OTHER;

		private static final Map<String, TitleType> STRING_TO_TITLE_TYPE_MAP = new HashMap<>();
		static {
			for (final TitleType tt: TitleType.values()) {
				STRING_TO_TITLE_TYPE_MAP.put(tt.name().toLowerCase(), tt);
			}
		}

		/** Gets a title type based on a supplied string.
		 * @param input a string representing a title type.
		 * @return a TitleType.
		 * @throws IllegalArgumentException if there is no title type related to the input string.
		 */
		public static TitleType getTitleType(final String input) {
			final String lowercaseInput = Util.checkString(input, "input").toLowerCase();
			if (STRING_TO_TITLE_TYPE_MAP.containsKey(lowercaseInput)) {
				return STRING_TO_TITLE_TYPE_MAP.get(lowercaseInput);
			}
			throw new IllegalArgumentException("Invalid titleType: " + input);
		}
	}

	private Title(
			final String titleString,
			final TitleType titleType,
			final String titleLanguage) {
		this.titleString = titleString;
		this.titleLanguage = titleLanguage;
		this.titleType = titleType;
	}

	/**
	 * Gets the title of the resource.
	 *
	 * @return the titleString.
	 */
	public String getTitleString() {
		return titleString;
	}

	/**
	 * Gets the type of title being represented.
	 *
	 * @return the titleType, if present.
	 */
	public Optional<TitleType> getTitleType() {
		return Optional.ofNullable(titleType);
	}

	/**
	 * Gets the language in which the title is written.
	 *
	 * @return the titleLanguage, if present.
	 */
	public Optional<String> getTitleLanguage() {
		return Optional.ofNullable(titleLanguage);
	}

	@Override
	public int hashCode() {
		return Objects.hash(titleLanguage, titleString, titleType);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Title other = (Title) obj;
		return Objects.equals(titleLanguage, other.titleLanguage)
				&& Objects.equals(titleString, other.titleString)
				&& Objects.equals(titleType, other.titleType);
	}

	/**
	 * Gets a builder for an {@link Title}.
	 *
	 * @return the builder.
	 */
	public static Builder getBuilder(final String titleString) {
		return new Builder(titleString);
	}

	/** A builder for an {@link Title}. */
	public static class Builder {

		private String titleString;
		private TitleType titleType = null;
		private String titleLanguage = null;

		private Builder(final String titleString) {
			this.titleString = Util.checkString(titleString, "titleString");
		}

		/**
		 * Sets the language of title being represented, for example en-GB.
		 *
		 * @param titleLanguage the language. Null or the empty string removes any
		 *                      current resource in the builder.
		 * @return this builder.
		 */
		public Builder withTitleLanguage(final String titleLanguage) {
			this.titleLanguage = Common.processString(titleLanguage);
			return this;
		}

		/**
		 * Sets the type of the title being represented, for example translated_title.
		 *
		 * @param titleType the titleType as a string. Null or the empty string removes any
		 *                  current titleType in the builder.
		 * @return this builder.
		 */
		public Builder withTitleType(final String titleType) {
			final String protoTitleType = Common.processString(titleType);
			this.titleType = protoTitleType == null
				? null
				: TitleType.getTitleType(protoTitleType);
			return this;
		}

		/**
		 * Sets the type of the title being represented, for example translated_title.
		 *
		 * @param titleType the titleType as an enum. Null removes any
		 *                  current titleType in the builder.
		 * @return this builder.
		 */
		public Builder withTitleType(final TitleType titleType) {
			this.titleType = titleType;
			return this;
		}

		/**
		 * Build the {@link Title}.
		 *
		 * @return the title object.
		 */
		public Title build() {
			return new Title(titleString, titleType, titleLanguage);
		}
	}
}
