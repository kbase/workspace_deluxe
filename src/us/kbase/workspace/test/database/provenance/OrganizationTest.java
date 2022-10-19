package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;

import java.util.Optional;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.Organization;

public class OrganizationTest {

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(Organization.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		final Organization org1 = Organization.getBuilder("The Organisation").build();
		assertThat("incorrect org name", org1.getOrganizationName(), is("The Organisation"));
		assertThat("incorrect org ID", org1.getOrganizationID(), is(Optional.empty()));
	}

	@Test
	public void buildMaximal() throws Exception {
		final Organization org1 = Organization.getBuilder("The Organisation").withOrganizationID("THIS:id").build();
		assertThat("incorrect org name", org1.getOrganizationName(), is("The Organisation"));
		assertThat("incorrect org ID", org1.getOrganizationID(), is(opt("THIS:id")));
	}

	@Test
	public void buildTrimOrgNameAndId() throws Exception {
		final Organization org1 = Organization.getBuilder("     \t\t    The Organisation      ")
				.withOrganizationID("    \t\t THIS:id  \n   ").build();
		assertThat("incorrect org name", org1.getOrganizationName(), is("The Organisation"));
		assertThat("incorrect org ID", org1.getOrganizationID(), is(opt("THIS:id")));
	}

	@Test
	public void buildWithWhitespaceOrgId() throws Exception {
		final Organization org1 = Organization.getBuilder("The Organisation").withOrganizationID("    \t\t   \n   ")
				.build();
		assertThat("incorrect org name", org1.getOrganizationName(), is("The Organisation"));
		assertThat("incorrect org ID", org1.getOrganizationID(), is(Optional.empty()));
	}

	@Test
	public void buildWithNullOrgId() throws Exception {
		final Organization org1 = Organization.getBuilder("The Organisation").withOrganizationID(null).build();
		assertThat("incorrect org name", org1.getOrganizationName(), is("The Organisation"));
		assertThat("incorrect org ID", org1.getOrganizationID(), is(Optional.empty()));
	}

	@Test
	public void buildAndOverwriteOrgIdWithNull() throws Exception {
		final Organization org1 = Organization.getBuilder("The Organisation").withOrganizationID("The:GrandWazoo")
				.withOrganizationID("    \t\t   \n   ").build();
		assertThat("incorrect org name", org1.getOrganizationName(), is("The Organisation"));
		assertThat("incorrect org ID", org1.getOrganizationID(), is(Optional.empty()));

		final Organization org2 = Organization.getBuilder("The Organisation").withOrganizationID("The:GrandWazoo")
				.withOrganizationID(null).build();
		assertThat("incorrect org name", org2.getOrganizationName(), is("The Organisation"));
		assertThat("incorrect org ID", org2.getOrganizationID(), is(Optional.empty()));
	}

	@Test
	public void buildFailWhitespaceOrgName() throws Exception {
		try {
			Organization.getBuilder("          ").build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("organizationName"));
		}
	}

	@Test
	public void buildFailNullOrgName() throws Exception {
		try {
			Organization.getBuilder(null).build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("organizationName"));
		}
	}

}
