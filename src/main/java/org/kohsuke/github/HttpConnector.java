package org.kohsuke.github;

import org.kohsuke.github.extras.ImpatientHttpConnector;

import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;

/**
 * Plugability for customizing HTTP request behaviors or using altogether different library.
 *
 * <p>
 * For example, you can implement this to st custom timeouts.
 *
 * @author Kohsuke Kawaguchi
 */
public interface HttpConnector {
    /**
     * Opens a connection to the given URL.
     */
    HttpConnection connect(URL url) throws IOException;

    /**
     * Default implementation that uses {@link URL#openConnection()}.
     */
    HttpConnector DEFAULT = new ImpatientHttpConnector(new HttpConnector() {
        public HttpConnection connect(URL url) throws IOException {
            final HttpURLConnection httpConn = (HttpURLConnection)url.openConnection();
            return new JavaNetHttpConnection(httpConn);
        }
    });
}
