
package main;

import models.ConvertedHymn;
import models.H4aKey;
import models.HymnType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class to fix errors in the Hymns For Android db mapping according to some manually predefined operations.
 */
public class H4ADbFixer {

    public static void fix(Map<H4aKey, ConvertedHymn> allH4aHymns) {
        cleanUp(allH4aHymns);

        // Invalid song
        allH4aHymns.remove(new H4aKey("NS582"));

        // Here are the only two German versions of new songs in the H4A db. This song is covered in the Hymnal db, so
        // it's easier to just remove them than to create a special cases to handle just two songs.
        // G419 and G10002 are two different translations of "God's eternal economy" (NS180)
        allH4aHymns.remove(new H4aKey("G419"));
        allH4aHymns.remove(new H4aKey("G10001"));
        deleteMapping("CS1004", Set.of("G10001"), allH4aHymns);
        deleteMapping("ZS1004", Set.of("G10001"), allH4aHymns);
        // G10002 and G420 are two different translations of "What miracle! What mystery!" (NS151)
        allH4aHymns.remove(new H4aKey("G10002"));
        allH4aHymns.remove(new H4aKey("G420"));
        deleteMapping("NS151", Set.of("G10002"), allH4aHymns);

        // K217 and C217 should map to E1360, not E267. K217 and C217's parent hymn already points to E1360 and E1360 is
        // already mapped to K217 and C217. So just a simple deletion is needed.
        deleteMapping("E267", Set.of("K217", "C217"), allH4aHymns);

        // C390 should map to E517 (which it is), but it's somehow in the "languages" filed for E527. So it needs to be
        // removed.
        deleteMapping("E527", Set.of("C390"), allH4aHymns);

        // E445 contains songs that actually should map to E1359. These songs are very similar, but distinct. Thus, we
        // need to remove the duplicate mappings.
        deleteMapping("E445", Set.of("C339", "K339"), allH4aHymns);
        // CB445 is the Cebunao song of E445, but also contains similar incorrect mappings that ought to be removed.
        deleteMapping("CB445", Set.of("T1359", "C339"), allH4aHymns);

        // E480 maps to C357 but C357 is actually the translation to h/8357 (covered in hymnal db). So we can safely
        // just remove the mapping here.
        deleteMapping("E480", Set.of("C357"), allH4aHymns);
        // There is also a missing link between C357 and K357 that we need to add.
        addMapping("C357", Set.of("K357"), allH4aHymns);
        setParentHymn("K357", "C357", allH4aHymns);

        // Based on HymnalDbFixer#fix_h79_h8079, E79 should not have C68 a nd Z68 as Chinese references.
        deleteMapping("E79", Set.of("C68", "Z68"), allH4aHymns);

        // Based on HymnalDbFixer#fix_h720_h8526, E720 should not have C526 and Z526 as Chinese references.
        deleteMapping("E720", Set.of("C526", "Z526"), allH4aHymns);

        // K31's parent should be E33, not E34.
        setParentHymn("K31", "E33", allH4aHymns);
        addMapping("E33", Set.of("K31"), allH4aHymns);
        // E34 doesn't reference K31, so no need to delete anything

        // K32's parent should be E36, even though they differ in verse count. This is because C32 is the Chinese
        // translation of E36. However, I'm not sure why E36 only has 2 verses while the others have 4 verses. Weird.
        setParentHymn("K32", "E36", allH4aHymns);
        addMapping("E36", Set.of("K32"), allH4aHymns);
        // there was no mapping to K32, so noeed to delete anything

        // K57 is the Korean translation of E61, not E51
        setParentHymn("K57", "E61", allH4aHymns);
        addMapping("E61", Set.of("K57"), allH4aHymns);
        // E51 maps to K57, so we need to delete it
        deleteMapping("E51", Set.of("K57"), allH4aHymns);

        // K61's parent is E78, but its languages list doesn't contain it.
        addMapping("E78", Set.of("K61"), allH4aHymns);

        // K644's parent is E1358, but that is not right. Rather, E890 is the correct parent.
        setParentHymn("K644", "E890", allH4aHymns);
        // E890 already references K644
        // E1358 already doesn't reference K644

        // Per HymnalDbFixer#fix_ht1358_h1358:
        // E1358 is a 1-verse song called "Rise! Preach the Gospel now!".
        // E921 is a 4-verse song called "Rescue the perishing".
        // These are two different songs with the same tune. Therefore, they should be separate in terms of language
        // translations.
        setParentHymn("C664", "E1358", allH4aHymns);
        setParentHymn("Z664", "E1358", allH4aHymns);
        setParentHymn("I664", "E1358", allH4aHymns);
        setParentHymn("J664", "E1358", allH4aHymns);
        addMapping("E1358", Set.of("I664", "J664"), allH4aHymns);
        setParentHymn("K664", "E1358", allH4aHymns);
        deleteMapping("E921", Set.of("J664", "I664"), allH4aHymns);

        // E1358 has a mapping to Z644 when it should be Z664 (typo)
        deleteMapping("E1358", Set.of("Z644"), allH4aHymns);
        addMapping("E1358", Set.of("Z664"), allH4aHymns);

        // I42's parent should be E49, not C43.
        setParentHymn("I42", "E49", allH4aHymns);
        // E49 doesn't reference I42, so we need to add it
        addMapping("E49", Set.of("I42"), allH4aHymns);

        // I269 is the Indonesian translation of E349, not E367
        setParentHymn("I269", "E349", allH4aHymns);
        deleteMapping("I269", Set.of("C289"), allH4aHymns);
        addMapping("E349", Set.of("I269"), allH4aHymns);
        deleteMapping("E367", Set.of("I269"), allH4aHymns);

        // E66 includes Z485 and I485 as mappings, but that is a mistake.
        deleteMapping("E666", Set.of("Z485", "I485"), allH4aHymns);
        // Also, I485's parent should be C485, not E666
        setParentHymn("I485", "C485", allH4aHymns);

        // K319 is the Korean translation of C319, not E419
        setParentHymn("K319", "C319", allH4aHymns);
        addMapping("C319", Set.of("K319"), allH4aHymns);
        deleteMapping("E419", Set.of("K319"), allH4aHymns);

        // K372 is the Korean translation of E495, not E494
        setParentHymn("K372", "E495", allH4aHymns);
        addMapping("E495", Set.of("K372"), allH4aHymns);
        deleteMapping("E494", Set.of("K372"), allH4aHymns);

        // This song has messed up mapping in the h4a db: it has two Korean songs (K460 and K446). K640 is wrong.
        // E605 maps to both K460 and K446. K460 should instead map to E623.
        deleteMapping("E605", Set.of("K460"), allH4aHymns);
        addMapping("E623", Set.of("K460"), allH4aHymns);
        setParentHymn("K460", "E623", allH4aHymns);

        // J539 is the Japanese translation of E743, not E734.
        deleteMapping("E734", Set.of("J539"), allH4aHymns);
        addMapping("E743", Set.of("J539"), allH4aHymns);
        setParentHymn("J539", "E743", allH4aHymns);

        // K667 is the Korean translation of E1349, not E894
        deleteMapping("E894", Set.of("K667"), allH4aHymns);
        addMapping("E1349", Set.of("K667"), allH4aHymns);
        setParentHymn("K667", "E1349", allH4aHymns);

        // Hymnal db separates E1017 from C643, so we will follow suit here. See HymnalDbFixer#fix_ch643
        deleteMapping("E1017", Set.of("Z643", "I643", "J643"), allH4aHymns);
        // Map I643, J643, and K643 to their new parent, C643
        setMapping("C643", Set.of("Z643", "I643", "J643", "K643"), allH4aHymns);
        setMapping("Z643", Set.of("C643"), allH4aHymns);
        setParentHymn("I643", "C643", allH4aHymns);
        setParentHymn("J643", "C643", allH4aHymns);
        setParentHymn("K643", "C643", allH4aHymns);

        // I709 is the translation of E1034, not E1028
        deleteMapping("E1028", Set.of("I709"), allH4aHymns);
        addMapping("E1034", Set.of("I709"), allH4aHymns);
        setParentHymn("I709", "E1034", allH4aHymns);

        // E1191 references ZS401 and I401, but shouldn't. ZS401 is a typo and should be ZS410 while I1401 actually maps
        // to CS401
        deleteMapping("E1191", Set.of("ZS401", "I1401"), allH4aHymns);
        setParentHymn("I1401", "CS401", allH4aHymns);
        setMapping("CS401", Set.of("ZS401", "I1401"), allH4aHymns);
        setMapping("ZS401", Set.of("CS401", "I1401"), allH4aHymns);

        // E1248 maps to both K1014 and K1001. K1014 should map to E1295
        deleteMapping("E1248", Set.of("K1014"), allH4aHymns);
        setParentHymn("K1014", "E1295", allH4aHymns);
        addMapping("E1295", Set.of("K1014"), allH4aHymns);

        // E1348 maps to both I153 and I1513. However, I1531 should only map to CS531.
        deleteMapping("E1348", Set.of("I1531"), allH4aHymns);
        // I1531 is mapped to the wrong song (CS513). So we need to clear that first
        clearMapping("I1531", allH4aHymns);
        setParentHymn("I1531", "CS531", allH4aHymns);
        addMapping("CS531", Set.of("I1531"), allH4aHymns);

        // I1832 maps to CS823, while it should map to CS832
        setParentHymn("I1832", "CS832", allH4aHymns);
        addMapping("CS832", Set.of("CS832"), allH4aHymns);
        // CS823's mapping is correct

    }

