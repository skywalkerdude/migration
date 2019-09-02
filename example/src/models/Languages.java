package models;

import java.util.ArrayList;
import java.util.List;

//{
//   "data": [
//     {
//       "path": "\/en\/hymn\/cb\/10",
//       "value": "Cebuano"
//     },
//     {
//       "path": "\/en\/hymn\/ch\/7?gb=1",
//       "value": "\u8bd7\u6b4c(\u7b80)"
//     },
//     {
//       "path": "\/en\/hymn\/ch\/7",
//       "value": "\u8a69\u6b4c(\u7e41)"
//     },
//     {
//       "path": "\/en\/hymn\/ht\/10",
//       "value": "Tagalog"
//     }
//   ],
//   "name": "Languages"
// }
public class Languages {

    private String name;
    private List<Datum> data;

    public Languages() {
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Datum> getData() {
        return data;
    }

    public void setData(List<Datum> data) {
        this.data = data;
    }

    public List<String> getPaths() {
        List<String> rtn = new ArrayList<>();
        for (Datum datum : data) {
            rtn.add(datum.getPath());
        }
        return rtn;
    }

    public List<String> getValues() {
        List<String> rtn = new ArrayList<>();
        for (Datum datum : data) {
            rtn.add(datum.getValue());
        }
        return rtn;
    }
}
