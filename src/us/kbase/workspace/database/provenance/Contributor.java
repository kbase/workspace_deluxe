package us.kbase.workspace.database.provenance;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import us.kbase.workspace.database.Util;

/**
 * Represents a contributor, which may be a person or an organization.
 */

public class Contributor {

	public enum ContributorType {
		PERSON, ORGANIZATION;

		/**
		 * Get a contributor type based on a supplied string.
		 *
		 * @param contributorType
		 *                 a string representing a contributor type.
		 * @return a contributor type.
		 * @throws IllegalArgumentException
		 *                 if there is no contributor type
		 *                 related to the input string.
		 */
		public static ContributorType getType(final String contributorType) {
			final String lowercaseInput = Util.checkString(contributorType, "contributorType").toLowerCase();
			switch (lowercaseInput) {
				case "person":
					return ContributorType.PERSON;
				case "organization":
				case "organisation":
					return ContributorType.ORGANIZATION;
				default:
					throw new IllegalArgumentException("Invalid contributorType: " + contributorType);
			}
		}
	}

	private final ContributorType contributorType;
	private final String contributorID;
	private final String name;
	private final String creditName;
	private final List<Organization> affiliations;
	private final List<ContributorRole> contributorRoles;

	private Contributor(
			final ContributorType contributorType,
			final String name,
			final String creditName,
			final String contributorID,
			final List<Organization> affiliations,
			final List<ContributorRole> contributorRoles) {
		this.contributorType = contributorType;
		this.name = name;
		this.creditName = creditName;
		this.contributorID = contributorID;
		this.affiliations = affiliations;
		this.contributorRoles = contributorRoles;
	}

	/**
	 * Gets the contributor type, either person or organization.
	 *
	 * @return the contributor type
	 */
	public ContributorType getContributorType() {
		return contributorType;
	}

	/**
	 * Gets the name associated with the contributor.
	 *
	 * @return the contributor's name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the credit name associated with the contributor.
	 *
	 * @return the contributor's credit name, if present
	 */
	public Optional<String> getCreditName() {
		return Optional.ofNullable(creditName);
	}

	/**
	 * Gets the ID for the contributor.
	 *
	 * @return the contributor ID, if present
	 */
	public Optional<String> getContributorID() {
		return Optional.ofNullable(contributorID);
	}

	/**
	 * Gets the contributor's affiliations.
	 *
	 * @return the contributor's affiliations, if present.
	 */
	public List<Organization> getAffiliations() {
		return Common.getList(affiliations);
	}

	/**
	 * Gets the roles that the contributor filled.
	 *
	 * @return the contributor roles, if present.
	 */
	public List<ContributorRole> getContributorRoles() {
		return Common.getList(contributorRoles);
	}

	/**
	 * Gets the roles that the contributor filled.
	 *
	 * @return the contributor roles, if present.
	 */
	public List<String> getContributorRoleStrings() {
		return Common.immutable(
			Common.getList(contributorRoles).stream()
			.map(ContributorRole::getPid)
			.collect(Collectors.toList()));
		}


