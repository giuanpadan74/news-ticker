package io.github.newsticker;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Downloads one RSS file and pulls the item titles out of it. */
final class RssFetcher {
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 11;) Android-TV news-ticker/0.2";

    static List<String> titles(String urlStr, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/rss+xml, text/xml, */*");
        c.setRequestProperty("Accept-Encoding", "identity");
        try {
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303) return titles(c.getHeaderField("Location"), max);
            InputStream in = code / 100 == 2
                    ? c.getInputStream()
                    : c.getErrorStream();
            if (in == null) throw new IllegalStateException("HTTP " + code);
            return parse(in, max);
        } finally {
            c.disconnect();
        }
    }

    static List<String> parse(InputStream in, int max) throws Exception {
        XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
        p.setInput(in, null);
        List<String> out = new ArrayList<>();
        boolean inItem = false;
        boolean inTitle = false;
        StringBuilder sb = null;
        int t = p.getEventType();
        while (t != XmlPullParser.END_DOCUMENT && out.size() < max) {
            if (t == XmlPullParser.START_TAG) {
                String n = p.getName();
                if ("item".equals(n) || "entry".equals(n)) {
                    inItem = true;
                    inTitle = false;
                    sb = null;
                } else if (inItem && "title".equals(n)) {
                    inTitle = true;
                    sb = new StringBuilder();
                }
            } else if (t == XmlPullParser.TEXT) {
                if (inTitle && sb != null) sb.append(p.getText());
            } else if (t == XmlPullParser.END_TAG) {
                String n = p.getName();
                if ("item".equals(n) || "entry".equals(n)) {
                    inItem = false;
                } else if (inTitle && "title".equals(n)) {
                    String s = sb == null ? "" : sb.toString().trim();
                    if (!s.isEmpty()) out.add(s);
                    inTitle = false;
                    sb = null;
                }
            }
            t = p.next();
        }
        return out;
    }

    private RssFetcher() {}
}
