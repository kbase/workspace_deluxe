package us.kbase.workspace.database.mongo;

public class IDName {
	
	long id;
	String name;
	
	IDName(long id, String name) {
		super();
		this.id = id;
		this.name = name;
	}

	@Override
	public String toString() {
		return "IDName [id=" + id + ", name=" + name + "]";
	}
}
