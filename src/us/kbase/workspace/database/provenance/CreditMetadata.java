package us.kbase.workspace.database.provenance;

import java.net.URL;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import us.kbase.workspace.database.Util;
/*
 * A class representing the credit metadata for a workspace object.
 */
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
			final String lowercaseInput = Util.checkString(resourceType, "resourceType")
					.toLowerCase();
			switch (lowercaseInput) {
				case "dataset":
				case "data_set":
				case "data set":
					return ResourceType.DATASET;
				default:
					throw new IllegalArgumentException(
							"Invalid resourceType: " + resourceType);
			}
		}
	}

	private final String identifier;
	private final String license;
	private final String version;
	private final ResourceType resourceType;
	private final List<String> comments;
	private final List<Contributor> contributors;
	private final List<EventDate> dates;
	private final List<FundingReference> funding;
	private final List<PermanentID> relatedIdentifiers;
	private final List<Title> titles;

	private CreditMetadata(
			final String identifier,
			final String license,
			final String version,
			final ResourceType resourceType,
			final List<String> comments,
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

	/**
	 * Gets the identifier for the resource.
	 *
	 * @return identifier
	 */
	public String getIdentifier() {
		return identifier;
	}

	/*
	 * Gets the license for the resource; may be a string like 'Apache 2.0' or an URL in string
	 * form.
	 *
	 * @return resource license (if present)
	 */
	public Optional<String> getLicense() {
		return Optional.ofNullable(license);
	}

	/*
	 * Gets the version information for the resource.
	 *
	 * @return resource version (if present)
	 */
	public Optional<String> getVersion() {
		return Optional.ofNullable(version);
	}

	/*
	 * Gets the resource type.
	 *
	 * @return the resource type
	 */
	public ResourceType getResourceType() {
		return resourceType;
	}

	/*
	 * Gets the comments for the resource.
	 *
	 * @return list of comments (strings)
	 */
	public List<String> getComments() {
		return Common.getList(comments);
	}

	/*
	 * Gets the contributor list.
	 *
	 * @return list of Contributor objects.
	 */
	public List<Contributor> getContributors() {
		return Common.getList(contributors);
	}

	/*
	 * Gets any lifecycle dates.
	 *
	 * @return list of EventDate objects
	 */
	public List<EventDate> getDates() {
		return Common.getList(dates);
	}

	/*
	 * Gets the funding information.
	 *
	 * @return list of FundingReference objects
	 */
	public List<FundingReference> getFunding() {
		return Common.getList(funding);
	}

	/*
	 * Gets the list of related identifiers.
	 *
	 * @return list of PermanentID objects
	 */
	public List<PermanentID> getRelatedIdentifiers() {
		return Common.getList(relatedIdentifiers);
	}

	/*
	 * Gets the list of titles used for the resource.
	 *
	 * @return list of Title objects
	 */
	public List<Title> getTitles() {
		return Common.getList(titles);
	}

	@Override
	public int hashCode() {
		return Objects.hash(comments,
				contributors,
				dates,
				funding,
				identifier,
				license,
				relatedIdentifiers,
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
	 * @param identifier
	 *                unique persistent ID for the resource  (i.e. the source data for this
	 *                workspace object). Should be in the format
	 *                <database name>:<identifier within database>
	 * @param resourceType
	 *                type of the resource, as a {@link ResourceType}
	 * @param contributors
	 *                list of {@link Contributor} objects; at least one contributor is required
	 * @param titles
	 *                list of {@link Title} objects; at least one title is required
	 * @return the builder.
	 */
	public static Builder getBuilder(
			final String identifier,
			final ResourceType resourceType,
			final List<Contributor> contributors,
			final List<Title> titles) {
		final List<String> errorList = new ArrayList<>();
		if (resourceType == null) {
			errorList.add("resourceType cannot be null");
		}
		return new Builder(identifier, resourceType, contributors, titles,
				errorList);
	}

	/**
	 * Gets a builder for the {@link CreditMetadata}.
	 *
	 * @param identifier
	 *                unique persistent ID for the resource
	 * @param resourceType
	 *                type of the resource as a string
	 * @param contributors
	 *                list of {@link Contributor} objects; at least one contributor is required
	 * @param titles
	 *                list of {@link Title} objects; at least one title is required
	 * @return the builder.
	 */
	public static Builder getBuilder(
			final String identifier,
			final String resourceType,
			final List<Contributor> contributors,
			final List<Title> titles) {
		final List<String> errorList = new ArrayList<>();

		ResourceType rt = null;
		if (resourceType == null || resourceType.isBlank()) {
			errorList.add("resourceType cannot be null or whitespace only");
		}
		else {
			try {
				rt = ResourceType.getResourceType(resourceType);
			} catch (IllegalArgumentException e) {
				errorList.add(e.getMessage());
			}
		}
		return new Builder(identifier, rt, contributors, titles, errorList);
	}

	/** A builder for {@link CreditMetadata}. */
	public static class Builder {
		private final String identifier;
		private String license = null;
		private String version = null;
		private final ResourceType resourceType;
		private List<String> comments = null;
		private final List<Contributor> contributors;
		private List<EventDate> dates = null;
		private List<FundingReference> funding = null;
		private List<PermanentID> relatedIdentifiers = null;
		private final List<Title> titles;
		private List<String> errorList;

		private Builder(
				final String identifier,
				final ResourceType resourceType,
				final List<Contributor> contributors,
				final List<Title> titles,
				final List<String> errorList) {

			this.errorList = errorList;
			this.resourceType = resourceType;

			String checkedPid = identifier;
			try {
				checkedPid = Common.checkPid(identifier, "identifier", false);
			} catch (IllegalArgumentException e) {
				this.errorList.add(e.getMessage());
			}
			this.identifier = checkedPid;

			// must be at least one contributor
			this.contributors = Common.dedupeSimpleList(contributors);
			if (this.contributors == null || this.contributors.isEmpty()) {
				this.errorList.add("at least one contributor must be provided");
			}

			// must be at least one title
			this.titles = Common.dedupeSimpleList(titles);
			if (this.titles == null || this.titles.isEmpty()) {
				this.errorList.add("at least one title must be provided");
			}
		}

		/**
		 * Sets comments for the resource.
		 *
		 * @param comments
		 *                comments as a list of strings
		 * @return this builder
		 */
		public Builder withComments(final List<String> comments) {
			if (comments != null) {
				// strip out any nulls, blanks, or repeated comments
				this.comments = comments.stream()
						.filter(c -> c != null && !c.isBlank())
						.map(String::strip)
						.distinct()
						.collect(Collectors.toList());
			}
			else {
				this.comments = comments;
			}
			return this;
		}

		/**
		 * Sets the license for the resource; can be a string like 'Apache 2.0' or an
		 * URL-like string; URL-like strings will be checked for well-formedness.
		 * For these purposes, a license string that starts with a series of letters
		 * followed by one or more colons and one or more slashes (e.g. "https://") is
		 * considered URL-like.
		 *
		 * @param license
		 *                license as a string
		 * @return this builder
		 */
		public Builder withLicense(final String license) {
			this.license = Common.processString(license);
			return this;
		}

		/**
		 * Sets the version.
		 *
		 * Valid credit metadata objects require either a version or one or more dates.
		 *
		 * @param withVersion
		 *                version string
		 * @return this builder
		 */
		public Builder withVersion(final String version) {
			this.version = Common.processString(version);
			return this;
		}

		/**
		 * Sets the dates of various resource life cycle events.
		 *
		 * Valid credit metadata objects require either a version or one or more dates.
		 *
		 * @param withDates
		 *                list of {@link EventDate}s.
		 * @return this builder
		 */
		public Builder withDates(final List<EventDate> dates) {
			this.dates = Common.dedupeSimpleList(dates);
			return this;
		}

		/**
		 * Sets the funding source(s).
		 *
		 * @param withFunding
		 *                list of {@link FundingReference}s.
		 * @return this builder
		 */
		public Builder withFunding(final List<FundingReference> funding) {
			this.funding = Common.dedupeSimpleList(funding);
			return this;
		}

		/**
		 * Sets the related identifiers.
		 *
		 * @param withRelatedIdentifiers
		 *                list of {@link PermanentID}s.
		 * @return this builder
		 */
		public Builder withRelatedIdentifiers(final List<PermanentID> relatedIdentifiers) {
			this.relatedIdentifiers = Common.dedupeSimpleList(relatedIdentifiers);
			return this;
		}

		/**
		 * Build the {@link CreditMetadata}.
		 *
		 * In addition to the required fields set in getBuilder(), credit metadata
		 * requires some sort of versioning information -- either an explicit version
		 * identifier or one or more dates.
		 *
		 * @return the credit metadata.
		 */
		public CreditMetadata build() {

			// ensure there is either a version or a non-empty list of dates
			if (version == null && (dates == null || dates.isEmpty())) {
				errorList.add("must provide either 'version' or one or more " +
						"'dates', ideally indicating when the resource " +
						"was published or when it was last updated");
			}

			// If the license looks like a URL, ensure it's properly formatted and seems
			// valid. For these purposes, a license string that starts with a series
			// of letters followed by one or more colons and one or more slashes is
			// considered URL-like.
			final Pattern pattern = Pattern.compile("^[a-zA-Z]+:+/+");
			if (license != null) {
				Matcher matcher = pattern.matcher(license);
				if (matcher.find()){
					try {
						final URL licenseURL = Common.processURL(license, "license");
						// license field is a string, so convert the URL
						// back to string form
						license = licenseURL.toURI().normalize().toString();
						// if there is user info in the url: reject it
						if (licenseURL.getUserInfo() != null) {
							errorList.add("Illegal license url '" + license + "': URLs must not contain user and/or password information");
						}
					} catch (Exception e) {
						errorList.add(e.getMessage());
					}
				}
			}

			if (errorList.isEmpty()) {
				return new CreditMetadata(
						identifier,
						license,
						version,
						resourceType,
						comments,
						contributors,
						dates,
						funding,
						relatedIdentifiers,
						titles);
			}

			throw new IllegalArgumentException(
					"Errors in CreditMetadata construction:\n" +
							String.join("\n", errorList));
		}

	}
}
