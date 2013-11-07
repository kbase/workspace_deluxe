package us.kbase.workspace.database.mongo;

import static us.kbase.common.utils.StringUtils.checkString;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import us.kbase.typedobj.core.AbsoluteTypeDefId;
import us.kbase.typedobj.core.TypeDefId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

public class TypeData {
	
	@JsonIgnore
	private static final int DIGEST_BUFFER_SIZE = 100000; //100kB
	
	@JsonIgnore
	public static final String TYPE_COL_PREFIX = "type_";
	
	@JsonIgnore
	private String data = null;
	
	//these attributes are actually saved in mongo
	private String type = null;
	private String chksum;
	@JsonInclude(value=JsonInclude.Include.ALWAYS)
	private Map<String, Object> subdata;
	private long size;
	
	public TypeData(final String data, final AbsoluteTypeDefId type,
			final Map<String,Object> subdata) {
		checkString(data, "data");
		if (type == null) {
			throw new IllegalArgumentException("type may not be null");
		}
		if (type.getMd5() != null) {
			throw new RuntimeException("MD5 types are not accepted");
		}
		this.data = data;
		this.type = type.getType().getTypeString() +
				AbsoluteTypeDefId.TYPE_VER_SEP + type.getMajorVersion();
		this.subdata = subdata;
		this.size = data.length();
		this.chksum = calcHexDigest(data);
	}
	
	//Digest utils causes OOM on 1G data
	private String calcHexDigest(String data) {
		final MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException("There definitely should be an MD5 digest");
		}
		for (int i = 0; i < data.length(); ) {
			final int end;
			final int next = i + DIGEST_BUFFER_SIZE;
			if (next >= data.length()) {
				end = data.length();
			} else {
				end = next;
			}
			digest.update(data.substring(i, end).getBytes());
			i = next;
		}
		final byte[] d = digest.digest();
		final StringBuilder sb = new StringBuilder();
		for (final byte b : d) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
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

	public String getData() {
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
