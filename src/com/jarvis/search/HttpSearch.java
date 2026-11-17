package com.jarvis.search;

import com.jarvis.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Shared HTTP code for search providers.
 *
 * OOP: ABSTRACT CLASS. Google, Brave and Wikipedia providers inherit the
 * request/error handling and only implement how to build the URL and read
 * their own JSON format.
 */
public abstract class HttpSearch implements SearchProvider {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    protected static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    protected Map<String, Object> getJson(String url, String... headers) throws SearchException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "JARVIS-Student-Project/2.0").header("Accept", "application/json");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        try {
            HttpResponse<String> res = HTTP.send(b.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                throw new SearchException(name() + " refused the request (HTTP " + res.statusCode()
                        + "). Check the API key in config/jarvis.properties.");
            }
            if (res.statusCode() == 429) {
                throw new SearchException(name() + " daily search limit reached. Try again later.");
            }
            if (res.statusCode() != 200) {
                throw new SearchException(name() + " returned HTTP " + res.statusCode() + ".");
            }
            return Json.parse(res.body());
        } catch (IOException e) {
            throw new SearchException("Could not reach " + name() + " (check your internet connection).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SearchException("Search interrupted.");
        }
    }
}
