package us.kbase.workspace.database;

/** The result of a copy operation.
 * @author gaprice@lbl.gov
 *
 */
public class CopyResult {
	
	//TODO TEST
	
	private final ObjectInformation objInfo;
	private final boolean allVersionsCopied;
	
	/** Create a new copy result.
	 * @param objInfo the information about the new object.
	 * @param allVersionsCopied true if the object is a brand new object and all the versions of
	 * the source object have been copied over. false if the copy target was an existing object or
	 * only the specified version of the source object was copied.
	 */
	public CopyResult(final ObjectInformation objInfo, final boolean allVersionsCopied) {
		this.objInfo = objInfo;
		this.allVersionsCopied = allVersionsCopied;
	}

	/** Get the object information for the new object.
	 * @return
	 */
	public ObjectInformation getObjectInformation() {
		return objInfo;
	}

	/** Get whether the new object consists of all the versions of the source object.
	 * @return true if all the versions of the source object were copied over.
	 */
	public boolean isAllVersionsCopied() {
		return allVersionsCopied;
	}
}
