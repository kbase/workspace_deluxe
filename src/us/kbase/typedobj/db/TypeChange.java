package us.kbase.typedobj.db;

import us.kbase.typedobj.core.TypeDefId;

public class TypeChange {
	private boolean unregistered;
	private TypeDefId typeVersion;
	private String jsonSchema;
	
	public TypeChange(boolean unregistered, TypeDefId typeVersion, String jsonSchema) {
		this.unregistered = unregistered;
		this.typeVersion = typeVersion;
		this.jsonSchema = jsonSchema;
	}
	
	public boolean isUnregistered() {
		return unregistered;
	}
	
	public TypeDefId getTypeVersion() {
		return typeVersion;
	}
	
	public String getJsonSchema() {
		return jsonSchema;
	}
}
