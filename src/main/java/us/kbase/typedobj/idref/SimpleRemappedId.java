package us.kbase.typedobj.idref;

import static java.util.Objects.requireNonNull;

public class SimpleRemappedId implements RemappedId {

	//TODO JAVADOC
	//TODO TEST
	
	private final String id;
	
	public SimpleRemappedId(final String id) {
		requireNonNull(id, "id");
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
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
		SimpleRemappedId other = (SimpleRemappedId) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("SimpleRemappedId [id=");
		builder.append(id);
		builder.append("]");
		return builder.toString();
	}

}
