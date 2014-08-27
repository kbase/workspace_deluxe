package us.kbase.workspace.database.mongo;

import java.util.List;
import java.util.Set;

import us.kbase.workspace.lib.ResolvedSaveObject;

class ObjectSavePackage {

	public ResolvedSaveObject wo;
	public String name;
	public TypeData td;
	public Set<String> refs;
	public List<String> provrefs;
	public MongoProvenance mprov;
	
	@Override
	public String toString() {
		return "ObjectSavePackage [wo=" + wo + ", name=" + name + ", td="
				+ td + ", mprov =" + mprov +  "]";
	}

}
