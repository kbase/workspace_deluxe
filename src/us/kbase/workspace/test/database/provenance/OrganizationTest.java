package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Map;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;

import static us.kbase.common.test.TestCommon.opt;
import static us.kbase.common.test.TestCommon.ES;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.VALID_PID_MAP;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.INVALID_PID_LIST;

import us.kbase.workspace.database.provenance.Organization;

public class OrganizationTest {
	private static final String INCORRECT_NAME = "incorrect org name";
	private static final String INCORRECT_ID = "incorrect org id";
	private static final String EXP_EXC = "expected exception";

	private static final String ORG_NAME = "The Illuminati, NorCal branch";
	private static final String ORG_NAME_WITH_WHITESPACE = "\t    \r\n The Illuminati, NorCal branch\f\f  \n\r";
	private static final String PID_STRING = "Some:reference";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(Organization.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		final Organization org1 = Organization.getBuilder(ORG_NAME).build();
		assertThat(INCORRECT_NAME, org1.getOrganizationName(), is(ORG_NAME));
		assertThat(INCORRECT_ID, org1.getOrganizationID(), is(ES));
	}

	@Test
	public void buildMaximal() throws Exception {
		final Organization org1 = Organization.getBuilder(ORG_NAME).withOrganizationID(PID_STRING).build();
		assertThat(INCORRECT_NAME, org1.getOrganizationName(), is(ORG_NAME));
		assertThat(INCORRECT_ID, org1.getOrganizationID(), is(opt(PID_STRING)));
	}

	@Test
	public void buildTrimOrgNameAndId() throws Exception {
		for (Map.Entry<String, String> mapElement : VALID_PID_MAP.entrySet()) {
			final Organization org1 = Organization.getBuilder(ORG_NAME_WITH_WHITESPACE)
					.withOrganizationID(mapElement.getKey()).build();
			assertThat(INCORRECT_NAME, org1.getOrganizationName(), is(ORG_NAME));
			assertThat(INCORRECT_ID, org1.getOrganizationID(), is(opt(mapElement.getValue())));
		}
	}

	@Test
	public void buildWithNullOrWhitespaceOrgId() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Organization org1 = Organization.getBuilder(ORG_NAME)
					.withOrganizationID(nullOrWs)
					.build();
			assertThat(INCORRECT_NAME, org1.getOrganizationName(), is(ORG_NAME));
			assertThat(INCORRECT_ID, org1.getOrganizationID(), is(ES));
		}
	}

	@Test
	public void buildAndOverwriteOrgIdWithNullOrWhitespace() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			final Organization org1 = Organization.getBuilder(ORG_NAME)
					.withOrganizationID(PID_STRING)
					.withOrganizationID(nullOrWs)
					.build();
			assertThat(INCORRECT_NAME, org1.getOrganizationName(), is(ORG_NAME));
			assertThat(INCORRECT_ID, org1.getOrganizationID(), is(ES));
		}
	}

	@Test
	public void buildFailInvalidPID() throws Exception {
		for (String invalidPid : INVALID_PID_LIST) {
			try {
				Organization.getBuilder(ORG_NAME)
						.withOrganizationID(invalidPid)
						.build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
						"Illegal format for organizationID: \"" + invalidPid + "\"\n" +
								"It should match the pattern \"^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$\""));
			}
		}
	}

	@Test
	public void buildFailNullOrWhitespaceOrgName() throws Exception {
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			try {
				Organization.getBuilder(nullOrWs).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"organizationName cannot be null or whitespace only"));
			}
		}
	}
}
