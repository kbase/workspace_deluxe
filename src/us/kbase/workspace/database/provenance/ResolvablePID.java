package us.kbase.workspace.database.provenance;

import java.util.Objects;
import java.util.Optional;

/**
 * A resolvable permanent identifier for a resource.
 */
public class ResolvablePID {

	private final String id;
	private final String description;
	private final String relationshipType;

	private ResolvablePID(final String id, final String description, final String relationshipType) {
		this.id = id;
		this.relationshipType = relationshipType;
		this.description = description;
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
	 * Get a free text description of the ID.
	 * 
	 * @return the description, if present.
	 */
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	/**
	 * Get the relationship between the ID and the resource, for example
	 * "isSupplementTo" or "isNewVersionOf".
	 * 
	 * @return the relationship type, if present.
	 */
	public Optional<String> getRelationshipType() {
		return Optional.ofNullable(relationshipType);
	}

	/**
	 * Get a builder for an {@link ResolvablePID}.
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
		ResolvablePID other = (ResolvablePID) obj;
		return Objects.equals(description, other.description) && Objects.equals(id, other.id)
				&& Objects.equals(relationshipType, other.relationshipType);
	}

	/** A builder for an {@link ResolvablePID}. */
	public static class Builder {

		private String id;
		private String description = null;
		private String relationshipType = null;

		private Builder(final String id) {
			this.id = Common.checkRpid(id, "id");
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
		 * Build the {@link ResolvablePID}.
		 * 
		 * @return the external data.
		 */
		public ResolvablePID build() {
			return new ResolvablePID(id, description, relationshipType);
		}
	}
}
