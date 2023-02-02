package us.kbase.workspace.database.provenance;

import java.net.URL;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import us.kbase.workspace.database.Util;

public class CreditMetadata {

	public enum ResourceType {
		DATASET;

		/**
		 * Get a resource type based on a supplied string.
		 *
		 * @param str
		 *                a string representing a resource type.
		 * @return a resource type.
		 * @throws IllegalArgumentException
		 *                 if there is no resource type
		 *                 related to the input string.
		 */
		public static ResourceType getResourceType(final String resourceType) {
			final String lowercaseInput = Util.checkString(resourceType, "resourceType").toLowerCase();
			switch (lowercaseInput) {
				case "dataset":
				case "data_set":
				case "data set":
					return ResourceType.DATASET;
				default:
					throw new IllegalArgumentException("Invalid resourceType: " + resourceType);
			}
		}
	}

	private final String comments;
	private final String identifier;
	private final String license;
	private final String version;
	private final ResourceType resourceType;
	private final List<Contributor> contributors;
	private final List<EventDate> dates;
	private final List<FundingReference> funding;
	private final List<PermanentID> relatedIdentifiers;
	private final List<Title> titles;

	private static final ResourceType DEFAULT_RESOURCE_TYPE = ResourceType.DATASET;

	private CreditMetadata(
			final String identifier,
			final String comments,
			final String license,
			final String version,
			final ResourceType resourceType,
			final List<Contributor> contributors,
			final List<EventDate> dates,
			final List<FundingReference> funding,
			final List<PermanentID> relatedIdentifiers,
			final List<Title> titles) {
		this.comments = comments;
		this.contributors = contributors;
		this.dates = dates;
		this.funding = funding;
		this.identifier = identifier;
		this.license = license;
		this.relatedIdentifiers = relatedIdentifiers;
		this.resourceType = resourceType;
		this.titles = titles;
		this.version = version;
	}

	public String getIdentifier() {
		return identifier;
	}

	public Optional<String> getComments() {
		return Optional.ofNullable(comments);
	}

	public Optional<String> getLicense() {
		return Optional.ofNullable(license);
	}

	public Optional<String> getVersion() {
		return Optional.ofNullable(version);
	}

	public ResourceType getResourceType() {
		return resourceType;
	}

	public List<Contributor> getContributors() {
		return Common.getList(contributors);
	}

	public List<EventDate> getDates() {
		return Common.getList(dates);
	}

	public List<FundingReference> getFunding() {
		return Common.getList(funding);
	}

	public List<PermanentID> getRelatedIdentifiers() {
		return Common.getList(relatedIdentifiers);
	}

	public List<Title> getTitles() {
		return Common.getList(titles);
	}

