package us.kbase.workspace.database;

/** A workspace ID that has been confirmed against the database.
 * 
 * This class should never be instantiated directly - only by classes
 * implementing the {@link us.kbase.workspace.database.WorkspaceDatabase} interface.
 * Those classes should ensure that the class passed to their methods is
 * the correct implementation.
 * 
 * Note that a set of resolved IDs will be consistent if they're pulled from
 * the database at the same time, but may not be consistent if not pulled in
 * the same batch.
 * 
 * @author gaprice@lbl.gov
 *
 */
public interface ResolvedWorkspaceID {
	
	public long getID();
	public String getName();
	public boolean isLocked();
	public boolean isDeleted();
	public int hashCode();
	public boolean equals(Object obj);

}
