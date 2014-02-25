package us.kbase.workspace.database.mongo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.fasterxml.jackson.databind.JsonNode;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.typedobj.core.TempFilesManager;

/**
 * Class is used for getting data from gridfs/shock and keeping it on disk before sending back to client.
 * @author rsutormin
 */
public class ByteStorageWithFileCache {
	private File tempFile;
	private JsonTokenStream jts;
	
	public ByteStorageWithFileCache(InputStream input, int maxInMemorySize, TempFilesManager tfm) throws IOException {
		byte[] rpcBuffer = new byte[maxInMemorySize];
		long rpcSize = 0;
		while (rpcSize < maxInMemorySize) {
			int count = input.read(rpcBuffer, (int)rpcSize, maxInMemorySize - (int)rpcSize);
			if (count < 0)
				break;
			rpcSize += count;
		}
		if (rpcSize >= maxInMemorySize) {
			tempFile = tfm.generateTempFile("resp", "json");
			OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile));
			os.write(rpcBuffer, 0, (int)rpcSize);
			while (true) {
				int count = input.read(rpcBuffer, 0, rpcBuffer.length);
				if (count < 0)
					break;
				os.write(rpcBuffer, 0, count);
				rpcSize += count;
			}
			os.close();
			if (tempFile == null) {
				jts = new JsonTokenStream(((ByteArrayOutputStream)os).toByteArray());
			} else {
				jts = new JsonTokenStream(tempFile);
			}
		} else {
			byte[] bdata = new byte[(int)rpcSize];
			System.arraycopy(rpcBuffer, 0, bdata, 0, bdata.length);
			jts = new JsonTokenStream(bdata);
		}
	}
	
	public UObject getUObject() {
		return new UObject(jts);
	}
	
	public JsonNode getAsJsonNode() {
		return getUObject().asJsonNode();
	}
	
	public void setJsonNode(JsonNode data) {
		try {
			jts = new JsonTokenStream(data);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
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
