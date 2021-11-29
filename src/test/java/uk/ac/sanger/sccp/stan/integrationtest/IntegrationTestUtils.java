package uk.ac.sanger.sccp.stan.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import uk.ac.sanger.sccp.stan.model.store.BasicLocation;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;
import uk.ac.sanger.sccp.utils.GraphQLClient;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Utility functions used within Stan's integration tests
 * @author dr6
 */
public class IntegrationTestUtils {
    @SuppressWarnings("unchecked")
    public static <T> T chainGet(Object container, Object... accessors) {
        for (int i = 0; i < accessors.length; i++) {
            Object accessor = accessors[i];
            assert container != null;
            Object item;
            if (accessor instanceof Integer) {
                if (!(container instanceof List)) {
                    throw new IllegalArgumentException("["+accessor+"]: container is not list: "+container);
                }
                item = ((List<?>) container).get((int) accessor);
            } else {
                if (!(container instanceof Map)) {
                    throw new IllegalArgumentException("["+accessor+"]: container is not map: "+container);
                }
                item = ((Map<?, ?>) container).get(accessor);
            }
            if (item==null && i < accessors.length-1) {
                throw new IllegalArgumentException("No such element as "+accessor+" in object "+container);
            }
            container = item;
        }
        return (T) container;
    }

    public static <T> List<T> chainGetList(Object container, Object... accessors) {
        return chainGet(container, accessors);
    }

    public static <E> void swap(List<E> list, int i, int j) {
        list.set(i, list.set(j, list.get(i)));
    }

    public static <K, V> Map<K, V> nullableMapOf(K key1, V value1, K key2, V value2, K key3, V value3) {
        Map<K, V> map = new HashMap<>(3);
        map.put(key1, value1);
        map.put(key2, value2);
        map.put(key3, value3);
        return map;
    }

    public static List<Map<String, String>> tsvToMap(String tsv) {
        String[] lines = tsv.split("\n");
        String[] headers = lines[0].split("\t");
        return IntStream.range(1, lines.length)
                .mapToObj(i -> lines[i].split("\t", -1))
                .map(values ->
                    IntStream.range(0, headers.length)
                            .boxed()
                            .collect(toMap(j -> headers[j], j -> values[j]))
        ).collect(toList());
    }

    public static void stubStorelightUnstore(StorelightClient mockStorelightClient) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode storelightDataNode = objectMapper.createObjectNode()
                .set("unstoreBarcodes", objectMapper.createObjectNode().put("numUnstored", 2));
        GraphQLClient.GraphQLResponse storelightResponse = new GraphQLClient.GraphQLResponse(storelightDataNode, null);
        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("unstoreBarcodes("), anyString())).thenReturn(storelightResponse);
    }

    public static void stubStorelightBasicLocation(StorelightClient mockStorelightClient, Map<String, BasicLocation> locations) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode storelightDataNode;
        if (locations==null || locations.isEmpty()) {
            storelightDataNode = objectMapper.createObjectNode()
                    .set("stored", objectMapper.createArrayNode());
        } else {
            ArrayNode itemArrayNode = objectMapper.createArrayNode();
            for (var entry : locations.entrySet()) {
                var loc = entry.getValue();
                ObjectNode node = objectMapper.createObjectNode()
                        .put("barcode", entry.getKey())
                        .put("address", loc.getAddress()!=null ? loc.getAddress().toString() : null)
                        .set("location", objectMapper.createObjectNode().put("barcode", loc.getBarcode()));
                itemArrayNode.add(node);
            }
            storelightDataNode = objectMapper.createObjectNode().set("stored", itemArrayNode);
        }
        GraphQLClient.GraphQLResponse storelightResponse = new GraphQLClient.GraphQLResponse(storelightDataNode, null);
        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("stored("), any())).thenReturn(storelightResponse);
    }

    public static void verifyStorelightQuery(StorelightClient mockStorelightClient, Collection<String> contents, String username) throws Exception {
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStorelightClient).postQuery(queryCaptor.capture(), eq(username));
        String storelightQuery = queryCaptor.getValue();
        assertThat(storelightQuery).contains(contents);
    }
}
