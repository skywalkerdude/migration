package models;

//     {
//       "path": "\/en\/hymn\/ch\/7?gb=1",
//       "value": "\u8bd7\u6b4c(\u7b80)"
//     },
public class Datum {

    private String path;
    private String value;

    public Datum() {
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value + " " + path;
    }
}
