package us.kbase.workspace.database;

import static us.kbase.common.utils.StringUtils.checkString;
import static us.kbase.workspace.database.Util.xorNameId;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Optional;

/** An object identifier that specifies neither the workspace nor the version of the object.
 * @author gaprice@lbl.gov
 *
 */
public class ObjectIDNoWSNoVer {
	
	private final static Pattern OBJ_NAME_INVALID = Pattern.compile("[^\\w\\|._-]");
	private final static Pattern OBJ_NAME_INTEGER = Pattern.compile("^-?\\d+$");
	private final static int MAX_NAME_LENGTH = 255;
	
	private final String name;
	private final Long id;
	
	/** Create an object id with a name.
	 * @param name the name of the object.
	 */
	public ObjectIDNoWSNoVer(final String name) {
		checkObjectName(name);
		this.name = name;
		this.id = null;
	}
	
	/** Create and object id with an id.
	 * @param id the id of the object.
	 */
	public ObjectIDNoWSNoVer(final long id) {
		if (id < 1) {
			throw new IllegalArgumentException("Object id must be > 0");
		}
		this.name = null;
		this.id = id;
	}
	
	/** Get the name of the object or absent if an id is specified.
	 * @return the name of the object.
	 */
	public Optional<String> getName() {
		return Optional.fromNullable(name);
	}

	/** Get the id of the object or absent if a name is specified.
	 * @return the id.
	 */
	public Optional<Long> getId() {
		return Optional.fromNullable(id);
	}
	
	/** Get a string representation of the object identifier - either the name or the id as a
	 * string.
	 * @return a string representing the object identifier.
	 */
	public String getIdentifierString() {
		if (id == null) {
			return name;
		}
		return "" + id;
	}
	
	/** Create an object identifier from a name or an id. One of the arguments is expected to be
	 * null.
	 * @param name the name of the object.
	 * @param id the id of the object.
	 * @return a new object identifier.
	 */
	public static ObjectIDNoWSNoVer create(final String name, final Long id) {
		xorNameId(name, id, "object");
		if (name != null) {
			return new ObjectIDNoWSNoVer(name);
		}
		return new ObjectIDNoWSNoVer(id);
	}
	
	/** Check that an object name is valid. Requirements are:
	 * <ul>
	 * <li>&lt; 256 characters</li>
	 * <li>is not an integer</li>
	 * <li>contains only a-z, A-Z, 0-9, and the characters |_.-</li>
	 * <ul>
	 * @param name the name of the object.
	 */
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
		if (getClass() != obj.getClass()) {
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
