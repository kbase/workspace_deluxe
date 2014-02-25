package us.kbase.typedobj.core.validatornew;

import java.io.IOException;
import java.io.OutputStream;

public interface Writable {
	public void write(OutputStream os) throws IOException;
	public void releaseResources() throws IOException;
}
