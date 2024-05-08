package us.kbase.workspace.database;

import java.time.Instant;

/** Packages an object information data object with the modification date of the object.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ObjectInfoWithModDate {
	
	//The mod date should maybe just be added to the ObjectInfo, but usually it's not needed.

	//TODO TEST
	
	private final ObjectInformation objectInfo;
	private final Instant modificationDate;
	
	public ObjectInfoWithModDate(
			final ObjectInformation objectInfo,
			final Instant modificationDate) {
		//TODO CODE test for null
		this.objectInfo = objectInfo;
		this.modificationDate = modificationDate;
	}

	public ObjectInformation getObjectInfo() {
		return objectInfo;
	}

	public Instant getModificationDate() {
		return modificationDate;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ObjectInfoWithModDate [objectInfo=");
		builder.append(objectInfo);
		builder.append(", modificationDate=");
		builder.append(modificationDate);
		builder.append("]");
		return builder.toString();
	}
}
