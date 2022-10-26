package us.kbase.workspace.database.provenance;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a permanent unique identifier for an entity.
 * Used for the 'related_identifiers' field of a {@link Resource} object.
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
	 * Get the ID, for example DOI:10.25982/59912.37.
	 *
	 * @return the id.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Get a free text description of the resource identified by the ID.
	 *
	 * @return the description, if present.
	 */
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	/**
	 * When used to represent objects in a {@link Resource}'s 'related_identifiers'
	 * field, captures the relationship between the {@link Resource} and the entity
	 * represented by this.id.
	 *
	 * @return the relationship type, if present.
	 */
	public Optional<String> getRelationshipType() {
		return Optional.ofNullable(relationshipType);
	}

	/**
	 * Get a builder for an {@link PermanentID}.
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
			this.id = Common.checkPid(id, "id");
		}

		/**
		 * Set a free text description of the ID.
		 *
		 * @param description the description. Null or the empty string removes any
		 *                    current description in the builder.
		 * @return this builder.
		 */
		public Builder withDescription(final String description) {
			this.description = Common.processString(description);
			return this;
		}

		/**
		 * Set the relationship type between the ID and the resource.
		 *
		 * @param relationshipType the relationship type. Null or the empty string
		 *                         removes any current resource in the builder.
		 * @return this builder.
		 */
		public Builder withRelationshipType(final String relationshipType) {
			this.relationshipType = Common.processString(relationshipType);
			return this;
		}

		/**
		 * Build the {@link PermanentID}.
		 *
		 * @return the external data.
		 */
		public PermanentID build() {
			return new PermanentID(id, description, relationshipType);
		}
	}
}
