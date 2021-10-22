package models;

/**
 * Models a reference that includes the text (e.g. 'English', 'Cebuano', 'New Tune', 'Alternate Tune', etc.) and the key
 * to the hymn it references
 */
public class Reference {

    public final String text;
    public final HymnalDbKey key;

    public static Reference create(String text, HymnalDbKey key) {
        return new Reference(text, key);
    }

    private Reference(String text, HymnalDbKey key) {
        this.text = text;
        this.key = key;
    }

    /**
     * Only use {@link #key} as part of equals and hashCode() because we don't want two references that point to the
     * same destination as part of a single song.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reference)) return false;
        Reference that = (Reference) o;
        return key.equals(that.key);
    }

    /**
     * Only use {@link #key} as part of equals and hashCode() because we don't want two references that point to the
     * same destination as part of a single song.
     */
    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "Reference{" +
                "text='" + text + '\'' +
                ", key=" + key +
                '}';
    }
}
