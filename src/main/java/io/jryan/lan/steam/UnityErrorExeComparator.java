package io.jryan.lan.steam;

import java.util.Comparator;

public class UnityErrorExeComparator implements Comparator<String> {

    @Override
    public int compare(String s1, String s2) {
        // Used to de-prioritize unity error exes
        int ret = 0;
        if (s1.startsWith("Unity")) {
            ret -= 1;
        }
        if (s2.startsWith("Unity")) {
            ret += 1;
        }
        return ret;
    }
}
