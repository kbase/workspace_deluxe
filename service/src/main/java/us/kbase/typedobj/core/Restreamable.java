package us.kbase.typedobj.core;

import java.io.InputStream;

/** A source of input streams that can be streamed multiple times, as opposed to a general input
 * stream, which is exhausted when used. Each invocation of {@link #getInputStream()} produces
 * a new input stream, which has the same contents as any other input stream produced from the
 * method.
 * @author gaprice@lbl.gov
 *
 */
public interface Restreamable {

	/** Generate an input stream from the source data.
	 * @return the input stream.
	 */
	InputStream getInputStream();
	
	
	/** Get the size of the streamed data.
	 * @return the data size.
	 */
	long getSize();
	
}
