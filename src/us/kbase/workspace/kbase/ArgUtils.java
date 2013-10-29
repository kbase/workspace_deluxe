package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.Tuple10;
import us.kbase.common.service.Tuple6;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.UTCDateFormat;
import us.kbase.auth.AuthToken;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.ObjectInfoUserMeta;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;

/**
 * not thread safe
 * @author gaprice@lbl.gov
 *
 */
public class ArgUtils {
	
	//simple date formats aren't synchronized
	private final UTCDateFormat dateFormat = new UTCDateFormat();
	
	public ArgUtils() {}
	
	public Provenance processProvenance(final WorkspaceUser user,
			final List<ProvenanceAction> actions) throws ParseException {
		
		final Provenance p = new Provenance(user);
		if (actions == null) {
			return p;
		}
		for (final ProvenanceAction a: actions) {
			checkAddlArgs(a.getAdditionalProperties(), a.getClass());
			p.addAction(new Provenance.ProvenanceAction()
					.withTime(a.getTime() == null ? null :
						dateFormat.parseDate(a.getTime()))
					.withServiceName(a.getService())
					.withServiceVersion(a.getServiceVer())
					.withMethod(a.getMethod())
					.withMethodParameters(translateMethodParametersToObject(
							a.getMethodParams()))
					.withScript(a.getScript())
					.withScriptVersion(a.getScriptVer())
					.withCommandLine(a.getScriptCommandLine())
					.withWorkspaceObjects(a.getInputWsObjects())
					.withIncomingArgs(a.getIntermediateIncoming())
					.withOutgoingArgs(a.getIntermediateOutgoing())
					.withDescription(a.getDescription())
					);
		}
		return p;
	}
	
	private List<Object> translateMethodParametersToObject(
			final List<UObject> methodParams) {
		if (methodParams == null) {
			return null;
		}
		final List<Object> params = new LinkedList<Object>();
		for (final UObject uo: methodParams) {
			params.add(uo.asInstance());
		}
		return params;
	}
	

	private List<UObject> translateMethodParametersToUObject(
			final List<Object> methodParams) {
		if (methodParams == null) {
			return null;
		}
		final List<UObject> params = new LinkedList<UObject>();
		for (final Object uo: methodParams) {
			params.add(new UObject(uo));
		}
		return params;
	}

	public Tuple6<Long, String, String, String, String, String>
			wsMetaToTuple (final WorkspaceInformation meta) {
		return new Tuple6<Long, String, String, String, String, String>()
				.withE1(meta.getId())
				.withE2(meta.getName())
				.withE3(meta.getOwner().getUser())
				.withE4(dateFormat.formatDate(meta.getModDate()))
				.withE5(translatePermission(meta.getUserPermission())) 
				.withE6(translatePermission(meta.isGloballyReadable()));
	}
	
	public List<Tuple9<Long, String, String, String, Long, String,
			Long, String, Long>>
			objMetaToTuple (final List<ObjectInformation> meta) {
		
		//oh the humanity
		final List<Tuple9<Long, String, String, String, Long, String,
				Long, String, Long>> ret = 
			new ArrayList<Tuple9<Long, String, String, String, Long,
			String, Long, String, Long>>();
		
		for (ObjectInformation m: meta) {
			ret.add(new Tuple9<Long, String, String, String, Long,
					String, Long, String, Long>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(dateFormat.formatDate(m.getCreatedDate()))
					.withE5(new Long(m.getVersion()))
					.withE6(m.getCreator().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize()));
		}
		return ret;
}
	
	public Tuple10<Long, String, String, String, Long, String,
			Long, String, Long, Map<String, String>>
			objUserMetaToTuple (final ObjectInfoUserMeta meta) {
		final List<ObjectInfoUserMeta> m = new ArrayList<ObjectInfoUserMeta>();
		m.add(meta);
		return objUserMetaToTuple(m).get(0);
	}
	
	public List<Tuple10<Long, String, String, String, Long, String,
			Long, String, Long, Map<String, String>>>
			objUserMetaToTuple (final List<ObjectInfoUserMeta> meta) {
		
		//oh the humanity
		final List<Tuple10<Long, String, String, String, Long, String,
			Long, String, Long, Map<String, String>>> ret = 
			new ArrayList<Tuple10<Long, String, String, String, Long,
			String, Long, String, Long, Map<String, String>>>();
		
		for (ObjectInfoUserMeta m: meta) {
			ret.add(new Tuple10<Long, String, String, String, Long,
					String, Long, String, Long, Map<String, String>>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(dateFormat.formatDate(m.getCreatedDate()))
					.withE5(new Long(m.getVersion()))
					.withE6(m.getCreator().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize())
					.withE10(m.getUserMetaData()));
		}
		return ret;
	}
	
	public static WorkspaceUser getUser(final AuthToken token) {
		if (token == null) {
			return null;
		}
		return new WorkspaceUser(token.getUserName());
	}
	
	public List<ObjectData> translateObjectData(
			final List<WorkspaceObjectData> objects) {
		final List<ObjectData> ret = new ArrayList<ObjectData>();
		for (final WorkspaceObjectData o: objects) {
			ret.add(new ObjectData()
					.withData(new UObject(o.getData()))
					.withInfo(objUserMetaToTuple(o.getMeta()))
					.withProvenance(translateProvenanceActions(
							o.getProvenance().getActions())));
		}
		return ret;
	}

	private List<ProvenanceAction> translateProvenanceActions(
			final List<Provenance.ProvenanceAction> actions) {
		final List<ProvenanceAction> pas = new LinkedList<ProvenanceAction>();
		for (final Provenance.ProvenanceAction a: actions) {
			pas.add(new ProvenanceAction()
					.withTime(dateFormat.formatDate(a.getTime()))
					.withService(a.getServiceName())
					.withServiceVer(a.getServiceVersion())
					.withMethod(a.getMethod())
					.withMethodParams(translateMethodParametersToUObject(
							a.getMethodParameters()))
					.withScript(a.getScript())
					.withScriptVer(a.getScriptVersion())
					.withScriptCommandLine(a.getCommandLine())
					.withInputWsObjects(a.getWorkspaceObjects())
					.withResolvedWsObjects(a.getResolvedObjects())
					.withIntermediateIncoming(a.getIncomingArgs())
					.withIntermediateOutgoing(a.getOutgoingArgs())
					.withDescription(a.getDescription())
					);
		}
		return pas;
	}
}
