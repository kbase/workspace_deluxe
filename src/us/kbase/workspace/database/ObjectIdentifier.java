package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.xorNameId;
import static us.kbase.workspace.database.Util.noNulls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import us.kbase.typedobj.core.SubsetSelection;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;
import static us.kbase.common.utils.StringUtils.checkString;

/** Identifies an object, or portions of an object, in a workspace.
 * 
 * Some methods or class variables refer to references. These are a reference to an object in
 * a workspace of the form X/Y/Z, where X is the workspace name or ID, Y is the object name or ID,
 * and Z is an optional version.
 */
public class ObjectIdentifier {
	
	// TODO TEST complete unit tests (move from WorkspaceTest, mostly covered there)
	// also need to test toString code, but not super high priority
	
	//this cannot be a legal object/workspace char
	/** The separator between parts of a workspace reference to an object. */
	public final static String REFERENCE_SEP = "/";
	
	private final WorkspaceIdentifier wsi;
	private final String name;
	private final long id;
	private final int version;
	
	private ObjectIdentifier(
			final WorkspaceIdentifier wsi,
			final long id,
			final String name,
			final int version) {
		this.wsi = wsi;
		this.name = name;
		this.id = id;
		this.version = version;
	}

	/** Get the identifier for the workspace in which the object resides.
	 * @return the workspace ID.
	 */
	public WorkspaceIdentifier getWorkspaceIdentifier() {
		return wsi;
	}

	/** Get the name of the object. Exactly one of the name or ID of the object must be specified.
	 * @return the object name if present.
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/** Get the ID of the object. Exactly one of the name or ID of the object must be specified.
	 * @return the object ID if present.
	 */
	public Optional<Long> getID() {
		return id < 1 ? Optional.empty() : Optional.of(id);
	}

	/** Get the version of the object.
	 * @return the version of the object, if present.
	 */
	public Optional<Integer> getVersion() {
		return version < 1 ? Optional.empty() : Optional.of(version);
	}
	
	/** Returns whether this object identifier has a reference path.
	 * @return true if this object identifier has a reference path, false otherwise.
	 */
	public boolean hasRefPath() {
		return false;
	}

	/** Returns the reference path to the target object, excluding the first object in the path.
	 * @return the reference path.
	 */
	public List<ObjectIdentifier> getRefPath() {
		return Collections.emptyList();
	}
	
	/** Returns the last (target) object identifier in the reference path.
	 * @return the last object identifier in the reference path.
	 * @throws IllegalStateException if this object has no reference path.
	 */
	public ObjectIdentifier getLast() {
		throw new IllegalStateException("This object identifier has no reference path");
	}
	
	/** Returns true if this object identifier represents a target object for which the
	 * permissions must be ascertained by a object reference DAG search.
	 * @return true if a lookup is required.
	 */
	public boolean isLookupRequired() {
		return false;
	}
	
	/** Get the subset of the object to return.
	 * @return the subset selection for the object.
	 */
	public SubsetSelection getSubSet() {
		return SubsetSelection.EMPTY;
	}
	
	/** Get an identifier string for the object. This is either the name or the ID, depending on
	 * which is present.
	 * @return the identifier string.
	 */
	public String getIdentifierString() {
		if (!getID().isPresent()) {
			return getName().get();
		}
		return "" + getID().get();
	}

	/** Get the workspace identifier string. This is identical to what is returned by
	 * {@link WorkspaceIdentifier#getIdentifierString()}.
	 * @return the identifier string.
	 */
	public String getWorkspaceIdentifierString() {
		return wsi.getIdentifierString();
	}
	
	/** Get a reference string for this object.
	 * @return the reference string.
	 */
	public String getReferenceString() {
		return getWorkspaceIdentifierString() + REFERENCE_SEP + getIdentifierString() +
				(version < 1 ? "" : REFERENCE_SEP + version);
	}
	
	/** Determine whether this object identifier must always point to the same object. This means
	 * that permanent IDs rather than mutable names are present for the workspace and object
	 * and a version is present.
	 * @return true if this object identifier is absolute.
	 */
	public boolean isAbsolute() {
		return version > 0 && id > 0 && wsi.getId() != null;
	}
	
