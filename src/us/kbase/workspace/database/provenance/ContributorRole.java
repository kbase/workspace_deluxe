package us.kbase.workspace.database.provenance;

import java.util.HashMap;
import java.util.Map;
import us.kbase.workspace.database.Util;
import static us.kbase.workspace.database.provenance.Common.DATACITE;
import static us.kbase.workspace.database.provenance.Common.CREDIT;

/**
 * A class representing contributor roles
 */
public enum ContributorRole {

	// DataCite contributor roles
	CONTACT_PERSON(DATACITE, "ContactPerson"),
	DATA_COLLECTOR(DATACITE, "DataCollector"),
	DATA_CURATOR(DATACITE, "DataCurator"),
	DATA_MANAGER(DATACITE, "DataManager"),
	DISTRIBUTOR(DATACITE, "Distributor"),
	EDITOR(DATACITE, "Editor"),
	HOSTING_INSTITUTION(DATACITE, "HostingInstitution"),
	PRODUCER(DATACITE, "Producer"),
	PROJECT_LEADER(DATACITE, "ProjectLeader"),
	PROJECT_MANAGER(DATACITE, "ProjectManager"),
	PROJECT_MEMBER(DATACITE, "ProjectMember"),
	REGISTRATION_AGENCY(DATACITE, "RegistrationAgency"),
	REGISTRATION_AUTHORITY(DATACITE, "RegistrationAuthority"),
	RELATED_PERSON(DATACITE, "RelatedPerson"),
	RESEARCHER(DATACITE, "Researcher"),
	RESEARCH_GROUP(DATACITE, "ResearchGroup"),
	RIGHTS_HOLDER(DATACITE, "RightsHolder"),
	SPONSOR(DATACITE, "Sponsor"),
	SUPERVISOR(DATACITE, "Supervisor"),
	WORK_PACKAGE_LEADER(DATACITE, "WorkPackageLeader"),
	OTHER(DATACITE, "Other"),
	// CRediT contributor roles
	CONCEPTUALIZATION(CREDIT, "conceptualization"),
	DATA_CURATION(CREDIT, "data-curation"),
	FORMAL_ANALYSIS(CREDIT, "formal-analysis"),
	FUNDING_ACQUISITION(CREDIT, "funding-acquisition"),
	INVESTIGATION(CREDIT, "investigation"),
	METHODOLOGY(CREDIT, "methodology"),
	PROJECT_ADMINISTRATION(CREDIT, "project-administration"),
	RESOURCES(CREDIT, "resources"),
	SOFTWARE(CREDIT, "software"),
	SUPERVISION(CREDIT, "supervision"),
	VALIDATION(CREDIT, "validation"),
	VISUALIZATION(CREDIT, "visualization"),
	WRITING_ORIGINAL_DRAFT(CREDIT, "writing-original-draft"),
	WRITING_REVIEW_EDITING(CREDIT, "writing-review-editing");

	// mapping of various name formats to ContributorRole
	private static final Map<String, ContributorRole> STRING_TO_ROLE_MAP = new HashMap<>();
	static {
		for (final ContributorRole cr : ContributorRole.values()) {
			STRING_TO_ROLE_MAP.put(cr.name().toLowerCase(), cr);
			STRING_TO_ROLE_MAP.put(cr.name().replace("_", "").toLowerCase(), cr);
			STRING_TO_ROLE_MAP.put((cr.source + ":" + cr.name().replace("_", "")).toLowerCase(), cr);
			STRING_TO_ROLE_MAP.put(cr.identifier.toLowerCase(), cr);
			STRING_TO_ROLE_MAP.put(cr.getPid().toLowerCase(), cr);
			STRING_TO_ROLE_MAP.put((cr.source + ":" + cr.name()).toLowerCase(), cr);
		}
	}

	private final String identifier;
	private final String source;

	private ContributorRole(final String source, final String identifier) {
		this.source = source;
		this.identifier = identifier;
	}

	/**
	 * Get the fully-qualified permanent ID of this contributor role.
	 *
	 * @return the PID.
	 */
	public String getPid() {
		return source + ":" + identifier;
	}

	/**
	 * Get a contributor role based on a supplied string.
	 *
	 * @param str a string representing a contributor role.
	 * @return a contributor role.
	 * @throws IllegalArgumentException if there is no contributor role
	 *                                  related to the input string.
	 */
	public static ContributorRole getContributorRole(final String input) {
		final String lowercaseInput = Util.checkString(input, "contributorRole").toLowerCase();
		if (!STRING_TO_ROLE_MAP.containsKey(lowercaseInput)) {
			throw new IllegalArgumentException("Invalid contributorRole: " + input);
		}
		return STRING_TO_ROLE_MAP.get(lowercaseInput);
	}
}
