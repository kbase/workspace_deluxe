package us.kbase.workspace.database;

import java.util.Date;
import java.util.List;


/** Various information about a workspace object, including the workspace and object names and
 * ids, the object version, they type, and more.
 * @author gaprice@lbl.gov
 *
 */
public interface ObjectInformation {
	
	/** Returns the id of the object.
	 * @return the id of the object.
	 */
	long getObjectId();
	
	/** Returns the name of the object.
	 * @return the name of the object.
	 */
	String getObjectName();
	
	/** Returns the type of the object as a string. This will always be the absolute type.
	 * @return the object type.
	 */
	String getTypeString();
	
	/** Returns the size of the object when serialized to a JSON string in bytes.
	 * @return the size of the object in bytes.
	 */
	long getSize();
	
	/** Returns the time the object was saved.
	 * @return the time the object was saved.
	 */
	Date getSavedDate();
	
	/** Returns the version of the object.
	 * @return the version of the object.
	 */
	int getVersion();
	
	/** Returns the user that saved or copied the object.
	 * @return the user that saved or copied the object.
	 */
	WorkspaceUser getSavedBy();
	
	/** Returns the id of the workspace in which the object is stored.
	 * @return the workspace id.
	 */
	long getWorkspaceId();
	
	/** Returns the name of the workspace in which the object is stored.
	 * @return the workspace name.
	 */
	String getWorkspaceName();
	
	/** Returns the md5 checksum of the object when serialized to a JSON string with sorted keys.
	 * @return the md5 checksum.
	 */
	String getCheckSum();
	
	/** Returns the user supplied and automatically generated metadata for the object.
	 * @return the object metadata.
	 */
	UncheckedUserMetadata getUserMetaData();

	/** Returns the resolved reference path to this object from a user-accessible object. There may
	 * be more than one possible path to an object; only the path used to verify accessibility for
	 * the current access is provided. If the object is directly user-accessible the path will only
	 * contain the object reference.
	 * @return the reference path to the object.
	 */
	List<Reference> getReferencePath();
	
	/** Updates the reference path to the path supplied and returns a new ObjectInformation with
	 * that reference path.
	 * @param refpath the reference path that should replace the current reference path. The last
	 * entry in the reference path must be identical to the last entry in the current reference
	 * path.
	 * @return a new ObjectInformation with an updated reference path.
	 */
	ObjectInformation updateReferencePath(List<Reference> refpath);
	
	@Override
	boolean equals(Object obj);
	
	@Override
	int hashCode();
}
