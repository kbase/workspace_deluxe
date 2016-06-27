package us.kbase.workspace.database.mongo;

import java.io.IOException;

import us.kbase.typedobj.core.Writable;

public class TypeData {
	
	//TODO PERFORMANCE SIMPLIFICATION remove this class. Do MD5 and size in TOVR at the same time as sorting
	private Writable data = null;
	
	private String chksum;
	
	public TypeData(final Writable data)  {
		if (data == null) {
			throw new IllegalArgumentException("data may not be null");
		}
		this.data = data;
		final MD5DigestOutputStream md5 = new MD5DigestOutputStream();
		try {
			//writes in UTF8
			data.write(md5);
		} catch (IOException ioe) {
			throw new RuntimeException("something is broken here", ioe);
		} finally {
			try {
				md5.close();
			} catch (IOException ioe) {
				throw new RuntimeException("something is broken here", ioe);
			}
		}
		this.chksum = md5.getMD5().getMD5();
	}
	
	public Writable getData() {
		return data;
	}
	
	public String getChksum() {
		return chksum;
	}
	
	@Override
	public String toString() {
		return "TypeData [data=" + data
				+ ", chksum=" + chksum + "]";
	}
}
