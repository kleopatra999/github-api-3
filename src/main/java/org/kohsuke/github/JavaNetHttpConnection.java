package org.kohsuke.github;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

public class JavaNetHttpConnection implements HttpConnection {
    private final HttpURLConnection delegate;

    public JavaNetHttpConnection(HttpURLConnection delegate) {
        this.delegate = delegate;
    }

    public String getRequestMethod() {
        return delegate.getRequestMethod();
    }

    public int getResponseCode() throws IOException {
        return delegate.getResponseCode();
    }

    public String getResponseMessage() throws IOException {
        return delegate.getResponseMessage();
    }

    public String getHeaderField(@Nonnull String headerName) {
        return delegate.getHeaderField(headerName);
    }

    public String getContentEncoding() {
        return delegate.getContentEncoding();
    }

    public URL getURL() {
        return delegate.getURL();
    }

    public InputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    public InputStream getErrorStream() throws IOException {
        return delegate.getErrorStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    public void setRequestMethod(String method) {
        try {
            delegate.setRequestMethod(method);
        } catch (ProtocolException e) {
            throw (IllegalArgumentException)new IllegalArgumentException("Unsupported method "+method).initCause(e);
        }
    }

    public void setRequestProperty(@Nonnull String propertyName, String value) {
        delegate.setRequestProperty(propertyName, value);
    }

    public void setConnectTimeout(int connectTimeout) {
        delegate.setConnectTimeout(connectTimeout);
    }

    public void setReadTimeout(int readTimeout) {
        delegate.setReadTimeout(readTimeout);
    }

    public void setDoOutput(boolean willWriteOutput) {
        delegate.setDoOutput(willWriteOutput);
    }
}
