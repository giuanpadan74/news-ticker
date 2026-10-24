package io.github.newsticker;

/**
 * A user-created category: a name plus where its news come from — a Google News query,
 * an ANSA site section slug, or a raw RSS URL. Converts into a regular Feed for the pipeline.
 */
final class Topic {
    static final String GNEWS = "GNEWS";
    static final String ANSA = "ANSA";
    static final String RSS = "RSS";

    final String name;
    final String type;
    final String q;

    Topic(String name, String type, String q) {
        this.name = name;
        this.type = type;
        this.q = q;
    }

    Feed toFeed() {
        switch (type) {
            case ANSA:
                return new Feed(name,
                        "https://www.ansa.it/sito/notizie/" + q + "/" + q + "_rss.xml", Sources.ANSA);
            case RSS:
                return new Feed(name, q, "RSS");
            default:
                return new Feed(name, "https://news.google.com/rss/search?q="
                        + q.replace(" ", "%20") + "&hl=it&gl=IT&ceid=IT:it", Sources.GNEWS);
        }
    }
}
