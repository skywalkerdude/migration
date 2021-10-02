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
 * Populates, audits, and makes a dense graph of all the relevant references in the hymnal db
 */
public class HymnalDbRelevantHandler {

    public static final Set<Set<HymnalDbKey>> HYMNAL_DB_RELEVANT_EXCEPTIONS = new HashSet<>();

    static {
        // Both h/528, ns/306, and h/8444 are basically different versions of the same song.
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

        // Both h/720, h/8526, nt/720, and nt/720b have all different tunes of the same song
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "720", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "8526", null),
                new HymnalDbKey(HymnType.NEW_TUNE, "720", null),
                new HymnalDbKey(HymnType.NEW_TUNE, "720b", null)));

        // Both h/666 is a brother Lee rewrite of h/8661
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

        // Both h/921 is the original and h/1358 is an adapted version
        HYMNAL_DB_RELEVANT_EXCEPTIONS.add(Set.of(
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "921", null),
                new HymnalDbKey(HymnType.CLASSIC_HYMN, "1358", null)));
    }

    private final HymnalDbKey currentKey;
    private final Set<Reference> allRelevantRefs;
    private final Map<HymnalDbKey, ConvertedHymn> allHymns;
    private final DatabaseClient client;

    public static HymnalDbRelevantHandler create(HymnalDbKey currentKey, Map<HymnalDbKey, ConvertedHymn> allHymns,
                                                 DatabaseClient client) {
        return new HymnalDbRelevantHandler(currentKey, allHymns, client);
    }

    private HymnalDbRelevantHandler(HymnalDbKey currentKey, Map<HymnalDbKey, ConvertedHymn> allHymns,
                                    DatabaseClient client) {
        this.currentKey = currentKey;
        this.allHymns = allHymns;
        this.allRelevantRefs = new LinkedHashSet<>();
        this.client = client;
    }

    public void handle() {
        List<Reference> references = extractRelevantReferences(currentKey);
        assert references != null;
        for (Reference reference : references) {
            populate(reference);
        }

        // Add current hymn to the set of all references, so we can properly audit the entire graph
        Set<HymnalDbKey> hymnsToAudit =
                allRelevantRefs
                        .stream()
                        .map(reference -> reference.key)
                        .collect(Collectors.toSet());
        hymnsToAudit.add(currentKey);
        audit(hymnsToAudit);

        write();
    }

    /**
     * Traverses the entire "relevant"s graph and lists all songs found during traversal.
     */
    private void populate(Reference relevantReference) {
        HymnalDbKey key = relevantReference.key;
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }
        // Don't add current hymn into "relevant"s map.
        if (currentKey.equals(key)) {
            return;
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

        List<Reference> references = extractRelevantReferences(key);
        for (Reference reference : references) {
            populate(reference);
        }
    }

    /**
     * Looks up the hymn and extracts the relevant references from that hymn.
     */
    private List<Reference> extractRelevantReferences(HymnalDbKey key) {
        ConvertedHymn currentHymn = allHymns.get(key);
        if (!allHymns.containsKey(key)) {
            throw new IllegalArgumentException(String.format("%s not found in hymnal db", key));
        }

        Relevant relevant = new Gson().fromJson(currentHymn.relevantJson, Relevant.class);
        if (relevant == null) {
            return new ArrayList<>();
        }
        return relevant
                .getData().stream()
                .map(datum ->
                        Reference.create(datum.getValue(), HymnalDbKey.extractFromPath(datum.getPath())))
                .collect(Collectors.toList());
    }

    /**
     * Audit set of {@link Reference}s to see if there are conflicting types.
     */
    public void audit(Set<HymnalDbKey> setToAudit) {
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
                    audit(setToAudit);
                    return;
                }
            }

            if (Collections.frequency(hymnTypes, hymnType) > timesAllowed) {
                throw new IllegalArgumentException(
                        String.format("%s has too many instances of %s", setToAudit, hymnType));
            }
        }
    }

    /**
     * Writes the set of {@link #allRelevantRefs} into the database entry for {@link #currentKey}.
     */
    private void write() {
        ConvertedHymn hymn = allHymns.get(currentKey);
        if (hymn == null) {
            throw new IllegalArgumentException("hymnalDbKey " + currentKey + " was not found");
        }

        Relevant relevant = new Gson().fromJson(hymn.relevantJson, Relevant.class);
        // If one is empty and the other is not, then something weird happened during processing.
        if (relevant == null != allRelevantRefs.isEmpty()) {
            throw new IllegalArgumentException("mismatch in reference availability. hymn.relevant: " + relevant + " allRelevantRefs: " + allRelevantRefs);
        }

        if (relevant == null) {
            return;
        }

        List<Reference> relevantKeys =
                relevant.getData()
                        .stream()
                        .map(datum -> Reference.create(datum.getValue(), HymnalDbKey.extractFromPath(datum.getPath())))
                        .collect(Collectors.toList());
        //noinspection SlowAbstractSetRemoveAll
        if (!allRelevantRefs.removeAll(relevantKeys)) {
            throw new IllegalArgumentException(currentKey + ": " + allRelevantRefs + " did not include all of " + relevantKeys);
        }
        for (Reference reference : allRelevantRefs) {
            HymnalDbKey key = reference.key;
            Datum datum = new Datum();
            datum.setPath("/en/hymn/" + key.hymnType.hymnalDb + "/" + key.hymnNumber + (TextUtils.isEmpty(key.queryParams) ? "" : key.queryParams));
            datum.setValue(reference.text);
            relevant.getData().add(datum);
            LOG("adding " + datum + " to " + currentKey);
            if (!DRY_RUN) {
                System.out.println("Writing " + datum + " to " + currentKey + " in the database");
                ContentValues contentValues = new ContentValues();
                contentValues.put("SONG_META_DATA_RELEVANT", new Gson().toJson(relevant));
                client.getDb().update("SONG_DATA", contentValues, "HYMN_TYPE = '" + currentKey.hymnType.hymnalDb + "' AND HYMN_NUMBER ='" + currentKey.hymnNumber + "' AND QUERY_PARAMS = '" + currentKey.queryParams + "'");
            }
        }
    }
}
