package us.kbase.workspace.workspaces;

import static us.kbase.workspace.util.Util.checkString;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObjectIdentifier {
	
	private final static String REFERENCE_ID_SEP = ".";
	private final static String REFERENCE_ID_SEP_REGEX = "\\" + REFERENCE_ID_SEP;
	private final static String REFERENCE_NAME_SEP = "/"; //this cannot be a legal object/workspace char
	private final static Pattern INVALID_OBJ_NAMES = 
			Pattern.compile("[^\\w\\|._-]");
	
	private final WorkspaceIdentifier wsi;
	private final String name;
	private final Integer id;
	private final Integer version;
	
	public ObjectIdentifier(WorkspaceIdentifier wsi, String name) {
		checkObjectName(name);
		this.wsi = wsi;
		this.name = name;
		this.id = null;
		this.version = null;
	}
	
	public ObjectIdentifier(WorkspaceIdentifier wsi, String name, int version) {
		checkObjectName(name);
		if (version < 1) {
			throw new IllegalArgumentException("Object version must be > 0");
		}
		this.wsi = wsi;
		this.name = name;
		this.id = null;
		this.version = version;
	}
	
	public ObjectIdentifier(WorkspaceIdentifier wsi, int id) {
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		this.wsi = wsi;
		this.name = null;
		this.id = id;
		this.version = null;
	}
	
	public ObjectIdentifier(WorkspaceIdentifier wsi, int id, int version) {
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		if (version < 1) {
			throw new IllegalArgumentException("Object version must be > 0");
		}
		this.wsi = wsi;
		this.name = null;
		this.id = id;
		this.version = version;
	}
	
	private static Integer parseInt(String s, String reference, String portion) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(String.format(
					"Unable to parse %s portion of object reference %s to an integer",
					portion, reference));
		}
	}
	
	public WorkspaceIdentifier getWorkspaceIdentifier() {
		return wsi;
	}

	public String getName() {
		return name;
	}

	public Integer getId() {
		return id;
	}

	public Integer getVersion() {
		return version;
	}
	
	public String getIdentifierString() {
		if (getId() == null) {
			return getName();
		}
		return "" + getId();
	}

	public String getWorkspaceIdentifierString() {
		return wsi.getIdentifierString();
	}
	
	public static ObjectIdentifier parseObjectReference(String reference) {
		checkString(reference, "reference");
		if (reference.contains(REFERENCE_NAME_SEP)) {
			final String[] r = reference.split(REFERENCE_NAME_SEP);
			if (r.length != 2 && r.length != 3) {
				throw new IllegalArgumentException(String.format(
						"Illegal number of separators %s in object name reference %s",
						REFERENCE_NAME_SEP, reference));
			}
			if (r.length == 3) {
				final Integer ver = parseInt(r[2], reference, "version");
				return new ObjectIdentifier(new WorkspaceIdentifier(r[0]),
						r[1], ver);
			}
			return new ObjectIdentifier(new WorkspaceIdentifier(r[0]), r[1]);
		}
		final String[] r = reference.split(REFERENCE_ID_SEP_REGEX);
		if (r.length != 2 && r.length != 3) {
			throw new IllegalArgumentException(String.format(
					"Illegal number of separators %s in object id reference %s",
					REFERENCE_ID_SEP, reference));
		}
		final Integer ws = parseInt(r[0], reference, "workspace");
		final Integer obj = parseInt(r[1], reference, "object");
		if (r.length == 3) {
			final Integer ver = parseInt(r[2], reference, "version");
			return new ObjectIdentifier(new WorkspaceIdentifier(ws), obj, ver);
		}
		return new ObjectIdentifier(new WorkspaceIdentifier(ws), obj);
	}
	
	public static void checkObjectName(String name) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("Object name cannot be null and must have at least one character");
		}
		final Matcher m = INVALID_OBJ_NAMES.matcher(name);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in object name %s: %s", name, m.group()));
		}
	}
	
	@Override
	public String toString() {
		return "ObjectIdentifier [wsi=" + wsi + ", name=" + name + ", id=" + id
				+ ", version=" + version + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		result = prime * result + ((wsi == null) ? 0 : wsi.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ObjectIdentifier other = (ObjectIdentifier) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		if (wsi == null) {
			if (other.wsi != null)
				return false;
		} else if (!wsi.equals(other.wsi))
			return false;
		return true;
	}
}
