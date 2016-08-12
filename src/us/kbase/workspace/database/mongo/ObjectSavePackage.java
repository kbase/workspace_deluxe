package us.kbase.workspace.database.mongo;

import java.util.List;
import java.util.Set;

import us.kbase.workspace.database.ResolvedSaveObject;

public class ObjectSavePackage {
	
	ObjectSavePackage() {}

	ResolvedSaveObject wo;
	String name;
	Set<String> refs;
	List<String> provrefs;
	MongoProvenance mprov;
	
	@Override
	public String toString() {
		return "ObjectSavePackage [wo=" + wo + ", name=" + name +
				", mprov =" + mprov +  "]";
	}

}
