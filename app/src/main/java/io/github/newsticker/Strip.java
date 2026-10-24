package io.github.newsticker;

import java.util.List;

/**
 * Builds the strip content: every enabled feed contributes titles in proportion to its
 * weight, using a smooth weighted round robin so big feeds come back more often while
 * every feed keeps its own title order and no feed monopolizes a stretch of the bar.
 */
final class Strip {
    /** Cap on total titles in one loop so a fat config can't grow the strip forever. */
    private static final int MAX_TOTAL = 60;

    static CharSequence join(List<Feed> feeds, List<Integer> weights, List<List<String>> byFeed) {        int n = feeds.size();
        if (n == 0) return "";

        int wsum = 0;
        for (int w : weights) wsum += Math.max(1, w);
        int[] quota = new int[n];          // titles each feed may contribute this loop
        int used = 0;
        for (int i = 0; i < n; i++) {
            List<String> titles = byFeed.get(i);
            int have = titles == null ? 0 : titles.size();
            quota[i] = Math.min(have, Math.max(1, Math.round(weights.get(i) * (float) MAX_TOTAL / wsum)));
            used += quota[i];
        }

        StringBuilder sb = new StringBuilder(4096);
        int[] left = quota.clone();
        int[] taken = new int[n];
        float[] credit = new float[n];     // smooth RR: +weight every round, -1 per emit
        int emitted = 0;
        while (emitted < used) {
            int best = -1;
            float bestVal = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (left[i] == 0) continue;
                credit[i] += Math.max(1, weights.get(i)) / (float) wsum;
                if (credit[i] > bestVal) {
                    bestVal = credit[i];
                    best = i;
                }
            }
            if (best < 0) break;
            credit[best] -= 1f;
            String t = byFeed.get(best).get(taken[best]++);
            if (sb.length() > 0) sb.append(Sources.SEP);
            sb.append(t);
            left[best]--;
            emitted++;
        }
        return sb;
    }

    /** Plain join for the breaking line: titles with the separator, no weighting. */
    static CharSequence joinSimple(List<String> titles) {
        StringBuilder sb = new StringBuilder(1024);
        for (String t : titles) {
            if (sb.length() > 0) sb.append(Sources.SEP);
            sb.append(t);
        }
        return sb;
    }

    private Strip() {}
}
