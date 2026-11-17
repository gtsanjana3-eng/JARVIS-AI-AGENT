package com.jarvis.search;

import com.jarvis.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Web search through the Brave Search API. Needs an API key in config/jarvis.properties. */
public class BraveSearch extends HttpSearch {

    private final String apiKey;

    public BraveSearch(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "Brave Search";
    }

    @Override
    public List<SearchHit> search(String query, int count) throws SearchException {
        Map<String, Object> json = getJson("https://api.search.brave.com/res/v1/web/search?count="
                + Math.max(1, Math.min(10, count)) + "&q=" + enc(query), "X-Subscription-Token", apiKey);
        List<SearchHit> hits = new ArrayList<>();
        for (Object o : Json.asList(Json.asMap(json.get("web")).get("results"))) {
            Map<String, Object> m = Json.asMap(o);
            hits.add(new SearchHit(Json.asString(m.get("title"), ""), Json.asString(m.get("url"), ""),
                    Json.asString(m.get("description"), "").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ")));
        }
        return hits;
    }
}
