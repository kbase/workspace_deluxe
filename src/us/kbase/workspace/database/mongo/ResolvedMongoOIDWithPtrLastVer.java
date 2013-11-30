package us.kbase.workspace.database.mongo;

/**
 * name is resolved *at the time the database was accessed and is not further
 * updated*
 * 
 * The underlying assumption of this class is all object IDs are unique and all
 * names are unique at the time of resolution. Therefore a set of
 * ResolvedObjectIDs constructed at the same time are all unique in name and id,
 * and removing one or the other field would not cause the number of unique
 * objects to change (as measured by the unique hashcode count, for example).
 * 
 * This is *not* the case for objects generated from different queries.
 * 
 * Versions are resolved.
 * 
 * @author gaprice@lbl.gov
 *
 */
public class ResolvedMongoOIDWithPtrLastVer extends ResolvedMongoObjectID {
	
	ResolvedMongoOIDWithPtrLastVer(final ResolvedMongoWSID rwsi, final String name,
			final long id, final int version) {
		super(rwsi, name, id, version);
	}
	
	@Override
	public boolean isFullyResolved() {
		return true;
	}
}
