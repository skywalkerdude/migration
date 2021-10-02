package models;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reference)) return false;
        Reference that = (Reference) o;
        return text.equals(that.text) && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, key);
    }

    @Override
    public String toString() {
        return "LanguageReference{" +
                "text='" + text + '\'' +
                ", key=" + key +
                '}';
    }
}
