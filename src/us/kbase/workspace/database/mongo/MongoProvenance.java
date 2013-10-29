package us.kbase.workspace.database.mongo;

import java.util.LinkedList;
import java.util.List;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.annotation.JsonIgnore;

import us.kbase.workspace.database.Provenance;

public class MongoProvenance extends Provenance {
	
	//TODO return resolved refs in correct order
	
	private ObjectId _id;
	
	MongoProvenance(final Provenance p) {
		super(p.getUser());
		for (final Provenance.ProvenanceAction pa: p.getActions()) {
			addAction(pa);
		}
	}
	
	//hacky hacky hacky but what're you gonna do
	void fixProvenanceActions() {
		final List<ProvenanceAction> actions =
				new LinkedList<ProvenanceAction>();
		for (final ProvenanceAction pa: this.actions) {
			actions.add(new MongoProvenanceAction(pa));
		}
		this.actions = actions;
	}
	
	@SuppressWarnings("unused")
	private MongoProvenance() {} //for jackson
	
	ObjectId getMongoId() {
		return _id;
	}
	
	static class MongoProvenanceAction extends Provenance.ProvenanceAction {

		MongoProvenanceAction(final ProvenanceAction pa) {
			setServiceName(pa.getService());
			setWorkspaceObjects(pa.getWorkspaceObjects());
		}
		
		@JsonIgnore
		private List<String> resolvedObjs = new LinkedList<String>();
		
		MongoProvenanceAction withResolvedObjects(
				final List<String> objRefs) {
			if (objRefs != null) {
				resolvedObjs = objRefs;
			}
			return this;
		}

		@Override
		public List<String> getResolvedObjects() {
			return resolvedObjs;
		}
	}
}
