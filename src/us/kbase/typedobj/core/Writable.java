package us.kbase.typedobj.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This interface is used for describing objects that can write itself
 * into output stream later when needed.
 * @author rsutormin
 */
public interface Writable {
	public void write(OutputStream os) throws IOException;
}
