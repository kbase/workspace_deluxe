package us.kbase.workspace.database;

import java.util.Optional;

/** Specifies a workspace ref in the form X/Y/Z where X, Y, and Z are the workspace integer ID,
 * object integer ID, and version, that limits the scope of an operation - for example, paging
 * through objects.
 *
 */
public class RefLimit {
	
	private static final RefLimit EMPTY = new RefLimit(null, null, null);
	
	private final Optional<Long> wsid;
	private final Optional<Long> objid;
	private final Optional<Integer> ver;
	
	private RefLimit(final Long wsid, final Long objid, final Integer ver) {
		this.wsid = wsid == null || wsid < 1 ? Optional.empty() : Optional.of(wsid);
		this.objid = objid == null || objid < 1 ? Optional.empty() : Optional.of(objid);
		this.ver = ver== null || ver < 1 ? Optional.empty() : Optional.of(ver);
		if (this.ver.isPresent() && (!this.objid.isPresent() || !this.wsid.isPresent())) {
			throw new IllegalArgumentException("If a version is specified in a reference " +
					"limit the object ID and workspace ID must also be specified");
		}
		if (this.objid.isPresent() && !this.wsid.isPresent()) {
			throw new IllegalArgumentException("If an object ID is specified in a reference " +
					"limit the workspace ID must also be specified");
		}
	}
	
	/** Get a reference limit containing no limiting information.
	 * @return the limit.
	 */
	public static RefLimit buildEmpty() {
		return EMPTY;
	}
	
	/** Create the limit. Specify null or  numbers < 1 to denote that the limit should start at
	 * the beginning of the containing item (e.g. a version < 1 means the limit should start
	 * at the first or last version, depending on context, and an object ID < 1 means the limit
	 * should start at the first or last object, etc). A workspace or object ID may not be
	 * omitted if the version is included, and an object ID may not be omitted if a workspace is
	 * included.
	 * @param wsid - the workspace ID
	 * @param objid - the object ID
	 * @param ver - the version
	 */
	public static RefLimit build(final Long wsid, final Long objid, final Integer ver) {
		final RefLimit rl = new RefLimit(wsid, objid, ver);
		return rl.isEmpty() ? EMPTY: rl;
	}

	/** Get the limit for the workspace ID, if any.
	 * @return the workspace ID
	 */
	public Optional<Long> getWorkspaceID() {
		return wsid;
	}

	/** Get the limit for the object ID, if any.
	 * @return the object ID
	 */
	public Optional<Long> getObjectID() {
		return objid;
	}

	/** Get the limit for the version, if any.
	 * @return the version
	 */
	public Optional<Integer> getVersion() {
		return ver;
	}

	/** Determine whether this limit contains limiting information.
	 * E.g, at least a workspace ID is specified. 
	 * @return True if there is limiting information.
	 */
	public boolean isPresent() {
		return wsid.isPresent();
	}
	
	/** Determine whether this limit contains limiting information.
	 * E.g, at least a workspace ID is specified. 
	 * @return True if there is no limiting information.
	 */
	public boolean isEmpty() {
		return !wsid.isPresent();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((objid == null) ? 0 : objid.hashCode());
		result = prime * result + ((ver == null) ? 0 : ver.hashCode());
		result = prime * result + ((wsid == null) ? 0 : wsid.hashCode());
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
		RefLimit other = (RefLimit) obj;
		if (objid == null) {
			if (other.objid != null)
				return false;
		} else if (!objid.equals(other.objid))
			return false;
		if (ver == null) {
			if (other.ver != null)
				return false;
		} else if (!ver.equals(other.ver))
			return false;
		if (wsid == null) {
			if (other.wsid != null)
				return false;
		} else if (!wsid.equals(other.wsid))
			return false;
		return true;
	}
}
