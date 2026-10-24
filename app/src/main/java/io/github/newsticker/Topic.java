package io.github.newsticker;

import java.util.ArrayList;
import java.util.List;

/**
 * A category: a name plus where its news come from — a Google News query, an ANSA site
 * section slug, a raw RSS URL, or (GOLIARDIA) a hand-written list of joke headlines that
 * never touch the network.
 */
final class Topic {
    static final String GNEWS = "GNEWS";
    static final String ANSA = "ANSA";
    static final String RSS = "RSS";
    static final String GOLIARDIA = "GOLIARDIA";

    final String name;
    final String type;
    final String q;
    final List<String> items;   // only for GOLIARDIA

    Topic(String name, String type, String q) {
        this(name, type, q, null);
    }

    Topic(String name, String type, String q, List<String> items) {
        this.name = name;
        this.type = type;
        this.q = q;
        this.items = items == null ? new ArrayList<>() : items;
    }

    Feed toFeed() {
        switch (type) {
            case ANSA:
                return new Feed(name,
                        "https://www.ansa.it/sito/notizie/" + q + "/" + q + "_rss.xml", Sources.ANSA);
            case RSS:
                return new Feed(name, q, "RSS");
            case GOLIARDIA:
                return new Feed(name, "", GOLIARDIA);
            default:
                return new Feed(name, "https://news.google.com/rss/search?q="
                        + q.replace(" ", "%20") + "&hl=it&gl=IT&ceid=IT:it", Sources.GNEWS);
        }
    }
}
