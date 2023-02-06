package us.kbase.workspace.database.provenance;

import java.net.URL;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import us.kbase.workspace.database.Util;

/**
 * A funding reference for a resource, including the funding body and the grant
 * awarded.
 */
public class FundingReference {

	private final String funderId;
	private final String funderName;
	private final String awardId;
	private final String awardTitle;
	private final URL awardUrl;

	private FundingReference(
			final String funderId,
			final String funderName,
			final String awardId,
			final String awardTitle,
			final URL awardUrl) {
		this.funderId = funderId;
		this.funderName = funderName;
		this.awardId = awardId;
		this.awardTitle = awardTitle;
		this.awardUrl = awardUrl;
	}

	/**
	 * Gets the funder ID, for example ROR:04xm1d337.
	 *
	 * @return the funder ID, if present.
	 */
	public Optional<String> getFunderId() {
		return Optional.ofNullable(funderId);
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
	public Optional<String> getAwardId() {
		return Optional.ofNullable(awardId);
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
	public Optional<URL> getAwardUrl() {
		return Optional.ofNullable(awardUrl);
	}

	@Override
	public int hashCode() {
		return Objects.hash(awardId, awardTitle, awardUrl, funderId, funderName);
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
		return Objects.equals(awardId, other.awardId) && Objects.equals(awardTitle, other.awardTitle)
				&& Objects.equals(awardUrl, other.awardUrl) && Objects.equals(funderId, other.funderId)
				&& Objects.equals(funderName, other.funderName);
	}

	/**
	 * Gets a builder for an {@link FundingReference}.
	 *
	 * @param funderName
	 *                name of the funding body
	 *
	 * @return the builder.
	 */
	public static Builder getBuilder(final String funderName) {
		return new Builder(funderName);
	}

	/** A builder for an {@link FundingReference}. */
	public static class Builder {

		private String funderId = null;
		private String funderName;
		private String awardId = null;
		private String awardTitle = null;
		private URL awardUrl = null;
		private List<String> errorList = new ArrayList<>();

		private Builder(final String funderName) {
			try {
				this.funderName = Util.checkString(funderName, "funderName");
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
		}

		/**
		 * Sets the ID of the funder, for example ROR:04xm1d337.
		 *
		 * @param funderId
		 *                the ID of the funder. Null or the empty string removes any
		 *                current ID in the builder.
		 * @return this builder.
		 */
		public Builder withFunderId(final String funderId) {
			try {
				this.funderId = Common.checkPid(funderId, "funderId", true);
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
			return this;
		}

		/**
		 * Sets the ID of the award, for example DOI:10.46936/10.25585/60000745.
		 * N.b. not all award IDs conform to the standard PID syntax.
		 *
		 * @param awardId
		 *                the ID of the award. Null or the empty string removes any
		 *                current ID in the builder.
		 * @return this builder.
		 */
		public Builder withAwardId(final String awardId) {
			this.awardId = Common.processString(awardId);
			return this;
		}

		/**
		 * Sets the title of the award, for example "Metagenomic analysis of the
		 * rhizosphere of three biofuel crops at the KBS intensive site".
		 *
		 * @param awardTitle
		 *                the title of the award. Null or the empty string removes
		 *                any current ID in the builder.
		 * @return this builder.
		 */
		public Builder withAwardTitle(final String awardTitle) {
			this.awardTitle = Common.processString(awardTitle);
			return this;
		}

		/**
		 * Sets the URL for the award.
		 *
		 * @param awardUrl
		 *                the URL for the award. Null or the empty string removes any
		 *                current url in the builder.
		 * @return this builder.
		 */
		public Builder withAwardUrl(final String awardUrl) {
			try {
				this.awardUrl = Common.processURL(awardUrl, "awardUrl");
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
			return this;
		}

		/**
		 * Sets the URL for the award.
		 *
		 * @param awardUrl
		 *                the URL of the award. Null removes any current url in the
		 *                builder.
		 * @return this builder.
		 */
		public Builder withAwardUrl(final URL awardUrl) {
			try {
				this.awardUrl = Common.processURL(awardUrl, "awardUrl");
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
			return this;
		}

		/**
		 * Builds the {@link FundingReference}.
		 *
		 * @return the funding reference.
		 */
		public FundingReference build() {
			if (errorList.isEmpty()) {
				return new FundingReference(funderId, funderName, awardId, awardTitle, awardUrl);
			}
			throw new IllegalArgumentException("Errors in FundingReference construction:\n" +
					String.join("\n", errorList));
		}
	}
}
