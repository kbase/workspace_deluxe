package us.kbase.workspace.database;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.ObjectPaths;
import us.kbase.typedobj.core.SubdataExtractor;
import us.kbase.typedobj.exceptions.TypedObjectExtractionException;

/**
 * Class is used for getting data from gridfs/shock and keeping it on disk before sending back to client.
 * @author rsutormin
 */
public class ByteArrayFileCache {
	private File tempFile = null;
	private final JsonTokenStream jts;
	private ByteArrayFileCache parent = null;
	
	public ByteArrayFileCache(ByteArrayFileCache parent, File tempFile, JsonTokenStream jts) {
		this.parent = parent;
		this.tempFile = tempFile;
		this.jts = jts;
	}
	
	public UObject getUObject() {
		return new UObject(jts);
	}
	
	public JsonNode getAsJsonNode() {
		return UObject.transformObjectToJackson(getUObject());
	}
	
	public void getSubdataExtractionAsStream(ObjectPaths paths, OutputStream os) throws TypedObjectExtractionException {
		try {
			JsonGenerator jgen = UObject.getMapper().getFactory().createGenerator(os);
			try {
				SubdataExtractor.extract(paths, jts.setRoot(null), jgen);
			} finally {
				jts.close();
				jgen.close();
			}
		} catch (IOException ex) {
			throw new TypedObjectExtractionException(ex.getMessage(), ex);
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
			if (f.exists()) {
				f.delete();
			}
	}
}
