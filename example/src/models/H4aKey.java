package models;

import main.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class H4aKey {

    private static final Pattern ID_PATTERN = Pattern.compile("([A-Z]+)([a-z]?\\d+\\D*)");

    public final String id;

    public H4aKey(HymnType type, String number) {
        this(type.h4a + number);
    }

    public H4aKey(String id) {
        this.id = id;
    }

    public HymnalDbKey toHymnalDbKey() {
        if (isLongBeachSong()) {
            return new HymnalDbKey(HymnType.HOWARD_HIGASHI, Integer.toString(Integer.parseInt(number()) - 1000), null);
        }
        return new HymnalDbKey(type(), number(), null);
    }

    private boolean isLongBeachSong() {
        if (type() == HymnType.NEW_SONG && TextUtils.isNumeric(number())) {
            return Integer.parseInt(number()) >= 1001 && Integer.parseInt(number()) <= 1087;
        }
        return false;
    }

    public boolean isSameType(H4aKey h4aKey) {
        if (isLongBeachSong()) {
            return h4aKey.isLongBeachSong();
        }
        return type().equals(h4aKey.type());
    }

    public HymnType type() {
        Matcher matcher = ID_PATTERN.matcher(id);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to extract type from " + id);
        }

        return HymnType.fromH4a(matcher.group(1));
    }

    public String number() {
        Matcher matcher = ID_PATTERN.matcher(id);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to extract number from " + id);
        }

        return matcher.group(2);
    }

    public boolean isTransliterable() {
        HymnType type = type();
        return type == HymnType.CHINESE || type == HymnType.CHINESE_SUPPLEMENT;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof H4aKey) {
            H4aKey key = (H4aKey) obj;
            return id.equals(key.id);
        }
        return false;
    }

    @Override
    public String toString() {
        return id;
    }
}
