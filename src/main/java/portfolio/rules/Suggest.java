package portfolio.rules;

import java.util.Collection;
import java.util.Locale;

/** "Did you mean" suggestions by edit distance. */
final class Suggest {
    private Suggest() {
    }

    /** Closest candidate within a small edit distance, or null. */
    static String closest(String word, Collection<String> candidates) {
        String best = null;
        int limit = Math.max(1, Math.min(3, word.length() / 3 + 1));
        int bestDistance = Integer.MAX_VALUE;
        for (String c : candidates) {
            int d = distance(word.toLowerCase(Locale.ROOT), c.toLowerCase(Locale.ROOT));
            if (d <= limit && d < bestDistance) {
                best = c;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Edit distance where swapping two neighbouring letters counts as one edit (Damerau). */
    static int distance(String a, String b) {
        if (a.length() > 64 || b.length() > 64) {
            return Integer.MAX_VALUE / 2;
        }
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
