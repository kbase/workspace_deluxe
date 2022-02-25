package us.kbase.workspace.database;

import static java.util.Objects.requireNonNull;
import static us.kbase.workspace.database.Util.xorNameId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.kbase.typedobj.core.SubsetSelection;

import static us.kbase.workspace.database.ObjectIDNoWSNoVer.checkObjectName;
import static us.kbase.common.utils.StringUtils.checkString;

public class ObjectIdentifier {
	
	// TODO TEST unittests (move from WorkspaceTest, mostly covered there)
	// also need to test toString code, but not super high priority
	// TODO NOW JAVADOC
	// TODO CODE return Optionals instead of nulls. That's a big change...
	
	//this cannot be a legal object/workspace char
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

	public WorkspaceIdentifier getWorkspaceIdentifier() {
		return wsi;
	}

	public String getName() {
		return name;
	}

	public Long getID() {
		return id < 1 ? null : id;
	}

	public Integer getVersion() {
		return version < 1 ? null : version;
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
	
	public String getIdentifierString() {
		if (getID() == null) {
			return getName();
		}
		return "" + getID();
	}

	public String getWorkspaceIdentifierString() {
		return wsi.getIdentifierString();
	}
	
	public String getReferenceString() {
		return getWorkspaceIdentifierString() + REFERENCE_SEP +
				getIdentifierString() + (version < 1 ? "" :
					REFERENCE_SEP + version);
	}
	
	public boolean isAbsolute() {
		return version > 0 && id > 0 && wsi.getId() != null;
	}
	
	public ObjectIDResolvedWS resolveWorkspace(ResolvedWorkspaceID rwsi) {
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
	
	public static ObjectIdentifier parseObjectReference(final String reference) {
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
		final Integer ver = r.length == 3 ? parseInt(r[2], reference, "version") : null;
		final Builder b = ObjectIdentifier.getBuilder(wsi).withVersion(ver);
		try {
			return b.withID(Long.parseLong(r[1])).build();
		} catch (NumberFormatException nfe) {
			return b.withName(r[1]).build();
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
		return "ObjectIdentifier [wsi=" + wsi + ", name=" + name + ", id=" + getID()
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
			this.id = oi.getID() == null ? -1 : oi.getID();
			this.name = oi.getName();
			this.version = oi.getVersion() == null ? -1 : oi.getVersion();
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
				if (id < 1) {
					throw new IllegalArgumentException("Object id must be > 0");
				}
				this.id = id;
				this.name = null;
			}
			return this;
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
			if (version != null && version < 1) {
				throw new IllegalArgumentException("Object version must be > 0");
			}
			this.version = version == null ? -1 : version;
			return this;
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
				// make immutable and prevent alteration by mutating the input list
				this.refpath = Collections.unmodifiableList(new ArrayList<>(refpath));
				for (final ObjectIdentifier oi: this.refpath) {
					if (oi == null) {
						throw new IllegalArgumentException(
								"Nulls are not allowed in reference paths");
					}
				}
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
