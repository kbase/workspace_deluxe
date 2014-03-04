package us.kbase.workspace.database.mongo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.SubdataExtractor;
import us.kbase.typedobj.core.TempFilesManager;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

/**
 * Class is used for getting data from gridfs/shock and keeping it on disk before sending back to client.
 * @author rsutormin
 */
public class ByteStorageWithFileCache {
	private TempFilesManager tfm;
	private long size = 0;
	private final File tempFile;
	private final JsonTokenStream jts;
	private ByteStorageWithFileCache parent = null;
	
	public ByteStorageWithFileCache(InputStream input, int maxInMemorySize, TempFilesManager tfm) throws IOException {
		this.tfm = tfm;
		byte[] buf = new byte[100000];
		ByteArrayOutputStream bufOs = new ByteArrayOutputStream();
		while (size < maxInMemorySize) {
			int count = input.read(buf, 0, Math.min(buf.length, maxInMemorySize - (int)size));
			if (count < 0)
				break;
			bufOs.write(buf, 0, count);
			size += count;
		}
		bufOs.close();
		if (size >= maxInMemorySize) {
			tempFile = tfm.generateTempFile("resp", "json");
			OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile));
			os.write(bufOs.toByteArray());
			bufOs = null;
			while (true) {
				int count = input.read(buf, 0, buf.length);
				if (count < 0)
					break;
				os.write(buf, 0, count);
				size += count;
			}
			os.close();
			jts = new JsonTokenStream(tempFile);
		} else {
			tempFile = null;
			jts = new JsonTokenStream(bufOs.toByteArray());
			bufOs = null;
		}
	}
	
	public ByteStorageWithFileCache(ByteStorageWithFileCache parent, File tempFile, JsonTokenStream jts) {
		this.parent = parent;
		this.tempFile = tempFile;
		this.jts = jts;
	}
	
	public long getSize() {
		return size;
	}
	
	public UObject getUObject() {
		return new UObject(jts);
	}
	
	public JsonNode getAsJsonNode() {
		return UObject.transformObjectToJackson(getUObject());
	}
	
	public ByteStorageWithFileCache getSubdataExtraction(ObjectPaths paths) throws TypedObjectExtractionException {
		OutputStream os = null;
		File tempFile2 = null;
		try {
			if (tempFile == null) {
				os = new ByteArrayOutputStream();
			} else {
				tempFile2 = tfm.generateTempFile("subdata", "json");
				os = new BufferedOutputStream(new FileOutputStream(tempFile2));
			}
			JsonGenerator jgen = UObject.getMapper().getFactory().createGenerator(os);
			try {
				SubdataExtractor.extract(paths, jts.setRoot(null), jgen);
			} finally {
				jts.close();
				jgen.close();
			}
			if (tempFile == null) {
				return new ByteStorageWithFileCache(this, null, 
						new JsonTokenStream(((ByteArrayOutputStream)os).toByteArray()));
			} else {
				return new ByteStorageWithFileCache(this, tempFile2, new JsonTokenStream(tempFile));
			}
		} catch (IOException ex) {
			throw new TypedObjectExtractionException(ex.getMessage(), ex);
		} finally {
			if (tempFile2 != null)
				tempFile2.delete();
		}
	}
	
	public Set<File> getTempFiles() {
		Set<File> ret = new HashSet<File>();
		if (tempFile != null)
			ret.add(tempFile);
		if (parent != null)
			ret.addAll(parent.getTempFiles());
		return ret;
	}
	
	public void deleteTempFiles() {
		for (File f : getTempFiles())
			f.delete();
	}
}
