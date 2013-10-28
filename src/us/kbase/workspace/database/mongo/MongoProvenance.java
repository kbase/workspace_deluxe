package us.kbase.workspace.database.mongo;

import org.bson.types.ObjectId;

import us.kbase.workspace.database.Provenance;

public class MongoProvenance extends Provenance {
	
	//TODO return resolved refs in correct order
	
	private ObjectId _id;
	
	MongoProvenance(final Provenance p) {
		super(p.getUser());
		for (final ProvenanceAction pa: p.getActions()) {
			addAction(pa);
		}
	}
	
	@SuppressWarnings("unused")
	private MongoProvenance() {} //for jackson
	
	ObjectId getMongoId() {
		return _id;
	}
}
