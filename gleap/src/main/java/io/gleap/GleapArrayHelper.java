package io.gleap;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class GleapArrayHelper<T> {
    public List<T> shiftArray(List<T> arrayList) {
        ArrayList<T> tmp = new ArrayList<>();
        for (int i = 1; i < arrayList.size() - 1; i++) {
            tmp.add(arrayList.get(i));
        }
        return tmp;
    }
}
