package us.kbase.workspace.database.provenance;

import com.github.zafarkhaja.semver.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import us.kbase.workspace.database.Util;
import us.kbase.workspace.database.WorkspaceUser;
/*
 * Container class for {@link CreditMetadata}; includes metadata about credit metadata.
 */
public class CreditMetadataEntry {
	private final WorkspaceUser user;
	private final String creditMetadataSchemaVersion;
	private final Instant timestamp;
	private final CreditMetadata creditMetadata;

	private CreditMetadataEntry(
			final WorkspaceUser user,
			final String creditMetadataSchemaVersion,
			final Instant timestamp,
			final CreditMetadata creditMetadata) {
		this.user = user;
		this.creditMetadataSchemaVersion = creditMetadataSchemaVersion;
		this.timestamp = timestamp;
		this.creditMetadata = creditMetadata;
	}

	/**
	 * Gets the workspace user who submitted this {@link CreditMetadata} record.
	 *
	 * @return the user.
	 */
	public WorkspaceUser getUser() {
		return user;
	}

	/**
	 * Gets the version of the credit metadata schema used in this record.
	 *
	 * The credit metadata schema version is documented in the Credit Engine
	 * repo at https://github.com/kbase/credit_engine/.
	 *
	 * @return the schema version.
	 */
	public String getCreditMetadataSchemaVersion() {
		return creditMetadataSchemaVersion;
	}

	/**
	 * Gets the timestamp for the creation of this record.
	 *
	 * @return the timestamp.
	 */
	public Instant getTimestamp() {
		return timestamp;
	}

	/**
	 * Gets the credit metadata.
	 *
	 * @return the metadata.
	 */
	public CreditMetadata getCreditMetadata() {
		return creditMetadata;
	}

	@Override
	public int hashCode() {
		return Objects.hash(creditMetadataSchemaVersion, creditMetadata, timestamp, user);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CreditMetadataEntry other = (CreditMetadataEntry) obj;
		return Objects.equals(creditMetadataSchemaVersion, other.creditMetadataSchemaVersion)
				&& Objects.equals(creditMetadata, other.creditMetadata)
				&& Objects.equals(timestamp, other.timestamp)
				&& Objects.equals(user, other.user);
	}

	/**
	 * Builds the {@link CreditMetadataEntry}.
	 *
	 * @param user
	 *                workspace user
	 * @param creditMetadataSchemaVersion
	 *                version of the metadata schema being used
	 *                The credit metadata schema version is documented in the
	 *                Credit Engine repo at https://github.com/kbase/credit_engine/.
     * @param timestamp
	 *                timestamp for submission of this credit metadata
	 * @param creditMetadata
	 *                creditMetadata object to be saved
	 *
	 * @throws IllegalArgumentException
	 *                 if there are any errors in the input params
	 *
	 * @return the CreditMetadata metadata.
	 */
	public static CreditMetadataEntry build(
			final WorkspaceUser user,
			final String creditMetadataSchemaVersion,
			final Instant timestamp,
			final CreditMetadata creditMetadata) {

		final List<String> errorList = new ArrayList<>();

		if (user == null) {
			errorList.add("user cannot be null");
		}

		String trimmedVersion = null;
		try {
			trimmedVersion = Util.checkString(creditMetadataSchemaVersion,
					"creditMetadataSchemaVersion");
		} catch (Exception e) {
			errorList.add(e.getMessage());
		}

		if (trimmedVersion != null) {
			try {
				final Version version = Version.valueOf(trimmedVersion);
				trimmedVersion = version.toString();
			} catch (Exception e) {
				errorList.add(trimmedVersion + " is not a valid semantic version");
			}
		}

		if (creditMetadata == null) {
			errorList.add("creditMetadata cannot be null");
		}

		if (timestamp == null) {
			errorList.add("timestamp cannot be null");
		}

		if (errorList.isEmpty()) {
			return new CreditMetadataEntry(user, trimmedVersion, timestamp, creditMetadata);
		}

		throw new IllegalArgumentException("Errors in CreditMetadataEntry construction:\n"
				+ String.join("\n", errorList));
	}
}
