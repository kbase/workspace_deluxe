package us.kbase.workspace.kbase;

import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import us.kbase.Tuple10;
import us.kbase.Tuple6;
import us.kbase.Tuple9;
import us.kbase.auth.AuthToken;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.ObjectUserMetaData;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.workspaces.Provenance;

public class ArgUtils {
	
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	
	public static void checkAddlArgs(Map<String, Object> addlargs,
			@SuppressWarnings("rawtypes") Class clazz) {
		if (addlargs.isEmpty()) {
			return;
		}
		throw new IllegalArgumentException(String.format(
				"Unexpected arguments in %s: %s",
				clazz.getName().substring(clazz.getName().lastIndexOf(".") + 1),
				StringUtils.join(addlargs.keySet(), " ")));
	}

	public static String formatDate(final Date d) {
		if (d == null) {
			return null;
		}
		return DATE_FORMAT.format(d);
	}
	
	public static Provenance processProvenance(String user,
			List<ProvenanceAction> actions) {
		
		Provenance p = new Provenance(user);
		if (actions == null) {
			return p;
		}
		for (ProvenanceAction a: actions) {
			checkAddlArgs(a.getAdditionalProperties(), a.getClass());
			Provenance.ProvenanceAction pa = new Provenance.ProvenanceAction();
			if (a.getService() != null) {
				pa = pa.withServiceName(a.getService());
			}
			//TODO remainder of provenance actions
			//TODO parse provenance date 
		}
		
		return p;
	}
	
	public static Tuple6<Integer, String, String, String, String, String> wsMetaToTuple (
			WorkspaceMetaData meta) {
		return new Tuple6<Integer, String, String, String, String, String>()
				.withE1(meta.getId())
				.withE2(meta.getName())
				.withE3(meta.getOwner().getUser())
				.withE4(formatDate(meta.getModDate()))
				.withE5(translatePermission(meta.getUserPermission())) 
				.withE6(translatePermission(meta.isGloballyReadable()));
	}
	
	public static List<Tuple9<Integer, String, String, String, Integer, String,
			Integer, String, Integer>>
			objMetaToTuple (List<ObjectMetaData> meta) {
		
		//oh the humanity
		final List<Tuple9<Integer, String, String, String, Integer, String,
			Integer, String, Integer>> ret = 
			new ArrayList<Tuple9<Integer, String, String, String, Integer,
			String, Integer, String, Integer>>();
		
		for (ObjectMetaData m: meta) {
			ret.add(new Tuple9<Integer, String, String, String, Integer,
					String, Integer, String, Integer>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(formatDate(m.getCreatedDate()))
					.withE5(m.getVersion())
					.withE6(m.getCreator().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize()));
		}
		return ret;
}
	
	public static List<Tuple10<Integer, String, String, String, Integer, String,
			Integer, String, Integer, Map<String, String>>>
			objUserMetaToTuple (List<ObjectUserMetaData> meta) {
		
		//oh the humanity
		final List<Tuple10<Integer, String, String, String, Integer, String,
			Integer, String, Integer, Map<String, String>>> ret = 
			new ArrayList<Tuple10<Integer, String, String, String, Integer,
			String, Integer, String, Integer, Map<String, String>>>();
		
		for (ObjectUserMetaData m: meta) {
			ret.add(new Tuple10<Integer, String, String, String, Integer,
					String, Integer, String, Integer, Map<String, String>>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(formatDate(m.getCreatedDate()))
					.withE5(m.getVersion())
					.withE6(m.getCreator().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize())
					.withE10(m.getUserMetaData()));
		}
		return ret;
	}
	
	
	public static WorkspaceUser getUser(AuthToken token) {
		if (token == null) {
			return null;
		}
		return new WorkspaceUser(token.getUserName());
	}
}