	@Override
	public int hashCode() {
		return Objects.hash(affiliations, contributorID, contributorRoles, contributorType, name, creditName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Contributor other = (Contributor) obj;
		return Objects.equals(affiliations, other.affiliations)
				&& Objects.equals(contributorID, other.contributorID)
				&& Objects.equals(contributorRoles, other.contributorRoles)
				&& Objects.equals(contributorType, other.contributorType)
				&& Objects.equals(name, other.name)
				&& Objects.equals(creditName, other.creditName);
	}

	/**
	 * Gets a builder for a {@link Contributor}.
	 *
	 * @param contributorType
	 *                the type of contributor.
	 * @param name    contributor name
	 * @return the builder.
	 */
	public static Builder getBuilder(final ContributorType contributorType, final String name) {
		final List<String> errorList = new ArrayList<>();
		if (contributorType == null) {
			errorList.add("contributorType cannot be null");
		}
		return new Builder(contributorType, name, errorList);
	}

	/**
	 * Gets a builder for a {@link Contributor}.
	 *
	 * @param contributorType
	 *                the type of contributor, as a string.
	 * @param name    contributor name
	 * @return the builder.
	 */
	public static Builder getBuilder(final String contributorType, final String name) {

		final List<String> errorList = new ArrayList<>();
		ContributorType ct = null;
		try {
			ct = ContributorType.getType(contributorType);
		} catch (Exception e) {
			errorList.add(e.getMessage());
		}
		return new Builder(ct, name, errorList);
	}

	/** A builder for a {@link Contributor}. */
	public static class Builder {

		private ContributorType contributorType;
		private String name;
		private String creditName = null;
		private String contributorID = null;
		private List<Organization> affiliations = null;
		private List<ContributorRole> contributorRoles = null;
		private List<String> errorList;

		private Builder(final ContributorType contributorType, final String name, final List<String> errorList) {
			this.errorList = errorList;
			this.contributorType = contributorType;
			this.name = Common.processString(name);
			if (this.name == null) {
				errorList.add("name cannot be null or whitespace only");
			}
		}

		/**
		 * Sets the ID for the contributor
		 * @param contributorID a persistent unique ID for the contributor
		 * @return this builder
		 */
		public Builder withContributorID(final String contributorID) {
			try {
				this.contributorID = Common.checkPid(contributorID, "contributorID", true);
			} catch (Exception e) {
				this.errorList.add(e.getMessage());
			}
			return this;
		}

		/**
		 * Sets the credit name, i.e. the contributor's name as it would appear in a citation
		 * @param creditName the name as it would appear in a citation
		 * @return this builder
		 */
		public Builder withCreditName(final String creditName) {
			this.creditName = Common.processString(creditName);
			return this;
		}

		/**
		 * Sets the affiliation(s) for a contributor
		 * @param affiliations list of organizations
		 * @return this builder
		 */
		public Builder withAffiliations(final List<Organization> affiliations) {
			this.affiliations = affiliations;
			return this;
		}

		/**
		 * Sets the role(s) for a contributor, list of ContributorRoles as input
		 * @param contributorRoles list of contributor roles
		 * @return this builder
		 */
		public Builder withContributorRoles(final List<ContributorRole> contributorRoles) {
			this.contributorRoles = contributorRoles;
			return this;
		}

		/**
		 * Sets the role(s) for a contributor, list of strings as input
		 * @param contributorRoleStrings list of strings representing contributor roles
		 * @return this builder
		 */
		public Builder withContributorRoleStrings(final List<String> contributorRoleStrings) {
			this.contributorRoles = null;
			if (contributorRoleStrings == null) {
				return this;
			}
			// remove null/whitespace and dedupe
			final List<String> prunedRoleStrings = contributorRoleStrings.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.distinct()
				.filter(c -> !Util.isNullOrWhitespace(c))
				.collect(Collectors.toList());

			final List<ContributorRole> prunedRoles = new ArrayList<>();
			// convert contributorRoleStrings to contributorRoles
			for (final String cs : prunedRoleStrings) {
				try {
					final ContributorRole cr = ContributorRole
						.getContributorRole(cs);
					prunedRoles.add(cr);

				} catch (Exception e) {
					this.errorList.add(e.getMessage());
				}
			}
			if (!prunedRoles.isEmpty()) {
				this.contributorRoles = prunedRoles;
			}
			return this;
		}

		/**
		 * Build the contributor, performing checks for field content and required fields.
		 * @return the contributor
		 */
		public Contributor build() {

			if (creditName != null && contributorType == ContributorType.ORGANIZATION) {
				errorList.add("the creditName field is only used with contributorType person");
			}

			if (errorList.isEmpty()) {
				return new Contributor(
						contributorType,
						name,
						creditName,
						contributorID,
						Common.dedupeSimpleList(affiliations),
						Common.dedupeSimpleList(contributorRoles));
			}

			throw new IllegalArgumentException("Errors in Contributor construction:\n" +
					String.join("\n", errorList));
		}
	}
}
