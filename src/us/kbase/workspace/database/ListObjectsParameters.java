package us.kbase.workspace.database;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import us.kbase.typedobj.core.TypeDefId;

public class ListObjectsParameters {
	
	//TODO unit tests
	//TODO NOW document
	
	private final static int MAX_INFO_COUNT = 10000;

	private final WorkspaceUser user;
	private final Set<WorkspaceIdentifier> wsis;
	private final TypeDefId type;
	
	private Permission minPerm = Permission.READ;
	private List<WorkspaceUser> savers = new LinkedList<WorkspaceUser>();
	private Map<String, String> meta = new HashMap<String, String>();
	private Date after = null;
	private Date before = null;
	private boolean showHidden = false;
	private boolean showDeleted = false;
	private boolean showOnlyDeleted = false;
	private boolean showAllVers = false;
	private boolean includeMetaData = false;
	private boolean excludeGlobal = false;
	private int skip = -1;
	private int limit = MAX_INFO_COUNT;
	
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

	public Set<WorkspaceIdentifier> getWorkspaces() {
		return wsis;
	}

	public TypeDefId getType() {
		return type;
	}

	public WorkspaceUser getUser() {
		return user;
	}

	public Permission getMinimumPermission() {
		return minPerm;
	}

	public ListObjectsParameters withMinimumPermission(
			final Permission minPerm) {
		if (minPerm == null || Permission.READ.compareTo(minPerm) > 0) {
			this.minPerm = Permission.READ;
		} else {
			this.minPerm = minPerm;
		}
		return this;
	}

	public List<WorkspaceUser> getSavers() {
		return savers;
	}

	public ListObjectsParameters withSavers(final List<WorkspaceUser> savers) {
		if (savers == null) {
			this.savers = Collections.unmodifiableList(
					new LinkedList<WorkspaceUser>());
		} else {
			this.savers = Collections.unmodifiableList(savers);
		}
		return this;
	}

	public Map<String, String> getMetadata() {
		return meta;
	}

	public ListObjectsParameters withMetadata(final Map<String, String> meta) {
		if (meta != null && meta.size() > 1) {
			throw new IllegalArgumentException(
					"Only one metadata spec allowed");
		}
		if (meta == null) {
			this.meta = Collections.unmodifiableMap(
					new HashMap<String, String>());
		} else {
			this.meta = Collections.unmodifiableMap(meta);
		}
		return this;
	}

	public Date getAfter() {
		return after;
	}

	public ListObjectsParameters withAfter(final Date after) {
		this.after = after;
		return this;
	}

	public Date getBefore() {
		return before;
	}

	public ListObjectsParameters withBefore(final Date before) {
		this.before = before;
		return this;
	}

	public boolean isShowHidden() {
		return showHidden;
	}

	public ListObjectsParameters withShowHidden(final boolean showHidden) {
		this.showHidden = showHidden;
		return this;
	}

	public boolean isShowDeleted() {
		return showDeleted;
	}

	public ListObjectsParameters withShowDeleted(final boolean showDeleted) {
		this.showDeleted = showDeleted;
		return this;
	}

	public boolean isShowOnlyDeleted() {
		return showOnlyDeleted;
	}

	public ListObjectsParameters withShowOnlyDeleted(
			final boolean showOnlyDeleted) {
		this.showOnlyDeleted = showOnlyDeleted;
		return this;
	}

	public boolean isShowAllVersions() {
		return showAllVers;
	}

	public ListObjectsParameters withShowAllVersions(
			final boolean showAllVers) {
		this.showAllVers = showAllVers;
		return this;
	}

	public boolean isIncludeMetaData() {
		return includeMetaData;
	}

	public ListObjectsParameters withIncludeMetaData(
			final boolean includeMetaData) {
		this.includeMetaData = includeMetaData;
		return this;
	}

	public boolean isExcludeGlobal() {
		return excludeGlobal;
	}

	public ListObjectsParameters withExcludeGlobal(
			final boolean excludeGlobal) {
		this.excludeGlobal = excludeGlobal;
		return this;
	}

	public int getSkip() {
		return skip;
	}

	public ListObjectsParameters withSkip(final int skip) {
		if (skip < 0) {
			this.skip = 0;
		} else {
			this.skip = skip;
		}
		return this;
	}

	public int getLimit() {
		return limit;
	}

	public ListObjectsParameters withLimit(final int limit) {
		if (limit < 1 || limit > MAX_INFO_COUNT) {
			this.limit = MAX_INFO_COUNT;
		} else {
			this.limit = limit;
		}
		return this;
	}
	
	GetObjectInformationParameters generateParameters(
			final PermissionSet perms) {
		if (perms == null) {
			throw new NullPointerException("perms cannot be null");
		}
		return new GetObjectInformationParameters(
				perms, type, savers, meta, after, before, showHidden,
				showDeleted, showOnlyDeleted, showAllVers, includeMetaData,
				skip, limit);
		
	}
}