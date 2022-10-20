package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;

import java.util.Optional;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.ResolvablePID;

public class ResolvablePIDTest {

	public static final String INCORRECT_RPID = "incorrect resolvable PID";
	public static final String INCORRECT_DESC = "incorrect description";
	public static final String INCORRECT_REL_TYPE = "incorrect relationship type";

	public static final String WHITESPACE = "    \n\n  \t   ";

	public static final String DESC_STRING = "It was a dark and stormy night...";
	public static final String DESC_STRING_UNTRIMMED = "  \nIt was a dark and stormy night...   \t\n  ";

	public static final String RPID_STRING = "Database:cross-reference";
	public static final String RPID_STRING_UNTRIMMED = "  \nDatabase:cross-reference\t\t    \n  ";

	public static final String REL_STRING = "isRelatedTo";
	public static final String REL_STRING_UNTRIMMED = "   isRelatedTo   ";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(ResolvablePID.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		final ResolvablePID rpid1 = ResolvablePID.getBuilder(RPID_STRING).build();
		assertThat(INCORRECT_RPID, rpid1.getId(), is(RPID_STRING));
		assertThat(INCORRECT_DESC, rpid1.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, rpid1.getRelationshipType(), is(Optional.empty()));
	}

	@Test
	public void buildMaximal() throws Exception {
		final ResolvablePID rpid1 = ResolvablePID.getBuilder(RPID_STRING).withDescription(DESC_STRING)
				.withRelationshipType(REL_STRING).build();
		assertThat(INCORRECT_RPID, rpid1.getId(), is(RPID_STRING));
		assertThat(INCORRECT_DESC, rpid1.getDescription(), is(opt(DESC_STRING)));
		assertThat(INCORRECT_REL_TYPE, rpid1.getRelationshipType(), is(opt(REL_STRING)));
	}

	@Test
	public void buildTrimAllFields() throws Exception {
		final ResolvablePID rpid1 = ResolvablePID.getBuilder(RPID_STRING_UNTRIMMED)
				.withDescription(DESC_STRING_UNTRIMMED).withRelationshipType(REL_STRING_UNTRIMMED).build();
		assertThat(INCORRECT_RPID, rpid1.getId(), is(RPID_STRING));
		assertThat(INCORRECT_DESC, rpid1.getDescription(), is(opt(DESC_STRING)));
		assertThat(INCORRECT_REL_TYPE, rpid1.getRelationshipType(), is(opt(REL_STRING)));
	}

	@Test
	public void buildWithWhitespaceDescriptionRelationshipType() throws Exception {
		final ResolvablePID rpid1 = ResolvablePID.getBuilder(RPID_STRING_UNTRIMMED).withDescription(WHITESPACE)
				.withRelationshipType(WHITESPACE).build();
		assertThat(INCORRECT_RPID, rpid1.getId(), is(RPID_STRING));
		assertThat(INCORRECT_DESC, rpid1.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, rpid1.getRelationshipType(), is(Optional.empty()));
	}

	@Test
	public void buildWithNullDescriptionRelationshipType() throws Exception {
		final ResolvablePID rpid1 = ResolvablePID.getBuilder(RPID_STRING).withDescription(null)
				.withRelationshipType(null).build();
		assertThat(INCORRECT_RPID, rpid1.getId(), is(RPID_STRING));
		assertThat(INCORRECT_DESC, rpid1.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, rpid1.getRelationshipType(), is(Optional.empty()));
	}

	@Test
	public void buildAndOverwriteDescriptionRelationshipType() throws Exception {
		final ResolvablePID rpid1 = ResolvablePID.getBuilder(RPID_STRING).withDescription(DESC_STRING)
				.withDescription(WHITESPACE).withRelationshipType(REL_STRING).withRelationshipType(WHITESPACE).build();
		assertThat(INCORRECT_RPID, rpid1.getId(), is(RPID_STRING));
		assertThat(INCORRECT_DESC, rpid1.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, rpid1.getRelationshipType(), is(Optional.empty()));

		final ResolvablePID rpid2 = ResolvablePID.getBuilder(RPID_STRING).withDescription(DESC_STRING)
				.withDescription(null).withRelationshipType(REL_STRING).withRelationshipType(null).build();
		assertThat(INCORRECT_RPID, rpid2.getId(), is(RPID_STRING));
		assertThat(INCORRECT_DESC, rpid2.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, rpid2.getRelationshipType(), is(Optional.empty()));
	}

	@Test
	public void buildFailInvalidRPID() throws Exception {
		try {
			ResolvablePID.getBuilder(REL_STRING).build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(String.format(
					"Illegal ID format for id: \"%s\"\n" + "ID prefixes should match the pattern \"^\\w+:\\w+\"",
					REL_STRING)));
		}

		final String notQuiteAnRpid = "Too long: didn't read";
		try {
			ResolvablePID.getBuilder(notQuiteAnRpid).build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(String.format(
					"Illegal ID format for id: \"%s\"\n" + "ID prefixes should match the pattern \"^\\w+:\\w+\"",
					notQuiteAnRpid)));
		}
	}

	@Test
	public void buildFailWhitespaceRPID() throws Exception {
		try {
			ResolvablePID.getBuilder(WHITESPACE).build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("id"));
		}
	}

	@Test
	public void buildFailNullRPID() throws Exception {
		try {
			ResolvablePID.getBuilder(null).build();
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new NullPointerException("id"));
		}
	}
}
