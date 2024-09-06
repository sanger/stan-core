package uk.ac.sanger.sccp.utils;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author dr6
 */
public class TestZip {

    @Test
    public void testZipForEach() {
        String[] a = {"Alpha", "Beta", "Gamma"};
        Integer[] b = {5,4,3,2,1};
        final List<String> results = new ArrayList<>(a.length);
        Zip.forEach(Arrays.stream(a), Arrays.stream(b), (s, n) -> results.add(s+n));
        assertThat(results).containsExactly("Alpha5", "Beta4", "Gamma3");
    }

    @Test
    public void testZipMap() {
        String[] a = {"Alpha", "Beta", "Gamma"};
        Integer[] b = {5,4,3,2,1};
        List<String> results = Zip.map(Arrays.stream(a), Arrays.stream(b), (s,n) -> s+n).toList();
        assertThat(results).containsExactly("Alpha5", "Beta4", "Gamma3");
    }

    @Test
    public void testEnumerateForEach() {
        String[] array = {"Alpha", "Beta", "Gamma"};
        final List<String> results = new ArrayList<>(array.length);
        Zip.enumerateForEach(Arrays.stream(array), (i,a) -> results.add(i+a));
        assertThat(results).containsExactly("0Alpha", "1Beta", "2Gamma");
    }

    @Test
    public void testEnumerateMap() {
        String[] array = {"Alpha", "Beta", "Gamma"};
        final List<String> results = Zip.enumerateMap(Arrays.stream(array), (i,a) -> i+a).toList();
        assertThat(results).containsExactly("0Alpha", "1Beta", "2Gamma");
    }
}
