package us.kbase.workspace.database.provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import us.kbase.workspace.database.Util;

/**
 * Information about an organization.
 */
public class Organization {
	private final String organizationName;
	private final String organizationID;

	private Organization(final String organizationName, final String organizationID) {
		this.organizationName = organizationName;
		this.organizationID = organizationID;
	}

	/**
	 * Get the name of the organization, for example KBase.
	 *
	 * @return the organization name.
	 */
	public String getOrganizationName() {
		return organizationName;
	}

	/**
	 * Get the ID of the organization, for example ROR:03rmrcq20.
	 *
	 * @return the data id, if present.
	 */
	public Optional<String> getOrganizationID() {
		return Optional.ofNullable(organizationID);
	}

	@Override
	public int hashCode() {
		return Objects.hash(organizationID, organizationName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;
		Organization other = (Organization) obj;
		return Objects.equals(organizationID, other.organizationID)
				&& Objects.equals(organizationName, other.organizationName);
	}

	/**
	 * Get a builder for an {@link Organization}.
	 *
	 * @param organizationName the name of the organization.
	 * @return the builder.
	 */
	public static Builder getBuilder(final String organizationName) {
		return new Builder(organizationName);
	}

	/** A builder for an {@link Organization}. */
	public static class Builder {

		private String organizationName;
		private String organizationID = null;
		private List<String> errorList = new ArrayList<>();

		private Builder(final String organizationName) {
			try {
				this.organizationName = Util.checkString(organizationName, "organizationName");
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
		}

		/**
		 * Set the ID of the organization, for example ROR:03rmrcq20.
		 *
		 * @param organizationID
		 *                the organization ID. Null or the empty string removes
		 *                any current organization ID in the builder.
		 * @return this builder.
		 */
		public Builder withOrganizationID(final String organizationID) {
			try {
				this.organizationID = Common.checkPid(organizationID, "organizationID", true);
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
			return this;
		}

		/**
		 * Build the {@link Organization}.
		 *
		 * @return the organization.
		 */
		public Organization build() {
			if (errorList.isEmpty()) {
				return new Organization(organizationName, organizationID);
			}
			throw new IllegalArgumentException("Errors in Organization construction:\n" +
					String.join("\n", errorList));
		}
	}
}
