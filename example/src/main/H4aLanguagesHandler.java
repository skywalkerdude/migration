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

    private final Map<H4aKey, ConvertedHymn> allHymns;
    private final Set<Set<H4aKey>> allReferenceSets;

    public static H4aLanguagesHandler create(Map<H4aKey, ConvertedHymn> allHymns) {
        return new H4aLanguagesHandler(allHymns);
    }

    private H4aLanguagesHandler(Map<H4aKey, ConvertedHymn> allHymns) {
        this.allHymns = allHymns;
        this.allReferenceSets = new LinkedHashSet<>();
    }

    public void handle(H4aKey currentKey) {
        Set<H4aKey> allLanguageRefs = new LinkedHashSet<>();
        Set<H4aKey> languages = extractLanguages(currentKey);
        languages.forEach(reference -> populate(reference, allLanguageRefs));

        // No references to add
        if (allLanguageRefs.isEmpty()) {
            return;
        }

        // Add itself. This won't create duplicates because we are working with a set.
        allLanguageRefs.add(currentKey);

        addToGlobalSet(allLanguageRefs);

//        // Add current hymn to the set of all references, so we can properly audit the entire graph
//        Set<H4aKey> hymnsToAudit = new HashSet<>(allLanguageRefs);
//        hymnsToAudit.add(currentKey);
//        audit(hymnsToAudit);
//
//        write();
    }

    /**
     * Looks up the hymn and extracts the language references from that hymn.
     */
    private Set<H4aKey> extractLanguages(H4aKey key) {
        ConvertedHymn currentHymn = allHymns.get(key);
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }
        Set<H4aKey> languages = new LinkedHashSet<>(currentHymn.languages);

        // Remove all BE_FILLED songs because those are too unpredictable (some are messed up, some are just duplicates,
        // and some have sketchy mappings)
        // Also remove all UNKNOWN songs
        languages.removeIf(language -> language.type() == HymnType.BE_FILLED || language.type() == HymnType.UNKNOWN);

        // Songs that show up in "related" column but don't actually exist in the h4a db. These should be ignored since
        // they map to nothing.
        List<String> ignore =
                Arrays.asList(
                        "C825", "C914", "C912", "C389", "C834", "T898", "C815", "C486", "C806", "C905",
                        "BF1040", "C856", "C812", "C810", "C850", "C901", "C517c", "C510c", "C513c", "CB57",
                        "C925", "C917", "C840", "CS352", "CS158", "CB1360", "C506c", "CB381", "CS46", "C481c",
                        "CS9117", "CS46", "CS400");
        languages.removeIf(language -> ignore.contains(language.id));

        // Replace SPANISH_MISTYPED references with SPANISH
        languages = languages.stream().map(language -> {
            if (language.type().equals(HymnType.SPANISH_MISTYPED)) {
                return new H4aKey(HymnType.SPANISH, language.number());
            }
            return language;
        }).collect(Collectors.toSet());

        // Legitimate songs, but aren't actually related to anything. Therefore, they should be removed from other
        // songs' "languages" mappings.
        languages.removeIf(language -> language.id.equals("C485") || language.id.equals("NS6"));

        // C426 is a legitimate song, but it's mapping in NS577 is wrong. It should be CS426. However, this is covered
        // by the hymnal db, so we can safely ignore this.
        languages.removeIf(language -> language.id.equals("C426"));

        // C305 is a legitimate song, but it's mapping in BF84 is wrong. It should be CS305. However, this is covered by
        // the hymnal db, so we can safely ignore this.
        // TODO
//        languages.removeIf(language -> language.id.equals("C305"));

        // Tagalog songs > T1360 are often times just repeats of their English counterpart or with small insignificant
        // changes. Even when they're not repeats, they're not very useful songs. Therefore, we just ignore them.
        languages.removeIf(language -> language.type().equals(HymnType.TAGALOG) && Integer.parseInt(language.number()) > 1360);
        return languages;
    }

    /**
     * Traverses the entire language graph and lists all songs found during traversal.
     */
    private void populate(H4aKey key, Set<H4aKey> allLanguageRefs) {
        if (key.type() == HymnType.UNKNOWN) {
            return;
        }
        // Correct mistyped spanish songs.
        if (key.type() == HymnType.SPANISH_MISTYPED) {
            String number = key.number();
            key = new H4aKey("S" + number);
        }
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in h4a db", key));
        }
        if (allLanguageRefs.contains(key)) {
            return;
        }
        allLanguageRefs.add(key);

