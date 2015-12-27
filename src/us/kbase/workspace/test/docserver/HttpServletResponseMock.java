package us.kbase.workspace.test.docserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

public class HttpServletResponseMock implements HttpServletResponse {

	private Map<String, String> headers = new HashMap<String, String>();
	private String contentType = null;
	private int statusCode;
	
	
	public String getHeader(String header) {
		return headers.get(header);
	}
	
	public int getStatusCode() {
		return statusCode;
	}
	
	@Override
	public void flushBuffer() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getBufferSize() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getCharacterEncoding() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public Locale getLocale() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		throw new IOException("ow");
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isCommitted() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void reset() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void resetBuffer() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBufferSize(int arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setCharacterEncoding(String arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentLength(int arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	@Override
	public void setLocale(Locale arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addCookie(Cookie arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addDateHeader(String arg0, long arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addHeader(String key, String value) {
		headers.put(key, value);
	}

	@Override
	public void addIntHeader(String arg0, int arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsHeader(String arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String encodeRedirectURL(String arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String encodeRedirectUrl(String arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String encodeURL(String arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String encodeUrl(String arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendError(int arg0) throws IOException {
		statusCode = arg0;
	}

	@Override
	public void sendError(int arg0, String arg1) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void sendRedirect(String arg0) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setDateHeader(String arg0, long arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setHeader(String key, String value) {
		headers.put(key, value);
	}

	@Override
	public void setIntHeader(String arg0, int arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setStatus(int arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setStatus(int arg0, String arg1) {
		throw new UnsupportedOperationException();
	}

}
