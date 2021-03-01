package uk.ac.sanger.sccp.stan.service.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.config.StorelightConfig;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;

import java.io.IOException;
import java.net.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link StorelightClient}
 * @author dr6
 */
public class TestStorelightClient {
    private StorelightConfig mockStorelightConfig;
    private StorelightClient storelightClient;

    @BeforeEach
    void setup() {
        mockStorelightConfig = mock(StorelightConfig.class);
        storelightClient = spy(new StorelightClient(mockStorelightConfig));
    }

    @Test
    public void testSetHeaders() {
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        String user = "dr6";
        String apiKey = "Squirrel";
        when(mockStorelightConfig.getApiKey()).thenReturn(apiKey);

        storelightClient.setHeaders(mockConnection, user);
        verify(mockConnection).setRequestProperty("Content-Type", "application/json");
        verify(mockConnection).setRequestProperty("Accept", "application/json");
        verify(mockConnection).setRequestProperty("STORELIGHT-APIKEY", apiKey);
        verify(mockConnection).setRequestProperty("STORELIGHT-USER", user);
    }

    @Test
    public void testGetURL() throws MalformedURLException {
        String host = "http://storelighturl/graphql";
        when(mockStorelightConfig.getHost()).thenReturn(host);
        assertEquals(new URL(host), storelightClient.getURL());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testPostQuery(boolean successful) throws IOException {
        String host = "http://storelighturl/graphql";
        String apiKey = "Squirrel";
        when(mockStorelightConfig.getHost()).thenReturn(host);
        HttpURLConnection mockConnection = mock(HttpURLConnection.class);
        doReturn(mockConnection).when(storelightClient).openConnection(any());
        when(mockStorelightConfig.getApiKey()).thenReturn(apiKey);

        String query = "{ Something }";
        String user = "dr6";
        if (successful) {
            GraphQLResponse response = new GraphQLResponse(null, null);
            doReturn(response).when(storelightClient).postQuery(mockConnection, query);
            assertSame(response, storelightClient.postQuery(query, user));
        } else {
            doThrow(IOException.class).when(storelightClient).postQuery(mockConnection, query);
            assertThrows(IOException.class, () -> storelightClient.postQuery(query, user));
        }

        verify(storelightClient).openConnection(new URL(host));
        verify(storelightClient).setHeaders(mockConnection, user);
        verify(storelightClient).postQuery(mockConnection, query);
        verify(mockConnection).disconnect();
    }
}
