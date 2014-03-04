package us.kbase.workspace.database;

import java.util.List;

import us.kbase.workspace.database.mongo.ByteStorageWithFileCache;

public class WorkspaceObjectData {
	
	private final ByteStorageWithFileCache data;
	private final ObjectInformation info;
	private final Provenance prov;
	private final List<String> references;

	public WorkspaceObjectData(final ByteStorageWithFileCache data,
			final ObjectInformation info, final Provenance prov,
			final List<String> references) {
		if (data == null || info == null || prov == null ||
				references == null) {
			throw new IllegalArgumentException(
					"references, data, prov and meta cannot be null");
		}
		this.data = data;
		this.info = info;
		this.prov = prov;
		this.references = references;
	}

	public ByteStorageWithFileCache getDataAsTokens() {
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
		} catch (RuntimeException jpe) {
			throw jpe;
		} catch (Exception jpe) {
			//this should never happen
			throw new RuntimeException("something's dun broke", jpe);
		} finally {
			data.deleteTempFiles();
		}
	}

	public ObjectInformation getObjectInfo() {
		return info;
	}

	public Provenance getProvenance() {
		return prov;
	}
	
	public List<String> getReferences() {
		return references;
	}

	@Override
	public String toString() {
		return "WorkspaceObjectData [data=" + data + ", info=" + info
				+ ", prov=" + prov + ", references=" + references + "]";
	}
}
