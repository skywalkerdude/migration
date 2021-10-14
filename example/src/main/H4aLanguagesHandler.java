package main;

import models.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Populates, audits, and makes a dense graph of all the language references in the H4A db
 */
public class H4aLanguagesHandler {

    public static final Set<Set<H4aKey>> H4A_DB_LANGUAGES_EXCEPTIONS = new LinkedHashSet<>();

    static {
    }

    private final H4aKey currentKey;
    private final Set<H4aKey> allLanguageRefs;
    private final Map<H4aKey, ConvertedHymn> allHymns;

    public static H4aLanguagesHandler create(H4aKey currentKey, Map<H4aKey, ConvertedHymn> allHymns) {
        return new H4aLanguagesHandler(currentKey, allHymns);
    }

    private H4aLanguagesHandler(H4aKey currentKey, Map<H4aKey, ConvertedHymn> allHymns) {
        this.currentKey = currentKey;
        this.allHymns = allHymns;
        this.allLanguageRefs = new LinkedHashSet<>();
    }

    public void handle() {
        List<H4aKey> languages = extractLanguages(currentKey);
        for (H4aKey language : languages) {
            populate(language);
        }

        // Add current hymn to the set of all references, so we can properly audit the entire graph
        Set<H4aKey> hymnsToAudit = new HashSet<>(allLanguageRefs);
        hymnsToAudit.add(currentKey);
        audit(hymnsToAudit);

        write();
    }

    /**
     * Traverses the entire language graph and lists all songs found during traversal.
     */
    private void populate(H4aKey key) {
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }
        // Don't add current hymn into language map.
        if (currentKey.equals(key)) {
            return;
        }
        if (allLanguageRefs.contains(key)) {
            return;
        }

        allLanguageRefs.add(key);

        List<H4aKey> languages = extractLanguages(key);
        for (H4aKey language : languages) {
            populate(language);
        }
    }

    /**
     * Looks up the hymn and extracts the language references from that hymn.
     */
    private List<H4aKey> extractLanguages(H4aKey key) {
        ConvertedHymn currentHymn = allHymns.get(key);
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }

        List<H4aKey> languages = currentHymn.languages;
        if (languages == null) {
            return new ArrayList<>();
        }
        return languages;
    }

    /**
     * Audit set of {@link Reference}s to see if there are conflicting types.
     */
    private void audit(Set<H4aKey> setToAudit) {
        // Extract the hymn types for audit.
        List<HymnType> hymnTypes =
                setToAudit
                        .stream()
                        .map(language -> language.toHymnalDbKey().hymnType)
                        .collect(Collectors.toList());

        // Verify that the same hymn type doesn't appear more than the allowed number of times the languages list.
        for (HymnType hymnType : HymnType.values()) {
            int timesAllowed = 1;
            if (hymnType == HymnType.CHINESE || hymnType == HymnType.CHINESE_SUPPLEMENT) {
                timesAllowed = 2;
            }
            // For each song like h225b or ns92f, increment the allowance of that type of hymn, since those are valid
            // alternates.
            if ((hymnType == HymnType.CLASSIC_HYMN || hymnType == HymnType.NEW_SONG || hymnType == HymnType.HOWARD_HIGASHI)) {
                for (H4aKey h4aKey : setToAudit) {
                    HymnalDbKey key = h4aKey.toHymnalDbKey();
                    if (key.hymnType == hymnType && key.hymnNumber.matches("(\\D+\\d+\\D*)|(\\D*\\d+\\D+)")) {
                        timesAllowed++;
                    }
                }
            }

            // If the current set includes an exception group, then remove that exception group from the list and audit
            // again.
            for (Set<H4aKey> exception : H4A_DB_LANGUAGES_EXCEPTIONS) {
                if (setToAudit.containsAll(exception)) {
                    if (!setToAudit.removeAll(exception)) {
                        throw new IllegalArgumentException(exception + " was unable to be removed from " + setToAudit);
                    }
                    audit(setToAudit);
                    return;
                }
            }

            if (Collections.frequency(hymnTypes, hymnType) > timesAllowed) {
                throw new IllegalArgumentException(
                        String.format("%s has too many instances of %s", setToAudit, hymnType));
            }
        }

        // Verify that incompatible hymn types don't appear together the languages list.
        if ((hymnTypes.contains(HymnType.CLASSIC_HYMN) && hymnTypes.contains(HymnType.NEW_SONG))
                || (hymnTypes.contains(HymnType.CLASSIC_HYMN) && hymnTypes.contains(HymnType.CHILDREN_SONG))
                || hymnTypes.contains(HymnType.CHILDREN_SONG) && hymnTypes.contains(HymnType.NEW_SONG)
                || hymnTypes.contains(HymnType.CHINESE) && hymnTypes.contains(HymnType.CHINESE_SUPPLEMENT)) {
            throw new IllegalArgumentException(
                    String.format("%s has incompatible languages types", setToAudit));
        }
    }

    /**
     * Writes the set of {@link #allLanguageRefs} into the database entry for {@link #currentKey}.
     */
    private void write() {
    }
}
