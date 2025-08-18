package uk.ac.sanger.sccp.utils;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests {@link Zip}
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

    @Test
    public void testBiMapToZip() {
        String[] array = {"Alpha", "Beta", "Gamma"};
        final List<String> results = Zip.enumerate(Arrays.stream(array)).map((i,a) -> i+a, (i,a) -> a+i)
                .map((a,b) -> a+b).toList();
        assertThat(results).containsExactly("0AlphaAlpha0", "1BetaBeta1", "2GammaGamma2");
    }

    @Test
    public void testMapToZip() {
        String[] array = {"Alpha", "Beta", "Gamma"};
        final List<String> results = Zip.enumerate(Arrays.stream(array)).map(i -> 2*i, String::toUpperCase)
                .map((i,a) -> i+a).toList();
        assertThat(results).containsExactly("0ALPHA", "2BETA", "4GAMMA");
    }

    @Test
    public void testFilter() {
        String[] array = {"Alpha", "Beta", "Gamma", "Delta"};
        List<String> results = Zip.enumerate(Arrays.stream(array)).filter((i,a) -> i%2==0).map((a,b) -> a+b).toList();
        assertThat(results).containsExactly("0Alpha", "2Gamma");
    }

    @Test
    public void testEntryZipForEach() {
        String[] array = {"Alpha", "Beta", "Gamma", "Delta"};
        List<String> results = new ArrayList<>(2);
        Zip.enumerate(Arrays.stream(array)).filter((i,a) -> i%2==0).forEach((i,a) -> results.add(i+a));
        assertThat(results).containsExactly("0Alpha", "2Gamma");
    }

    @Test
    public void testEntryZipMapToZip() {
        String[] array = {"Alpha", "Beta", "Gamma", "Delta"};
        List<String> results = Zip.enumerate(Arrays.stream(array)).filter((i,a) -> i%2==0)
                .map(i -> 2*i, String::toUpperCase)
                .map((i,b) -> i+b).toList();
        assertThat(results).containsExactly("0ALPHA", "4GAMMA");
    }
}
