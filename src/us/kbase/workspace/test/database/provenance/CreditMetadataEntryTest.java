package us.kbase.workspace.test.database.provenance;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static us.kbase.common.test.TestCommon.inst;
import static us.kbase.workspace.test.database.provenance.ProvenanceTestCommon.WHITESPACE_STRINGS_WITH_NULL;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import us.kbase.common.test.TestCommon;
import us.kbase.workspace.database.provenance.Contributor;
import us.kbase.workspace.database.provenance.CreditMetadata;
import us.kbase.workspace.database.provenance.CreditMetadata.ResourceType;
import us.kbase.workspace.database.provenance.CreditMetadataEntry;
import us.kbase.workspace.database.provenance.Title;
import us.kbase.workspace.database.WorkspaceUser;

public class CreditMetadataEntryTest {

	private static final String INCORRECT = "incorrect ";
	// field names
	private static final String USER = "user";
	private static final String SCHEMA_VERSION = "credit metadata schema version";
	private static final String CREDIT_METADATA = "credit metadata";
	private static final String TIMESTAMP = "timestamp";

	// field values
	private static final String VERSION_STRING = "1.2.3";
	private static final String VERSION_STRING_UNTRIMMED = "\n\r\n1.2.3\t\t";

	// test error strings
	private static final String INCORRECT_USER = INCORRECT + USER;
	private static final String INCORRECT_SCHEMA_VERSION = INCORRECT + SCHEMA_VERSION;
	private static final String INCORRECT_CREDIT_METADATA = INCORRECT + CREDIT_METADATA;
	private static final String INCORRECT_TIMESTAMP = INCORRECT + TIMESTAMP;

	// error messages
	private static final String USER_NON_NULL = "user cannot be null";
	private static final String SCHEMA_VERSION_NON_NULL =
			"creditMetadataSchemaVersion cannot be null or whitespace only";
	private static final String CREDIT_METADATA_NON_NULL = "creditMetadata cannot be null";
	private static final String TIMESTAMP_NON_NULL = "timestamp cannot be null";

	private static final List<Contributor> CONTRIBUTOR_LIST = Arrays.asList(
			Contributor.getBuilder("person", "Mr Blobby")
					.build(),
			Contributor.getBuilder("organisation", "The A Team")
					.build());

	private static final List<Title> TITLE_LIST = Arrays.asList(
			Title.getBuilder("\t\t\f\t\n\rA Series of Unfortunate Elephants\n\n\n   ")
					.build());

	private static final CreditMetadata CREDIT_METADATA_EXAMPLE = CreditMetadata
			.getBuilder("some:boring_identifier",
					ResourceType.DATASET,
					CONTRIBUTOR_LIST,
					TITLE_LIST)
			.withVersion("1.0.1-fml")
			.build();

	private static final String[] INVALID_SEM_VER_STRINGS = {
			"v1",
			"foo",
			"01.1.0",
			"alpha.01",
			"beta1..1",
			"build.01",
			".build.01" };

	private static final String EXP_EXC = "expected exception";

	@Test
	public void equals() throws Exception {
		EqualsVerifier.forClass(CreditMetadataEntry.class).usingGetClass().verify();
	}

	/**
	 * Check the resource metadata has the expected fields
	 *
	 * @param CreditMetadataEntry
	 *                instance to check
	 * @param user
	 *                workspace user
	 * @param version
	 *                creditMetadataSchemaVersion string
	 * @param creditMetadata
	 *                instance to check
	 */
	private void assertResourceMetadataFields(
			final CreditMetadataEntry creditMetadataEntry,
			final WorkspaceUser user,
			final String version,
			final CreditMetadata creditMetadata,
			final Instant timestamp) {
		assertThat(INCORRECT_USER, creditMetadataEntry.getUser(), is(user));
		assertThat(INCORRECT_SCHEMA_VERSION,
				creditMetadataEntry.getCreditMetadataSchemaVersion(),
				is(version));
		assertThat(INCORRECT_TIMESTAMP, creditMetadataEntry.getTimestamp(), is(timestamp));
		assertThat(INCORRECT_CREDIT_METADATA,
				creditMetadataEntry.getCreditMetadata(),
				is(creditMetadata));
	}