	/** Resolve the workspace for this identifier, guaranteeing it exists. Reference paths,
	 * lookup directions, and subset information is not preserved is the resolved object.
	 * @param rwsi the resolved workspace ID.
	 * @return an object identifier with a resolved workspace. 
	 */
	public ObjectIDResolvedWS resolveWorkspace(final ResolvedWorkspaceID rwsi) {
		if (rwsi == null) {
			throw new IllegalArgumentException("rwsi cannot be null");
		}
		if (name == null) {
			if (version < 1) {
				return new ObjectIDResolvedWS(rwsi, id);
			} else {
				return new ObjectIDResolvedWS(rwsi, id, version);
			}
		}
		if (version < 1) {
			return new ObjectIDResolvedWS(rwsi, name);
		} else {
			return new ObjectIDResolvedWS(rwsi, name, version);
		}
	}
	
	private static Integer parseInt(String s, String reference, String portion) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(String.format(
					"Unable to parse %s portion of object reference %s to an integer",
					portion, reference));
		}
	}
	
	@Override
	public String toString() {
		return "ObjectIdentifier [wsi=" + wsi + ", name=" + getName() + ", id=" + getID()
				+ ", version=" + getVersion() + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + version;
		result = prime * result + ((wsi == null) ? 0 : wsi.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ObjectIdentifier other = (ObjectIdentifier) obj;
		if (id != other.id)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (version != other.version)
			return false;
		if (wsi == null) {
			if (other.wsi != null)
				return false;
		} else if (!wsi.equals(other.wsi))
			return false;
		return true;
	}
	
	// so theoretically there could be a whole lot of different classes to represent different
	// configurations of object identifiers. For now we'll just go with these three because
	// they exist at the time of writing. However, we could add more classes to further reduce
	// memory usage by optimizing which fields are present in the future.
	
	private static class ObjectIDWithRefPath extends ObjectIdentifier {

		private final List<ObjectIdentifier> refpath;
		private final boolean lookup;
		
		private ObjectIDWithRefPath(
				final WorkspaceIdentifier wsi,
				final long id,
				final String name,
				final int version,
				final List<ObjectIdentifier> refpath,
				final boolean lookup) {
			super(wsi, id, name, version);
			this.refpath = refpath;
			this.lookup = lookup;
		}

		@Override
		public List<ObjectIdentifier> getRefPath() {
			return refpath == null ? Collections.emptyList() : refpath;
		}
		
		@Override
		public boolean hasRefPath() {
			return refpath != null;
		}
		
		@Override
		public ObjectIdentifier getLast() {
			if (!hasRefPath()) {
				throw new IllegalStateException("This object identifier has no reference path");
			}
			return refpath.get(refpath.size() - 1);
		}
		
		@Override
		public boolean isLookupRequired() {
			return lookup;
		}

		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + (lookup ? 1231 : 1237);
			result = prime * result + ((refpath == null) ? 0 : refpath.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!super.equals(obj)) {
				return false;
			}
			ObjectIDWithRefPath other = (ObjectIDWithRefPath) obj;
			if (lookup != other.lookup) {
				return false;
			}
			if (refpath == null) {
				if (other.refpath != null) {
					return false;
				}
			} else if (!refpath.equals(other.refpath)) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ObjectIDWithRefPath [refpath=");
			builder.append(refpath);
			builder.append(", lookup=");
			builder.append(lookup);
			builder.append(", getWorkspaceIdentifier()=");
			builder.append(getWorkspaceIdentifier());
			builder.append(", getName()=");
			builder.append(getName());
			builder.append(", getId()=");
			builder.append(getID());
			builder.append(", getVersion()=");
			builder.append(getVersion());
			builder.append("]");
			return builder.toString();
		}
	}
	
	private static class ObjIDWithRefPathAndSubset extends ObjectIDWithRefPath {

		private final SubsetSelection subset;
		
		private ObjIDWithRefPathAndSubset(
				final WorkspaceIdentifier wsi,
				final long id,
				final String name,
				final int version,
				final List<ObjectIdentifier> refpath,
				final boolean lookup,
				final SubsetSelection subset) {
			super(wsi, id, name, version, refpath, lookup);
			this.subset = subset;
		}
		
		@Override
		public SubsetSelection getSubSet() {
			return subset;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((subset == null) ? 0 : subset.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			ObjIDWithRefPathAndSubset other = (ObjIDWithRefPathAndSubset) obj;
			if (subset == null) {
				if (other.subset != null)
					return false;
			} else if (!subset.equals(other.subset))
				return false;
			return true;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ObjIDWithRefPathAndSubset [subset=");
			builder.append(subset);
			builder.append(", getRefPath()=");
			builder.append(getRefPath());
			builder.append(", isLookupRequired()=");
			builder.append(isLookupRequired());
			builder.append(", getWorkspaceIdentifier()=");
			builder.append(getWorkspaceIdentifier());
			builder.append(", getName()=");
			builder.append(getName());
			builder.append(", getId()=");
			builder.append(getID());
			builder.append(", getVersion()=");
			builder.append(getVersion());
			builder.append("]");
			return builder.toString();
		}
	}

	/** Get a builder for an {@link ObjectIdentifier}.
	 * @param wsi the workspace identifier for the object.
	 * @return a new builder.
	 */
	public static Builder getBuilder(final WorkspaceIdentifier wsi) {
		return new Builder(wsi);
	}
	
	/** Get a builder for an {@link ObjectIdentifier} starting with the same.
	 * @param oi the object identifier from which to initialize the build.
	 * @return a new builder.
	 */
	public static Builder getBuilder(final ObjectIdentifier oi) {
		return new Builder(oi);
	}
	
	/** Get a builder given an object reference.
	 * @param reference the object reference.
	 * @return a new builder.
	 */
	public static Builder getBuilder(final String reference) {
		// TODO CODE add a builder for a reference path like X/Y/Z;X/Y;X/Y/Z and replace parsers
		return new Builder(reference);
	}
	
	/** Create a copy of the input builder.
	 * @param b the builder to copy.
	 * @return a new builder with the same state as the input builder.
	 */
	public static Builder getBuilder(final Builder b) {
		return new Builder(b);
	}
	
	/** A builder for an {@link ObjectIdentifier}. */
	public static class Builder {
		
		private final WorkspaceIdentifier wsi;
		private String name = null;
		private long id = -1;
		private int version = -1;
		private List<ObjectIdentifier> refpath = null;
		private boolean lookup = false;
		private SubsetSelection subset = SubsetSelection.EMPTY;
		

		private Builder(final WorkspaceIdentifier wsi) {
			this.wsi = requireNonNull(wsi, "wsi");
		}

		private Builder(final ObjectIdentifier oi) {
			this.wsi = requireNonNull(oi, "oi").getWorkspaceIdentifier();
			this.id = oi.getID().orElse(-1L);
			this.name = oi.getName().orElse(null);
			this.version = oi.getVersion().orElse(-1);
			this.refpath = oi.getRefPath().isEmpty() ? null : oi.getRefPath();
			this.lookup = oi.isLookupRequired();
			this.subset = oi.getSubSet();
		}
		
		private Builder(final Builder b) {
			this.wsi = requireNonNull(b, "b").wsi;
			this.id = b.id;
			this.name = b.name;
			this.version = b.version;
			this.refpath = b.refpath; // immutable so safe to make a shallow copy
			this.lookup = b.lookup;
			this.subset = b.subset;
		}

		public Builder(final String reference) {
			checkString(reference, "reference");
			final String[] r = reference.split(REFERENCE_SEP);
			if (r.length != 2 && r.length != 3) {
				throw new IllegalArgumentException(String.format(
						"Illegal number of separators %s in object reference %s",
						REFERENCE_SEP, reference));
			}
			WorkspaceIdentifier wsi;
			try {
				wsi = new WorkspaceIdentifier(Long.parseLong(r[0]));
			} catch (NumberFormatException nfe) {
				wsi = new WorkspaceIdentifier(r[0]);
			}
			this.wsi = wsi;  // java complains if we assign directly
			this.version = r.length == 3 ? checkVersion(parseInt(r[2], reference, "version")) : -1;
			try {
				this.id = checkID(Long.parseLong(r[1]));
			} catch (NumberFormatException nfe) {
				this.name = checkObjectName(r[1]);
			}
		}

		/** Set a name for the object. This will remove any previously set ID.
		 * @param name the objects's name. null is treated as a no-op.
		 * @return this builder.
		 */
		public Builder withName(final String name) {
			if (name != null) {
				this.name = checkObjectName(name);
				this.id = -1;
			}
			return this;
		}
		
		/** Behaves identically to {@link #withName(String)}, except that optionally if an ID
		 * is already set an error can be thrown.
		 * @param name the name of the object. null is treated as a no-op.
		 * @param throwError true to throw an error if the ID is already set. 
		 * @return this builder
		 */
		public Builder withName(final String name, boolean throwError) {
			if (throwError && id > 0) {
				xorNameId(name, id, "object"); // reuse the error generating code
			}
			return withName(name);
		}
		
		/** Set an ID for the object. This will remove any previously set name.
		 * @param id the object's ID, which must be > 0. null is treated as a no-op.
		 * @return this builder.
		 */
		public Builder withID(final Long id) {
			if (id != null) {
				this.id = checkID(id);
				this.name = null;
			}
			return this;
		}
		
		private long checkID(final Long id) {
			if (id < 1) {
				throw new IllegalArgumentException("Object id must be > 0");
			}
			return id;
		}
		
		/** Behaves identically to {@link #withID(Long)}, except that optionally if a name
		 * is already set an error can be thrown.
		 * @param id the object's ID, which must be > 0. null is treated as a no-op.
		 * @param throwError true to throw an error if the name is already set. 
		 * @return this builder.
		 */
		public Builder withID(final Long id, boolean throwError) {
			if (throwError && name != null) {
				xorNameId(name, id, "object"); // reuse the error generating code
			}
			return withID(id);
		}
		
		/** Set the version of this object. Passing null removes any previously set version.
		 * @param version the object's version, which must be > 0.
		 * @return this builder.
		 */
		public Builder withVersion(final Integer version) {
			this.version = checkVersion(version);
			return this;
		}
		
		private int checkVersion(final Integer version) {
			if (version != null && version < 1) {
				throw new IllegalArgumentException("Object version must be > 0");
			}
			return version == null ? -1 : version;
		}
		
		/** Set whether this object identifier represents a target object for which the
		 * permissions must be ascertained by a object reference DAG search.
		 * Passing true will remove any previously set reference path.
		 * @param lookupRequired set whether a DAG lookup is required.
		 * @return this builder.
		 */
		public Builder withLookupRequired(final boolean lookupRequired) {
			this.lookup = lookupRequired;
			this.refpath = lookupRequired ? null : this.refpath;
			return this;
		}
		
		/** Add a reference path to a target object from the head of the path, where the path
		 * head is not included and is represented by this {@link ObjectIdentifier}.
		 * @param refpath the reference path. Passing a null or empty list will remove any
		 * previously set path. If the case is otherwise, the lookup required flag will be set
		 * to false.
		 * @return this builder.
		 */
		public Builder withReferencePath(final List<ObjectIdentifier> refpath) {
			if (refpath == null || refpath.isEmpty()) {
				this.refpath = null;
			} else {
				noNulls(refpath, "Nulls are not allowed in reference paths");
				// make immutable and prevent alteration by mutating the input list
				this.refpath = Collections.unmodifiableList(new ArrayList<>(refpath));
				this.lookup = false;
			}
			return this;
		}
		
		/** Add a specification of what parts of the object to return.
		 * @param subset the subset of the object to return.
		 * @return this builder.
		 */
		public Builder withSubsetSelection(final SubsetSelection subset) {
			this.subset = subset == null ? SubsetSelection.EMPTY : subset;
			return this;
		}
		
		/** Build the {@link ObjectIdentifier}. One of {@link #withName(String)},
		 * {@link #withName(String, boolean), {@link #withID(Long)}, or
		 * {@link #withID(Long, boolean)} must have been called successfully prior to building.
		 * @return the identifier.
		 */
		public ObjectIdentifier build() {
			xorNameId(name, id < 1 ? null : id, "object"); // reuse error creation
			if (!subset.equals(SubsetSelection.EMPTY)) {
				return new ObjIDWithRefPathAndSubset(
						wsi, id, name, version, refpath, lookup, subset);
			} else if (lookup || refpath != null) {
				return new ObjectIDWithRefPath(wsi, id, name, version, refpath, lookup);
			} else {
				return new ObjectIdentifier(wsi, id, name, version);
			}
		}
	}
}
