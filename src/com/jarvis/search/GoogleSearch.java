package com.jarvis.search;

import com.jarvis.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Google web search through Google's official Programmable Search (Custom Search JSON) API.
 * Needs an API key and a search-engine ID ("cx") in config/jarvis.properties.
 */
public class GoogleSearch extends HttpSearch {

    private final String apiKey;
    private final String cx;

    public GoogleSearch(String apiKey, String cx) {
        this.apiKey = apiKey;
        this.cx = cx;
    }

    @Override
    public String name() {
        return "Google";
    }

    @Override
    public List<SearchHit> search(String query, int count) throws SearchException {
        Map<String, Object> json = getJson("https://www.googleapis.com/customsearch/v1?key=" + enc(apiKey) + "&cx=" + enc(cx)
                + "&num=" + Math.max(1, Math.min(10, count)) + "&q=" + enc(query));
        List<SearchHit> hits = new ArrayList<>();
        for (Object o : Json.asList(json.get("items"))) {
            Map<String, Object> m = Json.asMap(o);
            hits.add(new SearchHit(Json.asString(m.get("title"), ""), Json.asString(m.get("link"), ""),
                    Json.asString(m.get("snippet"), "").replaceAll("\\s+", " ")));
        }
        return hits;
    }
}
