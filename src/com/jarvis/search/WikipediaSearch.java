package com.jarvis.search;

import com.jarvis.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The default, key-free provider: searches Wikipedia's article index. */
public class WikipediaSearch extends HttpSearch {

    @Override
    public String name() {
        return "Wikipedia";
    }

    @Override
    public List<SearchHit> search(String query, int count) throws SearchException {
        Map<String, Object> json = getJson("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1"
                + "&srlimit=" + Math.max(1, Math.min(10, count)) + "&srsearch=" + enc(query));
        List<SearchHit> hits = new ArrayList<>();
        for (Object o : Json.asList(Json.asMap(json.get("query")).get("search"))) {
            Map<String, Object> m = Json.asMap(o);
            String title = Json.asString(m.get("title"), "");
            hits.add(new SearchHit(title, "https://en.wikipedia.org/wiki/" + title.replace(' ', '_'),
                    Json.asString(m.get("snippet"), "").replaceAll("<[^>]+>", "").replace("&quot;", "\"")));
        }
        return hits;
    }

    /** Builds the provider chosen in the configuration, falling back to Wikipedia when no key is set. */
    public static SearchProvider fromConfig(String provider, String googleKey, String googleCx, String braveKey) {
        return switch (provider.toLowerCase()) {
            case "google" -> googleKey.isBlank() || googleCx.isBlank() ? new WikipediaSearch() : new GoogleSearch(googleKey, googleCx);
            case "brave" -> braveKey.isBlank() ? new WikipediaSearch() : new BraveSearch(braveKey);
            default -> new WikipediaSearch();
        };
    }
}
