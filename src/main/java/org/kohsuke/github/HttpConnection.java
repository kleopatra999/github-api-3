package org.kohsuke.github;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

public interface HttpConnection {
    String getRequestMethod();
    int getResponseCode() throws IOException;
    String getResponseMessage() throws IOException;
    String getHeaderField(@Nonnull String headerName);
    String getContentEncoding();
    URL getURL();
    InputStream getInputStream() throws IOException;
    InputStream getErrorStream() throws IOException;
    OutputStream getOutputStream() throws IOException;

    void setRequestMethod(String method);
    void setRequestProperty(@Nonnull String propertyName, String value);
    void setConnectTimeout(int connectTimeout);
    void setReadTimeout(int readTimeout);
    void setDoOutput(boolean willWriteOutput);
}
