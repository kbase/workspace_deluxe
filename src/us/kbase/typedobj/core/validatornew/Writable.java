package us.kbase.typedobj.core.validatornew;

import java.io.IOException;
import java.io.Writer;

public interface Writable {
	public void write(Writer w) throws IOException;
}
