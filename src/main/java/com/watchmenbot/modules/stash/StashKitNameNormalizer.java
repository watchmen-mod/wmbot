package com.watchmenbot.modules.stash;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class StashKitNameNormalizer {
    private static final int CACHE_LIMIT = 512;
    private static final Pattern MINECRAFT_FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");
    private static final Pattern POSSESSIVE = Pattern.compile("'s\\b");
    private static final Pattern MARKS_AND_CONTROLS = Pattern.compile("[\\p{M}\\p{C}]");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern NON_CANONICAL_WORD = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}']+");
    private static final Pattern NON_POSSESSIVE_APOSTROPHE = Pattern.compile("'(?!s\\b)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<CacheKey, String> CACHE = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, String> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private StashKitNameNormalizer() {
    }

    static String alias(String value) {
        return alias(value, true);
    }

    static String phraseAlias(String value) {
        return canonicalAlias(value);
    }

    static String canonicalAlias(String value) {
        return canonicalAlias(value, false);
    }

    private static String alias(String value, boolean dropWrapperWords) {
        if (value == null || value.isBlank()) return "";

        CacheKey key = new CacheKey(value, dropWrapperWords);
        synchronized (CACHE) {
            String cached = CACHE.get(key);
            if (cached != null) return cached;
        }

        String normalized = normalizedWords(value, false);

        if (normalized.isEmpty()) return cache(key, "");
        if (!dropWrapperWords) return cache(key, normalized);

        List<String> words = new ArrayList<>(List.of(normalized.split(" ")));
        words.removeIf(word -> word.equals("the") || word.equals("kit"));
        if (words.isEmpty()) return cache(key, normalized);

        return cache(key, String.join(" ", words));
    }

    static boolean matches(String name, String query) {
        return matches(name, query, false);
    }

    static boolean matches(String name, String query, boolean quotedSearch) {
        List<String> nameAliases = quotedSearch ? phraseAliases(name) : List.of(alias(name));
        List<String> queryAliases = quotedSearch ? queryPhraseAliases(query) : List.of(alias(query));
        for (String nameAlias : nameAliases) {
            if (nameAlias.isEmpty()) continue;

            for (String queryAlias : queryAliases) {
                if (!queryAlias.isEmpty() && nameAlias.contains(queryAlias)) return true;
            }
        }

        return false;
    }

    static boolean matchesExactPhrase(String name, String query) {
        List<String> nameAliases = phraseAliases(name);
        List<String> queryAliases = queryPhraseAliases(query);
        for (String nameAlias : nameAliases) {
            if (nameAlias.isEmpty()) continue;

            for (String queryAlias : queryAliases) {
                if (!queryAlias.isEmpty() && nameAlias.equals(queryAlias)) return true;
            }
        }

        return false;
    }

    static boolean matchesAliasKey(String name, String aliasKey) {
        String loose = alias(name);
        String canonical = canonicalAlias(name);
        String depossessed = canonicalAlias(name, true);
        return loose.equals(aliasKey) || canonical.equals(aliasKey) || depossessed.equals(aliasKey);
    }

    private static String canonicalAlias(String value, boolean removePossessive) {
        if (value == null || value.isBlank()) return "";

        CacheKey key = new CacheKey(value, removePossessive ? CacheMode.DEPOSSESSED_CANONICAL : CacheMode.CANONICAL);
        synchronized (CACHE) {
            String cached = CACHE.get(key);
            if (cached != null) return cached;
        }

        return cache(key, normalizedWords(value, !removePossessive));
    }

    private static List<String> phraseAliases(String value) {
        String canonical = canonicalAlias(value);
        String depossessed = canonicalAlias(value, true);
        if (canonical.equals(depossessed)) return List.of(canonical);
        return List.of(canonical, depossessed);
    }

    private static List<String> queryPhraseAliases(String value) {
        String canonical = canonicalAlias(value);
        if (canonical.contains("'s")) return List.of(canonical);

        String depossessed = canonicalAlias(value, true);
        if (canonical.equals(depossessed)) return List.of(canonical);
        return List.of(canonical, depossessed);
    }

    private static String normalizedWords(String value, boolean keepPossessive) {
        String normalized = Normalizer.normalize(decorativeAsciiFallback(value), Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replace('\u2019', '\'');

        normalized = MINECRAFT_FORMATTING.matcher(normalized).replaceAll(" ");
        if (!keepPossessive) normalized = POSSESSIVE.matcher(normalized).replaceAll(" ");
        normalized = MARKS_AND_CONTROLS.matcher(normalized).replaceAll(" ");
        normalized = keepPossessive
            ? NON_CANONICAL_WORD.matcher(normalized).replaceAll(" ")
            : NON_WORD.matcher(normalized).replaceAll(" ");
        if (keepPossessive) normalized = NON_POSSESSIVE_APOSTROPHE.matcher(normalized).replaceAll(" ");

        return WHITESPACE.matcher(normalized.trim()).replaceAll(" ");
    }

    private static String decorativeAsciiFallback(String value) {
        StringBuilder builder = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            int ascii = enclosedCapitalLetter(codePoint);
            if (ascii >= 0) builder.append((char) ascii);
            else builder.appendCodePoint(codePoint);
        });
        return builder.toString();
    }

    private static int enclosedCapitalLetter(int codePoint) {
        if (codePoint >= 0x1F130 && codePoint <= 0x1F149) return 'A' + (codePoint - 0x1F130);
        if (codePoint >= 0x1F150 && codePoint <= 0x1F169) return 'A' + (codePoint - 0x1F150);
        if (codePoint >= 0x1F170 && codePoint <= 0x1F189) return 'A' + (codePoint - 0x1F170);
        return -1;
    }

    private static String cache(CacheKey key, String value) {
        synchronized (CACHE) {
            CACHE.put(key, value);
        }
        return value;
    }

    private record CacheKey(String value, CacheMode mode) {
        private CacheKey(String value, boolean dropWrapperWords) {
            this(value, dropWrapperWords ? CacheMode.LOOSE : CacheMode.DEPOSSESSED_CANONICAL);
        }
    }

    private enum CacheMode {
        LOOSE,
        CANONICAL,
        DEPOSSESSED_CANONICAL
    }
}
