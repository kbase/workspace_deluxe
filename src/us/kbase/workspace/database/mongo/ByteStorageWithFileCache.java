package us.kbase.workspace.database.mongo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
	private File tempFile;
	private JsonTokenStream jts;
	
	public ByteStorageWithFileCache(InputStream input, int maxInMemorySize, TempFilesManager tfm) throws IOException {
		this.tfm = tfm;
		byte[] buffer = new byte[maxInMemorySize];
		long size = 0;
		while (size < maxInMemorySize) {
			int count = input.read(buffer, (int)size, maxInMemorySize - (int)size);
			if (count < 0)
				break;
			size += count;
		}
		if (size >= maxInMemorySize) {
			tempFile = tfm.generateTempFile("resp", "json");
			OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile));
			os.write(buffer, 0, (int)size);
			while (true) {
				int count = input.read(buffer, 0, buffer.length);
				if (count < 0)
					break;
				os.write(buffer, 0, count);
				size += count;
			}
			os.close();
			jts = new JsonTokenStream(tempFile);
		} else {
			byte[] bdata = new byte[(int)size];
			System.arraycopy(buffer, 0, bdata, 0, bdata.length);
			jts = new JsonTokenStream(bdata);
		}
	}
	
	public UObject getUObject() {
		return new UObject(jts);
		/*} else {
			return new UObject(jts) {
				@Override
				public void write(JsonGenerator jgen) throws IOException {
					try {
						SubdataExtractor.extract(subdataPaths, jts.setRoot(null), jgen);
					} catch (TypedObjectExtractionException e) {
						throw new IllegalStateException(e.getMessage(), e);
					} finally {
						jts.close();
					}
				}
				
				@Override
				public <T> T asClassInstance(Class<T> retType) {
					return UObject.transformObjectToObject(this, retType);
				}
			};
		}*/
	}
	
	public JsonNode getAsJsonNode() {
		return UObject.transformObjectToJackson(getUObject());
	}
	
	public void setSubdataExtraction(ObjectPaths paths) throws TypedObjectExtractionException {
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
				jts = new JsonTokenStream(((ByteArrayOutputStream)os).toByteArray());
			} else {
				tempFile.delete();
				tempFile = tempFile2;
				tempFile2 = null;
				jts = new JsonTokenStream(tempFile);
			}
		} catch (IOException ex) {
			throw new TypedObjectExtractionException(ex.getMessage(), ex);
		} finally {
			if (tempFile2 != null)
				tempFile2.delete();
		}
	}
	
	public File getTempFile() {
		return tempFile;
	}
	
	public void deleteTempFile() {
		if (tempFile != null)
			tempFile.delete();
	}
}
