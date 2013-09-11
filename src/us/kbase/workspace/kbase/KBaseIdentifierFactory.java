package us.kbase.workspace.kbase;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.WorkspaceIdentity;
import us.kbase.workspace.workspaces.ObjectIdentifier;
import us.kbase.workspace.workspaces.TypeId;
import us.kbase.workspace.workspaces.WorkspaceIdentifier;
import us.kbase.workspace.workspaces.WorkspaceType;

public class KBaseIdentifierFactory {
	
	private static final Pattern KB_WS_ID = Pattern.compile("kb\\|ws.(\\d+)");
	private static final String TYPE_SEP = "\\."; //regex
	private static final String VER_SEP = "\\."; //regex
	
	
	private static WorkspaceIdentifier createWSID(String wsname) {
		Matcher m = KB_WS_ID.matcher(wsname);
		if (m.find()) {
			return new WorkspaceIdentifier(new Integer(m.group(1)));
		}
		return new WorkspaceIdentifier(wsname);
	}
	
	private static WorkspaceIdentifier createWSID(int id) {
		return new WorkspaceIdentifier(id);
	}
	
	public static WorkspaceIdentifier processWorkspaceIdentifier(final WorkspaceIdentity wsi) {
		ArgUtils.checkAddlArgs(wsi.getAdditionalProperties(), wsi.getClass());
		return processWorkspaceIdentifier(wsi.getWorkspace(), wsi.getId());
	}
	
	public static WorkspaceIdentifier processWorkspaceIdentifier(final String workspace, final Integer id) {
		if (!(workspace == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of workspace (was: %s) or id (was: %s)",
					workspace, id));
		}
		if (id != null) {
			return createWSID(id);
		}
		return createWSID(workspace);
	}
	
	public static ObjectIdentifier processObjectIdentifier(final ObjectIdentity oi) {
		ArgUtils.checkAddlArgs(oi.getAdditionalProperties(), oi.getClass());
		if (oi.getRef() != null) {
			if (oi.getWorkspace() != null || oi.getWsid() != null 
					|| oi.getName() != null || oi.getObjid() != null ||
					oi.getVer() != null) {
				final List<Object> err = new ArrayList<Object>(4);
				if (oi.getWorkspace() != null) {
					err.add(oi.getWorkspace());
				}
				if (oi.getWsid() != null) {
					err.add(oi.getWsid());
				}
				if (oi.getName() != null) {
					err.add(oi.getName());
				}
				if (oi.getObjid() != null) {
					err.add(oi.getObjid());
				}
				if (oi.getVer() != null) {
					err.add(oi.getVer());
				}
				throw new IllegalArgumentException(String.format(
						"Object reference %s provided; cannot provide any other means of identifying an object: %s",
						oi.getRef(), StringUtils.join(err, " ")));
			}
			//TODO process ref
		}
		final WorkspaceIdentifier wsi = processWorkspaceIdentifier(
				oi.getWorkspace(), oi.getWsid());
		return processObjectIdentifier(wsi, oi.getName(), oi.getObjid());
	}
	
	public static ObjectIdentifier processObjectIdentifier(final WorkspaceIdentifier wsi,
			final String name, final Integer id) {
		if (name == null && id == null) {
			return null;
		}
		if (!(name == null ^ id == null)) {
			throw new IllegalArgumentException(String.format(
					"Must provide one and only one of an object name or id: %s/%s",
					name, id));
		}
		if (name != null) {
			return new ObjectIdentifier(wsi, name);
		}
		return new ObjectIdentifier(wsi, id);
	}
	
	public static TypeId processTypeId(final String type, final String ver,
			final String errprefix) {
		if (type == null) {
			throw new IllegalArgumentException(errprefix + " has no type");
		}
		final String[] t = type.split(TYPE_SEP);
		if (t.length != 2) {
			throw new IllegalArgumentException(errprefix + String.format(
					" type %s could not be split into a module and name",
					type));
		}
		final WorkspaceType wt = new WorkspaceType(t[0], t[1]);
		if (ver == null) {
			return new TypeId(wt);
		}
		final String[] v = ver.split(VER_SEP);
		if (v.length == 1) {
			try {
				return new TypeId(wt, Integer.parseInt(v[0]));
			} catch (NumberFormatException ne) {
				throwTypeVerException(errprefix, ver);
			}
		}
		if (v.length == 2) {
			try {
				return new TypeId(wt, Integer.parseInt(v[0]),
						Integer.parseInt(v[1]));
			} catch (NumberFormatException ne) {
				throwTypeVerException(errprefix, ver);
			}
		}
		throwTypeVerException(errprefix, ver);
		return null; //shut up java
	}
	
	private static void throwTypeVerException(final String errprefix, final String ver) {
		throw new IllegalArgumentException(errprefix + String.format(
				" type version string %s could not be parsed to a version",
				ver));
	}

}
