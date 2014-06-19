package us.kbase.typedobj.idref;

public class IDReferenceType {
	
	private final String type;

	public IDReferenceType(String type) {
		super();
		if (type == null) {
			throw new NullPointerException("type cannot be null");
		}
		this.type = type;
	}

	public String getType() {
		return type;
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
		IDReferenceType other = (IDReferenceType) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

}
