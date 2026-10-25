package io.github.newsticker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the service obeys: on/off, where the bar shows, which feeds are on and
 * each feed's raw weight. Selection is stored as a JSON array of feed names; the
 * percent a user sees is always a feed's weight relative to the sum of all weights.
 */
final class Prefs {
    /** The seven feeds of v0.1, on by default so an upgrade keeps the old mix. */
    private static final String DEFAULT_SEL =
            "[\"Cronaca\",\"Politica\",\"Sport\",\"Juventus\",\"Formula 1\",\"MotoGP\",\"Tennis\"]";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("tick", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", false);
    }

    static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("enabled", v).apply();
    }

    /** "always" shows the bar everywhere; "home" only on launchers. */
    static String mode(Context c) {
        return sp(c).getString("mode", "always");
    }

    static void setMode(Context c, String v) {
        sp(c).edit().putString("mode", v).apply();
    }

    /** Slides the bar away while the TV renders audio/video; default on. */
    static boolean autohide(Context c) {
        return sp(c).getBoolean("autohide", true);
    }

    static void setAutohide(Context c, boolean v) {
        sp(c).edit().putBoolean("autohide", v).apply();
    }

    /** Bar pinned to the top or the bottom edge; default bottom. */
    static boolean atTop(Context c) {
        return sp(c).getBoolean("attop", false);
    }

    static void setAtTop(Context c, boolean v) {
        sp(c).edit().putBoolean("attop", v).apply();
    }

    // ---------- topic selection & weights ----------

    /** Names of the enabled feeds, in catalog order; falls back to defaults when empty. */
    static List<String> selection(Context c) {
        List<String> out = readSel(sp(c).getString("sel", DEFAULT_SEL));
        if (out.isEmpty()) out = readSel(DEFAULT_SEL);
        return out;
    }

    static void setSelection(Context c, List<String> names) {
        JSONArray a = new JSONArray();
        for (String n : names) a.put(n);
        sp(c).edit().putString("sel", a.toString()).apply();
    }

    private static List<String> readSel(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    /** Raw weight of one feed (1..100, default 10); the shown percent is its share of the sum. */
    static int weight(Context c, String name) {
        return sp(c).getInt("w." + name, 10);
    }

    static void setWeight(Context c, String name, int v) {
        sp(c).edit().putInt("w." + name, Math.max(1, Math.min(100, v))).apply();
    }

    /** 1 = single mixed line; 2 = ULTIM'ORA strip on top + mixed line below. */
    static int lines(Context c) {
        return sp(c).getInt("lines", 1);
    }

    static void setLines(Context c, int v) {
        sp(c).edit().putInt("lines", Math.max(1, Math.min(2, v))).apply();
    }

    /** Bar skin index, see TickerView.STYLE_NAMES. */
    static int styleIndex(Context c) {
        return sp(c).getInt("style", 0);
    }

    static void setStyleIndex(Context c, int v) {
        sp(c).edit().putInt("style", v).apply();
    }

    /** Enabled feeds resolved against the full editable catalog. */
    static List<Feed> activeFeeds(Context c) {
        List<String> sel = selection(c);
        List<Feed> all = allFeeds(c);
        List<Feed> out = new ArrayList<>(sel.size());
        for (Feed f : all) if (sel.contains(f.name)) out.add(f);
        return out;
    }

    /** Every category as a feed: built-ins migrated once to the editable "cats" store. */
    static List<Feed> allFeeds(Context c) {
        List<Feed> out = new ArrayList<>();
        for (Topic t : allTopics(c)) out.add(t.toFeed());
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));   // home sempre in ordine alfabetico
        return out;
    }

    /** The joke headlines of one GOLIARDIA category (empty when missing or other type). */
    static List<String> goliardiaTitles(Context c, String categoryName) {
        for (Topic t : allTopics(c)) {
            if (t.name.equals(categoryName) && Topic.GOLIARDIA.equals(t.type)) return t.items;
        }
        return new ArrayList<>();
    }

    // ---------- editable catalog ----------

    /** All categories; first run seeds the 18 built-ins, then the user owns them. */
    static List<Topic> allTopics(Context c) {
        String raw = sp(c).getString("cats", null);
        if (raw == null) {
            List<Topic> seed = Sources.seedTopics();
            setAllTopics(c, seed);
            return seed;
        }
        List<Topic> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Topic t = new Topic(o.getString("n"), o.optString("t", Topic.GNEWS), o.optString("q"));
                JSONArray items = o.optJSONArray("i");
                if (items != null) {
                    for (int j = 0; j < items.length(); j++) t.items.add(items.getString(j));
                }
                out.add(t);
            }
        } catch (Exception ignored) {}
        if (out.isEmpty()) {
            out = Sources.seedTopics();
            setAllTopics(c, out);
        }
        return out;
    }

    static void setAllTopics(Context c, List<Topic> ts) {
        JSONArray a = new JSONArray();
        try {
            for (Topic t : ts) {
                JSONObject o = new JSONObject();
                o.put("n", t.name);
                o.put("t", t.type);
                o.put("q", t.q);
                if (Topic.GOLIARDIA.equals(t.type)) {
                    JSONArray items = new JSONArray();
                    for (String s : t.items) items.put(s);
                    o.put("i", items);
                }
                a.put(o);
            }
        } catch (Exception ignored) {}
        sp(c).edit().putString("cats", a.toString()).apply();
    }

    // ---------- italian rss directory ----------

    /** Cached directory of Italian feeds; empty = use the built-in list. */
    static List<String[]> rssDirectory(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("rssdir", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.getString("n"), o.getString("u")});
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void setRssDirectory(Context c, List<String[]> pairs) {
        JSONArray a = new JSONArray();
        try {
            for (String[] p : pairs) {
                JSONObject o = new JSONObject();
                o.put("n", p[0]);
                o.put("u", p[1]);
                a.put(o);
            }
        } catch (Exception ignored) {}
        sp(c).edit().putString("rssdir", a.toString()).apply();
    }

    // ---------- user-verified rss feeds ----------

    /** Feeds the app itself verified while saving an RSS category; separate from the
        remote "rssdir" cache so the gist refresh never wipes them out. */
    static List<String[]> rssUser(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("rssuser", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.getString("n"), o.getString("u")});
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** Adds (or renames, when the URL is already there) one user-verified feed. */
    static void addRssUser(Context c, String name, String url) {
        List<String[]> cur = rssUser(c);
        String key = url.toLowerCase(Locale.ROOT).trim();
        List<String[]> out = new ArrayList<>();
        boolean replaced = false;
        for (String[] p : cur) {
            if (p[1].toLowerCase(Locale.ROOT).trim().equals(key)) {
                out.add(new String[]{name, url});
                replaced = true;
            } else {
                out.add(p);
            }
        }
        if (!replaced) out.add(new String[]{name, url});
        JSONArray a = new JSONArray();
        try {
            for (String[] p : out) {
                JSONObject o = new JSONObject();
                o.put("n", p[0]);
                o.put("u", p[1]);
                a.put(o);
            }
        } catch (Exception ignored) {}
        sp(c).edit().putString("rssuser", a.toString()).apply();
    }

    /** Removes one user source (matched by URL). */
    static void removeRssUser(Context c, String url) {
        String key = url.toLowerCase(Locale.ROOT).trim();
        JSONArray a = new JSONArray();
        for (String[] p : rssUser(c)) {
            if (p[1].toLowerCase(Locale.ROOT).trim().equals(key)) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("n", p[0]);
                o.put("u", p[1]);
            } catch (Exception ignored) {}
            a.put(o);
        }
        sp(c).edit().putString("rssuser", a.toString()).apply();
    }

    // ---------- rss sources the user explicitly removed ("‹ elimina") ----------

    /** URLs hidden from the combined directory; survives the gist refresh and upgrades. */
    static List<String> rssHidden(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("rsshidden", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {}
        return out;
    }

    static void addRssHidden(Context c, String url) {
        String key = url.toLowerCase(Locale.ROOT).trim();
        JSONArray a = new JSONArray();
        boolean seen = false;
        for (String s : rssHidden(c)) {
            if (s.toLowerCase(Locale.ROOT).trim().equals(key)) seen = true;
            a.put(s);
        }
        if (!seen) a.put(key);
        sp(c).edit().putString("rsshidden", a.toString()).apply();
    }

    static void removeRssHidden(Context c, String url) {
        String key = url.toLowerCase(Locale.ROOT).trim();
        JSONArray a = new JSONArray();
        for (String s : rssHidden(c)) {
            if (!s.toLowerCase(Locale.ROOT).trim().equals(key)) a.put(s);
        }
        sp(c).edit().putString("rsshidden", a.toString()).apply();
    }

    /** Percent share of every enabled feed: weights normalized so the total is 100. */
    static Map<String, Integer> shares(Context c) {
        List<String> sel = selection(c);
        Map<String, Integer> out = new HashMap<>();
        int total = 0;
        for (String n : sel) total += weight(c, n);
        if (total == 0) return out;
        int rounded = 0;
        for (String n : sel) {
            int p = Math.round(weight(c, n) * 100f / total);
            out.put(n, p);
            rounded += p;
        }
        // give the rounding difference to the biggest feed so the pie always sums to 100
        if (rounded != 100 && !sel.isEmpty()) {
            String best = sel.get(0);
            for (String n : sel) if (weight(c, n) > weight(c, best)) best = n;
            out.put(best, out.get(best) + (100 - rounded));
        }
        return out;
    }

    private Prefs() {}
}
