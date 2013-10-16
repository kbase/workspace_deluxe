package us.kbase.typedobj.db;

import us.kbase.typedobj.core.AbsoluteTypeDefId;

public class TypeChange {
	private boolean unregistered;
	private AbsoluteTypeDefId typeVersion;
	private String jsonSchema;
	
	public TypeChange(boolean unregistered, AbsoluteTypeDefId typeVersion, String jsonSchema) {
		this.unregistered = unregistered;
		this.typeVersion = typeVersion;
		this.jsonSchema = jsonSchema;
	}
	
	public boolean isUnregistered() {
		return unregistered;
	}
	
	public AbsoluteTypeDefId getTypeVersion() {
		return typeVersion;
	}
	
	public String getJsonSchema() {
		return jsonSchema;
	}

	@Override
	public String toString() {
		return "TypeChange [unregistered=" + unregistered + ", typeVersion="
				+ typeVersion + ", jsonSchema=" + jsonSchema + "]";
	}
}
