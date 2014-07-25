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

}
