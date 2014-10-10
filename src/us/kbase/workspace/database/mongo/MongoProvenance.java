package us.kbase.workspace.database.mongo;

import java.util.LinkedList;
import java.util.List;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.annotation.JsonIgnore;

import us.kbase.workspace.database.Provenance;

public class MongoProvenance extends Provenance {
	
	private ObjectId _id;
	
	MongoProvenance(final Provenance p) {
		super(p.getUser());
		for (final Provenance.ProvenanceAction pa: p.getActions()) {
			addAction(pa);
		}
	}
	
	void resolveReferences(final List<String> resolvedRefs) {
		final List<String> refs = new LinkedList<String>();
		for (final String s: resolvedRefs) {//stupid LazyBSONLists
			refs.add(s);
		}
		final List<ProvenanceAction> actions =
				new LinkedList<ProvenanceAction>();
		for (Provenance.ProvenanceAction pa: this.actions) {
			final int refcnt = pa.getWorkspaceObjects().size();
			final List<String> actionRefs = new LinkedList<String>(
					refs.subList(0, refcnt));
			refs.subList(0, refcnt).clear();
			actions.add(new MongoProvenanceAction(pa)
					.withResolvedObjects(actionRefs));
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
			setTime(pa.getTime());
			setServiceName(pa.getServiceName());
			setServiceVersion(pa.getServiceVersion());
			setMethod(pa.getMethod());
			setMethodParameters(pa.getMethodParameters());
			setScript(pa.getScript());
			setScriptVersion(pa.getScriptVersion());
			setCommandLine(pa.getCommandLine());
			setWorkspaceObjects(pa.getWorkspaceObjects());
			setIncomingArgs(pa.getIncomingArgs());
			setOutgoingArgs(pa.getOutgoingArgs());
			setExternalData(pa.getExternalData());
			setDescription(pa.getDescription());
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

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("MongoProvenanceAction [resolvedObjs=");
			builder.append(resolvedObjs);
			builder.append(", time=");
			builder.append(time);
			builder.append(", service=");
			builder.append(service);
			builder.append(", serviceVersion=");
			builder.append(serviceVersion);
			builder.append(", method=");
			builder.append(method);
			builder.append(", methodParameters=");
			builder.append(methodParameters);
			builder.append(", script=");
			builder.append(script);
			builder.append(", scriptVersion=");
			builder.append(scriptVersion);
			builder.append(", commandLine=");
			builder.append(commandLine);
			builder.append(", wsobjs=");
			builder.append(wsobjs);
			builder.append(", incomingArgs=");
			builder.append(incomingArgs);
			builder.append(", outgoingArgs=");
			builder.append(outgoingArgs);
			builder.append(", description=");
			builder.append(description);
			builder.append(", externalData=");
			builder.append(externalData);
			builder.append("]");
			return builder.toString();
		}
	}
}