    /**
     * Go through entire database and remove things that shouldn't be there.
     */
    private static void cleanUp(Map<H4aKey, ConvertedHymn> allH4aHymns) {
        // Delete invalid references
        allH4aHymns.forEach((key, hymn) -> {
            if (hymn.parentHymn != null) {
                switch (hymn.parentHymn.type()) {
                    case TAGALOG:
                        // Keep tagalog songs that are <= 1360.
                        if (Integer.parseInt(key.number()) <= 1360) {
                            return;
                        }
                    case UNKNOWN:
                    case BE_FILLED:
                        hymn.parentHymn = null;
                }
            }

            hymn.languages.removeIf(language -> language.type() == HymnType.UNKNOWN);

            // Ignore BE_FILLED songs because those are too unpredictable (some are messed up, some are just
            // duplicates, and some have sketchy mappings)
            hymn.languages.removeIf(language -> language.type() == HymnType.BE_FILLED);

            // Tagalog songs > T1360 are often times just repeats of their English counterpart or with small
            // insignificant changes. Even when they're not repeats, they're not very useful songs. Therefore, we
            // just ignore them.
            hymn.languages.removeIf(language -> language.type() == HymnType.TAGALOG && Integer.parseInt(language.number()) > 1360);

            // Songs that show up in "related" column but don't actually exist in the h4a db. These should be ignored since
            // they map to nothing.
            List<String> ignore =
                    Arrays.asList(
                            "C825", "C914", "C912", "C389", "C834", "T898", "C815", "C486", "C806", "C905",
                            "BF1040", "C856", "C812", "C810", "C850", "C901", "C517c", "C510c", "C513c", "CB57",
                            "C925", "C917", "C840", "CS352", "CS158", "CB1360", "C506c", "CB381", "CS46", "C481c",
                            "CS9117", "CS46", "CS400");
            hymn.languages.removeIf(language -> ignore.contains(language.id));
        });

        // Delete invalid songs
        new HashSet<>(allH4aHymns.keySet()).forEach(key -> {
            switch (key.type()) {
                case TAGALOG:
                    // Keep tagalog songs that are <= 1360.
                    if (Integer.parseInt(key.number()) <= 1360) {
                        return;
                    }
                case UNKNOWN:
                case BE_FILLED:
                    allH4aHymns.remove(key);
                    break;
                default:
            }
        });
    }

