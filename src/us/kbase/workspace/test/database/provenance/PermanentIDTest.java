package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.opt;

import java.util.Optional;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.PermanentID;

public class PermanentIDTest {

	public static final String INCORRECT_PID = "incorrect resolvable PID";
	public static final String INCORRECT_DESC = "incorrect description";
	public static final String INCORRECT_REL_TYPE = "incorrect relationship type";
	public static final String EXP_EXC = "expected exception";

	public static final String WHITESPACE = "    \n\n  \t   ";

	public static final String DESC_STRING = "It was a dark and stormy night...";
	public static final String DESC_STRING_UNTRIMMED = "  \nIt was a dark and stormy night...   \t\n  ";

	public static final String PID_STRING = "Database:cross-reference";
	public static final String PID_STRING_UNTRIMMED = "  \nDatabase:cross-reference\t\t    \n  ";

	public static final String REL_STRING = "isRelatedTo";
	public static final String REL_STRING_UNTRIMMED = "   isRelatedTo   ";

	public static final String ID = "id";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(PermanentID.class).usingGetClass().verify();
	}

	@Test
	public void buildMinimal() throws Exception {
		final String tldr = "TL:DR";
		Map<String, String> validPids = new HashMap<>();
		validPids.put(tldr, tldr);
		validPids.put("\r\n  TL:DR    \n\n\t ", tldr);
		validPids.put("Too.Long:Didn't.Read", "Too.Long:Didn't.Read");
		validPids.put("toolong : didn't read", "toolong:didn't read");
		validPids.put("2.long: didn't read", "2.long:didn't read");
		validPids.put("too.long:\n\ndidn't.read", "too.long:didn't.read");
		validPids.put("tl\n\n  :d\tr\n\n  ", "tl:d\tr");
		validPids.put("tl: +__+ ", "tl:+__+");
		validPids.put(" https://this.is.the.url/ ", "https://this.is.the.url/");

		for (Map.Entry<String, String> mapElement : validPids.entrySet()) {
			final PermanentID pid1 = PermanentID.getBuilder(mapElement.getKey()).build();
			assertThat(INCORRECT_PID, pid1.getId(), is(mapElement.getValue()));
			assertThat(INCORRECT_DESC, pid1.getDescription(), is(Optional.empty()));
			assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(Optional.empty()));
		}
	}

	@Test
	public void buildMaximal() throws Exception {
		final PermanentID pid1 = PermanentID.getBuilder(PID_STRING).withDescription(DESC_STRING)
				.withRelationshipType(REL_STRING).build();
		assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
		assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
		assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_STRING)));
	}

	@Test
	public void buildTrimAllFields() throws Exception {
		final PermanentID pid1 = PermanentID.getBuilder(PID_STRING_UNTRIMMED)
				.withDescription(DESC_STRING_UNTRIMMED).withRelationshipType(REL_STRING_UNTRIMMED).build();
		assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
		assertThat(INCORRECT_DESC, pid1.getDescription(), is(opt(DESC_STRING)));
		assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(opt(REL_STRING)));
	}

	@Test
	public void buildWithWhitespaceDescriptionRelationshipType() throws Exception {
		final PermanentID pid1 = PermanentID.getBuilder(PID_STRING_UNTRIMMED).withDescription(WHITESPACE)
				.withRelationshipType(WHITESPACE).build();
		assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
		assertThat(INCORRECT_DESC, pid1.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(Optional.empty()));
	}

	@Test
	public void buildWithNullDescriptionRelationshipType() throws Exception {
		final PermanentID pid1 = PermanentID.getBuilder(PID_STRING).withDescription(null)
				.withRelationshipType(null).build();
		assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
		assertThat(INCORRECT_DESC, pid1.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(Optional.empty()));
	}

	@Test
	public void buildAndOverwriteDescriptionRelationshipType() throws Exception {
		final PermanentID pid1 = PermanentID.getBuilder(PID_STRING).withDescription(DESC_STRING)
				.withDescription(WHITESPACE).withRelationshipType(REL_STRING).withRelationshipType(WHITESPACE).build();
		assertThat(INCORRECT_PID, pid1.getId(), is(PID_STRING));
		assertThat(INCORRECT_DESC, pid1.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, pid1.getRelationshipType(), is(Optional.empty()));

		final PermanentID pid2 = PermanentID.getBuilder(PID_STRING).withDescription(DESC_STRING)
				.withDescription(null).withRelationshipType(REL_STRING).withRelationshipType(null).build();
		assertThat(INCORRECT_PID, pid2.getId(), is(PID_STRING));
		assertThat(INCORRECT_DESC, pid2.getDescription(), is(Optional.empty()));
		assertThat(INCORRECT_REL_TYPE, pid2.getRelationshipType(), is(Optional.empty()));
	}

	@Test
	public void buildFailInvalidPID() throws Exception {
		final List<String> invalidPids = Arrays.asList(
				// spaces in prefix
			"Too long: didn't read",
				"t\tl:d\tr",
				// no prefix
				":didn't read",
				"\f  :didn't read",
				"\t\t\t      :D\n  \t  ",
				// invalid prefix
				"too-long:didn'tread",
				"'too.long:didnotread'",
				".toolong:didnotread",
				"too\blong:didn't.read",
				// no suffix
				"too long:",
				"too long:\r\n   \f     \n");

		for (String invalidPid : invalidPids) {
			try {
				PermanentID.getBuilder(invalidPid).build();
				fail(EXP_EXC);
			} catch (Exception got) {
				TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(String.format(
						"Illegal ID format for %s: \"%s\"%nPermanent IDs should match the pattern \"%s\"",
						ID, invalidPid, "^([a-zA-Z0-9][a-zA-Z0-9\\.]+)\\s*:\\s*(\\S.+)$")));
			}
		}
	}

	@Test
	public void buildFailWhitespacePID() throws Exception {
		try {
			PermanentID.getBuilder(WHITESPACE).build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(String.format(
					"%s cannot be null or whitespace only", ID)));
		}
	}

	@Test
	public void buildFailNullPID() throws Exception {
		try {
			PermanentID.getBuilder(null).build();
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(String.format(
					"%s cannot be null or whitespace only", ID)));
		}
	}
}