	@Override
	public int hashCode() {
		return Objects.hash(comments, contributors, dates, funding, identifier, license, relatedIdentifiers,
				resourceType,
				titles,
				version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CreditMetadata other = (CreditMetadata) obj;
		return Objects.equals(comments, other.comments)
				&& Objects.equals(contributors, other.contributors)
				&& Objects.equals(dates, other.dates)
				&& Objects.equals(funding, other.funding)
				&& Objects.equals(identifier, other.identifier)
				&& Objects.equals(license, other.license)
				&& Objects.equals(relatedIdentifiers, other.relatedIdentifiers)
				&& Objects.equals(resourceType, other.resourceType)
				&& Objects.equals(titles, other.titles)
				&& Objects.equals(version, other.version);
	}

	/**
	 * Gets a builder for the {@link CreditMetadata}.
	 *
	 * @param identifier unique persistent ID for the resource
	 * @param resourceType type of the resource, as a {@link ResourceType}
	 * @param titles list of {@link Title} objects
	 * @return the builder.
	 */
	public static Builder getBuilder(
			final String identifier,
			final ResourceType resourceType,
			final List<Title> titles) {
		return new Builder(identifier, resourceType, titles, new ArrayList<>());
	}

	/**
	 * Gets a builder for the {@link CreditMetadata}.
	 *
	 * @param identifier unique persistent ID for the resource
	 * @param resourceType type of the resource as a string
	 * @param titles list of {@link Title} objects
	 * @return the builder.
	 */
	public static Builder getBuilder(
			final String identifier,
			final String resourceType,
			final List<Title> titles) {
		final List<String> errorList = new ArrayList<>();

		ResourceType rt = null;
		if (resourceType != null) {
			try {
				rt = ResourceType.getResourceType(resourceType);
			} catch (IllegalArgumentException e) {
				// TEMPORARY HACK
				// As there is only one resource type at present (ResourceType.DATASET), null input
				// in the resourceType field will be automatically set to that value.
				// The error message "resourceType cannot be null or whitespace only" thus
				// needs to be edited to say "resourceType cannot be whitespace only".
				String errorMessage = e.getMessage();
				if ("resourceType cannot be null or whitespace only".equals(errorMessage)) {
					errorMessage = "resourceType cannot be whitespace only";
				}
				errorList.add(errorMessage);
			}
		}
		return new Builder(identifier, rt, titles, errorList);
	}

	/** A builder for {@link CreditMetadata}. */
	public static class Builder {
		private String comments;
		private String identifier;
		private String license = null;
		private String version = null;
		private ResourceType resourceType = ResourceType.DATASET;
		private List<Contributor> contributors = null;
		private List<EventDate> dates = null;
		private List<FundingReference> funding = null;
		private List<PermanentID> relatedIdentifiers = null;
		private List<Title> titles;
		private List<String> errorList;

		private Builder(
				final String identifier,
				final ResourceType resourceType,
				final List<Title> titles,
				final List<String> errorList) {

			this.errorList = errorList;
			this.resourceType = resourceType;

			// TEMPORARY HACK
			// as there is only one resource type at present, the field defaults to being set
			// to ResourceType.DATASET if the input is null
			if (this.resourceType == null && errorList.isEmpty()) {
				this.resourceType = DEFAULT_RESOURCE_TYPE;
			}

			try {
				this.identifier = Common.checkPid(identifier, "identifier", false);
			} catch (IllegalArgumentException e) {
				this.errorList.add(e.getMessage());
			}

			// must be at least one title
			this.titles = Common.dedupeSimpleList(titles);
			if (this.titles == null || this.titles.isEmpty()) {
				this.errorList.add("at least one title must be provided");
			}
		}

		/**
		 * Sets comments for the resource
		 * @param comments comments as a string
		 * @return this builder
		 */
		public Builder withComments(final String comments) {
			this.comments = Common.processMultilineString(comments);
			return this;
		}

		/**
		 * Sets the license for the resource
		 * @param license license as a string
		 * @return this builder
		 */
		public Builder withLicense(final String license) {
			this.license = Common.processString(license);
			return this;
		}

		/**
		 * Sets the version
		 * @param withVersion version string
		 * @return this builder
		 */
		public Builder withVersion(final String version) {
			this.version = Common.processString(version);
			return this;
		}

		/**
		 * Sets the contributor(s)
		 * @param withContributors list of {@link Contributor}s.
		 * @return this builder
		 */
		public Builder withContributors(final List<Contributor> contributors) {
			this.contributors = Common.dedupeSimpleList(contributors);
			return this;
		}

		/**
		 * Sets the dates of various resource life cycle events
		 * @param withDates list of {@link EventDate}s.
		 * @return this builder
		 */
		public Builder withDates(final List<EventDate> dates) {
			this.dates = Common.dedupeSimpleList(dates);
			return this;
		}

		/**
		 * Sets the funding source(s)
		 * @param withFunding list of {@link FundingReference}s.
		 * @return this builder
		 */
		public Builder withFunding(final List<FundingReference> funding) {
			this.funding = Common.dedupeSimpleList(funding);
			return this;
		}

		/**
		 * Sets the related identifiers
		 * @param withRelatedIdentifiers list of {@link PermanentID}s.
		 * @return this builder
		 */
		public Builder withRelatedIdentifiers(final List<PermanentID> relatedIdentifiers) {
			this.relatedIdentifiers = Common.dedupeSimpleList(relatedIdentifiers);
			return this;
		}

		/**
		 * Build the {@link CreditMetadata}.
		 *
		 * @return the credit metadata.
		 */
		public CreditMetadata build() {

			if (version == null && (dates == null || dates.isEmpty())) {
				errorList.add("must provide either 'version' or one or more 'dates', ideally indicating when the resource was published or when it was last updated");
			}

			// If the license looks like a URL, ensure it's properly formatted and seems valid.
			// For these purposes, if it starts with http, assume it's an URL.
			if (license != null && license.toLowerCase().startsWith("http")) {
				try {
					final URL licenseURL = Common.processURL(license, "license");
					if (licenseURL != null) {
						license = licenseURL.toString();
					}
					else {
						license = null;
					}
				}
				catch (Exception e) {
					errorList.add(e.getMessage());
				}
			}


			if (errorList.isEmpty()) {
				return new CreditMetadata(
						identifier, comments, license, version, resourceType,
						contributors, dates, funding, relatedIdentifiers, titles);
			}

			throw new IllegalArgumentException("Errors in CreditMetadata construction:\n" +
					String.join("\n", errorList));
		}

	}
}
