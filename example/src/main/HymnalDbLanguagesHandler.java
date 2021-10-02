package main;

import com.google.gson.Gson;
import com.tylersuehr.sql.ContentValues;
import models.*;
import repositories.DatabaseClient;

import java.util.*;
import java.util.stream.Collectors;

import static main.Main.DRY_RUN;
import static main.Main.LOG;

/**
 * Populates, audits, and makes a dense graph of all the language references in the hymnal db
 */
public class HymnalDbLanguagesHandler {

    public static final Set<Set<HymnalDbKey>> HYMNAL_DB_LANGUAGES_EXCEPTIONS = new LinkedHashSet<>();

    static {
        // Both h/1353 and h/8476 are valid translations of the Chinese song ch/476 and the French song hf/129.
        HYMNAL_DB_LANGUAGES_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1353", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8476", null),
                new HymnalDbKey(HymnType.FRENCH, "129", null),
                new HymnalDbKey(HymnType.TAGALOG, "1353", null),
                new HymnalDbKey(HymnType.CHINESE, "476", null),
                new HymnalDbKey(HymnType.CHINESE, "476", "?gb=1")));

        // Both ns/19 and ns/474 are valid translations of the Chinese song ts/428.
        HYMNAL_DB_LANGUAGES_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.NEW_SONG, "19", null),
                new HymnalDbKey(HymnType.NEW_SONG, "474", null)));

        // h/505 seems to have two linked Chinese songs, that from my investigation via Google Translate, both are
        // valid translations of that song.
        HYMNAL_DB_LANGUAGES_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CHINESE, "383", null),
                new HymnalDbKey(HymnType.CHINESE, "383", "?gb=1"),
                new HymnalDbKey(HymnType.CHINESE_SUPPLEMENT, "27", null),
                new HymnalDbKey(HymnType.CHINESE_SUPPLEMENT, "27", "?gb=1")));

        // h/893 seems to have two linked Chinese songs, that from my investigation via Google Translate, both are
        // valid translations of that song.
        HYMNAL_DB_LANGUAGES_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CHINESE, "641", null),
                new HymnalDbKey(HymnType.CHINESE, "641", "?gb=1"),
                new HymnalDbKey(HymnType.CHINESE_SUPPLEMENT, "917", null),
                new HymnalDbKey(HymnType.CHINESE_SUPPLEMENT, "917", "?gb=1")));

        // h/1353 and h/8476 are essentially two slightly different versions of the same song. So both should link to
        // the same set of translations, since the lyrics are very similar.
        HYMNAL_DB_LANGUAGES_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1353", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8476", null),
                new HymnalDbKey(HymnType.TAGALOG, "1353", null),
                new HymnalDbKey(HymnType.CHINESE, "476", null),
                new HymnalDbKey(HymnType.CHINESE, "476", "?gb=1")));
    }

    private final HymnalDbKey currentKey;
    private final Set<Reference> allLanguageRefs;
    private final Map<HymnalDbKey, ConvertedHymn> allHymns;
    private final DatabaseClient client;

    public static HymnalDbLanguagesHandler create(HymnalDbKey currentKey, Map<HymnalDbKey, ConvertedHymn> allHymns,
                                                  DatabaseClient client) {
        return new HymnalDbLanguagesHandler(currentKey, allHymns, client);
    }

    private HymnalDbLanguagesHandler(HymnalDbKey currentKey, Map<HymnalDbKey, ConvertedHymn> allHymns,
                                     DatabaseClient client) {
        this.currentKey = currentKey;
        this.allHymns = allHymns;
        this.allLanguageRefs = new LinkedHashSet<>();
        this.client = client;
    }

    public void handle() {
        List<Reference> references = extractLanguageReferences(currentKey);
        assert references != null;
        for (Reference reference : references) {
            populate(reference);
        }

        // Add current hymn to the set of all references, so we can properly audit the entire graph
        Set<HymnalDbKey> hymnsToAudit =
                allLanguageRefs
                        .stream()
                        .map(reference -> reference.key)
                        .collect(Collectors.toSet());
        hymnsToAudit.add(currentKey);
        audit(hymnsToAudit);

        write();
    }

    /**
     * Traverses the entire language graph and lists all songs found during traversal.
     */
    private void populate(Reference languageReference) {
        HymnalDbKey key = languageReference.key;
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }
        // Don't add current hymn into language map.
        if (currentKey.equals(key)) {
            return;
        }
        Set<HymnalDbKey> languageKeys =
                allLanguageRefs
                        .stream()
                        .map(reference -> reference.key)
                        .collect(Collectors.toSet());
        if (languageKeys.contains(key)) {
            return;
        }

        allLanguageRefs.add(Reference.create(languageReference.text, key));

        List<Reference> references = extractLanguageReferences(key);
        for (Reference reference : references) {
            populate(reference);
        }
    }

    /**
     * Looks up the hymn and extracts the language references from that hymn.
     */
    private List<Reference> extractLanguageReferences(HymnalDbKey key) {
        ConvertedHymn currentHymn = allHymns.get(key);
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }

        Languages languages = new Gson().fromJson(currentHymn.languagesJson, Languages.class);
        if (languages == null) {
            return new ArrayList<>();
        }
        return languages
                .getData().stream()
                .map(datum ->
                        Reference.create(datum.getValue(), HymnalDbKey.extractFromPath(datum.getPath())))
                .collect(Collectors.toList());
    }

    /**
     * Audit set of {@link Reference}s to see if there are conflicting types.
     */
    private void audit(Set<HymnalDbKey> setToAudit) {
        // Extract the hymn types for audit.
        List<HymnType> hymnTypes = setToAudit.stream().map(language -> language.hymnType).collect(Collectors.toList());

        // Verify that the same hymn type doesn't appear more than the allowed number of times the languages list.
        for (HymnType hymnType : HymnType.values()) {
            int timesAllowed = 1;
            if (hymnType == HymnType.CHINESE || hymnType == HymnType.CHINESE_SUPPLEMENT) {
                timesAllowed = 2;
            }
            // For each song like h225b or ns92f, increment the allowance of that type of hymn, since those are valid
            // alternates.
            if ((hymnType == HymnType.CLASSIC_HYMN || hymnType == HymnType.NEW_SONG || hymnType == HymnType.HOWARD_HIGASHI)) {
                for (HymnalDbKey key : setToAudit) {
                    if (key.hymnType == hymnType && key.hymnNumber.matches("(\\D+\\d+\\D*)|(\\D*\\d+\\D+)")) {
                        timesAllowed++;
                    }
                }
            }

            // If the current set includes an exception group, then remove that exception group from the list and audit
            // again.
            for (Set<HymnalDbKey> exception : HYMNAL_DB_LANGUAGES_EXCEPTIONS) {
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
        ConvertedHymn hymn = allHymns.get(currentKey);
        if (hymn == null) {
            throw new IllegalArgumentException("hymnalDbKey " + currentKey + " was not found");
        }

        Languages languages = new Gson().fromJson(hymn.languagesJson, Languages.class);
        // If one is empty and the other is not, then something weird happened during processing.
        if (languages == null != allLanguageRefs.isEmpty()) {
            throw new IllegalArgumentException("mismatch in language availability. hymn.languages: " + languages + " allLanguageRefs: " + allLanguageRefs);
        }

        if (languages == null) {
            return;
        }

        List<Reference> languageKeys =
                languages.getData()
                        .stream()
                        .map(datum -> Reference.create(datum.getValue(), HymnalDbKey.extractFromPath(datum.getPath())))
                        .collect(Collectors.toList());
        //noinspection SlowAbstractSetRemoveAll
        if (!allLanguageRefs.removeAll(languageKeys)) {
            throw new IllegalArgumentException(currentKey + ": " + allLanguageRefs + " did not include all of " + languageKeys);
        }
        for (Reference reference : allLanguageRefs) {
            HymnalDbKey key = reference.key;
            Datum datum = new Datum();
            datum.setPath("/en/hymn/" + key.hymnType.hymnalDb + "/" + key.hymnNumber + (TextUtils.isEmpty(key.queryParams) ? "" : key.queryParams));
            datum.setValue(reference.text);
            languages.getData().add(datum);
            LOG("adding " + datum + " to " + currentKey);
            if (!DRY_RUN) {
                System.out.println("Writing " + datum + " to " + currentKey + " in the database");
                ContentValues contentValues = new ContentValues();
                contentValues.put("SONG_META_DATA_LANGUAGES", new Gson().toJson(languages));
                client.getDb().update("SONG_DATA", contentValues, "HYMN_TYPE = '" + currentKey.hymnType.hymnalDb + "' AND HYMN_NUMBER ='" + currentKey.hymnNumber + "' AND QUERY_PARAMS = '" + currentKey.queryParams + "'");
            }
        }
    }
}
