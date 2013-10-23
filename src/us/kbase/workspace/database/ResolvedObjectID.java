package us.kbase.workspace.database;

/**
 * name is resolved *at the time the database was accessed and is not further
 * updated*
 * 
 * The underlying assumption of this class is all object IDs are unique and all
 * names are unique at the time of resolution. Therefore a set of
 * ResolvedObjectIDs constructed at the same time are all unique in name and id,
 * and removing one or the other field would not cause the number of unique
 * objects to change (as measured by the unique hashcode count, for example).
 * 
 * This is *not* the case for objects generated from different queries.
 * 
 * Versions are not resolved.
 * 
 * @author gaprice@lbl.gov
 *
 */
public interface ResolvedObjectID {
	
	public ResolvedWorkspaceID getWorkspaceIdentifier();
	public String getName();
	public Long getId();
	public Integer getVersion();
	@Override
	public String toString();
	@Override
	public int hashCode();
	@Override
	public boolean equals(Object obj);
}
