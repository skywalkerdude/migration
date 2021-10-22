package main;

import com.google.gson.Gson;
import models.ConvertedHymn;
import models.HymnType;
import models.HymnalDbKey;
import models.Languages;
import repositories.DatabaseClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static main.HymnalDbFixer.fix;

/**
 * Populates, audits, and makes a dense graph of all the songs in the hymnal db
 */
public class HymnalDbHandler {

    public final DatabaseClient client;
    public final Map<HymnalDbKey, ConvertedHymn> allHymns;
    public final HymnalDbLanguagesHandler languagesHandler;
    public final HymnalDbRelevantHandler relevantHandler;

    public static HymnalDbHandler create(DatabaseClient client) throws SQLException {
        return new HymnalDbHandler(client);
    }

    public HymnalDbHandler(DatabaseClient client) throws SQLException {
        fix(client);
        this.client= client;
        this.allHymns = populateHymns(client);
        this.languagesHandler = HymnalDbLanguagesHandler.create(client, allHymns);
        this.relevantHandler = HymnalDbRelevantHandler.create(client, allHymns);
    }

    public void handle() throws SQLException {
        for (HymnalDbKey hymnalDbKey : allHymns.keySet()) {
            languagesHandler.handle(hymnalDbKey);
            relevantHandler.handle(hymnalDbKey);
        }

        languagesHandler.auditGlobalLanguagesSet();
        languagesHandler.writeLanguageReferences();

        relevantHandler.auditGlobalRelevantSet();
        relevantHandler.writeRelevantReferences();

        // Ensure languageReferences is up-to-date with languageJson written in the database for H4a processing
        allHymns.clear();
        allHymns.putAll(populateHymns(client));

        allHymns.forEach(HymnalDbHandler::auditLanguageReferences);
    }

    private static Map<HymnalDbKey, ConvertedHymn> populateHymns(DatabaseClient client) throws SQLException {
        Map<HymnalDbKey, ConvertedHymn> allHymns = new LinkedHashMap<>();
        ResultSet resultSet = client.getDb().rawQuery("SELECT * FROM song_data");
        if (resultSet == null) {
            throw new IllegalArgumentException("hymnalDb query returned null");
        }
        while (resultSet.next()) {
            HymnType hymnType = HymnType.fromHymnalDb(resultSet.getString(2));
            String hymnNumber = resultSet.getString(3);
            String queryParams = resultSet.getString(4);
            allHymns.put(new HymnalDbKey(hymnType, hymnNumber, queryParams),
                    new ConvertedHymn(resultSet.getString(5),
                            resultSet.getString(6),
                            resultSet.getString(7),
                            resultSet.getString(8),
                            resultSet.getString(9),
                            resultSet.getString(10),
                            resultSet.getString(11),
                            resultSet.getString(12),
                            resultSet.getString(13),
                            resultSet.getString(14),
                            resultSet.getString(15),
                            resultSet.getString(16),
                            resultSet.getString(17),
                            resultSet.getString(18),
                            resultSet.getString(19),
                            resultSet.getString(20)));
        }
        return allHymns;
    }

    /**
     * Ensures that a {@link ConvertedHymn}'s {@link ConvertedHymn#languageReferences} matches with its
     * {@link ConvertedHymn#languagesJson}.
     */
    private static void auditLanguageReferences(HymnalDbKey key, ConvertedHymn hymn) {
        Set<HymnalDbKey> languageReferences = hymn.languageReferences.stream().map(reference -> reference.key).collect(Collectors.toUnmodifiableSet());
        Languages languages = new Gson().fromJson(hymn.languagesJson, Languages.class);
        if (languages == null) {
            if (!languageReferences.isEmpty()) {
                throw new IllegalArgumentException("Language references for " + key + " is not empty but language json is");
            } else {
                // both are empty, so all looks good.
                return;
            }
        }
        Set<HymnalDbKey> languageJson = languages.getData().stream()
                .map(datum ->HymnalDbKey.extractFromPath(datum.getPath()))
                .collect(Collectors.toUnmodifiableSet());
        if (!languageReferences.equals(languageJson)) {
            throw new IllegalArgumentException("Language references for " + key + " was not equal to language json");
        }
    }
}
