package us.kbase.typedobj.idref;

public class SimpleRemappedId implements RemappedId {

	private final String id;
	
	public SimpleRemappedId(final String id) {
		if (id == null) {
			throw new NullPointerException("id cannot be null");
		}
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
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
