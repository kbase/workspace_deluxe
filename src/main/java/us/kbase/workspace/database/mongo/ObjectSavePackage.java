package us.kbase.workspace.database.mongo;

import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;

import us.kbase.workspace.database.ResolvedSaveObject;

public class ObjectSavePackage {
	
	ObjectSavePackage() {}

	ResolvedSaveObject wo;
	String name;
	Set<String> refs;
	List<String> provrefs;
	ObjectId provid;

}
