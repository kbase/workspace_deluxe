package us.kbase.typedobj.idref;

import static us.kbase.workspace.database.Util.checkString;

import java.util.Objects;

/** The type of and ID that references some other entity. An arbitrary string, typically with
 * some semantic value.
 * @author gaprice@lbl.gov
 *
 */
public class IdReferenceType implements Comparable<IdReferenceType>{
	
	private final String type;

	/** The ID reference type.
	 * @param type the type.
	 */
	public IdReferenceType(final String type) {
		// do we want any more checks here? Internal only so don't worry about it for now.
		this.type = checkString(type, "type");
	}

	/** Get the type.
	 * @return the type.
	 */
	public String getType() {
		return type;
	}
	
	@Override
	public int compareTo(final IdReferenceType other) {
		return this.type.compareTo(Objects.requireNonNull(other, "other").type);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "IDReferenceType [type=" + type + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IdReferenceType other = (IdReferenceType) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}
}
