package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import us.kbase.typedobj.core.TypeDefId;

/** The parameters for the listObjects method.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ListObjectsParameters {
	
	private final static int MAX_INFO_COUNT = 10000;
	private final static int MAX_WS = 10000;

	private final Set<WorkspaceIdentifier> wsis;
	private final Optional<WorkspaceUser> user;
	private final Optional<TypeDefId> type;
	private final List<WorkspaceUser> savers;
	private final WorkspaceUserMetadata meta;
	private final RefLimit startFrom;
	private final Optional<Instant> after;
	private final Optional<Instant> before;
	private final long minObjectID;
	private final long maxObjectID;
	private final boolean showHidden;
	private final boolean showDeleted;
	private final boolean showOnlyDeleted;
	private final boolean showAllVers;
	private final boolean includeMetaData;
	private final boolean asAdmin;
	private final int limit;
	
	private ListObjectsParameters(
			final Set<WorkspaceIdentifier> wsis,
			final Optional<WorkspaceUser> user,
			final Optional<TypeDefId> type,
			final List<WorkspaceUser> savers,
			final WorkspaceUserMetadata meta,
			final RefLimit startFrom,
			final Optional<Instant> after,
			final Optional<Instant> before,
			final long minObjectID,
			final long maxObjectID,
			final boolean showHidden,
			final boolean showDeleted,
			final boolean showOnlyDeleted,
			final boolean showAllVers,
			final boolean includeMetaData,
			final boolean asAdmin,
			final int limit) {
		this.wsis = wsis;
		this.user = user;
		this.type = type;
		this.savers = savers;
		this.meta = meta;
		this.startFrom = startFrom;
		this.after = after;
		this.before = before;
		this.minObjectID = minObjectID;
		this.maxObjectID = maxObjectID;
		this.showHidden = showHidden;
		this.showDeleted = showDeleted;
		this.showOnlyDeleted = showOnlyDeleted;
		this.showAllVers = showAllVers;
		this.includeMetaData = includeMetaData;
		this.asAdmin = asAdmin;
		this.limit = limit;
	}

	/** Get the workspaces to list.
	 * @return the workspaces to list.
	 */
	public Set<WorkspaceIdentifier> getWorkspaces() {
		return wsis;
	}

	/** Get the type of objects to list.
	 * @return the type of objects to list.
	 */
	public Optional<TypeDefId> getType() {
		return type;
	}

	/** Get the workspace user for whom to list objects.
	 * @return the workspace user. May be null.
	 */
	public Optional<WorkspaceUser> getUser() {
		return user;
	}

	/** Get the list of workspace users that saved objects to be listed.
	 * Only objects saved by these users will be listed.
	 * @return the list of workspace users.
	 */
	public List<WorkspaceUser> getSavers() {
		return savers;
	}

	/** Get the metadata by which the object list should be filtered.
	 * @return the metadata.
	 */
	public WorkspaceUserMetadata getMetadata() {
		return meta;
	}
	
	/** Get the position from which listing objects should begin. Allows paging through lists
	 * of objects.
	 * @return the position.
	 */
	public RefLimit getStartFrom() {
		return startFrom;
	}

	/** Get the date that specifies the earliest record that should be
	 * listed.
	 * @return the date. May be null.
	 */
	public Optional<Instant> getAfter() {
		return after;
	}

	/** Get the date that specifies the latest record that should be
	 * listed.
	 * @return the date. May be null.
	 */
	public Optional<Instant> getBefore() {
		return before;
	}

	/** Get the minimum object ID for objects that should be listed.
	 * @return the minimum object ID.
	 */
	public long getMinObjectID() {
		return minObjectID;
	}

	/** Get the maximum object ID for objects that should be listed.
	 * @return the maximum object ID.
	 */
	public long getMaxObjectID() {
		return maxObjectID;
	}

	/** Returns whether hidden objects should be listed
	 * @return true if hidden objects should be listed.
	 */
	public boolean isShowHidden() {
		return showHidden;
	}

	/** Returns whether deleted objects should be listed
	 * @return true if deleted objects should be listed.
	 */
	public boolean isShowDeleted() {
		return showDeleted;
	}

	/** Returns whether only deleted objects should be listed
	 * @return true if only deleted objects should be listed.
	 */
	public boolean isShowOnlyDeleted() {
		return showOnlyDeleted;
	}

	/** Returns whether all versions of objects should be listed
	 * @return true if all versions of objects should be listed.
	 */
	public boolean isShowAllVersions() {
		return showAllVers;
	}

	/** Returns whether object metadata should be listed
	 * @return true if object metadata should be listed.
	 */
	public boolean isIncludeMetaData() {
		return includeMetaData;
	}

	/** Get the maximum number of objects to list.
	 * @return the maximum number of objects to list.
	 */
	public int getLimit() {
		return limit;
	}
	
	/** Get whether the command should be run as an admin.
	 * @return whether the effective user is an admin.
	 */
	public boolean asAdmin() {
		return asAdmin;
	}
	
	public ResolvedListObjectParameters resolve(final PermissionSet perms) {
		return new ResolvedListObjectParameters(
				requireNonNull(perms, "perms cannot be null"), type, savers, meta, startFrom,
				after, before, minObjectID, maxObjectID,
				showHidden, showDeleted, showOnlyDeleted, showAllVers, includeMetaData,
				limit, asAdmin);
		
	}
	
	/** Parameters for listing objects resolved with workspace permissions for the user. */
	public static class ResolvedListObjectParameters {
		
		private final PermissionSet pset;
		private final Optional<TypeDefId> type;
		private final List<WorkspaceUser> savers;
		private final WorkspaceUserMetadata meta;
		// TODO NOW_PAGING implement paging in mongo layer, add type/upa index
		// TODO NOW_PAGING add to spec and transform ref string to Reference, allowing missing fields
		// TODO NOW_PAGING add paging documentation
		private final RefLimit startFrom;
		private final Optional<Instant> after;
		private final Optional<Instant> before;
		private final long minObjectID;
		private final long maxObjectID;
		private final boolean showHidden;
		private final boolean showDeleted;
		private final boolean showOnlyDeleted;
		private final boolean showAllVers;
		private final boolean includeMetaData;
		private final boolean asAdmin;
		private final int limit;
		
		private ResolvedListObjectParameters(
				final PermissionSet pset,
				final Optional<TypeDefId> type,
				final List<WorkspaceUser> savers,
				final WorkspaceUserMetadata meta,
				final RefLimit startFrom,
				final Optional<Instant> after,
				final Optional<Instant> before,
				final long minObjectID,
				final long maxObjectID,
				final boolean showHidden,
				final boolean showDeleted,
				final boolean showOnlyDeleted,
				final boolean showAllVers,
				final boolean includeMetaData,
				final int limit,
				final boolean asAdmin) {
			this.pset = pset;
			this.type = type;
			this.savers = savers;
			this.meta = meta;
			this.startFrom = startFrom;
			this.after = after;
			this.before = before;
			this.minObjectID = minObjectID;
			this.maxObjectID = maxObjectID;
			this.showHidden = showHidden;
			this.showDeleted = showDeleted;
			this.showOnlyDeleted = showOnlyDeleted;
			this.showAllVers = showAllVers;
			this.includeMetaData = includeMetaData;
			this.limit = limit;
			this.asAdmin = asAdmin;
		}

		/** Returns the set of workspace permissions to be used when listing
		 *  objects.
		 * @return the set of workspace permissions.
		 */
		public PermissionSet getPermissionSet() {
			return pset;
		}

		/** Get the type of objects to list.
		 * @return the type of objects to list.
		 */
		public Optional<TypeDefId> getType() {
			return type;
		}

		/** Get the list of workspace users that saved objects to be listed.
		 * Only objects saved by these users will be listed.
		 * @return the list of workspace users.
		 */
		public List<WorkspaceUser> getSavers() {
			return savers;
		}

		/** Get the metadata by which the object list should be filtered.
		 * @return the metadata.
		 */
		public WorkspaceUserMetadata getMetadata() {
			return meta;
		}
		
		/** Get the position from which listing objects should begin. Allows paging through lists
		 * of objects.
		 * @return the position.
		 */
		public RefLimit getStartFrom() {
			return startFrom;
		}

		/** Get the date that specifies the earliest record that should be
		 * listed.
		 * @return the date. May be null.
		 */
		public Optional<Instant> getAfter() {
			return after;
		}

		/** Get the date that specifies the latest record that should be
		 * listed.
		 * @return the date. May be null.
		 */
		public Optional<Instant> getBefore() {
			return before;
		}

		/** Get the minimum object ID for objects that should be listed.
		 * @return the minimum object ID.
		 */
		public long getMinObjectID() {
			return minObjectID;
		}

		/** Get the maximum object ID for objects that should be listed.
		 * @return the maximum object ID.
		 */
		public long getMaxObjectID() {
			return maxObjectID;
		}

		/** Returns whether hidden objects should be listed
		 * @return true if hidden objects should be listed.
		 */
		public boolean isShowHidden() {
			return showHidden;
		}

		/** Returns whether deleted objects should be listed
		 * @return true if deleted objects should be listed.
		 */
		public boolean isShowDeleted() {
			return showDeleted;
		}

		/** Returns whether only deleted objects should be listed
		 * @return true if only deleted objects should be listed.
		 */
		public boolean isShowOnlyDeleted() {
			return showOnlyDeleted;
		}

		/** Returns whether all versions of objects should be listed
		 * @return true if all versions of objects should be listed.
		 */
		public boolean isShowAllVersions() {
			return showAllVers;
		}

		/** Returns whether object metadata should be listed
		 * @return true if object metadata should be listed.
		 */
		public boolean isIncludeMetaData() {
			return includeMetaData;
		}

		/** Get the maximum number of objects to list.
		 * @return the maximum number of objects to list.
		 */
		public int getLimit() {
			return limit;
		}
		
		/** Return whether this query should be executed with administrator privileges.
		 * @return whether the user requesting the query is a global administrator.
		 */
		public boolean asAdmin() {
			return asAdmin;
		}
	}
	
	/** Get a builder for {@link ListObjectsParameters}. The user by default is treated as
	 * an anonymous user.
	 * @param wsis the workspaces on which to query, at least 1, at most 10000.
	 * @return the new builder.
	 */
	public static Builder getBuilder(final Collection<WorkspaceIdentifier> wsis) {
		return new Builder(wsis);
	}
	
	/** A builder for {@link ListObjectsParameters}. */
	public static class Builder {
		
		private static final String ERRSTR = String.format(
				"At least one and no more than %s workspaces must be specified", MAX_WS);
		
		private final Set<WorkspaceIdentifier> wsis;
		private Optional<WorkspaceUser> user = Optional.empty();
		private Optional<TypeDefId> type = Optional.empty();
		private List<WorkspaceUser> savers = Arrays.asList(); // immutable
		private WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
		private RefLimit startFrom = RefLimit.buildEmpty();
		private Optional<Instant> after = Optional.empty();
		private Optional<Instant> before = Optional.empty();
		private long minObjectID = -1;
		private long maxObjectID = -1;
		private boolean showHidden = false;
		private boolean showDeleted = false;
		private boolean showOnlyDeleted = false;
		private boolean showAllVers = false;
		private boolean includeMetaData = false;
		private boolean asAdmin = false;
		private int limit = MAX_INFO_COUNT;
		
		private Builder(final Collection<WorkspaceIdentifier> wsis) {
			requireNonNull(wsis, ERRSTR);
			this.wsis = Collections.unmodifiableSet(new HashSet<>(wsis));
			if (wsis.isEmpty() || wsis.size() > MAX_WS) {
				throw new IllegalArgumentException(ERRSTR);
			}
		}
		
		/** Set whether the user is an admin or not.
		 * @param admin true if the user is an admin, false otherwise.
		 * @return this builder.
		 */
		public Builder withAsAdmin(final boolean admin) {
			this.asAdmin = admin;
			return this;
		}
		
		/** Set the user for the parameter set.
		 * @param user the user that will be listing objects, or null for an anonymous user.
		 * @return this builder.
		 */
		public Builder withUser(final WorkspaceUser user) {
			this.user = Optional.ofNullable(user);
			return this;
		}
		
		/** Set the type of objects to be listed.
		 * @param type the type of objects to be listed or null to set no type requirement.
		 * @return this builder.
		 */
		public Builder withType(final TypeDefId type) {
			this.type = Optional.ofNullable(type);
			return this;
		}
		
		/** Set the list of workspace users that saved objects to be listed.
		 * Only objects saved by these users will be listed.
		 * @param savers the list of workspace users. If null, set to an empty list.
		 * @return this builder.
		 */
		public Builder withSavers(final List<WorkspaceUser> savers) {
			if (savers == null) {
				this.savers = Collections.unmodifiableList(new LinkedList<>());
			} else {
				this.savers = Collections.unmodifiableList(new LinkedList<>(savers));
			}
			return this;
		}

		/** Set the metadata by which the object list should be filtered.
		 * Only one key/value pair is currently allowed.
		 * @param meta the metadata. If null, set to an empty map.
		 * @return this builder.
		 */
		public Builder withMetadata(final WorkspaceUserMetadata meta) {
			if (meta != null) {
				if (meta.size() > 1) {
					throw new IllegalArgumentException("Only one metadata spec allowed");
				}
				this.meta = meta;
			} else {
				this.meta = new WorkspaceUserMetadata();
			}
			return this;
		}
		
		/** Set the position, inclusive, from where objects should be listed.
		 * If this parameter is set, metadata, savers, min/max object IDs, and timestamps
		 * cannot be set as filters.
		 * @param start the position from where listing objects should start.
		 * @return this builder.
		 */
		public Builder withStartFrom(final RefLimit start) {
			this.startFrom = start == null? RefLimit.buildEmpty() : start;
			return this;
		}
		
		/** Set the date that specifies the earliest record that should be
		 * listed.
		 * @param after the date. Pass null to specify that objects from the
		 * beginning of time should be listed.
		 * @return this builder.
		 */
		public Builder withAfter(final Instant after) {
			this.after = Optional.ofNullable(after);
			return this;
		}
		
		/** Set the date that specifies the latest record that should be
		 * listed.
		 * @param before the date. Pass null to specify that objects saved up to
		 * the immediate present should be listed.
		 * @return this builder.
		 */
		public Builder withBefore(final Instant before) {
			this.before = Optional.ofNullable(before);
			return this;
		}
		
		/** Set the minimum object ID for objects that should be listed.
		 * @param minObjectID the minimum object ID, or <= 1 to specify no minimum.
		 * @return this builder.
		 */
		public Builder withMinObjectID(long minObjectID) {
			this.minObjectID = minObjectID;
			return this;
		}
		
		/** Set the maximum object ID for objects that should be listed.
		 * @param maxObjectID the maximum object ID, or <= 0 to specify no maximum
		 * @return this builder.
		 */
		public Builder withMaxObjectID(long maxObjectID) {
			this.maxObjectID = maxObjectID;
			return this;
		}
		
		/** Set whether hidden objects should be listed
		 * @param showHidden true if hidden objects should be listed
		 * @return this builder.
		 */
		public Builder withShowHidden(final boolean showHidden) {
			this.showHidden = showHidden;
			return this;
		}
		
		/** Set whether deleted objects should be listed
		 * @param showDeleted true if deleted objects should be listed
		 * @return this builder.
		 */
		public Builder withShowDeleted(final boolean showDeleted) {
			this.showDeleted = showDeleted;
			return this;
		}
		
		/** Set whether only deleted objects should be listed
		 * @param showOnlyDeleted true if only deleted objects should be listed
		 * @return this builder.
		 */
		public Builder withShowOnlyDeleted(final boolean showOnlyDeleted) {
			this.showOnlyDeleted = showOnlyDeleted;
			return this;
		}

		/** Set whether all versions of objects should be listed
		 * @param showAllVers true if all versions of objects should be listed
		 * @return this builder.
		 */
		public Builder withShowAllVersions(final boolean showAllVers) {
			this.showAllVers = showAllVers;
			return this;
		}
		
		/** Set whether object metadata should be listed
		 * @param includeMetaData true if object metadata should be listed
		 * @return this builder.
		 */
		public Builder withIncludeMetaData(final boolean includeMetaData) {
			this.includeMetaData = includeMetaData;
			return this;
		}
		
		/** Set the maximum number of objects to list.
		 * If limit < 1 or limit > 10000, limit is set to 10000.
		 * @param limit the maximum number of objects to list.
		 * @return this builder.
		 */
		public Builder withLimit(final int limit) {
			if (limit < 1 || limit > MAX_INFO_COUNT) {
				this.limit = MAX_INFO_COUNT;
			} else {
				this.limit = limit;
			}
			return this;
		}
		
		public ListObjectsParameters build() {
			if (startFrom.isPresent() && (
					!savers.isEmpty() || !meta.isEmpty() ||
					after.isPresent() || before.isPresent() ||
					minObjectID > 1 || maxObjectID > 0)) {
				throw new IllegalArgumentException("If a starting reference for paging is " +
					"provided, metadata, savers, min/max object IDs, and timestamps cannot " +
					"be set as filters.");
			}
			// throw an error if min obj id > max obj id?
			// throw an error if after > before?
			return new ListObjectsParameters(wsis, user, type, savers, meta, startFrom,
					after, before, minObjectID, maxObjectID,
					showHidden, showDeleted, showOnlyDeleted, showAllVers,
					includeMetaData, asAdmin, limit);
		}
		
	}
}
