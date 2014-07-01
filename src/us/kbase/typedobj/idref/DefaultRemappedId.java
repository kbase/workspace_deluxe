package us.kbase.typedobj.idref;

public class DefaultRemappedId implements RemappedId {

	private final String id;
	
	public DefaultRemappedId(String id) {
		super();
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return "DefaultRemappedId [id=" + id + "]";
	}

}
