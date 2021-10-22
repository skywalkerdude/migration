package main;

import com.google.gson.Gson;
import com.tylersuehr.sql.ContentValues;
import models.*;
import repositories.DatabaseClient;

import java.util.*;
import java.util.stream.Collectors;

import static main.Main.DRY_RUN;
import static main.Main.LOGGER;

/**
 * Populates, audits, and makes a dense graph of all the relevant references in the hymnal db
 */
public class HymnalDbRelevantHandler {

    public static final Set<Set<HymnalDbKey>> HYMNAL_DB_RELEVANT_EXCEPTIONS = new HashSet<>();

    static {
        // h/528, ns/306, and h/8444 are basically different versions of the same song.
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "528", null),
                new HymnalDbKey(HymnType.NEW_SONG, "306", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8444", null)));

        // Both h/79 and h/8079 have the same chorus
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "79", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8079", null)));

        // Both ns/19 and ns/474 are two English translations of the same song
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.NEW_SONG, "19", null),
                new HymnalDbKey(HymnType.NEW_SONG, "474", null)));

        // Both h/267 and h/1360 have the same chorus
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "267", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1360", null)));

        // h/720, h/8526, nt/720, and nt/720b have all different tunes of the same song
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "720", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8526", null),
                new HymnalDbKey(HymnType.NEW_TUNE, "720", null),
                new HymnalDbKey(HymnType.NEW_TUNE, "720b", null)));

        // h/666 is a brother Lee rewrite of h/8661
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "666", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8661", null)));

        // Both h/445 is h/1359 but without the chorus
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "445", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1359", null)));

        // Both h/1353 are h/8476 are alternate versions of each other (probably different translations of the same
        // Chinese song)
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1353", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8476", null)));

        // h/921 is the original and h/1358 is an adapted version
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "921", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1358", null)));

        // h/18 is the original and ns/7 is an adapted version
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "18", null),
                new HymnalDbKey(HymnType.NEW_SONG, "7", null)));

        // c/21 is a shortened version of h/70
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CHILDREN_SONG, "21", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "70", null)));

        // c/162 is a shortened version of h/993
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CHILDREN_SONG, "162", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "993", null)));

        // ns/179 is the adapted version of h/1248
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1248", null),
                new HymnalDbKey(HymnType.NEW_SONG, "179", null)));

        // Both ns/154 and h/8330 are valid translations of the Chinese song ch/330.
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.NEW_SONG, "154", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8330", null)));
    }

    private final DatabaseClient client;
    private final Map<HymnalDbKey, ConvertedHymn> allHymns;
    private final Set<Set<Reference>> allReferenceSets;

    public static HymnalDbRelevantHandler create(DatabaseClient client, Map<HymnalDbKey, ConvertedHymn> allHymns) {
        return new HymnalDbRelevantHandler(client, allHymns);
    }

    private HymnalDbRelevantHandler(DatabaseClient client, Map<HymnalDbKey, ConvertedHymn> allHymns) {
        this.client = client;
        this.allHymns = allHymns;
        this.allReferenceSets = new LinkedHashSet<>();
    }

    public void handle(HymnalDbKey currentKey) {
        Set<Reference> allRelevantRefs = new LinkedHashSet<>();
        Set<Reference> references = extractRelevantReferences(currentKey);
        assert references != null;
        references.forEach(reference -> populate(reference, allRelevantRefs));

        // No references to add
        if (allRelevantRefs.isEmpty()) {
            return;
        }

        findUnreferencedSongs(currentKey, allRelevantRefs);
        addToGlobalSet(allRelevantRefs);
    }

    /**
     * Looks up the hymn and extracts the relevant references from that hymn.
     */
    private Set<Reference> extractRelevantReferences(HymnalDbKey key) {
        ConvertedHymn currentHymn = allHymns.get(key);
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }

        Relevant relevant = new Gson().fromJson(currentHymn.relevantJson, Relevant.class);
        if (relevant == null) {
            return new LinkedHashSet<>();
        }
        return relevant
                .getData().stream()
                .map(datum ->
                             Reference.create(datum.getValue(), HymnalDbKey.extractFromPath(datum.getPath())))
                .collect(Collectors.toSet());
    }

    /**
     * Traverses the entire relevants graph and adds all songs found during traversal into allRelevantRefs.
     */
    private void populate(Reference relevantReference, Set<Reference> allRelevantRefs) {
        HymnalDbKey key = relevantReference.key;
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }
        Set<HymnalDbKey> relevantKeys =
                allRelevantRefs
                        .stream()
                        .map(reference -> reference.key)
                        .collect(Collectors.toSet());
        if (relevantKeys.contains(key)) {
            return;
        }

        allRelevantRefs.add(Reference.create(relevantReference.text, key));
        extractRelevantReferences(key).forEach(reference -> populate(reference, allRelevantRefs));
    }

    /**
     * We hope that most songs are some kind of circular reference (i.e. h/1 -> nt/1 -> h/1). This way, we get the text
     * of the reference for free. However, there are some cases where a song references a group of songs, but there is
     * no reference to it. In those cases, we should blow up the pipeline, so we can resolve it.
     */
    private void findUnreferencedSongs(HymnalDbKey currentKey, Set<Reference> allRelevantRefs) {
        Set<HymnalDbKey> relevants = allRelevantRefs.stream()
                                                    .map(reference -> reference.key)
                                                    .collect(Collectors.toUnmodifiableSet());
        if (!relevants.contains(currentKey)) {
            throw new IllegalArgumentException("Unable to infer text for unreferenced hymn: " + currentKey + "-" + allRelevantRefs);
        }
    }

    /**
     * Writes the relevant refs to the global set of all relevant songs.
     */
    private void addToGlobalSet(Set<Reference> relevantRefs) {
        Set<Set<Reference>> matched = new LinkedHashSet<>();
        for (Reference reference : relevantRefs) {
            for (Set<Reference> references : allReferenceSets) {
                if (references.contains(reference)) {
                    matched.add(references);
                }
            }
        }
        // Did not match anything in the current global set, so we should add it.
        if (matched.isEmpty()) {
            allReferenceSets.add(relevantRefs);
        } else if (matched.size() == 1) {
            matched.stream().findFirst().get().addAll(relevantRefs);
        } else {
            // Each relevant reference should be in its unique set. If there are multiple matching sets, then something
            // is wrong.
            throw new IllegalArgumentException(relevantRefs + " was not in a unique set, but was in " + matched);
        }
    }

    /**
     * Audits {@link #allReferenceSets} to see if there are conflicting sets.
     */
    public void auditGlobalRelevantSet() {
        for (Set<Reference> references : allReferenceSets) {
            auditRelevantSet(references.stream().map(reference -> reference.key).collect(Collectors.toSet()));
        }
    }

    /**
     * Audit set of {@link Reference}s to see if there are conflicting types.
     */
    public void auditRelevantSet(Set<HymnalDbKey> setToAudit) {
        if (setToAudit.size() == 1) {
            throw new IllegalArgumentException("Relevant set with only 1 key is a dangling reference, which needs fixing: " + setToAudit);
        }

        // Extract the hymn types for audit.
        List<HymnType> hymnTypes = setToAudit.stream().map(relevant -> relevant.hymnType).collect(Collectors.toList());

        // Verify that the same hymn type doesn't appear more than the allowed number of times the relevant list.
        for (HymnType hymnType : HymnType.values()) {
            int timesAllowed = 1;

            // For each song like h/810, ns/698b, nt/394b, de/786b increment the allowance of that type of hymn,
            // since those are valid alternates.
            if ((hymnType == HymnType.CLASSIC_HYMN || hymnType == HymnType.NEW_TUNE || hymnType == HymnType.NEW_SONG || hymnType == HymnType.GERMAN)) {
                for (HymnalDbKey key : setToAudit) {
                    if (key.hymnType == hymnType && key.hymnNumber.matches("(\\D+\\d+\\D*)|(\\D*\\d+\\D+)")) {
                        timesAllowed++;
                    }
                }
            }

            // If the current set includes an exception group, then remove that exception group from the list and audit
            // again.
            for (Set<HymnalDbKey> exception : HYMNAL_DB_RELEVANT_EXCEPTIONS) {
                if (setToAudit.containsAll(exception)) {
                    if (!setToAudit.removeAll(exception)) {
                        throw new IllegalArgumentException(exception + " was unable to be removed from " + setToAudit);
                    }
                    auditRelevantSet(setToAudit);
                    return;
                }
            }

            // Verify that incompatible hymn types don't appear together the relevants list.
            if ((hymnTypes.contains(HymnType.CLASSIC_HYMN) && hymnTypes.contains(HymnType.NEW_SONG))
                || (hymnTypes.contains(HymnType.CLASSIC_HYMN) && hymnTypes.contains(HymnType.CHILDREN_SONG))
                || hymnTypes.contains(HymnType.CHILDREN_SONG) && hymnTypes.contains(HymnType.NEW_SONG)
                || hymnTypes.contains(HymnType.CHINESE) && hymnTypes.contains(HymnType.CHINESE_SUPPLEMENT)) {
                throw new IllegalArgumentException(String.format("%s has incompatible relevant types", setToAudit));
            }

            if (Collections.frequency(hymnTypes, hymnType) > timesAllowed) {
                throw new IllegalArgumentException(
                        String.format("%s has too many instances of %s", setToAudit, hymnType));
            }
        }
    }

    /**
     * Augments the {@link ConvertedHymn#relevantJson} field if there are references that should be added from
     * {@link #allReferenceSets}
     */
    public void writeRelevantReferences() {
        int timesWritten = 0;
        for (Set<Reference> currentSet : allReferenceSets) {
            for (Reference currentReference : currentSet) {
                HymnalDbKey currentKey = currentReference.key;
                ConvertedHymn hymn = allHymns.get(currentKey);
                if (hymn == null) {
                    throw new IllegalArgumentException("hymnalDbKey " + currentKey + " was not found");
                }

                // If this current hymn doesn't have any relevant songs, then it shouldn't be part of any set
                Relevant relevant = new Gson().fromJson(hymn.relevantJson, Relevant.class);
                if (relevant == null) {
                    throw new IllegalArgumentException(
                            currentKey + " has a mismatch in relevant availability. Found in set " + currentSet);
                }

                Set<Reference> relevantKeys =
                        relevant.getData()
                                .stream()
                                .map(datum -> Reference.create(datum.getValue(), HymnalDbKey.extractFromPath(datum.getPath())))
                                .collect(Collectors.toSet());
                Set<Reference> otherRelevants =
                        currentSet.stream()
                                  // Don't add itself into the relevant set
                                  .filter(reference -> !reference.equals(currentReference))
                                  .collect(Collectors.toSet());
                // Remove keys that are already in the current relevants json
                if (!otherRelevants.removeAll(relevantKeys)) {
                    throw new IllegalArgumentException(currentKey + ": " + otherRelevants + " did not include all of " + relevantKeys);
                }

                if (otherRelevants.isEmpty()) {
                    LOGGER.finer("No new relevant songs to add for " + currentKey);
                    continue;
                }

                // Add new relevant songs into the relevants json
                otherRelevants.forEach(otherRelevant -> {
                    HymnalDbKey key = otherRelevant.key;
                    Datum datum = new Datum();
                    datum.setPath("/en/hymn/" + key.hymnType.hymnalDb + "/" + key.hymnNumber + (TextUtils.isEmpty(key.queryParams) ? "" : key.queryParams));
                    datum.setValue(otherRelevant.text);
                    relevant.getData().add(datum);
                    LOGGER.fine("Will add " + datum + " to " + currentReference.key);
                });

                // Write to database
                if (!DRY_RUN) {
                    String relevantJson = new Gson().toJson(relevant);
                    ContentValues contentValues = new ContentValues();
                    contentValues.put("SONG_META_DATA_RELEVANT", relevantJson);
                    LOGGER.info("Writing to " + currentKey + " relevantJson: " + relevantJson);
                    timesWritten++;
                    client.getDb().update("SONG_DATA", contentValues, "HYMN_TYPE = '" + currentKey.hymnType.hymnalDb + "' AND HYMN_NUMBER ='" + currentKey.hymnNumber + "' AND QUERY_PARAMS = '" + currentKey.queryParams + "'");
                }
            }
        }
        if (!DRY_RUN) {
            System.out.println("Rewrote " + timesWritten + " relevantJsons");
        }
    }
}
