package us.kbase.workspace.database;

import java.util.List;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;


public class WorkspaceObjectData extends WorkspaceObjectInformation {
	
	private final ByteArrayFileCache data;

	public WorkspaceObjectData(final ByteArrayFileCache data,
			final ObjectInformation info, final Provenance prov,
			final List<String> references) {
		super(info, prov, references);
		if (data == null) {
			throw new IllegalArgumentException("data cannot be null");
		}
		this.data = data;
	}

	public ByteArrayFileCache getDataAsTokens() {
		return data;
	}
	
	/**
	 * You can call this method only once since it deleted temporary file with data.
	 * @return Maps/lists/scalars
	 */
	public Object getData() {
		try {
			//return MAPPER.treeToValue(data, Object.class);
			return data.getUObject().asClassInstance(Object.class);
		} catch (RuntimeException jpe) { //don't wrap RTEs in RTEs
			throw jpe;
		} catch (Exception jpe) {
			//this should never happen
			throw new RuntimeException("something's dun broke", jpe);
		}
	}

	@Override
	public String toString() {
		return "WorkspaceObjectData [data=" + data + ", info=" +
				getObjectInfo() + ", prov=" + getProvenance() + ", references="
				+ getReferences() + "]";
	}
}