    private static void clearMapping(String songToClear, Map<H4aKey, ConvertedHymn> allH4aHymns) {
        setMapping(songToClear, Set.of(), allH4aHymns);
        allH4aHymns.get(new H4aKey(songToClear)).parentHymn = null;
    }

    private static void setParentHymn(String songToSet, String newParent, Map<H4aKey, ConvertedHymn> allH4aHymns) {
        ConvertedHymn hymn = allH4aHymns.get(new H4aKey(songToSet));
        H4aKey oldParent = hymn.parentHymn;
        hymn.parentHymn = new H4aKey(newParent);
        addMapping(songToSet, Set.of(newParent), allH4aHymns);
        if (oldParent != null) {
            deleteMapping(songToSet, Set.of(oldParent.id), allH4aHymns);
        }
    }

    private static void setMapping(String songToEdit, Set<String> mapping, Map<H4aKey, ConvertedHymn> allH4aHymns) {
        allH4aHymns.get(new H4aKey(songToEdit)).languages = new ArrayList<>(mapping.stream().map(H4aKey::new).collect(Collectors.toUnmodifiableSet()));
    }

    private static void deleteMapping(String songToDeleteFrom, Set<String> songsToDelete, Map<H4aKey, ConvertedHymn> allH4aHymns) {
        List<H4aKey> languages = allH4aHymns.get(new H4aKey(songToDeleteFrom)).languages;
        int sizeAfterRemoval = languages.size() - songsToDelete.size();
        if (!allH4aHymns.get(new H4aKey(songToDeleteFrom)).languages.removeAll(songsToDelete.stream().map(H4aKey::new).collect(Collectors.toUnmodifiableSet()))) {
            throw new IllegalArgumentException("Wasn't able to delete any: " + songsToDelete + " from: " + songToDeleteFrom);
        }
        if (sizeAfterRemoval != languages.size()) {
            throw new IllegalArgumentException("Wasn't able to delete all references: " + songsToDelete + " from: " + songToDeleteFrom);
        }
    }

    private static void addMapping(String songToAddTo, Set<String> songsToAdd, Map<H4aKey, ConvertedHymn> allH4aHymns) {
        List<H4aKey> languages = allH4aHymns.get(new H4aKey(songToAddTo)).languages;
        int sizeAfterRemoval = languages.size() + songsToAdd.size();
        if (!allH4aHymns.get(new H4aKey(songToAddTo)).languages.addAll(songsToAdd.stream().map(H4aKey::new).collect(Collectors.toUnmodifiableSet()))) {
            throw new IllegalArgumentException("Wasn't able to add any: " + songsToAdd + " to: " + songToAddTo);
        }
        if (sizeAfterRemoval != languages.size()) {
            throw new IllegalArgumentException("Wasn't able to add all references: " + songsToAdd + " to: " + songToAddTo);
        }
    }
}
