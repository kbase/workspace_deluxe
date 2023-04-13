package us.kbase.workspace.database.provenance;

import java.net.URL;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * A funding reference for a resource, including the funding body and the grant.
 */
public class FundingReference {

	private final Organization funder;
	private final String grantID;
	private final String grantTitle;
	private final URL grantURL;

	private FundingReference(
			final Organization funder,
			final String grantID,
			final String grantTitle,
			final URL grantURL) {
		this.funder = funder;
		this.grantID = grantID;
		this.grantTitle = grantTitle;
		this.grantURL = grantURL;
	}

	/**
	 * Gets the funding organization.
	 *
	 * @return the funder organization.
	 */
	public Organization getFunder() {
		return funder;
	}

	/**
	 * Gets the code assigned by the funder to the grant, for example
	 * DOI:10.46936/10.25585/60000745.
	 *
	 * @return the grant ID, if present.
	 */
	public Optional<String> getGrantID() {
		return Optional.ofNullable(grantID);
	}

	/**
	 * Gets the title of the grant, for example "Metagenomic analysis of the
	 * rhizosphere of three biofuel crops at the KBS intensive site".
	 *
	 * @return the grant title, if present.
	 */
	public Optional<String> getGrantTitle() {
		return Optional.ofNullable(grantTitle);
	}

	/**
	 * Gets the URL of the grant.
	 *
	 * @return the grant URL, if present.
	 */
	public Optional<URL> getGrantURL() {
		return Optional.ofNullable(grantURL);
	}

	@Override
	public int hashCode() {
		return Objects.hash(grantID, grantTitle, grantURL, funder);
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
		return Objects.equals(grantID, other.grantID) && Objects.equals(grantTitle, other.grantTitle)
				&& Objects.equals(grantURL, other.grantURL) && Objects.equals(funder, other.funder);
	}

	/**
	 * Gets a builder for an {@link FundingReference}.
	 *
	 * @param funder
	 *                the funding body
	 *
	 * @return the builder.
	 */
	public static Builder getBuilder(final Organization funder) {
		return new Builder(funder);
	}

	/** A builder for an {@link FundingReference}. */
	public static class Builder {

		private final Organization funder;
		private String grantID = null;
		private String grantTitle = null;
		private URL grantURL = null;
		private List<String> errorList = new ArrayList<>();

		private Builder(final Organization funder) {
			if (funder == null) {
				this.errorList.add("funder cannot be null");
			}
			this.funder = funder;
		}

		/**
		 * Sets the ID of the grant, for example DOI:10.46936/10.25585/60000745.
		 * N.b. not all grant IDs conform to the standard PID syntax.
		 *
		 * @param grantID
		 *                the ID of the grant. Null or the empty string removes any
		 *                current ID in the builder.
		 * @return this builder.
		 */
		public Builder withGrantID(final String grantID) {
			this.grantID = Common.processString(grantID);
			return this;
		}

		/**
		 * Sets the title of the grant, for example "Metagenomic analysis of the
		 * rhizosphere of three biofuel crops at the KBS intensive site".
		 *
		 * @param grantTitle
		 *                the title of the grant. Null or the empty string removes
		 *                any current ID in the builder.
		 * @return this builder.
		 */
		public Builder withGrantTitle(final String grantTitle) {
			this.grantTitle = Common.processString(grantTitle);
			return this;
		}

		/**
		 * Sets the URL for the grant.
		 *
		 * @param grantURL
		 *                the URL for the grant. Null or the empty string removes any
		 *                current url in the builder.
		 * @return this builder.
		 */
		public Builder withGrantURL(final String grantURL) {
			try {
				this.grantURL = Common.processURL(grantURL, "grantURL");
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
			return this;
		}

		/**
		 * Sets the URL for the grant.
		 *
		 * @param grantURL
		 *                the URL of the grant. Null removes any current url in the
		 *                builder.
		 * @return this builder.
		 */
		public Builder withGrantURL(final URL grantURL) {
			try {
				this.grantURL = Common.processURL(grantURL, "grantURL");
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
				return new FundingReference(funder, grantID, grantTitle, grantURL);
			}
			throw new IllegalArgumentException("Errors in FundingReference construction:\n" +
					String.join("\n", errorList));
		}
	}
}
