package us.kbase.workspace.database;

/** A workspace ID that has been confirmed against the database.
 * 
 * This class should never be instantiated directly - only by classes
 * implementing the {@link us.kbase.workspace.database.Database} interface.
 * Those classes should ensure that the class passed to their methods is
 * the correct implementation.
 * 
 * @author gaprice@lbl.gov
 *
 */
public interface ResolvedWorkspaceID {
	
	public int getID();

}
