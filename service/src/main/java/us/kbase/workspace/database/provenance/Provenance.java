package us.kbase.workspace.database.provenance;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import us.kbase.workspace.database.WorkspaceUser;

/** Provenance for a data object that describes how that data object was created. */
public class Provenance {
	
	private final List<ProvenanceAction> actions;
	private final WorkspaceUser user;
	private final Instant date;
	private final long wsid;
	
	private Provenance(
			final WorkspaceUser user,
			final Instant date,
			final long wsid,
			final List<ProvenanceAction> actions) {
		this.actions = actions;
		this.user = user;
		this.date = date;
		this.wsid = wsid;
	}

	/** Get the user that created the data.
	 * @return the user.
	 */
	public WorkspaceUser getUser() {
		return user;
	}

	/** Get the time the data was saved to the workspace.
	 * @return the time.
	 */
	public Instant getDate() {
		return date;
	}

	/** Get the original workspace in which the data was saved.
	 * Data saved before version 0.4.1 of the workspace never have this field.
	 * @return the original workspace ID, if present.
	 */
	public Optional<Long> getWorkspaceID() {
		return wsid < 1 ? Optional.empty() : Optional.ofNullable(wsid);
	}

	/** Get the actions that took place while creating the data object.
	 * @return the actions.
	 */
	public List<ProvenanceAction> getActions() {
		return Common.getList(actions);
	}

	/** Update the workspace ID for the provenance. This returns this provenance instance if
	 * the workspace ID doesn't change, or a new provenance instance if the incoming workspace
	 * ID is different.
	 * @param workspaceID the new workspace ID.
	 * @return this or a new {@link Provenance}.
	 */
	public Provenance updateWorkspaceID(final Long workspaceID) {
		if (workspaceID == null) {
			if (wsid == -1) {
				return this;
			}
			return new Provenance(user, date, -1, actions);
		}
		if (workspaceID < 1) {
			throw new IllegalArgumentException("workspace ID must be > 0");
		}
		if (wsid == workspaceID) {
			return this;
		}
		return new Provenance(user, date, workspaceID, actions);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((actions == null) ? 0 : actions.hashCode());
		result = prime * result + ((date == null) ? 0 : date.hashCode());
		result = prime * result + ((user == null) ? 0 : user.hashCode());
		result = prime * result + (int) (wsid ^ (wsid >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Provenance other = (Provenance) obj;
		if (actions == null) {
			if (other.actions != null)
				return false;
		} else if (!actions.equals(other.actions))
			return false;
		if (date == null) {
			if (other.date != null)
				return false;
		} else if (!date.equals(other.date))
			return false;
		if (user == null) {
			if (other.user != null)
				return false;
		} else if (!user.equals(other.user))
			return false;
		if (wsid != other.wsid)
			return false;
		return true;
	}
	
	/** Get a builder for a {@link Provenance}.
	 * @param user the user that created the data that is the subject of this provenance.
	 * @param date the date the data was saved to the workspace.
	 * @return the builder.
	 */
	public static Builder getBuilder(final WorkspaceUser user, final Instant date) {
		return new Builder(user, date);
	}
	
	/** A builder for a {@link Provenance}. */
	public static class Builder {
		
		private final WorkspaceUser user;
		private final Instant date;
		private long wsid = -1;
		private List<ProvenanceAction> actions = null;
		
		private Builder(final WorkspaceUser user, final Instant date) {
			this.user = requireNonNull(user, "user");
			this.date = requireNonNull(date, "date");
		}
		
		/** Set the ID of the workspace in which the data was originally saved. Passing null
		 * removes any ID in the builder.
		 * @param workspaceID the workspace ID.
		 * @return this builder.
		 */
		public Builder withWorkspaceID(final Long workspaceID) {
			if (workspaceID == null) {
				this.wsid = -1;
			} else if (workspaceID < 1) {
				throw new IllegalArgumentException("workspace ID must be > 0");
			} else {
				this.wsid = workspaceID;
			}
			return this;
		}
		
		/** Add an action to the provenance.
		 * @param action a provenance action.
		 * @return this builder.
		 */
		public Builder withAction(final ProvenanceAction action) {
			// could check if the output args from the prior PA in the list match the input
			// args to this PA, but no one uses this feature AFAIK so not worth it
			requireNonNull(action, "action");
			if (actions == null) {
				actions = new LinkedList<>();
			}
			actions.add(action);
			return this;
		}
		
		/** Build the {@link Provenance}.
		 * @return the provenance.
		 */
		public Provenance build() {
			return new Provenance(
					user, date, wsid, actions == null ? null : Common.immutable(actions));
		}
	}
}
