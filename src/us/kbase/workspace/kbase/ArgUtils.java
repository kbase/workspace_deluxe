package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.Tuple10;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.UTCDateFormat;
import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenExpiredException;
import us.kbase.auth.TokenFormatException;
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
	
	public List<Tuple7<Long, String, String, String, Long, String, String>>
			wsInfoToTuple (final List<WorkspaceInformation> info) {
		final List<Tuple7<Long, String, String, String, Long, String, String>> ret =
				new LinkedList<Tuple7<Long, String, String, String, Long,
				String, String>>();
		for (final WorkspaceInformation wi: info) {
			ret.add(wsInfoToTuple(wi));
		}
		return ret;
	}

	public Tuple7<Long, String, String, String, Long, String, String>
			wsInfoToTuple(final WorkspaceInformation info) {
		return new Tuple7<Long, String, String, String, Long, String, String>()
				.withE1(info.getId())
				.withE2(info.getName())
				.withE3(info.getOwner().getUser())
				.withE4(dateFormat.formatDate(info.getModDate()))
				.withE5(info.getApproximateObjects())
				.withE6(translatePermission(info.getUserPermission())) 
				.withE7(translatePermission(info.isGloballyReadable()));
	}
	
	public List<Tuple7<String, String, String, Long, String, String, Long>> wsInfoToMetaTuple(
			List<WorkspaceInformation> info) {
		final List<Tuple7<String, String, String, Long, String, String, Long>> ret =
				new LinkedList<Tuple7<String, String, String, Long, String,
				String, Long>>();
		for (final WorkspaceInformation wi: info) {
			ret.add(wsInfoToMetaTuple(wi));
		}
		return ret;
	}
	
	public Tuple7<String, String, String, Long, String, String, Long>
				wsInfoToMetaTuple(final WorkspaceInformation info) {
		return new Tuple7<String, String, String, Long, String, String, Long>()
				.withE7(info.getId())
				.withE1(info.getName())
				.withE2(info.getOwner().getUser())
				.withE3(dateFormat.formatDate(info.getModDate()))
				.withE4(info.getApproximateObjects())
				.withE5(translatePermission(info.getUserPermission())) 
				.withE6(translatePermission(info.isGloballyReadable()));
	}
	
	public List<Tuple10<Long, String, String, String, Long, String,
			Long, String, String, Long>>
			objInfoToTuple (final List<ObjectInformation> info) {
		
		//oh the humanity
		final List<Tuple10<Long, String, String, String, Long, String,
				Long, String, String, Long>> ret = 
			new ArrayList<Tuple10<Long, String, String, String, Long,
			String, Long, String, String, Long>>();
		
		for (ObjectInformation m: info) {
			ret.add(new Tuple10<Long, String, String, String, Long,
					String, Long, String, String, Long>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(dateFormat.formatDate(m.getSavedDate()))
					.withE5(new Long(m.getVersion()))
					.withE6(m.getSavedBy().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getWorkspaceName())
					.withE9(m.getCheckSum())
					.withE10(m.getSize()));
		}
		return ret;
	}

	public Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>
			objInfoUserMetaToTuple(final ObjectInfoUserMeta info) {
		final List<ObjectInfoUserMeta> m = new ArrayList<ObjectInfoUserMeta>();
		m.add(info);
		return objInfoUserMetaToTuple(m).get(0);
	}

	public List<Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>>
			objInfoUserMetaToTuple(final List<ObjectInfoUserMeta> info) {

		//oh the humanity
		final List<Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>> ret = 
			new ArrayList<Tuple11<Long, String, String, String, Long,
			String, Long, String, String, Long, Map<String, String>>>();

		for (ObjectInfoUserMeta m: info) {
			ret.add(new Tuple11<Long, String, String, String, Long,
					String, Long, String, String, Long, Map<String, String>>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(dateFormat.formatDate(m.getSavedDate()))
					.withE5(new Long(m.getVersion()))
					.withE6(m.getSavedBy().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getWorkspaceName())
					.withE9(m.getCheckSum())
					.withE10(m.getSize())
					.withE11(m.getUserMetaData()));
		}
		return ret;
	}
	
	
	public Tuple12<String, String, String, Long, String, String, String,
	String, String, String, Map<String, String>, Long>
	objInfoUserMetaToMetaTuple(final ObjectInfoUserMeta info) {
		final List<ObjectInfoUserMeta> m = new ArrayList<ObjectInfoUserMeta>();
		m.add(info);
		return objInfoUserMetaToMetaTuple(m).get(0);
	}
	
	public List<Tuple12<String, String, String, Long, String, String, String,
	String, String, String, Map<String, String>, Long>>
	objInfoUserMetaToMetaTuple(final List<ObjectInfoUserMeta> info) {
		
		//oh the humanity
		final List<Tuple12<String, String, String, Long, String, String, String,
		String, String, String, Map<String, String>, Long>> ret = 
		new ArrayList<Tuple12<String, String, String, Long, String, String,
		String, String, String, String, Map<String, String>, Long>>();
		
		for (ObjectInfoUserMeta m: info) {
			ret.add(new Tuple12<String, String, String, Long, String, String, String,
					String, String, String, Map<String, String>, Long>()
					.withE1(m.getObjectName())
					.withE2(m.getTypeString())
					.withE3(dateFormat.formatDate(m.getSavedDate()))
					.withE4(new Long(m.getVersion()))
					.withE5("") //command is deprecated
					.withE6(m.getSavedBy().getUser())
					.withE7(m.getSavedBy().getUser()) //owner is deprecated
					.withE8(m.getWorkspaceName())
					.withE9("")//ref is deprecated
					.withE10(m.getCheckSum())
					.withE11(m.getUserMetaData())
					.withE12(m.getObjectId()));
		}
		return ret;
	}
	
	public static WorkspaceUser getUser(final String tokenstring,
			final AuthToken token)
			throws TokenExpiredException, TokenFormatException, IOException,
			AuthException {
		return getUser(tokenstring, token, false);
	}
	
	public static WorkspaceUser getUser(final String tokenstring,
			final AuthToken token, final boolean authrqd)
			throws TokenExpiredException, TokenFormatException, IOException,
			AuthException {
		if (tokenstring != null) {
			final AuthToken t = new AuthToken(tokenstring);
			if (!AuthService.validateToken(t)) {
				throw new AuthException("Token is invalid");
			}
			return new WorkspaceUser(t.getUserName());
		}
		if (token == null) {
			if (authrqd) {
				throw new AuthException("Authorization is required");
			}
			return null;
		}
		return new WorkspaceUser(token.getUserName());
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
					.withData(new UObject(o.getDataAsJsonNode()))
					.withInfo(objInfoUserMetaToTuple(o.getMeta()))
					.withProvenance(translateProvenanceActions(
							o.getProvenance().getActions()))
					.withCreator(o.getProvenance().getUser().getUser())
					.withCreated(dateFormat.formatDate(
							o.getProvenance().getDate())));
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
	
	public boolean longToBoolean(final Long b) {
		return longToBoolean(b, false);
	}
	
	public boolean longToBoolean(final Long b, final boolean def) {
		if (b == null) {
			return false;
		}
		return b != 0;
	}
}
