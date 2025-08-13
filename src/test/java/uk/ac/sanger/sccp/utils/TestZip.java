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
        Zip.of(Arrays.stream(a), Arrays.stream(b)).forEach((s, n) -> results.add(s+n));
        assertThat(results).containsExactly("Alpha5", "Beta4", "Gamma3");
    }

    @Test
    public void testZipMap() {
        String[] a = {"Alpha", "Beta", "Gamma"};
        Integer[] b = {5,4,3,2,1};
        List<String> results = Zip.of(Arrays.stream(a), Arrays.stream(b)).map((s,n) -> s+n).toList();
        assertThat(results).containsExactly("Alpha5", "Beta4", "Gamma3");
    }

    @Test
    public void testEnumerateForEach() {
        String[] array = {"Alpha", "Beta", "Gamma"};
        final List<String> results = new ArrayList<>(array.length);
        Zip.enumerate(Arrays.stream(array)).forEach((i,a) -> results.add(i+a));
        assertThat(results).containsExactly("0Alpha", "1Beta", "2Gamma");
    }

    @Test
    public void testEnumerateMap() {
        String[] array = {"Alpha", "Beta", "Gamma"};
        final List<String> results = Zip.enumerate(Arrays.stream(array)).map((i,a) -> i+a).toList();
        assertThat(results).containsExactly("0Alpha", "1Beta", "2Gamma");
    }
}
