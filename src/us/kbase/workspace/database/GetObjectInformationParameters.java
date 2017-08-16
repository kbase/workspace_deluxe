package us.kbase.workspace.database;

import java.util.Date;
import java.util.List;

import us.kbase.typedobj.core.TypeDefId;

/** Parameters for the WorkspaceDatabase getObjectInformation method.
 * Created from {@link ListObjectsParameters}.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class GetObjectInformationParameters {
	
	final private PermissionSet pset;
	final private TypeDefId type;
	final private List<WorkspaceUser> savers;
	final private WorkspaceUserMetadata meta;
	final private Date after;
	final private Date before;
	final private long minObjectID;
	final private long maxObjectID;
	final private boolean showHidden;
	final private boolean showDeleted;
	final private boolean showOnlyDeleted;
	final private boolean showAllVers;
	final private boolean includeMetaData;
	final private boolean asAdmin;
	final private int limit;
	
	GetObjectInformationParameters(
			final PermissionSet pset,
			final TypeDefId type,
			final List<WorkspaceUser> savers,
			final WorkspaceUserMetadata meta,
			final Date after,
			final Date before,
			final long minObjectID,
			final long maxObjectID,
			final boolean showHidden,
			final boolean showDeleted,
			final boolean showOnlyDeleted,
			final boolean showAllVers,
			final boolean includeMetaData,
			final int limit,
			final boolean asAdmin) {
		super();
		this.pset = pset;
		this.type = type;
		this.savers = savers;
		this.meta = meta;
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
	public TypeDefId getType() {
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

	/** Get the date that specifies the earliest record that should be
	 * listed.
	 * @return the date. May be null.
	 */
	public Date getAfter() {
		return after;
	}

	/** Get the date that specifies the latest record that should be
	 * listed.
	 * @return the date. May be null.
	 */
	public Date getBefore() {
		return before;
	}

	/** Get the minimum object ID for objects that should be listed.
	 * @return the minimum object ID.
	 */
	public long getMinObjectID() {
		return minObjectID;
	}

	/** Get the maximum object ID for objects that should be listed.
	 * @return the maxium object ID.
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
	
	/** Check if there are no filters set other than the object ID filters. The object ID filters
	 * may or may not be set.
	 * The other filters are the two date filters, the metadata filter, the savers filter, and
	 * the type filter.
	 * @return true if no filters other than the object ID filters are set.
	 */
	public boolean isObjectIDFiltersOnly() {
		boolean oidFiltersOnly = after == null;
		oidFiltersOnly = oidFiltersOnly && before == null;
		oidFiltersOnly = oidFiltersOnly && meta.isEmpty();
		oidFiltersOnly = oidFiltersOnly && savers.isEmpty();
		oidFiltersOnly = oidFiltersOnly && type == null; // must have workspaces specified
		return oidFiltersOnly;
	}
}