//        extractParent(key).ifPresent(parent -> populate(parent, allLanguageRefs));
        extractLanguages(key).forEach(language -> populate(language, allLanguageRefs));
    }

    /**
     * Writes the language refs to the global set of all languages.
     */
    private void addToGlobalSet(Set<H4aKey> languageRefs) {
        Set<Set<H4aKey>> matched = new LinkedHashSet<>();
        for (H4aKey language : languageRefs) {
            for (Set<H4aKey> key : allReferenceSets) {
                if (key.contains(language)) {
                    matched.add(key);
                }
            }
        }
        // Did not match anything in the current global set, so we should add it.
        if (matched.isEmpty()) {
            allReferenceSets.add(languageRefs);
        } else if (matched.size() == 1) {
            matched.stream().findFirst().get().addAll(languageRefs);
        } else {
            // Each language reference should be in its unique set. If there are multiple matching sets, then something
            // is wrong.
            throw new IllegalArgumentException(languageRefs + " was not in a unique set, but was in " + matched);
        }
    }

    /**
     * Looks up the hymn and extracts the parent of that hymn.
     */
    private Optional<H4aKey> extractParent(H4aKey key) {
        ConvertedHymn currentHymn = allHymns.get(key);
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }

        Optional<H4aKey> parentHymn = Optional.ofNullable(currentHymn.parentHymn);
        // Ignore BE_FILLED songs because those are too unpredictable (some are messed up, some are just duplicates, and
        // some have sketchy mappings)
        if (parentHymn.isPresent() && parentHymn.get().type() == HymnType.BE_FILLED) {
            return Optional.empty();
        }
        return parentHymn;
    }

    /**
     * German songs in H4A has incorrect numbering compared to the German songs in hymnal db. Therefore, we need some
     * way to reconcile them.
     */
    public void fixGermanSongs() {
        boolean somethingChanged;
        do {
            somethingChanged = false;
            for (H4aKey h4aKey : new HashSet<>(allHymns.keySet())) {
                // if not a german song, continue
                if (h4aKey.type() != HymnType.GERMAN) {
                    continue;
                }

                ConvertedHymn h4aHymn = allHymns.get(h4aKey);
                // if it doesn't have a parent hymn, then there's no way to reconcile the numbering
                if (h4aHymn.parentHymn == null) {
                    continue;
                }

                String h4aNumber = h4aKey.number();
                HymnType parentType = h4aHymn.parentHymn.type();
                String parentNumber = h4aHymn.parentHymn.number();

                // number is already correct
                if (parentType == HymnType.CLASSIC_HYMN && h4aNumber.equals(parentNumber)) {
                    continue;
                }

                if (parentType == HymnType.NEW_SONG && h4aNumber.equals(parentNumber + "de")) {
                    continue;
                }

                final H4aKey newId;
                if (parentType == HymnType.CLASSIC_HYMN) {
                    newId = new H4aKey("G" + parentNumber);
                } else if (parentType == HymnType.NEW_SONG) {
                    newId = new H4aKey("G" + parentNumber + "de");
                } else {
                    throw new IllegalArgumentException("Parent hymn is not classic or new song");
                }

                if (allHymns.containsKey(newId)) {
                    H4aKey tempId = new H4aKey(h4aKey + "temp");
                    allHymns.get(h4aHymn.parentHymn).languages.remove(h4aKey);
                    allHymns.get(h4aHymn.parentHymn).languages.add(tempId);
                    allHymns.remove(h4aKey);
                    allHymns.put(tempId, h4aHymn);
                    somethingChanged = true;
                } else {
                    allHymns.get(h4aHymn.parentHymn).languages.remove(h4aKey);
                    allHymns.get(h4aHymn.parentHymn).languages.add(newId);
                    allHymns.remove(h4aKey);
                    allHymns.put(newId, h4aHymn);
                    somethingChanged = true;
                }
            }
        } while (somethingChanged);
    }

    /**
     * Audits {@link #allReferenceSets} to see if there are conflicting sets.
     */
    public void auditGlobalLanguagesSet() {
        for (Set<H4aKey> languageSet : allReferenceSets) {
            auditLanguageSet(languageSet);
        }
    }

    /**
     * Audit set of {@link H4aKey}s to see if there are conflicting types.
     */
    private void auditLanguageSet(Set<H4aKey> setToAudit) {
        if (setToAudit.size() == 1) {
            throw new IllegalArgumentException("Language set with only 1 key is a dangling reference, which needs fixing: " + setToAudit);
        }

        // Extract the hymn types for audit.
        Set<HymnType> hymnTypes = setToAudit.stream().map(H4aKey::type).collect(Collectors.toSet());

        // Verify that the same hymn type doesn't appear more than the allowed number of times the languages list.
        for (HymnType hymnType : HymnType.values()) {
            int timesAllowed = 1;
            if (hymnType == HymnType.CHINESE || hymnType == HymnType.CHINESE_SUPPLEMENT) {
                timesAllowed = 2;
            }

            // If the current set includes an exception group, then remove that exception group from the list and audit
            // again.
            for (Set<H4aKey> exception : H4A_DB_LANGUAGES_EXCEPTIONS) {
                if (setToAudit.containsAll(exception)) {
                    if (!setToAudit.removeAll(exception)) {
                        throw new IllegalArgumentException(exception + " was unable to be removed from " + setToAudit);
                    }
                    auditLanguageSet(setToAudit);
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
}
