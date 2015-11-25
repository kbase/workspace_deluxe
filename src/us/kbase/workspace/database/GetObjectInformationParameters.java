package us.kbase.workspace.database;

import java.util.Date;
import java.util.List;
import java.util.Map;

import us.kbase.typedobj.core.TypeDefId;

public class GetObjectInformationParameters {
	
	//TODO NOW document

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

	public PermissionSet getPermissionSet() {
		return pset;
	}

	public TypeDefId getType() {
		return type;
	}

	public List<WorkspaceUser> getSavers() {
		return savers;
	}

	public Map<String, String> getMetadata() {
		return meta;
	}

	public Date getAfter() {
		return after;
	}

	public Date getBefore() {
		return before;
	}

	public boolean isShowHidden() {
		return showHidden;
	}

	public boolean isShowDeleted() {
		return showDeleted;
	}

	public boolean isShowOnlyDeleted() {
		return showOnlyDeleted;
	}

	public boolean isShowAllVersions() {
		return showAllVers;
	}

	public boolean isIncludeMetaData() {
		return includeMetaData;
	}

	public int getSkip() {
		return skip;
	}

	public int getLimit() {
		return limit;
	}
}
