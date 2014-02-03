package us.kbase.workspace.database;

import java.util.Collections;
import java.util.List;

public class ObjectChainResolvedWS {
	
	private ObjectIDResolvedWS head;
	private List<ObjectIDResolvedWS> chain;
	
	public ObjectChainResolvedWS(final ObjectIDResolvedWS head,
			final List<ObjectIDResolvedWS> chain) {
		if (head == null || chain == null) {
			throw new IllegalArgumentException("Neither head nor chain can be null");
		}
		for (final ObjectIDResolvedWS oi: chain) {
			if (oi == null) {
				throw new IllegalArgumentException("Nulls are not allowed in reference chains");
			}
		}
		this.head = head;
		this.chain = Collections.unmodifiableList(chain);
	}

	public ObjectIDResolvedWS getHead() {
		return head;
	}

	public List<ObjectIDResolvedWS> getChain() {
		return chain;
	}
	
	public ObjectIDResolvedWS getLast() {
		return chain.get(chain.size() - 1);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((chain == null) ? 0 : chain.hashCode());
		result = prime * result + ((head == null) ? 0 : head.hashCode());
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
		ObjectChainResolvedWS other = (ObjectChainResolvedWS) obj;
		if (chain == null) {
			if (other.chain != null)
				return false;
		} else if (!chain.equals(other.chain))
			return false;
		if (head == null) {
			if (other.head != null)
				return false;
		} else if (!head.equals(other.head))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ObjectChain [head=" + head + ", chain=" + chain + "]";
	}

}
