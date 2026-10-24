package io.github.newsticker;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalog of every feed the user can pick from, grouped by source (ANSA general feeds,
 * Google News topic queries). Which ones run and with what weight lives in Prefs.
 */
final class Sources {
    static final String ANSA = "ANSA";
    static final String GNEWS = "Google News";

    private static Feed ansa(String name, String slug) {
        return new Feed(name,
                "https://www.ansa.it/sito/notizie/" + slug + "/" + slug + "_rss.xml", ANSA);
    }

    private static Feed gnews(String query) {
        return new Feed(query,
                "https://news.google.com/rss/search?q=" + query.replace(" ", "%20")
                        + "&hl=it&gl=IT&ceid=IT:it", GNEWS);
    }

    /** Every known feed, in stable order (also drives the topic colors). */
    static final Feed[] ALL = {
            ansa("Prima pagina", "topnews"),
            ansa("Cronaca",      "cronaca"),
            ansa("Politica",     "politica"),
            ansa("Economia",     "economia"),
            ansa("Mondo",        "mondo"),
            ansa("Sport",        "sport"),
            ansa("Cultura",      "cultura"),
            ansa("Scienza",      "scienza"),
            gnews("Juventus"),
            gnews("Formula 1"),
            gnews("MotoGP"),
            gnews("Tennis"),
            gnews("Ciclismo"),
            gnews("Basket"),
            gnews("Calciomercato"),
            gnews("Cinema"),
            gnews("Musica"),
            gnews("Videogiochi"),
    };

    static Feed byName(String name) {
        for (Feed f : ALL) if (f.name.equals(name)) return f;
        return null;
    }

    /** Breaking-news line source (ANSA "Ultima ora"); only fetched in two-line mode. */
    static final Feed BREAKING = ansa("Ultim'ora", "topnews");

    /** The 18 built-in feeds as editable topics; persisted once, then user-owned. */
    static List<Topic> seedTopics() {
        List<Topic> out = new ArrayList<>();
        for (Feed f : ALL) {
            if (ANSA.equals(f.source)) {
                String[] parts = f.url.split("/");
                out.add(new Topic(f.name, Topic.ANSA, parts[parts.length - 2]));
            } else {
                out.add(new Topic(f.name, Topic.GNEWS,
                        android.net.Uri.parse(f.url).getQueryParameter("q")));
            }
        }
        return out;
    }

    /** Full-width bullet between titles. */
    static final String SEP = "     \u2022     ";

    private Sources() {}
}
