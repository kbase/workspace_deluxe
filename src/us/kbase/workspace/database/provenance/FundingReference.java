package us.kbase.workspace.database.provenance;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import us.kbase.workspace.database.Util;

/**
 * A funding reference for a resource, including the funding body and the grant
 * awarded.
 */
public class FundingReference {

	private final String funderID;
	private final String funderName;
	private final String awardID;
	private final String awardTitle;
	private final URL awardURL;

	private FundingReference(
			final String funderID,
			final String funderName,
			final String awardID,
			final String awardTitle,
			final URL awardURL) {
		this.funderID = funderID;
		this.funderName = funderName;
		this.awardID = awardID;
		this.awardTitle = awardTitle;
		this.awardURL = awardURL;
	}

	/**
	 * Gets the funder ID, for example ROR:04xm1d337.
	 *
	 * @return the funder ID, if present.
	 */
	public Optional<String> getFunderID() {
		return Optional.ofNullable(funderID);
	}

	/**
	 * Gets the funder name, for example US Department of Energy.
	 *
	 * @return the funder name.
	 */
	public String getFunderName() {
		return funderName;
	}

	/**
	 * Gets the code assigned by the funder to the award, for example
	 * DOI:10.46936/10.25585/60000745.
	 *
	 * @return the award ID, if present.
	 */
	public Optional<String> getAwardID() {
		return Optional.ofNullable(awardID);
	}

	/**
	 * Gets the title of the award, for example "Metagenomic analysis of the
	 * rhizosphere of three biofuel crops at the KBS intensive site".
	 *
	 * @return the award title, if present.
	 */
	public Optional<String> getAwardTitle() {
		return Optional.ofNullable(awardTitle);
	}

	/**
	 * Gets the URL of the award.
	 *
	 * @return the award URL, if present.
	 */
	public Optional<URL> getAwardURL() {
		return Optional.ofNullable(awardURL);
	}

	@Override
	public int hashCode() {
		return Objects.hash(awardID, awardTitle, awardURL, funderID, funderName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FundingReference other = (FundingReference) obj;
		return Objects.equals(awardID, other.awardID) && Objects.equals(awardTitle, other.awardTitle)
				&& Objects.equals(awardURL, other.awardURL) && Objects.equals(funderID, other.funderID)
				&& Objects.equals(funderName, other.funderName);
	}

	/**
	 * Gets a builder for an {@link FundingReference}.
	 *
	 * @param funderName  name of the funding body
	 *
	 * @return the builder.
	 */
	public static Builder getBuilder(final String funderName) {
		return new Builder(funderName);
	}

	/** A builder for an {@link FundingReference}. */
	public static class Builder {

		private String funderID = null;
		private String funderName;
		private String awardID = null;
		private String awardTitle = null;
		private URL awardURL = null;

		private Builder(final String funderName) {
			this.funderName = Util.checkString(funderName, "funderName");
		}

		/**
		 * Sets the ID of the funder, for example ROR:04xm1d337.
		 *
		 * @param funderID the ID of the funder. Null or the empty string removes any
		 *                 current ID in the builder.
		 * @return this builder.
		 */
		public Builder withFunderID(final String funderID) {
			this.funderID = Common.checkPid(funderID, "funderID", true);
			return this;
		}

		/**
		 * Sets the ID of the award, for example DOI:10.46936/10.25585/60000745.
		 * N.b. not all award IDs conform to the standard PID syntax.
		 *
		 * @param awardID the ID of the award. Null or the empty string removes any
		 *                current ID in the builder.
		 * @return this builder.
		 */
		public Builder withAwardID(final String awardID) {
			this.awardID = Util.checkString(awardID, "awardID", true);
			return this;
		}

		/**
		 * Sets the title of the award, for example "Metagenomic analysis of the
		 * rhizosphere of three biofuel crops at the KBS intensive site".
		 *
		 * @param awardTitle the title of the award. Null or the empty string removes
		 *                   any current ID in the builder.
		 * @return this builder.
		 */
		public Builder withAwardTitle(final String awardTitle) {
			this.awardTitle = Util.checkString(awardTitle, "awardTitle", true);
			return this;
		}

		/**
		 * Sets the URL for the award.
		 *
		 * @param awardURL the URL for the award. Null or the empty string removes any
		 *                 current url in the builder.
		 * @return this builder.
		 */
		public Builder withAwardURL(final String awardURL) {
			this.awardURL = Common.processURL(awardURL, "awardURL");
			return this;
		}

		/**
		 * Sets the URL for the award.
		 *
		 * @param awardURL the URL of the award. Null removes any current url in the
		 *                 builder.
		 * @return this builder.
		 */
		public Builder withAwardURL(final URL awardURL) {
			this.awardURL = Common.processURL(awardURL, "awardURL");
			return this;
		}

		/**
		 * Builds the {@link FundingReference}.
		 *
		 * @return the funding reference.
		 */
		public FundingReference build() {
			return new FundingReference(funderID, funderName, awardID, awardTitle, awardURL);
		}
	}
}
