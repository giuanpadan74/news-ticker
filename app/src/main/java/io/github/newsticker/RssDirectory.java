package io.github.newsticker;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Curated list of main Italian RSS feeds. The built-ins are all verified reachable; a
 * remote JSON ([{"n":"Nome","u":"https://..."}]) can refresh the list every time the app
 * opens — put its URL in DIR_URL. Failures are silent: the last cached or built-in list
 * keeps working offline.
 */
final class RssDirectory {
    /** Where the refreshed list comes from; empty = built-ins only. */
    static final String DIR_URL =
            "https://gist.githubusercontent.com/giuanpadan74/ecbf1b969e17947fa47dee3d1f791c55/raw/rss_italia.json";

    static final String[][] BUILT_IN = {
            {"ANSA Ultima ora",       "https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml"},
            {"Sky TG24",              "https://tg24.sky.it/rss/tg24.xml"},
            {"Corriere della Sera",   "https://xml2.corriereobjects.it/rss/homepage.xml"},
            {"la Repubblica",         "https://www.repubblica.it/rss/homepage/rss2.0.xml"},
            {"Gazzetta dello Sport",  "https://www.gazzetta.it/rss/homepage.xml"},
            {"TgCom24",               "https://www.tgcom24.mediaset.it/rss/homepage.xml"},
            {"Il Fatto Quotidiano",   "https://www.ilfattoquotidiano.it/feed/"},
            {"Il Sole 24 Ore",        "https://www.ilsole24ore.com/rss/italia.xml"},
            {"Wired Italia",          "https://www.wired.it/feed/rss"},
            {"HDblog",                "https://www.hdblog.it/rss/"},
            {"DDay.it",               "https://www.dday.it/rss"},
            {"Formiche",              "https://formiche.net/feed/"},
    };

    private RssDirectory() {}

    /** Downloads and validates the remote directory; throws on any problem. */
    static List<String[]> fetchRemote() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(DIR_URL).openConnection();
        c.setConnectTimeout(8_000);
        c.setReadTimeout(10_000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() / 100 != 2) throw new IllegalStateException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            StringBuilder sb = new StringBuilder(4096);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            List<String[]> out = new ArrayList<>();
            JSONArray a = new JSONArray(sb.toString());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String n = o.optString("n").trim();
                String u = o.optString("u").trim();
                if (!n.isEmpty() && u.startsWith("http")) out.add(new String[]{n, u});
            }
            return out;
        } finally {
            c.disconnect();
        }
    }
}
