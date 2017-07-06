package us.kbase.workspace.database;

import static us.kbase.common.utils.StringUtils.checkString;
import static us.kbase.workspace.database.Util.xorNameId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObjectIDNoWSNoVer {
	
	//TODO NOW TEST unit tests
	//TODO NOW JAVADOC
	
	private final static Pattern OBJ_NAME_INVALID = Pattern.compile("[^\\w\\|._-]");
	private final static Pattern OBJ_NAME_INTEGER = Pattern.compile("^-?\\d+$");
	private final static int MAX_NAME_LENGTH = 255;
	
	private final String name;
	private final Long id;
	
	public ObjectIDNoWSNoVer(String name) {
		checkObjectName(name);
		this.name = name;
		this.id = null;
	}
	
	public ObjectIDNoWSNoVer(long id) {
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		this.name = null;
		this.id = id;
	}
	
	public String getName() {
		return name;
	}

	public Long getId() {
		return id;
	}
	
	public String getIdentifierString() {
		if (getId() == null) {
			return getName();
		}
		return "" + getId();
	}
	
	public static ObjectIDNoWSNoVer create(final String name, 
			final Long id) {
		xorNameId(name, id, "object");
		if (name != null) {
			return new ObjectIDNoWSNoVer(name);
		}
		return new ObjectIDNoWSNoVer(id);
	}
	
	public static void checkObjectName(final String name) {
		checkString(name, "Object name", MAX_NAME_LENGTH);
		
		Matcher m = OBJ_NAME_INVALID.matcher(name);
		if (m.find()) {
			throw new IllegalArgumentException(String.format(
					"Illegal character in object name %s: %s", name, m.group()));
		}
		m = OBJ_NAME_INTEGER.matcher(name);
		if (m.find()) {
			throw new IllegalArgumentException("Object names cannot be integers: " + name);
		}
	}

	@Override
	public String toString() {
		return "ObjectIDNoWSNoVer [name=" + name + ", id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof ObjectIDNoWSNoVer)) {
			return false;
		}
		ObjectIDNoWSNoVer other = (ObjectIDNoWSNoVer) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

}
