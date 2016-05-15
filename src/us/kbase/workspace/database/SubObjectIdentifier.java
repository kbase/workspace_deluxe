package us.kbase.workspace.database;

import us.kbase.typedobj.core.ObjectPaths;

public class SubObjectIdentifier {
	
	private final ObjectIdentifier oi;
	private final ObjectPaths paths;
	
	public SubObjectIdentifier(final ObjectIdentifier oi,
			final ObjectPaths paths) {
		super();
		if (oi == null) {
			throw new IllegalArgumentException("oi cannot be null");
		}
		this.oi = oi;
		this.paths = paths == null ? ObjectPaths.EMPTY : paths;
	}

	public ObjectIdentifier getObjectIdentifer() {
		return oi;
	}

	public ObjectPaths getPaths() {
		return paths;
	}

	@Override
	public String toString() {
		return "SubObjectIdentifier [oi=" + oi + ", paths=" + paths + "]";
	}
}
