package us.kbase.typedobj.idref;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class IDReferenceTree {
	/* in the future, change implementation so that optionally the tree can be
	 * stored in a file, and only the current branch is stored in memory
	 * as the json is traversed by a tokenizer
	 */
	
	public static final char DEFAULT_PATHSEP = '/';
	private final String pathSep;
	private final TreeRoot root = new TreeRoot();
	
	public IDReferenceTree() {
		this(DEFAULT_PATHSEP);
	}
	
	public IDReferenceTree(final char pathSep) {
		this.pathSep = String.valueOf(pathSep);
	}
	

	public class TreeLocation {
		
		private final String relativeLocation;
		private final TreeLocation parent;

		private TreeLocation(final String relativeLocation,
				final TreeLocation parent) {
			super();
			this.relativeLocation = relativeLocation;
			this.parent = parent;
		}
		
		public String getLocation() {
			if (isRoot()) {
				return pathSep;
			}
			return parent.getLocation() + pathSep + relativeLocation;
		}
		
		public String getRelativeLocation() {
			return relativeLocation;
		}
		
		public boolean isRoot() {
			return false;
		}
		
		public boolean containsID() {
			return false;
		}
		
	};
	
	public class TreeRoot extends TreeLocation {
		
		private TreeRoot() {
			super(null, null);
		}
		
		public boolean isRoot() {
			return true;
		}
	};
	
	public class TreeIDNode extends TreeLocation {
		
		private final IDReferenceType type;
		//the value at this node is an ID
		private final String valueID;
		//the location of this node (e.g. the hash key) is an ID
		private final boolean locationIsID;
		private final List<String> valueAttributes;
		//TODO 1 location is a placeholder, string may be a bad idea
		
		private TreeIDNode(final String relativeLocation,
				final TreeLocation parent,
				final IDReferenceType type, final String valueID,
				final boolean locationIsID, List<String> attributes) {
			super(relativeLocation, parent);
			if (type == null || valueID == null) {
				throw new NullPointerException(
						"type and id cannot be null");
			}
			this.type = type;
			this.valueID = valueID;
			this.locationIsID = locationIsID;
			attributes = attributes == null ? new LinkedList<String>() :
				attributes;
			this.valueAttributes = Collections.unmodifiableList(attributes);

		}
		
		public void setLocationIsID(final List<String> attributes) {
			
		}
		
		public boolean containsID() {
			return true;
		}
	}
	
}
