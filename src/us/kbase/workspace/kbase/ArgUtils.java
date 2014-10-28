package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_NONE;
import static us.kbase.workspace.kbase.KBasePermissions.PERM_READ;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.kbase.common.service.JsonClientException;
import us.kbase.common.service.ServerException;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.Tuple12;
import us.kbase.common.service.Tuple7;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;
import us.kbase.handlemngr.HandleMngrClient;
import us.kbase.auth.AuthException;
import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.auth.TokenExpiredException;
import us.kbase.auth.TokenFormatException;
import us.kbase.workspace.ExternalDataUnit;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ObjectProvenanceInfo;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.database.ByteArrayFileCacheManager.ByteArrayFileCache;
import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.Provenance;
import us.kbase.workspace.database.Provenance.ExternalData;
import us.kbase.workspace.database.WorkspaceInformation;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceObjectInformation;
import us.kbase.workspace.database.WorkspaceUser;

/**
 * not thread safe
 * @author gaprice@lbl.gov
 *
 */
public class ArgUtils {
	
	private final static Logger LOGGER =
			LoggerFactory.getLogger(ArgUtils.class);
	
	private final static DateTimeFormatter DATE_PARSER =
			new DateTimeFormatterBuilder()
				.append(DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss"))
				.appendOptional(DateTimeFormat.forPattern(".SSS").getParser())
				.append(DateTimeFormat.forPattern("Z"))
				.toFormatter();
	
	private final static DateTimeFormatter DATE_FORMATTER =
			DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZoneUTC();
	
	public static Provenance processProvenance(final WorkspaceUser user,
			final List<ProvenanceAction> actions) throws ParseException {
		
		final Provenance p = new Provenance(user);
		if (actions == null) {
			return p;
		}
		for (final ProvenanceAction a: actions) {
			checkAddlArgs(a.getAdditionalProperties(), a.getClass());
			p.addAction(new Provenance.ProvenanceAction()
					.withTime(parseDate(a.getTime()))
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
					.withExternalData(processExternalData(a.getExternalData()))
					.withDescription(a.getDescription())
					);
		}
		return p;
	}
	
	private static List<ExternalData> processExternalData(
			final List<ExternalDataUnit> externalData) throws ParseException {
		final List<ExternalData> ret = new LinkedList<ExternalData>();
		if (externalData == null) {
			return ret;
		}
		for (final ExternalDataUnit edu: externalData) {
			checkAddlArgs(edu.getAdditionalProperties(), edu.getClass());
			ret.add(new ExternalData()
					.withDataId(edu.getDataId())
					.withDataUrl(edu.getDataUrl())
					.withDescription(edu.getDescription())
					.withResourceName(edu.getResourceName())
					.withResourceReleaseDate(
							parseDate(edu.getResourceReleaseDate()))
					.withResourceUrl(edu.getResourceUrl())
					.withResourceVersion(edu.getResourceVersion())
					);
		}
		return ret;
	}

	public static Date parseDate(final String date) throws ParseException {
		if (date == null) {
			return null;
		}
		try {
			return DATE_PARSER.parseDateTime(date).toDate();
		} catch (IllegalArgumentException iae) {
			throw new IllegalArgumentException("Unparseable date: " +
					iae.getMessage());
		}
	}
	
	public static String formatDate(final Date date) {
		if (date == null) {
			return null;
		}
		return DATE_FORMATTER.print(new DateTime(date));
	}
	
	private static List<Object> translateMethodParametersToObject(
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
	

	private static List<UObject> translateMethodParametersToUObject(
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
	
	public static List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>>
			wsInfoToTuple (final List<WorkspaceInformation> info) {
		final List<Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>> ret =
				new LinkedList<Tuple9<Long, String, String, String, Long, String,
						String, String, Map<String, String>>>();
		for (final WorkspaceInformation wi: info) {
			ret.add(wsInfoToTuple(wi));
		}
		return ret;
	}

	public static Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>
			wsInfoToTuple(final WorkspaceInformation info)  {
		return new Tuple9<Long, String, String, String, Long, String, String, String, Map<String, String>>()
				.withE1(info.getId())
				.withE2(info.getName())
				.withE3(info.getOwner().getUser())
				.withE4(formatDate(info.getModDate()))
				.withE5(info.getApproximateObjects())
				.withE6(translatePermission(info.getUserPermission())) 
				.withE7(translatePermission(info.isGloballyReadable()))
				.withE8(info.getLockState())
				.withE9(info.getUserMeta());
	}
	
	public static List<Tuple7<String, String, String, Long, String, String, Long>> wsInfoToMetaTuple(
			List<WorkspaceInformation> info) {
		final List<Tuple7<String, String, String, Long, String, String, Long>> ret =
				new LinkedList<Tuple7<String, String, String, Long, String,
				String, Long>>();
		for (final WorkspaceInformation wi: info) {
			ret.add(wsInfoToMetaTuple(wi));
		}
		return ret;
	}
	
	public static Tuple7<String, String, String, Long, String, String, Long>
				wsInfoToMetaTuple(final WorkspaceInformation info) {
		return new Tuple7<String, String, String, Long, String, String, Long>()
				.withE7(info.getId())
				.withE1(info.getName())
				.withE2(info.getOwner().getUser())
				.withE3(formatDate(info.getModDate()))
				.withE4(info.getApproximateObjects())
				.withE5(translatePermission(info.getUserPermission())) 
				.withE6(translatePermission(info.isGloballyReadable()));
	}
	
	public static Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>
			objInfoToTuple(
					final ObjectInformation info,
					final boolean logObjects) {
		final List<ObjectInformation> m = new ArrayList<ObjectInformation>();
		m.add(info);
		return objInfoToTuple(m, logObjects).get(0);
	}

	public static List<List<Tuple11<Long, String, String, String, Long, String, Long, String, String, Long, Map<String, String>>>>
			translateObjectDataList(
					final List<Set<ObjectInformation>> lsoi,
					final boolean logObjects) {
		final List<List<Tuple11<Long, String, String, String, Long, String,
				Long, String, String, Long, Map<String, String>>>> ret = 
				new LinkedList<List<Tuple11<Long,String,String,String,Long,String,Long,String,String,Long,Map<String,String>>>>();
		for (Set<ObjectInformation> soi: lsoi) {
			ret.add(objInfoToTuple(new LinkedList<ObjectInformation>(soi),
					logObjects));
		}
		return ret;
	}
	
	public static List<Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>>
			objInfoToTuple(
					final List<ObjectInformation> info,
					final boolean logObjects) {

		//oh the humanity
		final List<Tuple11<Long, String, String, String, Long, String,
			Long, String, String, Long, Map<String, String>>> ret = 
			new ArrayList<Tuple11<Long, String, String, String, Long,
			String, Long, String, String, Long, Map<String, String>>>();
		for (ObjectInformation m: info) {
			if (m == null) {
				ret.add(null);
			} else {
				if (logObjects) {
					LOGGER.info("Object {}/{}/{} {}", m.getWorkspaceId(),
							m.getObjectId(), m.getVersion(),
							m.getTypeString());
				}
				ret.add(new Tuple11<Long, String, String, String, Long,
						String, Long, String, String, Long, Map<String, String>>()
						.withE1(m.getObjectId())
						.withE2(m.getObjectName())
						.withE3(m.getTypeString())
						.withE4(formatDate(m.getSavedDate()))
						.withE5(new Long(m.getVersion()))
						.withE6(m.getSavedBy().getUser())
						.withE7(m.getWorkspaceId())
						.withE8(m.getWorkspaceName())
						.withE9(m.getCheckSum())
						.withE10(m.getSize())
						.withE11(m.getUserMetaData()));
			}
		}
		return ret;
	}
	
	
	public static Tuple12<String, String, String, Long, String, String, String,
			String, String, String, Map<String, String>, Long>
			objInfoToMetaTuple(
					final ObjectInformation info,
					final boolean logObjects) {
		final List<ObjectInformation> m = new ArrayList<ObjectInformation>();
		m.add(info);
		return objInfoToMetaTuple(m, logObjects).get(0);
	}
	
	public static List<Tuple12<String, String, String, Long, String, String, String,
			String, String, String, Map<String, String>, Long>>
			objInfoToMetaTuple(
					final List<ObjectInformation> info,
					final boolean logObjects) {
		//oh the humanity
		final List<Tuple12<String, String, String, Long, String, String, String,
		String, String, String, Map<String, String>, Long>> ret = 
		new ArrayList<Tuple12<String, String, String, Long, String, String,
		String, String, String, String, Map<String, String>, Long>>();
		
		for (ObjectInformation m: info) {
			if (logObjects) {
				LOGGER.info("Object {}/{}/{} {}", m.getWorkspaceId(),
						m.getObjectId(), m.getVersion(), m.getTypeString());
			}
			ret.add(new Tuple12<String, String, String, Long, String, String, String,
					String, String, String, Map<String, String>, Long>()
					.withE1(m.getObjectName())
					.withE2(m.getTypeString())
					.withE3(formatDate(m.getSavedDate()))
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
	
	public static List<WorkspaceUser> validateUsers(
			final List<String> users, final AuthToken token)
			throws IOException, AuthException {
		final List<WorkspaceUser> wsusers = convertUsers(users);
		final Map<String, Boolean> userok;
		try {
			userok = AuthService.isValidUserName(users, token);
		} catch (UnknownHostException uhe) {
			//message from UHE is only the host name
			throw new AuthException(
					"Could not contact Authorization Service host to validate user names: "
							+ uhe.getMessage(), uhe);
		}
		for (String u: userok.keySet()) {
			if (!userok.get(u)) {
				throw new IllegalArgumentException(String.format(
						"User %s is not a valid user", u));
			}
		}
		return wsusers;
	}
	
	public static List<WorkspaceUser> convertUsers(final List<String> users) {
		final List<WorkspaceUser> wsusers = new ArrayList<WorkspaceUser>();
		if (users == null) {
			return null;
		}
		for (String u: users) {
			wsusers.add(new WorkspaceUser(u));
		}
		return wsusers;
	}
	
	public static List<ObjectData> translateObjectData(
			final List<WorkspaceObjectData> objects, 
			final WorkspaceUser user,
			final Set<ByteArrayFileCache> resourcesToDestroy,
			final URL handleManagerURl,
			final RefreshingToken handleManagertoken,
			final boolean logObjects) {
		final List<ObjectData> ret = new ArrayList<ObjectData>();
		for (final WorkspaceObjectData o: objects) {
			final HandleError error = makeHandlesReadable(
					o, user, handleManagerURl, handleManagertoken);
			final ByteArrayFileCache resource = o.getDataAsTokens();
			ret.add(new ObjectData()
					.withData(resource.getUObject())
					.withInfo(objInfoToTuple(o.getObjectInfo(), logObjects))
					.withProvenance(translateProvenanceActions(
							o.getProvenance().getActions()))
					.withCreator(o.getProvenance().getUser().getUser())
					.withCreated(formatDate(
							o.getProvenance().getDate()))
					.withRefs(o.getReferences())
					.withCopied(o.getCopyReference() == null ? null :
						o.getCopyReference().getId())
					.withCopySourceInaccessible(
							o.isCopySourceInaccessible() ? 1L: 0L)
					.withExtractedIds(o.getExtractedIds())
					.withHandleError(error.error)
					.withHandleStacktrace(error.stackTrace));
			resourcesToDestroy.add(resource);
		}
		return ret;
	}
	
	public static List<ObjectProvenanceInfo> translateObjectProvInfo(
			final List<WorkspaceObjectInformation> objects,
			final WorkspaceUser user,
			final URL handleManagerURl,
			final RefreshingToken handleManagertoken,
			final boolean logObjects) {
		final List<ObjectProvenanceInfo> ret =
				new ArrayList<ObjectProvenanceInfo>();
		for (final WorkspaceObjectInformation o: objects) {
			final HandleError error = makeHandlesReadable(
					o, user, handleManagerURl, handleManagertoken);
			ret.add(new ObjectProvenanceInfo()
					.withInfo(objInfoToTuple(o.getObjectInfo(), logObjects))
					.withProvenance(translateProvenanceActions(
							o.getProvenance().getActions()))
					.withCreator(o.getProvenance().getUser().getUser())
					.withCreated(formatDate(
							o.getProvenance().getDate()))
					.withRefs(o.getReferences())
					.withCopied(o.getCopyReference() == null ? null :
						o.getCopyReference().getId())
					.withCopySourceInaccessible(
						o.isCopySourceInaccessible() ? 1L: 0L)
					.withExtractedIds(o.getExtractedIds())
					.withHandleError(error.error)
					.withHandleStacktrace(error.stackTrace));
		}
		return ret;
	}
	
	private static class HandleError {
		
		public String error;
		public String stackTrace;

		public HandleError(String error, String stackTrace) {
			super();
			this.error = error;
			this.stackTrace = stackTrace;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("HandleError [error=");
			builder.append(error);
			builder.append(", stackTrace=");
			builder.append(stackTrace);
			builder.append("]");
			return builder.toString();
		}
		
	}

	private static HandleError makeHandlesReadable(
			final WorkspaceObjectInformation o,
			final WorkspaceUser user,
			final URL handleManagerURL,
			final RefreshingToken handleManagertoken) {
		final List<String> handles = o.getExtractedIds().get(
				HandleIdHandlerFactory.type.getType());
		if (handles == null || handles.isEmpty()) {
			return new HandleError(null, null);
		}
		final AuthToken token;
		try {
			token = handleManagertoken.getToken();
		} catch (AuthException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - " +
							"couldn't refresh the workspace credentials: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - " +
							"an IO error occured while attempting to " + 
							"refresh the workspace credentials: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		}
		
		final HandleMngrClient hmc;
		try {
			hmc = new HandleMngrClient(handleManagerURL, token);
			if (handleManagerURL.getProtocol().equals("http")) {
				hmc.setIsInsecureHttpConnectionAllowed(true);
			}
		} catch (UnauthorizedException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - " +
							"the Workspace credentials were rejected: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (IOException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - IO exception " +
							"attempting to validate credentials with the " +
							"Auth Service: " + e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		}
		try {
			hmc.addReadAcl(handles, user.getUser());
		} catch (IOException e) {
			return new HandleError(
					"There was an IO problem while attempting to set " +
							"Handle ACLs: " + e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (UnauthorizedException e) {
			return new HandleError(
					"Unable to contact the Handle Manager - " +
							"the Workspace credentials were rejected: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (ServerException e) {
			return new HandleError(
					"The Handle Manager reported a problem while attempting " +
							"to set Handle ACLs: " + e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		} catch (JsonClientException e) {
			return new HandleError(
					"There was an unexpected problem while contacting the " +
							"Handle Manager to set Handle ACLs: " +
							e.getMessage(),
					ExceptionUtils.getStackTrace(e));
		}
		return new HandleError(null, null);
	}

	private static List<ProvenanceAction> translateProvenanceActions(
			final List<Provenance.ProvenanceAction> actions) {
		final List<ProvenanceAction> pas = new LinkedList<ProvenanceAction>();
		for (final Provenance.ProvenanceAction a: actions) {
			pas.add(new ProvenanceAction()
					.withTime(formatDate(a.getTime()))
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
					.withExternalData(
							translateExternalDataUnits(a.getExternalData()))
					.withDescription(a.getDescription())
					);
		}
		return pas;
	}
	
	private static List<ExternalDataUnit> translateExternalDataUnits(
			List<ExternalData> externalData) {
		final List<ExternalDataUnit> ret = new LinkedList<ExternalDataUnit>();
		if (externalData == null) {
			return ret; //this should never happen, but just in case
		}
		for (final ExternalData ed: externalData) {
			ret.add(new ExternalDataUnit()
					.withDataId(ed.getDataId())
					.withDataUrl(ed.getDataUrl())
					.withDescription(ed.getDescription())
					.withResourceName(ed.getResourceName())
					.withResourceReleaseDate(
							formatDate(ed.getResourceReleaseDate()))
					.withResourceUrl(ed.getResourceUrl())
					.withResourceVersion(ed.getResourceVersion())
					);
		}
		return ret;
	}

	public static boolean longToBoolean(final Long b) {
		if (b == null) {
			return false;
		}
		return b != 0;
	}
	
	public static int longToInt(final Long l, final String name, final int deflt) {
		if (l == null) {
			return deflt;
		}
		if (l > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(
						name + " can be no greater than " + Integer.MAX_VALUE);
		}
		return new Long(l).intValue();
	}
	
	public static Permission getGlobalWSPerm(final String globalRead) {
		Permission p = Permission.NONE;
		if (globalRead != null) {
			if (!globalRead.equals(PERM_READ) && 
					!globalRead.equals(PERM_NONE)) {
				throw new IllegalArgumentException(String.format(
						"globalread must be %s or %s", PERM_NONE, PERM_READ));
			}
			p = translatePermission(globalRead);
		}
		return p;
	}
}
