package us.kbase.workspace.kbase;

import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonProcessingException;

import us.kbase.Tuple10;
import us.kbase.Tuple6;
import us.kbase.UObject;
import us.kbase.auth.AuthToken;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.workspaces.ObjectMetaData;
import us.kbase.workspace.workspaces.Provenance;
import us.kbase.workspace.workspaces.WorkspaceMetaData;

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
				.withE3(meta.getOwner())
				.withE4(formatDate(meta.getModDate()))
				.withE5(translatePermission(meta.getUserPermission())) 
				.withE6(translatePermission(meta.isGloballyReadable()));
	}
	
	public static List<Tuple10<Integer, String, String, String, Integer, String,
			Integer, String, Integer, Map<String, UObject>>>
			objMetaToTuple (List<ObjectMetaData> meta) {
		
		//oh the humanity
		final List<Tuple10<Integer, String, String, String, Integer, String,
			Integer, String, Integer, Map<String, UObject>>> ret = 
			new ArrayList<Tuple10<Integer, String, String, String, Integer,
			String, Integer, String, Integer, Map<String, UObject>>>();
		
		for (ObjectMetaData m: meta) {
			ret.add(new Tuple10<Integer, String, String, String, Integer,
					String, Integer, String, Integer, Map<String, UObject>>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(formatDate(m.getCreatedDate()))
					.withE5(m.getVersion())
					.withE6(m.getCreator())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize())
					.withE10(convertToUObj(m.getUserMetaData())));
		}
		return ret;
	}
	
	
	public static String getUserName(AuthToken token) {
		if (token == null) {
			return null;
		}
		return token.getUserName();
	}
	
	public static Map<String, Object> parseUObj(Map<String, UObject> map) {
		Map<String, Object> ret = new HashMap<String, Object>();
		try {
			for (String s: map.keySet()) {
				ret.put(s, map.get(s).asInstance());
			}
		} catch (JsonProcessingException jpe) {
			throw new RuntimeException("Something is very broken", jpe);
		}
		return ret;
	}
	
	public static Map<String, UObject> convertToUObj(Map<String, Object> map) {
		Map<String, UObject> ret = new HashMap<String, UObject>();
		for (String s: map.keySet()) {
			ret.put(s, new UObject(map.get(s)));
		}
		return ret;
	}

}