	@Test
	public void buildResourceMetadata() throws Exception {
		final WorkspaceUser user = new WorkspaceUser("worst_ws_user");
		final Instant ts = inst(70000);
		final CreditMetadataEntry cmc = CreditMetadataEntry.build(
				user,
				VERSION_STRING_UNTRIMMED,
				ts,
				CREDIT_METADATA_EXAMPLE);
		assertResourceMetadataFields(cmc, user, VERSION_STRING, CREDIT_METADATA_EXAMPLE, ts);
	}

	/**
	 * Checks the error thrown when a ResourceMetadata object is built with invalid
	 * arguments
	 *
	 * @param builder
	 *                resource metadata builder, complete with args
	 * @param error
	 *                the expected error
	 */
	private void buildResourceMetadataFailWithError(
			final WorkspaceUser wsUser,
			final String version,
			final CreditMetadata creditMetadata,
			final Instant timestamp,
			final String errorString) {
		try {
			CreditMetadataEntry.build(wsUser, version, timestamp, creditMetadata);
			fail(EXP_EXC);
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, new IllegalArgumentException(
					"Errors in CreditMetadataEntry construction:\n"
							+ errorString));
		}
	}

	@Test
	public void buildFailNullUser() throws Exception {
		buildResourceMetadataFailWithError(
				null,
				VERSION_STRING_UNTRIMMED,
				CREDIT_METADATA_EXAMPLE,
				Instant.now(),
				USER_NON_NULL);
	}

	@Test
	public void buildFailInvalidSchemaVersion() throws Exception {
		final WorkspaceUser wsUser = new WorkspaceUser("some_idiot");
		for (String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildResourceMetadataFailWithError(
					wsUser,
					nullOrWs,
					CREDIT_METADATA_EXAMPLE,
					Instant.now(),
					SCHEMA_VERSION_NON_NULL);
		}
		for (final String invalidSemVer : INVALID_SEM_VER_STRINGS) {
			buildResourceMetadataFailWithError(
					wsUser,
					invalidSemVer,
					CREDIT_METADATA_EXAMPLE,
					Instant.now(),
					invalidSemVer + " is not a valid semantic version");
		}
	}

	@Test
	public void buildFailNullCreditMetadata() {
		buildResourceMetadataFailWithError(
				new WorkspaceUser("tedious_bore"),
				VERSION_STRING_UNTRIMMED,
				null,
				Instant.now(),
				CREDIT_METADATA_NON_NULL);
	}


	@Test
	public void buildFailNullTimestamp() {
		buildResourceMetadataFailWithError(
				new WorkspaceUser("tedious_bore"),
				VERSION_STRING_UNTRIMMED,
				CREDIT_METADATA_EXAMPLE,
				null,
				TIMESTAMP_NON_NULL);
	}


	@Test
	public void buildFailAllFields() {
		final String[] errs = {
				USER_NON_NULL,
				SCHEMA_VERSION_NON_NULL,
				CREDIT_METADATA_NON_NULL,
				TIMESTAMP_NON_NULL
		};
		final String errorString = String.join("\n", errs);

		for (final String nullOrWs : WHITESPACE_STRINGS_WITH_NULL) {
			buildResourceMetadataFailWithError(
					null,
					nullOrWs,
					null,
					null,
					errorString);
		}

		for (final String invalidSemVer : INVALID_SEM_VER_STRINGS) {
			buildResourceMetadataFailWithError(
					(WorkspaceUser) null,
					invalidSemVer,
					(CreditMetadata) null,
					(Instant) null,
					// error messages
					USER_NON_NULL + "\n"
							+ invalidSemVer
							+ " is not a valid semantic version\n"
							+ CREDIT_METADATA_NON_NULL + "\n"
							+ TIMESTAMP_NON_NULL);
		}
	}
}
