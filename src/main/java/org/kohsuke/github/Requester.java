/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.kohsuke.github;

import com.fasterxml.jackson.databind.JsonMappingException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.annotation.WillClose;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.util.Arrays.asList;
import static java.util.logging.Level.FINE;
import static org.kohsuke.github.GitHub.*;

/**
 * A builder pattern for making HTTP call and parsing its output.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("WeakerAccess")
class Requester {
    private final GitHub root;
    private final List<Entry> args = new ArrayList<Entry>();
    private final Map<String,String> headers = new LinkedHashMap<String, String>();

    /**
     * Request method.
     */
    private String method = "POST";
    private String contentType = "application/x-www-form-urlencoded";
    private InputStream body;

    /**
     * Current connection.
     */
    private HttpConnection uc;

    private static class Entry {
        String key;
        Object value;

        private Entry(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    Requester(GitHub root) {
        this.root = root;
    }

    /**
     * Sets the request HTTP header.
     *
     * If a header of the same name is already set, this method overrides it.
     */
    public void setHeader(String name, String value) {
        headers.put(name,value);
    }

    public Requester withHeader(String name, String value) {
        setHeader(name,value);
        return this;
    }

    /*package*/ Requester withPreview(String name) {
        return withHeader("Accept",name);
    }

    /**
     * Makes a request with authentication credential.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public Requester withCredential() {
        // keeping it inline with retrieveWithAuth not to enforce the check
        // root.requireCredential();
        return this;
    }

    public Requester with(String key, int value) {
        return _with(key, value);
    }

    public Requester with(String key, Integer value) {
        if (value!=null)
            _with(key, value);
        return this;
    }

    public Requester with(String key, boolean value) {
        return _with(key, value);
    }
    public Requester with(String key, Boolean value) {
        return _with(key, value);
    }

    public Requester with(String key, Enum e) {
        if (e==null)    return _with(key, null);

        // by convention Java constant names are upper cases, but github uses
        // lower-case constants. GitHub also uses '-', which in Java we always
        // replace by '_'
        return with(key, e.toString().toLowerCase(Locale.ENGLISH).replace('_', '-'));
    }

    public Requester with(String key, String value) {
        return _with(key, value);
    }

    public Requester with(String key, Collection<String> value) {
        return _with(key, value);
    }

    public Requester with(String key, Map<String, String> value) {
        return _with(key, value);
    }

    public Requester with(@WillClose/*later*/ InputStream body) {
        this.body = body;
        return this;
    }

    public Requester _with(String key, Object value) {
        if (value!=null) {
            args.add(new Entry(key,value));
        }
        return this;
    }

    /**
     * Unlike {@link #with(String, String)}, overrides the existing value
     */
    public Requester set(String key, Object value) {
        for (Entry e : args) {
            if (e.key.equals(key)) {
                e.value = value;
                return this;
            }
        }
        return _with(key,value);
    }

    public Requester method(String method) {
        this.method = method;
        return this;
    }

    public Requester contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public void to(String tailApiUrl) throws IOException {
        to(tailApiUrl,null);
    }

    /**
     * Sends a request to the specified URL, and parses the response into the given type via databinding.
     *
     * @throws IOException
     *      if the server returns 4xx/5xx responses.
     * @return
     *      {@link Reader} that reads the response.
     */
    public <T> T to(String tailApiUrl, Class<T> type) throws IOException {
        return _to(tailApiUrl, type, null);
    }

    /**
     * Like {@link #to(String, Class)} but updates an existing object instead of creating a new instance.
     */
    public <T> T to(String tailApiUrl, T existingInstance) throws IOException {
        return _to(tailApiUrl, null, existingInstance);
    }

    /**
     * Short for {@code method(method).to(tailApiUrl,type)}
     */
    @Deprecated
    public <T> T to(String tailApiUrl, Class<T> type, String method) throws IOException {
        return method(method).to(tailApiUrl, type);
    }

    @SuppressFBWarnings("SBSC_USE_STRINGBUFFER_CONCATENATION")
    private <T> T _to(String tailApiUrl, Class<T> type, T instance) throws IOException {
        if (METHODS_WITHOUT_BODY.contains(method) && !args.isEmpty()) {
            boolean questionMarkFound = tailApiUrl.indexOf('?') != -1;
            tailApiUrl += questionMarkFound ? '&' : '?';
            for (Iterator<Entry> it = args.listIterator(); it.hasNext();) {
                Entry arg = it.next();
                tailApiUrl += arg.key + '=' + URLEncoder.encode(arg.value.toString(),"UTF-8");
                if (it.hasNext()) {
                    tailApiUrl += '&';
                }
            }
        }

        while (true) {// loop while API rate limit is hit
            setupConnection(root.getApiURL(tailApiUrl));

            buildRequest();

            try {
                T result = parse(type, instance);
                if (type != null && type.isArray()) { // we might have to loop for pagination - done through recursion
                    final String links = uc.getHeaderField("link");
                    if (links != null && links.contains("rel=\"next\"")) {
                        Pattern nextLinkPattern = Pattern.compile(".*<(.*)>; rel=\"next\"");
                        Matcher nextLinkMatcher = nextLinkPattern.matcher(links);
                        if (nextLinkMatcher.find()) {
                            final String link = nextLinkMatcher.group(1);
                            T nextResult = _to(link, type, instance);

                            final int resultLength = Array.getLength(result);
                            final int nextResultLength = Array.getLength(nextResult);
                            T concatResult = (T) Array.newInstance(type.getComponentType(), resultLength + nextResultLength);
                            System.arraycopy(result, 0, concatResult, 0, resultLength);
                            System.arraycopy(nextResult, 0, concatResult, resultLength, nextResultLength);
                            result = concatResult;
                        }
                    }
                }
                return result;
            } catch (IOException e) {
                handleApiError(e);
            }
        }
    }

    /**
     * Makes a request and just obtains the HTTP status code.
     */
    public int asHttpStatusCode(String tailApiUrl) throws IOException {
        while (true) {// loop while API rate limit is hit
            method("GET");
            setupConnection(root.getApiURL(tailApiUrl));

            buildRequest();

            try {
                return uc.getResponseCode();
            } catch (IOException e) {
                handleApiError(e);
            }
        }
    }

    public InputStream asStream(String tailApiUrl) throws IOException {
        while (true) {// loop while API rate limit is hit
            setupConnection(root.getApiURL(tailApiUrl));

            buildRequest();

            try {
                return wrapStream(uc.getInputStream());
            } catch (IOException e) {
                handleApiError(e);
            }
        }
    }

    public String getResponseHeader(String header) {
        return uc.getHeaderField(header);
    }


    /**
     * Set up the request parameters or POST payload.
     */
    private void buildRequest() throws IOException {
        if (isMethodWithBody()) {
            uc.setDoOutput(true);
            uc.setRequestProperty("Content-type", contentType);

            final OutputStream output = uc.getOutputStream();
            if (body == null) {
                Map<String, Object> json = new HashMap<String, Object>();
                for (Entry e : args) {
                    json.put(e.key, e.value);
                }
                MAPPER.writeValue(output, json);
            } else {
                try {
                    byte[] bytes = new byte[32768];
                    int read;
                    while ((read = body.read(bytes)) != -1) {
                        output.write(bytes, 0, read);
                    }
                } finally {
                    body.close();
                }
            }
        }
    }

    private boolean isMethodWithBody() {
        return !METHODS_WITHOUT_BODY.contains(method);
    }

    /**
     * Loads paginated resources.
     *
     * Every iterator call reports a new batch.
     * @throws RuntimeException for an {@code MalformedURLException} when trying build the request URL
     */
    /*package*/ <T> Iterator<T> asIterator(String tailApiUrl, Class<T> type, int pageSize) {
        method("GET");

        if (pageSize!=0)
            args.add(new Entry("per_page",pageSize));

        StringBuilder s = new StringBuilder(tailApiUrl);
        if (!args.isEmpty()) {
            boolean first = true;
            try {
                for (Entry a : args) {
                    s.append(first ? '?' : '&');
                    first = false;
                    s.append(URLEncoder.encode(a.key, "UTF-8"));
                    s.append('=');
                    s.append(URLEncoder.encode(a.value.toString(), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError(e);    // UTF-8 is mandatory
            }
        }

        try {
            return new PagingIterator<T>(type, root.getApiURL(s.toString()));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    class PagingIterator<T> implements Iterator<T> {

        private final Class<T> type;

        /**
         * The next batch to be returned from {@link #next()}.
         */
        private T next;

        /**
         * URL of the next resource to be retrieved, or null if no more data is available.
         */
        private URL url;

        PagingIterator(Class<T> type, URL url) {
            this.url = url;
            this.type = type;
        }

        public boolean hasNext() {
            fetch();
            return next!=null;
        }

        public T next() {
            fetch();
            T r = next;
            if (r==null)    throw new NoSuchElementException();
            next = null;
            return r;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void fetch() {
            if (next!=null) return; // already fetched
            if (url==null)  return; // no more data to fetch

            try {
                while (true) {// loop while API rate limit is hit
                    setupConnection(url);
                    try {
                        next = parse(type,null);
                        assert next!=null;
                        findNextURL();
                        return;
                    } catch (IOException e) {
                        handleApiError(e);
                    }
                }
            } catch (IOException e) {
                throw new Error(e);
            }
        }

        /**
         * Locate the next page from the pagination "Link" tag.
         */
        private void findNextURL() throws MalformedURLException {
            url = null; // start defensively
            String link = uc.getHeaderField("Link");
            if (link==null) return;

            for (String token : link.split(", ")) {
                if (token.endsWith("rel=\"next\"")) {
                    // found the next page. This should look something like
                    // <https://api.github.com/repos?page=3&per_page=100>; rel="next"
                    int idx = token.indexOf('>');
                    url = new URL(token.substring(1,idx));
                    return;
                }
            }

            // no more "next" link. we are done.
        }
    }


    private void setupConnection(URL url) throws IOException {
        uc = root.getConnector().connect(url);

        // if the authentication is needed but no credential is given, try it anyway (so that some calls
        // that do work with anonymous access in the reduced form should still work.)
        if (root.encodedAuthorization!=null)
            uc.setRequestProperty("Authorization", root.encodedAuthorization);

        for (Map.Entry<String, String> e : headers.entrySet()) {
            String v = e.getValue();
            if (v!=null)
                uc.setRequestProperty(e.getKey(), v);
        }

        setRequestMethod(uc);
        uc.setRequestProperty("Accept-Encoding", "gzip");
    }

    private void setRequestMethod(HttpConnection uc) throws IOException {
        uc.setRequestMethod(method);
        if (!uc.getRequestMethod().equals(method))
            throw new IllegalStateException("Failed to set the request method to "+method);
    }

    private <T> T parse(Class<T> type, T instance) throws IOException {
        InputStreamReader r = null;
        int responseCode = -1;
        String responseMessage = null;
        try {
            responseCode = uc.getResponseCode();
            responseMessage = uc.getResponseMessage();
            if (responseCode == 304) {
                return null;    // special case handling for 304 unmodified, as the content will be ""
            }
            if (responseCode == 204 && type!=null && type.isArray()) {
                // no content
                return type.cast(Array.newInstance(type.getComponentType(),0));
            }

            r = new InputStreamReader(wrapStream(uc.getInputStream()), "UTF-8");
            String data = IOUtils.toString(r);
            if (type!=null)
                try {
                    return MAPPER.readValue(data,type);
                } catch (JsonMappingException e) {
                    //noinspection UnnecessaryInitCause
                    throw (IOException)new IOException("Failed to deserialize " +data).initCause(e);
                }
            if (instance!=null)
                return MAPPER.readerForUpdating(instance).readValue(data);
            return null;
        } catch (FileNotFoundException e) {
            // java.net.URLConnection handles 404 exception has FileNotFoundException, don't wrap exception in HttpException
            // to preserve backward compatibility
            throw e;
        } catch (IOException e) {
            throw new HttpException(responseCode, responseMessage, uc.getURL(), e);
        } finally {
            IOUtils.closeQuietly(r);
        }
    }

    /**
     * Handles the "Content-Encoding" header.
     */
    private InputStream wrapStream(InputStream in) throws IOException {
        String encoding = uc.getContentEncoding();
        if (encoding==null || in==null) return in;
        if (encoding.equals("gzip"))    return new GZIPInputStream(in);

        throw new UnsupportedOperationException("Unexpected Content-Encoding: "+encoding);
    }

    /**
     * Handle API error by either throwing it or by returning normally to retry.
     */
    /*package*/ void handleApiError(IOException e) throws IOException {
        int responseCode;
        try {
            responseCode = uc.getResponseCode();
        } catch (IOException e2) {
            // likely to be a network exception (e.g. SSLHandshakeException),
            // uc.getResponseCode() and any other getter on the response will cause an exception
            if (LOGGER.isLoggable(FINE))
                LOGGER.log(FINE, "Silently ignore exception retrieving response code for '" + uc.getURL() + "'" +
                        " handling exception " + e, e);
            throw e;
        }
        if (responseCode == HTTP_UNAUTHORIZED) // 401 / Unauthorized == bad creds
            throw e;

        if ("0".equals(uc.getHeaderField("X-RateLimit-Remaining"))) {
            root.rateLimitHandler.onError(e,uc);
            return;
        }

        // Retry-After is not documented but apparently that field exists
        if (responseCode == HTTP_FORBIDDEN &&
            uc.getHeaderField("Retry-After") != null) {
            this.root.abuseLimitHandler.onError(e,uc);
            return;
        }

        InputStream es = wrapStream(uc.getErrorStream());
        try {
            if (es!=null) {
                if (e instanceof FileNotFoundException) {
                    // pass through 404 Not Found to allow the caller to handle it intelligently
                    throw (IOException) new FileNotFoundException(IOUtils.toString(es, "UTF-8")).initCause(e);
                } else
                    //noinspection UnnecessaryInitCause
                    throw (IOException) new IOException(IOUtils.toString(es, "UTF-8")).initCause(e);
            } else
                throw e;
        } finally {
            IOUtils.closeQuietly(es);
        }
    }

    private static final List<String> METHODS_WITHOUT_BODY = asList("GET", "DELETE");
    private static final Logger LOGGER = Logger.getLogger(Requester.class.getName());
}
