package us.kbase.workspace.database;

import java.util.Date;
import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.TypeDefId;

/** Parameters for the WorkspaceDatabase getObjectInformation method.
 * Created from ListObjectsParameters.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class GetObjectInformationParameters {
	
	final PermissionSet pset;
	final TypeDefId type;
	final List<WorkspaceUser> savers;
	final Map<String, String> meta;
	final Date after;
	final Date before;
	final boolean showHidden;
	final boolean showDeleted;
	final boolean showOnlyDeleted;
	final boolean showAllVers;
	final boolean includeMetaData;
	final int skip;
	final int limit;
	
	GetObjectInformationParameters(
			final PermissionSet pset,
			final TypeDefId type,
			final List<WorkspaceUser> savers,
			final Map<String, String> meta,
			final Date after,
			final Date before,
			final boolean showHidden,
			final boolean showDeleted,
			final boolean showOnlyDeleted,
			final boolean showAllVers,
			final boolean includeMetaData,
			final int skip,
			final int limit) {
		super();
		this.pset = pset;
		this.type = type;
		this.savers = savers;
		this.meta = meta;
		this.after = after;
		this.before = before;
		this.showHidden = showHidden;
		this.showDeleted = showDeleted;
		this.showOnlyDeleted = showOnlyDeleted;
		this.showAllVers = showAllVers;
		this.includeMetaData = includeMetaData;
		this.skip = skip;
		this.limit = limit;
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
	public Map<String, String> getMetadata() {
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

	/** Get the number of objects to skip before listing objects.
	 * @return the number of objects to skip.
	 */
	public int getSkip() {
		return skip;
	}

	/** Get the maximum number of objects to list.
	 * @return the maximum number of objects to list.
	 */
	public int getLimit() {
		return limit;
	}
}
