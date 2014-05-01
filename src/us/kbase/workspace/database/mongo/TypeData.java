package us.kbase.workspace.database.mongo;


import java.io.IOException;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;
import us.kbase.typedobj.core.Writable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class TypeData {
	
	@JsonIgnore
	private static final ObjectMapper MAPPER = new ObjectMapper();
	static {
		MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}
	
	@JsonIgnore
	public static final String TYPE_COL_PREFIX = "type_";
	
	@JsonIgnore
	private Writable data = null;
	
	//these attributes are actually saved in mongo
	private String type = null;
	private String chksum;
	@JsonInclude(value=JsonInclude.Include.ALWAYS)
	private Map<String, Object> subdata;
	private long size;
	
	public TypeData(final Writable data, final AbsoluteTypeDefId type,
			final Map<String,Object> subdata)  {
		if (data == null) {
			throw new IllegalArgumentException("data may not be null");
		}
		if (type == null) {
			throw new IllegalArgumentException("type may not be null");
		}
		if (type.getMd5() != null) {
			throw new RuntimeException("MD5 types are not accepted");
		}
		this.data = data;
		/* Only the major type is stored here since different minor versions
		 * of the same type can have the same checksum, and there's no reason
		 * to store identical subdata twice. Knowing the exact type is not
		 * necessary for the subdata.
		 */
		this.type = type.getType().getTypeString() +
				AbsoluteTypeDefId.TYPE_VER_SEP + type.getMajorVersion();
		this.subdata = subdata;
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
		this.size = md5.getSize();
		this.chksum = md5.getMD5().getMD5();
	}
	
	public String getTypeCollection() {
		return TYPE_COL_PREFIX + DigestUtils.md5Hex(this.type);
	}
	
	public static String getTypeCollection(final TypeDefId type) {
		if (type == null) {
			throw new IllegalArgumentException("type may not be null");
		}
		if (type.getMd5() != null) {
			throw new RuntimeException("MD5 types are not accepted");
		}
		if (type.getMajorVersion() == null) {
			throw new IllegalArgumentException(
					"Cannot get a type collection for a typedef without a major version");
		}
		final String t = type.getType().getTypeString() +
				AbsoluteTypeDefId.TYPE_VER_SEP + type.getMajorVersion();
		return TYPE_COL_PREFIX + DigestUtils.md5Hex(t);
	}

	public Writable getData() {
		return data;
	}
	
	public TypeDefId getType() {
		return TypeDefId.fromTypeString(type);
	}
	
	public String getChksum() {
		return chksum;
	}
	
	public long getSize() {
		return size;
	}

	@Override
	public String toString() {
		return "TypeData [data=" + data + ", type=" + type
				+ ", chksum=" + chksum + ", subdata=" + subdata + ", size="
				+ size + "]";
	}
}
