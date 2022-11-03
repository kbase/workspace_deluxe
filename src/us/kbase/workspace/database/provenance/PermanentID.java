package us.kbase.workspace.database.provenance;

import java.util.Objects;
import java.util.Optional;
import us.kbase.workspace.database.Util;

/**
 * Represents a permanent unique identifier for an entity with an optional
 * relationship to some other entity. That entity is expected to hold a
 * reference to the PID.
 */
public class PermanentID {

	private final String id;
	private final String description;
	private final String relationshipType;

	private PermanentID(final String id, final String description, final String relationshipType) {
		this.id = id;
		this.description = description;
		this.relationshipType = relationshipType;
	}

	/**
	 * Gets the ID, for example DOI:10.25982/59912.37.
	 *
	 * @return the id.
	 */
	public String getID() {
		return id;
	}

	/**
	 * Gets a free text description of the resource identified by the ID.
	 *
	 * @return the description, if present.
	 */
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	/**
	 * Gets the relationship between the ID and some other entity. For
	 * example, when a {@link PermanentID] class is used to represent
	 * objects in a {@link Resource}'s 'related_identifiers' field, this
	 * field captures the relationship between the {@link Resource} and
	 * the entity represented by this.id.
	 *
	 * This field is currently only settable by workspace admins. More
	 * validation may be added in the future to control the field contents.
	 *
	 * @return the relationship type, if present.
	 */
	public Optional<String> getRelationshipType() {
		return Optional.ofNullable(relationshipType);
	}

	/**
	 * Gets a builder for an {@link PermanentID}.
	 *
	 * @return the builder.
	 */
	public static Builder getBuilder(final String id) {
		return new Builder(id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(description, id, relationshipType);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if ((obj == null) || (getClass() != obj.getClass()))
			return false;
		PermanentID other = (PermanentID) obj;
		return Objects.equals(description, other.description) && Objects.equals(id, other.id)
				&& Objects.equals(relationshipType, other.relationshipType);
	}

	/** A builder for an {@link PermanentID}. */
	public static class Builder {

		private String id;
		private String description = null;
		private String relationshipType = null;

		private Builder(final String id) {
			this.id = Common.checkPid(id, "id", false);
		}

		/**
		 * Sets a free text description of the ID.
		 *
		 * @param description the description. Null, whitespace, or
		 *                    the empty string will remove any
		 *                    current content in the builder.
		 * @return this builder.
		 */
		public Builder withDescription(final String description) {
			this.description = Util.checkString(description, "description", true);
			return this;
		}

		/**
		 * Sets the relationship type between the ID and the resource.
		 *
		 * @param relationshipType the relationship type. Null, whitespace, or
		 *                         the empty string will remove the
		 *                         current content in the builder.
		 *
		 * @return this builder.
		 */
		public Builder withRelationshipType(final String relationshipType) {
			this.relationshipType = Util.checkString(relationshipType, "relationshipType", true);
			return this;
		}

		/**
		 * Builds the {@link PermanentID}.
		 *
		 * @return the external data.
		 */
		public PermanentID build() {
			return new PermanentID(id, description, relationshipType);
		}
	}
}
