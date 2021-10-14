
package main;

import repositories.DatabaseClient;

/**
 * Class to fix errors in the Hymns For Anrdoid db mapping according to some manually predefined operations.
 */
public class H4ADbFixer {

    public static void fix(DatabaseClient client) {
        fix_G10001(client);
    }

    /**
     * Weird mapping in h4a where G10001 maps to the Chinese version of "God's eternal economy" instead of the English.
     */
    private static void fix_G10001(DatabaseClient client) {
        client.getDb().execSql("UPDATE hymns set parent_hymn='NS180' WHERE _id = 'G10001'");
    }
}
