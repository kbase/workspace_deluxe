package us.kbase.workspace.database;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import us.kbase.typedobj.core.TypeDefId;

/** The parameters for the listObjects method.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ListObjectsParameters {
	
	//TODO TEST unit tests
	//TODO CODE make this a real builder with an immutable result, and test for immutability
	
	private final static int MAX_INFO_COUNT = 10000;
	private final static int MAX_WS_AS_ADMIN = 1000;

	private final WorkspaceUser user;
	private final Set<WorkspaceIdentifier> wsis;
	private final TypeDefId type;
	
	private Permission minPerm = Permission.READ;
	private List<WorkspaceUser> savers = new LinkedList<WorkspaceUser>();
	private WorkspaceUserMetadata meta = new WorkspaceUserMetadata();
	private Date after = null;
	private Date before = null;
	private long minObjectID = -1;
	private long maxObjectID = -1;
	private boolean showHidden = false;
	private boolean showDeleted = false;
	private boolean showOnlyDeleted = false;
	private boolean showAllVers = false;
	private boolean includeMetaData = false;
	private boolean excludeGlobal = false;
	private boolean asAdmin = false;
	private int limit = MAX_INFO_COUNT;
	
	/** Create a set of parameters for calling the list objects method.
	 * @param user the user calling the method. If null, only globally
	 * readable objects will be returned.
	 * @param wsis the workspaces for which to list objects.
	 */
	public ListObjectsParameters(
			final WorkspaceUser user,
			final Collection<WorkspaceIdentifier> wsis) {
		this.user = user;
		if (wsis == null || wsis.isEmpty()) {
			throw new IllegalArgumentException(
					"Must provide at least one workspace");
		}
		this.wsis = Collections.unmodifiableSet(
				new HashSet<WorkspaceIdentifier>(wsis));
		type = null;
	}
	
	/** Create a set of parameters for calling the list objects method as an admin.
	 * @param wsis the workspaces for which to list objects.
	 */
	public ListObjectsParameters(
			final Collection<WorkspaceIdentifier> wsis) {
		this.user = null;
		this.asAdmin = true;
		if (wsis == null || wsis.isEmpty() || wsis.size() > MAX_WS_AS_ADMIN) {
			throw new IllegalArgumentException(String.format(
					"Must provide between 1 and %s workspaces", MAX_WS_AS_ADMIN));
		}
		this.wsis = Collections.unmodifiableSet(new HashSet<WorkspaceIdentifier>(wsis));
		type = null;
	}
	
	/** Create a set of parameters for calling the list objects method.
	 * @param user the user calling the method. If null, only globally
	 * readable objects will be returned.
	 * @param type the type of objects to list.
	 */
	public ListObjectsParameters(
			final WorkspaceUser user,
			final TypeDefId type) {
		this.user = user;
		if (type == null) {
			throw new NullPointerException("Type cannot be null");
		}
		this.type = type;
		this.wsis = Collections.unmodifiableSet(
				new HashSet<WorkspaceIdentifier>());
	}
	
	/** Create a set of parameters for calling the list objects method.
	 * @param user the user calling the method. If null, only globally
	 * readable objects will be returned.
	 * @param wsis the workspaces for which to list objects.
	 * @param type the type of objects to list.
	 */
	public ListObjectsParameters(
			final WorkspaceUser user,
			final Collection<WorkspaceIdentifier> wsis,
			final TypeDefId type) {
		this.user = user;
		if (wsis == null || wsis.isEmpty()) {
			throw new IllegalArgumentException(
					"Must provide at least one workspace");
		}
		this.wsis = Collections.unmodifiableSet(
				new HashSet<WorkspaceIdentifier>(wsis));
		if (type == null) {
			throw new NullPointerException("Type cannot be null");
		}
		this.type = type;
	}
	
	/** Create a set of parameters for calling the list objects method as an admin.
	 * @param wsis the workspaces for which to list objects.
	 * @param type the type of objects to list.
	 */
	public ListObjectsParameters(
			final Collection<WorkspaceIdentifier> wsis,
			final TypeDefId type) {
		this.user = null;
		this.asAdmin = true;
		if (wsis == null || wsis.isEmpty() || wsis.size() > MAX_WS_AS_ADMIN) {
			throw new IllegalArgumentException(String.format(
					"Must provide between 1 and %s workspaces", MAX_WS_AS_ADMIN));
		}
		this.wsis = Collections.unmodifiableSet(new HashSet<WorkspaceIdentifier>(wsis));
		if (type == null) {
			throw new NullPointerException("Type cannot be null");
		}
		this.type = type;
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
	public TypeDefId getType() {
		return type;
	}

	/** Get the workspace user for whom to list objects.
	 * @return the workspace user. May be null.
	 */
	public WorkspaceUser getUser() {
		return user;
	}

	/** Get the minimum permission the user must have to the objects for 
	 * said objects to be listed. Defaults to read.
	 * @return the minimum permission.
	 */
	public Permission getMinimumPermission() {
		return minPerm;
	}

	/** Set the minimum permission the user must have to the objects for 
	 * said objects to be listed.
	 * @param minPerm the minimum permission. If null or a permission less than
	 * read is passed in, the permission is set to read.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withMinimumPermission(
			final Permission minPerm) {
		if (minPerm == null || Permission.READ.compareTo(minPerm) > 0) {
			this.minPerm = Permission.READ;
		} else {
			this.minPerm = minPerm;
		}
		return this;
	}

	/** Get the list of workspace users that saved objects to be listed.
	 * Only objects saved by these users will be listed.
	 * @return the list of workspace users.
	 */
	public List<WorkspaceUser> getSavers() {
		return savers;
	}

	/** Set the list of workspace users that saved objects to be listed.
	 * Only objects saved by these users will be listed.
	 * @param savers the list of workspace users. If null, set to an empty list.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withSavers(final List<WorkspaceUser> savers) {
		if (savers == null) {
			this.savers = Collections.unmodifiableList(
					new LinkedList<WorkspaceUser>());
		} else {
			this.savers = Collections.unmodifiableList(savers);
		}
		return this;
	}

	/** Get the metadata by which the object list should be filtered.
	 * @return the metadata.
	 */
	public WorkspaceUserMetadata getMetadata() {
		return meta;
	}

	/** Set the metadata by which the object list should be filtered.
	 * Only one key/value pair is currently allowed.
	 * @param meta the metadata. If null, set to an empty map.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withMetadata(
			final WorkspaceUserMetadata meta) {
		if (meta != null) {
			if (meta.size() > 1) {
				throw new IllegalArgumentException(
						"Only one metadata spec allowed");
			}
			this.meta = meta;
		} else {
			this.meta = new WorkspaceUserMetadata();
		}
		return this;
	}

	/** Get the date that specifies the earliest record that should be
	 * listed.
	 * @return the date. May be null.
	 */
	public Date getAfter() {
		return after;
	}

	/** Set the date that specifies the earliest record that should be
	 * listed.
	 * @param after the date. Pass null to specify that objects from the
	 * beginning of time should be listed.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withAfter(final Date after) {
		this.after = after;
		return this;
	}

	/** Get the date that specifies the latest record that should be
	 * listed.
	 * @return the date. May be null.
	 */
	public Date getBefore() {
		return before;
	}

	/** Set the date that specifies the latest record that should be
	 * listed.
	 * @param before the date. Pass null to specify that objects saved up to
	 * the immediate present should be listed.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withBefore(final Date before) {
		this.before = before;
		return this;
	}

	/** Get the minimum object ID for objects that should be listed.
	 * @return the minimum object ID.
	 */
	public long getMinObjectID() {
		return minObjectID;
	}

	/** Set the minimum object ID for objects that should be listed.
	 * @param minObjectID the minimum object ID.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withMinObjectID(long minObjectID) {
		this.minObjectID = minObjectID;
		return this;
	}

	/** Get the maximum object ID for objects that should be listed.
	 * @return the maxium object ID.
	 */
	public long getMaxObjectID() {
		return maxObjectID;
	}

	/** Set the maximum object ID for objects that should be listed.
	 * @param maxObjectID the maximum object ID.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withMaxObjectID(long maxObjectID) {
		this.maxObjectID = maxObjectID;
		return this;
	}

	/** Returns whether hidden objects should be listed
	 * @return true if hidden objects should be listed.
	 */
	public boolean isShowHidden() {
		return showHidden;
	}

	/** Set whether hidden objects should be listed
	 * @param showHidden true if hidden objects should be listed
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withShowHidden(final boolean showHidden) {
		this.showHidden = showHidden;
		return this;
	}

	/** Returns whether deleted objects should be listed
	 * @return true if deleted objects should be listed.
	 */
	public boolean isShowDeleted() {
		return showDeleted;
	}

	/** Set whether deleted objects should be listed
	 * @param showDeleted true if deleted objects should be listed
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withShowDeleted(final boolean showDeleted) {
		this.showDeleted = showDeleted;
		return this;
	}

	/** Returns whether only deleted objects should be listed
	 * @return true if only deleted objects should be listed.
	 */
	public boolean isShowOnlyDeleted() {
		return showOnlyDeleted;
	}

	/** Set whether only deleted objects should be listed
	 * @param showOnlyDeleted true if only deleted objects should be listed
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withShowOnlyDeleted(
			final boolean showOnlyDeleted) {
		this.showOnlyDeleted = showOnlyDeleted;
		return this;
	}

	/** Returns whether all versions of objects should be listed
	 * @return true if all versions of objects should be listed.
	 */
	public boolean isShowAllVersions() {
		return showAllVers;
	}

	/** Set whether all versions of objects should be listed
	 * @param showAllVers true if all versions of objects should be listed
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withShowAllVersions(
			final boolean showAllVers) {
		this.showAllVers = showAllVers;
		return this;
	}

	/** Returns whether object metadata should be listed
	 * @return true if object metadata should be listed.
	 */
	public boolean isIncludeMetaData() {
		return includeMetaData;
	}

	/** Set whether object metadata should be listed
	 * @param includeMetaData true if object metadata should be listed
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withIncludeMetaData(
			final boolean includeMetaData) {
		this.includeMetaData = includeMetaData;
		return this;
	}

	/** Returns whether objects in globally readable workspaces should be
	 *  excluded.
	 * @return true if objects in globally readable workspaces should be
	 *  excluded.
	 */
	public boolean isExcludeGlobal() {
		return excludeGlobal;
	}

	/** Set whether objects in globally readable workspaces should be
	 *  excluded.
	 * @param exludeGlobal true if objects in globally readable workspaces
	 * should be excluded.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withExcludeGlobal(
			final boolean excludeGlobal) {
		this.excludeGlobal = excludeGlobal;
		return this;
	}

	/** Get the maximum number of objects to list.
	 * @return the maximum number of objects to list.
	 */
	public int getLimit() {
		return limit;
	}

	/** Set the maximum number of objects to list.
	 * If limit < 1 or limit > 10000, limit is set to 10000.
	 * @param limit the maximum number of objects to list.
	 * @return this ListObjectsParameters instance.
	 */
	public ListObjectsParameters withLimit(final int limit) {
		if (limit < 1 || limit > MAX_INFO_COUNT) {
			this.limit = MAX_INFO_COUNT;
		} else {
			this.limit = limit;
		}
		return this;
	}
	
	/** Get whether the command should be run as an admin. If this is the case the user will always
	 * be null.
	 * @return whether the effective user is an admin.
	 */
	public boolean asAdmin() {
		return asAdmin;
	}
	
	GetObjectInformationParameters generateParameters(
			final PermissionSet perms) {
		if (perms == null) {
			throw new NullPointerException("perms cannot be null");
		}
		return new GetObjectInformationParameters(
				perms, type, savers, meta, after, before, minObjectID,
				maxObjectID, showHidden, showDeleted, showOnlyDeleted,
				showAllVers, includeMetaData, limit, asAdmin);
		
	}
}
