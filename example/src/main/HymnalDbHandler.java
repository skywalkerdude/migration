package main;

import models.ConvertedHymn;
import models.HymnType;
import models.HymnalDbKey;
import repositories.DatabaseClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static main.HymnalDbFix.fixHymnalDb;

/**
 * Populates, audits, and makes a dense graph of all the songs in the hymnal db
 */
public class HymnalDbHandler {

    private final DatabaseClient client;
    private final Map<HymnalDbKey, ConvertedHymn> allHymns;

    public static HymnalDbHandler create(DatabaseClient client) throws SQLException {
        return new HymnalDbHandler(client);
    }

    public HymnalDbHandler(DatabaseClient client) throws SQLException {
        this.client = client;
        fixHymnalDb(client);

        // Populate HYMNAL_DB_HYMNS
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
        this.allHymns = Map.copyOf(allHymns);
    }

    public void handle() {
        for (HymnalDbKey hymnalDbKey : allHymns.keySet()) {
            handleLanguages(client, hymnalDbKey);
            handleRelevant(client, hymnalDbKey);
        }
    }

    private void handleLanguages(DatabaseClient client, HymnalDbKey hymnalDbKey) {
        HymnalDbLanguagesHandler languagesHandler = HymnalDbLanguagesHandler.create(hymnalDbKey, allHymns, client);
        languagesHandler.handle();
    }

    private void handleRelevant(DatabaseClient client, HymnalDbKey hymnalDbKey) {
        HymnalDbRelevantHandler relevantHandler = HymnalDbRelevantHandler.create(hymnalDbKey, allHymns, client);
        relevantHandler.handle();
    }
}