package uk.ac.sanger.sccp.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class TestTopoSorter {
    static void forCombinations(final List<Integer> items, BiConsumer<Integer, Integer> action) {
        for (int i = 0; i < items.size()-1; ++i) {
            for (int j = i+1; j < items.size(); ++j) {
                action.accept(items.get(i), items.get(j));
            }
        }
    }

    @Test
    public void testSort() {
        List<Integer> input = List.of(1, 5, 10, 2, 99, 3, 11, 50);
        TopoSorter<Integer> sorter = new TopoSorter<>(input);
        forCombinations(input, (ni, nj) -> {
            if (ni%nj==0) {
                sorter.addLink(nj, ni);
            } else if (nj%ni==0) {
                sorter.addLink(ni, nj);
            }
        });

        List<Integer> result = sorter.sort();
        assertThat(result).containsExactlyInAnyOrderElementsOf(input);
        forCombinations(result, (ni, nj) -> {
            if (ni%nj==0) {
                fail(String.format("%s %% %s == 0", ni, nj));
            }
        });
    }
}