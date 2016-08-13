package org.kohsuke.github.extras;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.io.EmptyInputStream;
import org.kohsuke.github.HttpConnector;
import org.kohsuke.github.HttpConnection;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

@SuppressWarnings("unused")
public class HttpCommonsHttpConnector implements HttpConnector {
    private class HCHttpConnection implements HttpConnection {
        private final URL url;

        private String method;
        private HttpUriRequest request;
        private HttpResponse resp;

        private HCHttpConnection(URL url) {
            this.url = url;
        }

        public String getRequestMethod() {
            return method;
        }

        public int getResponseCode() throws IOException {
            resp = httpClient.execute(request);
            return resp.getStatusLine().getStatusCode();
        }

        public String getResponseMessage() throws IOException {
            return resp.getStatusLine().getReasonPhrase();
        }

        public String getHeaderField(@Nonnull String headerName) {
            final Header maybeHeader = resp.getFirstHeader(headerName);
            if (maybeHeader == null) {
                return null;
            }
            return maybeHeader.getValue();
        }

        public String getContentEncoding() {
            final HttpEntity entity = resp.getEntity();
            if (entity == null) {
                return null;
            }
            final Header header = entity.getContentEncoding();
            if (header == null) {
                return null;
            }
            return header.getValue();
        }

        public URL getURL() {
            return url;
        }

        public InputStream getInputStream() throws IOException {
            final HttpEntity entity = resp.getEntity();
            if (entity == null) {
                return EmptyInputStream.INSTANCE;
            }
            return entity.getContent();
        }

        public InputStream getErrorStream() throws IOException {
            return null;
        }

        public OutputStream getOutputStream() {
            final HttpEntityEnclosingRequest withEntity = (HttpEntityEnclosingRequest)request;
            return new EntityOutputStream(withEntity);
        }

        public void setRequestMethod(String method) {
            this.method = method;
            final URI uri;
            try {
                uri = url.toURI();
            } catch (URISyntaxException e) {
                //noinspection UnnecessaryInitCause
                throw (AssertionError)new AssertionError("wat?").initCause(e);
            }
            if (method.equals("GET")) {
                request = new HttpGet(uri);
            } else if (method.equals("POST")) {
                request = new HttpPost(uri);
            } else {
                throw new UnsupportedOperationException("METHOD? " + method);
            }
        }

        public void setRequestProperty(@Nonnull String propertyName, String value) {
            request.setHeader(propertyName, value);
        }

        public void setConnectTimeout(int connectTimeout) {
        }

        public void setReadTimeout(int readTimeout) {
        }

        public void setDoOutput(boolean willWriteOutput) {
        }
    }

    private class EntityOutputStream extends OutputStream {
        private final HttpEntityEnclosingRequest request;
        private final ByteArrayOutputStream out;
        private boolean closed;

        private EntityOutputStream(HttpEntityEnclosingRequest request) {
            this.request = request;
            this.out = new ByteArrayOutputStream();
        }

        public void write(int b) throws IOException {
            if (closed) {
                throw new IOException("closed stream");
            }
            out.write(b);
        }

        @Override
        public void close() throws IOException {
            request.setEntity(new ByteArrayEntity(out.toByteArray()));
            closed = true;
        }
    }

    private final HttpClient httpClient;

    @SuppressWarnings("unused")
    public HttpCommonsHttpConnector(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HttpConnection connect(URL url) throws IOException {
        return new HCHttpConnection(url);
    }
}
