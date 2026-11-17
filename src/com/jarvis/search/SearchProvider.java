package com.jarvis.search;

import java.util.List;

/**
 * A web search engine JARVIS can ask.
 *
 * OOP: INTERFACE + POLYMORPHISM. The answer workflow only knows "a search
 * provider"; whether results come from Google, Brave or Wikipedia is decided
 * by one line in config/jarvis.properties.
 */
public interface SearchProvider {

    /** Name shown to the user as the source, e.g. "Google". */
    String name();

    /** The best results for a query, most relevant first. */
    List<SearchHit> search(String query, int count) throws SearchException;

    /** One search result. */
    record SearchHit(String title, String url, String snippet) {
    }

    /** A search failure with a message that is safe to show the user. */
    class SearchException extends Exception {
        public SearchException(String message) {
            super(message);
        }
    }
}
