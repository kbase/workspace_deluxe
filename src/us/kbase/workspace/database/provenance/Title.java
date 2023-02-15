package us.kbase.workspace.database.provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import us.kbase.workspace.database.Util;
import java.util.Locale;
import java.util.IllformedLocaleException;

/**
 * Represents a title or name of a resource.
 */
public class Title {

	/**
	 * An enum representing types of title.
	 */
	public enum TitleType {
		TITLE,
		SUBTITLE,
		ALTERNATIVE_TITLE,
		TRANSLATED_TITLE,
		OTHER;

		private static final Map<String, TitleType> STRING_TO_TITLE_TYPE_MAP = new HashMap<>();
		static {
			for (final TitleType tt : TitleType.values()) {
				STRING_TO_TITLE_TYPE_MAP.put(tt.name().toLowerCase(), tt);
			}
		}

		/**
		 * Gets a title type based on a supplied string.
		 *
		 * @param titleType
		 *                a string representing a title type.
		 * @return a TitleType.
		 * @throws IllegalArgumentException
		 *                 if there is no title type related to the input string.
		 */
		public static TitleType getTitleType(final String titleType) {
			final String lowercaseInput = Util.checkString(titleType, "titleType").toLowerCase();
			if (STRING_TO_TITLE_TYPE_MAP.containsKey(lowercaseInput)) {
				return STRING_TO_TITLE_TYPE_MAP.get(lowercaseInput);
			}
			throw new IllegalArgumentException("Invalid titleType: " + titleType);
		}
	}

	private static final TitleType DEFAULT_TITLE_TYPE = TitleType.TITLE;

	private final String titleString;
	private final TitleType titleType;
	private final String titleLanguage;

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
	 * @return the titleType; a null value returns the default titleType
	 *         TitleType.TITLE.
	 */
	public TitleType getTitleType() {
		return titleType;
	}

	/**
	 * Gets the language in which the title is written, in IETF BCP-47-compliant syntax.
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
		private List<String> errorList = new ArrayList<>();

		private Builder(final String titleString) {
			try {
				this.titleString = Util.checkString(titleString, "titleString");
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
		}

		/**
		 * Sets the language of title being represented, for example 'en_GB'.
		 *
		 * @param titleLanguage
		 *                the language. Null or the empty string removes any
		 *                current value in the builder.
		 * @return this builder.
		 */
		public Builder withTitleLanguage(final String titleLanguage) {
			this.titleLanguage = Common.processString(titleLanguage);
			return this;
		}

		/**
		 * Sets the type of the title being represented, for example 'subtitle'.
		 *
		 * If the title type is 'translated_title', the title language must be set.
		 *
		 * @param titleType
		 *                the titleType as a string. Null or the empty string resets
		 *                the current titleType in the builder.
		 * @return this builder.
		 */
		public Builder withTitleType(final String titleType) {
			final String protoTitleType = Common.processString(titleType);
			if (protoTitleType == null) {
				this.titleType = null;
			} else {
				try {
					this.titleType = TitleType.getTitleType(protoTitleType);
				} catch (IllegalArgumentException e) {
					this.errorList.add(e.getMessage());
				}
			}

			return this;
		}

		/**
		 * Sets the type of the title being represented, for example 'subtitle'.
		 *
		 * @param titleType
		 *                the titleType as an enum. Null resets any
		 *                current titleType in the builder.
		 * @return this builder.
		 */
		public Builder withTitleType(final TitleType titleType) {
			this.titleType = titleType;
			return this;
		}

		/**
		 * Build the {@link Title}.
		 *
		 * The titleLanguage value is checked for conformance to IETF BCP-47 syntax
		 * rules; note that Java does not check the values themselves, but rather the
		 * syntax of the value.
		 *
		 * If the titleType is null, it is set to the default DEFAULT_TITLE_TYPE.
		 *
		 * If the titleType is 'translated_title', titleLanguage must also be set.
		 * titleLanguage can also be specified for titles of other types.
		 *
		 * @return the title object.
		 */
		public Title build() {

			// check and reformat the titleLanguage
			if (titleLanguage != null) {
				titleLanguage = titleLanguage.replace("_", "-");
				Locale.Builder localeBuilder = null;
				try {
					localeBuilder = new Locale.Builder().setLanguageTag(titleLanguage);
				} catch (IllformedLocaleException e) {
					errorList.add("titleLanguage error: " + e.getMessage());
				}
				if (localeBuilder != null) {
					titleLanguage = localeBuilder.build().toLanguageTag();
				}
			}

			if (titleType == null) {
				titleType = DEFAULT_TITLE_TYPE;
			}

			if (titleType == TitleType.TRANSLATED_TITLE && titleLanguage == null) {
				errorList.add("titleLanguage must be set if titleType is set to 'translated_title'");
			}

			if (errorList.isEmpty()) {
				return new Title(titleString, titleType, titleLanguage);
			}

			throw new IllegalArgumentException("Errors in Title construction:\n" +
					String.join("\n", errorList));
		}
	}
}
