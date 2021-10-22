package main;

import com.google.gson.Gson;
import com.tylersuehr.sql.ContentValues;
import models.*;
import repositories.DatabaseClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import static main.Main.DRY_RUN;
import static main.Main.LOGGER;

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

        // Both h/8330 and ns/154 are valid translations of the Chinese song ch/330.
        HYMNAL_DB_LANGUAGES_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8330", null),
                new HymnalDbKey(HymnType.NEW_SONG, "154", null)));

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

        // T437 is from H4A, and seems like also a valid translation of h/437 as well as ht/c333
        HYMNAL_DB_LANGUAGES_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.TAGALOG, "c333", null),
                new HymnalDbKey(HymnType.TAGALOG, "437", null)));
    }

    private final DatabaseClient client;
    private final Map<HymnalDbKey, ConvertedHymn> allHymns;
    private final Set<Set<Reference>> allReferenceSets;

    public static HymnalDbLanguagesHandler create(DatabaseClient client, Map<HymnalDbKey, ConvertedHymn> allHymns) {
        return new HymnalDbLanguagesHandler(client, allHymns);
    }

    private HymnalDbLanguagesHandler(DatabaseClient client, Map<HymnalDbKey, ConvertedHymn> allHymns) {
        this.allHymns = allHymns;
        this.client = client;
        this.allReferenceSets = new LinkedHashSet<>();
    }

    public void handle(HymnalDbKey currentKey) {
        Set<Reference> allLanguageRefs = new LinkedHashSet<>();
        Set<Reference> references = allHymns.get(currentKey).languageReferences;
        assert references != null;
        references.forEach(reference -> populate(reference, allLanguageRefs));

        // No references to add
        if (allLanguageRefs.isEmpty()) {
            return;
        }

        addUnreferencedSongs(currentKey, allLanguageRefs);
        addToGlobalSet(allLanguageRefs);
    }

    /**
     * Traverses the entire language graph and adds all songs found during traversal into allLanguageRefs.
     */
    private void populate(Reference languageReference, Set<Reference> allLanguageRefs) {
        HymnalDbKey key = languageReference.key;
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
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
        allHymns.get(key).languageReferences.forEach(reference -> populate(reference, allLanguageRefs));
    }

    /**
     * We hope that most songs are some kind of circular reference (i.e. h/1 -> cb/1 -> h/1). This way, we get the text
     * of the reference for free. However, there are some cases where a song references a group of songs, but there is
     * no reference to it. In those cases, we will need to infer the text from the type of the hymn.
     */
    private void addUnreferencedSongs(HymnalDbKey currentKey, Set<Reference> allLanguageRefs) {
        Set<HymnalDbKey> languages = allLanguageRefs.stream()
                                                    .map(reference -> reference.key)
                                                    .collect(Collectors.toUnmodifiableSet());
        if (!languages.contains(currentKey)) {
            // Infer reference from type.
            switch (currentKey.hymnType) {
                case GERMAN:
                    allLanguageRefs.add(Reference.create("German", currentKey));
                    return;
                case FRENCH:
                    allLanguageRefs.add(Reference.create("French", currentKey));
                    return;
                case NEW_TUNE:
                    // Fall Through
                case CLASSIC_HYMN:
                    // It's a new or alternate tune that references the translation of the original tune. For now, we
                    // are NOT supporting such linkages since the languages they refer to likely is not going to be
                    // adhering to that new tune (sheet music, mp3, etc.). So in that case, just ignore it and leave the
                    // hymn as unreferenced.
                    if (currentKey.hymnNumber.matches("\\d+b")) {
                        return;
                    }
                default:
                    throw new IllegalArgumentException("Unable to infer text for unreferenced hymn: " + currentKey + "-" + allLanguageRefs);
            }
        }
    }

    /**
     * Writes the language refs to the global set of all languages.
     */
    private void addToGlobalSet(Set<Reference> languageRefs) {
        Set<Set<Reference>> matched = new LinkedHashSet<>();
        for (Reference reference : languageRefs) {
            for (Set<Reference> references : allReferenceSets) {
                if (references.contains(reference)) {
                    matched.add(references);
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
     * Audits {@link #allReferenceSets} to see if there are conflicting sets.
     */
    public void auditGlobalLanguagesSet() {
        for (Set<Reference> references : allReferenceSets) {
            auditLanguageSet(references.stream().map(reference -> reference.key).collect(Collectors.toSet()));
        }
    }

    /**
     * Audit set of {@link HymnalDbKey}s to see if there are conflicting types.
     */
    private void auditLanguageSet(Set<HymnalDbKey> setToAudit) {
        if (setToAudit.size() == 1) {
            throw new IllegalArgumentException("Language set with only 1 key is a dangling reference, which needs fixing: " + setToAudit);
        }

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

    /**
     * Augments the {@link ConvertedHymn#languagesJson} field if there are references that should be added from
     * {@link #allReferenceSets}
     */
    public void writeLanguageReferences() throws SQLException {
        int timesWritten = 0;
        int timesInserted = 0;
        for (Set<Reference> currentSet : allReferenceSets) {
            for (Reference currentReference : currentSet) {
                HymnalDbKey currentKey = currentReference.key;
                ConvertedHymn hymn = allHymns.get(currentKey);
                if (hymn == null) {
                    throw new IllegalArgumentException("hymnalDbKey " + currentKey + " was not found");
                }

                Set<Reference> languageKeys = hymn.extractLanguageReferences();

                Set<Reference> otherLanguages =
                        currentSet.stream()
                                  // Don't add itself into the languages set
                                  .filter(reference -> !reference.equals(currentReference))
                                  .collect(Collectors.toSet());

                if (!otherLanguages.containsAll(languageKeys)) {
                    // If the set of all languages doesn't contain the existing languages, then something wemnt wrong.
                    throw new IllegalArgumentException(currentKey + ": " + otherLanguages + " did not include all of " + languageKeys);
                } else {
                    // Remove keys that are already in the current languages json
                    otherLanguages.removeAll(languageKeys);
                }

                if (otherLanguages.isEmpty()) {
                    LOGGER.finer("No new languages to add for " + currentKey);
                    continue;
                }

                // Add new languages into the languages json
                Languages languages = new Gson().fromJson(hymn.languagesJson, Languages.class);
                if (languages == null) {
                    languages = new Languages();
                    languages.setName("Languages");
                    List<Datum> data = new ArrayList<>();
                    languages.setData(data);
                    for (Reference language : languageKeys) {
                        // If the language json is null, then we need to create a new Languages object and populate it
                        // with what is existing in the languageKeys.
                        HymnalDbKey keyToAdd = language.key;
                        String textToAdd = language.text;
                        Reference referenceToAdd = Reference.create(textToAdd, keyToAdd);
                        hymn.languageReferences.add(referenceToAdd);
                        Datum datum = new Datum();
                        datum.setPath("/en/hymn/" + keyToAdd.hymnType.hymnalDb + "/" + keyToAdd.hymnNumber + (TextUtils.isEmpty(keyToAdd.queryParams) ? "" : keyToAdd.queryParams));
                        datum.setValue(textToAdd);
                        languages.getData().add(datum);
                    }
                }
                for (Reference referenceToAdd : otherLanguages) {
                    HymnalDbKey keyToAdd = referenceToAdd.key;
                    String textToAdd = referenceToAdd.text;
                    hymn.languageReferences.add(referenceToAdd);
                    LOGGER.fine("Will add " + referenceToAdd + " to " + currentReference.key);
                    Datum datum = new Datum();
                    datum.setPath("/en/hymn/" + keyToAdd.hymnType.hymnalDb + "/" + keyToAdd.hymnNumber + (TextUtils.isEmpty(keyToAdd.queryParams) ? "" : keyToAdd.queryParams));
                    datum.setValue(textToAdd);
                    languages.getData().add(datum);
                }

                // Write to database
                String selection = "HYMN_TYPE = '" + currentKey.hymnType.hymnalDb + "' AND HYMN_NUMBER ='" + currentKey.hymnNumber + "' AND QUERY_PARAMS = '" + currentKey.queryParams + "'";
                ResultSet resultSet = client.getDb().query("SONG_DATA", selection, null, null);
                if (resultSet == null || !resultSet.next()) {
                    LOGGER.finer("Created new song: " + currentKey + " - " + hymn);
                    ContentValues contentValues = writeSong(currentKey, languages);
                    if (!DRY_RUN) {
                        LOGGER.info("Inserting " + currentKey);
                        timesInserted++;
                        client.getDb().insert("song_data", contentValues);
                    }
                } else {
                    String languageJson = new Gson().toJson(languages);
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("SONG_META_DATA_LANGUAGES", languageJson);
                    if (!DRY_RUN) {
                        LOGGER.info("Writing to " + currentKey + " languageJson: " + languageJson);
                        timesWritten++;
                        client.getDb().update("SONG_DATA", contentValues, selection);
                    }
                }
            }
        }
        if (!DRY_RUN) {
            System.out.println("Rewrote " + timesWritten + " languageJsons");
            System.out.println("Inserted " + timesInserted + " new songs");
        }
    }

    private ContentValues writeSong(HymnalDbKey key, Languages languages) {
        ConvertedHymn hymn = allHymns.get(key);

        ContentValues contentValues = new ContentValues();
        contentValues.put("HYMN_TYPE", key.hymnType.hymnalDb);
        contentValues.put("HYMN_NUMBER", key.hymnNumber);
        contentValues.put("QUERY_PARAMS", key.queryParams);
        contentValues.put("SONG_TITLE", TextUtils.escapeSingleQuotes(hymn.title));
        contentValues.put("SONG_LYRICS", TextUtils.escapeSingleQuotes(hymn.lyricsJson));
        contentValues.put("SONG_META_DATA_CATEGORY", TextUtils.escapeSingleQuotes(hymn.category));
        contentValues.put("SONG_META_DATA_SUBCATEGORY", TextUtils.escapeSingleQuotes(hymn.subCategory));
        contentValues.put("SONG_META_DATA_AUTHOR", TextUtils.escapeSingleQuotes(hymn.author));
        contentValues.put("SONG_META_DATA_COMPOSER", TextUtils.escapeSingleQuotes(hymn.composer));
        contentValues.put("SONG_META_DATA_KEY", TextUtils.escapeSingleQuotes(hymn.key));
        contentValues.put("SONG_META_DATA_TIME", TextUtils.escapeSingleQuotes(hymn.time));
        contentValues.put("SONG_META_DATA_METER", TextUtils.escapeSingleQuotes(hymn.meter));
        contentValues.put("SONG_META_DATA_SCRIPTURES", TextUtils.escapeSingleQuotes(hymn.scriptures));
        contentValues.put("SONG_META_DATA_HYMN_CODE", TextUtils.escapeSingleQuotes(hymn.hymnCode));
        contentValues.put("SONG_META_DATA_MUSIC", TextUtils.escapeSingleQuotes(hymn.musicJson));
        contentValues.put("SONG_META_DATA_SVG_SHEET_MUSIC", TextUtils.escapeSingleQuotes(hymn.svgJson));
        contentValues.put("SONG_META_DATA_PDF_SHEET_MUSIC", TextUtils.escapeSingleQuotes(hymn.pdfJson));

        if (!languages.getData().isEmpty()) {
            String languagesJson = new Gson().toJson(languages);
            contentValues.put("SONG_META_DATA_LANGUAGES", TextUtils.escapeSingleQuotes(languagesJson));
        }
        return contentValues;
    }
}
