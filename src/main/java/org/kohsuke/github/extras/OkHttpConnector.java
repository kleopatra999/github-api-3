package org.kohsuke.github.extras;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.OkUrlFactory;
import org.kohsuke.github.HttpConnector;
import org.kohsuke.github.HttpConnection;
import org.kohsuke.github.JavaNetHttpConnection;

import java.io.IOException;
import java.net.URL;

/**
 * {@link HttpConnector} for {@link OkHttpClient}.
 *
 * Unlike {@link #DEFAULT}, OkHttp does response caching.
 * Making a conditional request against GitHubAPI and receiving a 304
 * response does not count against the rate limit.
 * See http://developer.github.com/v3/#conditional-requests
 *
 * @author Roberto Tyley
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("unused")
public class OkHttpConnector implements HttpConnector {
    private final OkUrlFactory urlFactory;

    public OkHttpConnector(OkUrlFactory urlFactory) {
        this.urlFactory = urlFactory;
    }

    public HttpConnection connect(URL url) throws IOException {
        return new JavaNetHttpConnection(urlFactory.open(url));
    }
}
