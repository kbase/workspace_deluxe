package us.kbase.workspace.database;

import java.util.Optional;

/** Specifies a workspace ref in the form X/Y/Z where X, Y, and Z are the workspace integer ID,
 * object integer ID, and version, that limits the scope of an operation - for example, paging
 * through objects.
 *
 */
public class RefLimit {
	
	private static final RefLimit EMPTY = new RefLimit(null, null, null);
	
	private final long wsid;
	private final long objid;
	private final int ver;
	
	private RefLimit(final Long wsid, final Long objid, final Integer ver) {
		this.wsid = wsid == null || wsid < 1 ? -1 : wsid;
		this.objid = objid == null || objid < 1 ? -1 : objid;
		this.ver = ver== null || ver < 1 ? -1 : ver;
		if (this.ver > 0 && (this.objid < 1 || this.wsid < 1)) {
			throw new IllegalArgumentException("If a version is specified in a reference " +
					"limit the object ID and workspace ID must also be specified");
		}
		if (this.objid > 0 && this.wsid < 1) {
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
	
	/** Create a limit from a refstring in the format 'workspace ID / object ID / version', e.g.
	 * 655/34/7. Nulls and empty strings result in an empty limit. The version may be omitted, and
	 * the object ID may be omitted if the version is omitted. If a '/' separator is present
	 * then either the next integer must be present or no characters, including whitespace, may
	 * be present - in other words "7/ " is an error.
	 * @param refstring the ref string to process.
	 * @return the limit.
	 * @throws IllegalArgumentException if there are more than 2 separators or there are
	 * characters but no number after a separator.
	 */
	public static RefLimit fromRefString(final String refstring) {
		// TODO CODE after switching completely to java 11, swap .trim() for .strip() globally
		if (refstring == null || refstring.trim().isEmpty()) {
			return EMPTY;
		}
		final String[] parts = refstring.split("/");
		if (parts.length > 3) {
			throw new IllegalArgumentException(
					"Illegal reference string, expected no more than 2 separators: " + refstring);
		}
		final Long ws = parseLong(parts[0].trim(), refstring, "workspace ID");
		Long id = null;
		Integer ver = null;
		if (parts.length > 1) {
			id = parseLong(parts[1].trim(), refstring, "object ID");
		}
		if (parts.length > 2) {
			final Long lver = parseLong(parts[2].trim(), refstring, "version");
			// let's just hope that we never hit 9 quintillion workspaces or objects in a workspace
			ver = lver > Integer.MAX_VALUE ? Integer.MAX_VALUE : lver.intValue();
		}
		return new RefLimit(ws, id, ver);
	}

	private static Long parseLong(
			final String putativeNum,
			final String refstring,
			final String name) {
		try {
			return Long.parseLong(putativeNum);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(String.format(
					"Illegal integer %s in reference string %s: %s",
					name, refstring, putativeNum));
		}
	}

	/** Get the limit for the workspace ID, if any.
	 * @return the workspace ID
	 */
	public Optional<Long> getWorkspaceID() {
		return Optional.ofNullable(wsid < 1 ? null : wsid);
	}

	/** Get the limit for the object ID, if any.
	 * @return the object ID
	 */
	public Optional<Long> getObjectID() {
		return Optional.ofNullable(objid < 1 ? null : objid);
	}

	/** Get the limit for the version, if any.
	 * @return the version
	 */
	public Optional<Integer> getVersion() {
		return Optional.ofNullable(ver < 1 ? null : ver);
	}

	/** Determine whether this limit contains limiting information.
	 * E.g, at least a workspace ID is specified. 
	 * @return True if there is limiting information.
	 */
	public boolean isPresent() {
		return wsid > 0;
	}
	
	/** Determine whether this limit contains limiting information.
	 * E.g, at least a workspace ID is specified. 
	 * @return True if there is no limiting information.
	 */
	public boolean isEmpty() {
		return wsid < 1;
	}

	/** Return a new ref limit, decrementing the version by one and incrementing the object ID if
	 * necessary.
	 * 
	 * If there is no version present, then this instance is returned unchanged.
	 * If the version decrement causes the version to be zero, in the returned instance the
	 * object ID is incremented by one and the version removed.
	 * @return the ref limit
	 */
	public RefLimit decrementVersionIncrementingObjectID() {
		if (ver < 1) {
			return this;
		}
		return new RefLimit(wsid, ver == 1 ? objid + 1 : objid, ver == 1 ? null : ver - 1);
	}
	
	@Override
	public String toString() {
		return "RefLimit [wsid=" + (wsid < 1 ? null : wsid) +
				", objid=" + (objid < 1 ? null : objid) +
				", ver=" + (ver < 1 ? null : ver) + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (objid ^ (objid >>> 32));
		result = prime * result + ver;
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
		RefLimit other = (RefLimit) obj;
		if (objid != other.objid)
			return false;
		if (ver != other.ver)
			return false;
		if (wsid != other.wsid)
			return false;
		return true;
	}
}
