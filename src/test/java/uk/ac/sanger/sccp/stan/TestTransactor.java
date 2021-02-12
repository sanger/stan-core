package uk.ac.sanger.sccp.stan;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.IOException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link Transactor}
 * @author dr6
 */
public class TestTransactor {
    private PlatformTransactionManager mockPtm;
    private Transactor transactor;
    private TransactionStatus txStatus;
    private final String TXNAME = "TXNAME";

    @BeforeEach
    void setup() {
        mockPtm = mock(PlatformTransactionManager.class);
        transactor = new Transactor(mockPtm);
        txStatus = mock(TransactionStatus.class);
        when(mockPtm.getTransaction(any())).thenReturn(txStatus);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testDataFetcher(boolean success) throws Exception {
        DataFetchingEnvironment dfe = mock(DataFetchingEnvironment.class);
        DataFetcher<String> df;
        final IOException ex = success ? null : new IOException("Everything.");
        if (success) {
            df = _dfe -> (_dfe==dfe ? "OK" : "Not OK");
        } else {
            df = _dfe -> { throw ex; };
        }

        DataFetcher<String> tx = transactor.dataFetcher(TXNAME, df);

        if (success) {
            assertEquals("OK", tx.get(dfe));
        } else {
            assertException(ex, () -> tx.get(dfe));
        }
        verifyTransaction(success);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testSupplier(boolean success) {
        final IllegalArgumentException ex = success ? null : new IllegalArgumentException("Everything.");
        final Supplier<String> supplier;
        if (success) {
            supplier = () -> "OK";
        } else {
            supplier = () -> { throw ex; };
        }
        Supplier<String> tx = transactor.supplier(TXNAME, supplier);
        if (success) {
            assertEquals("OK", tx.get());
        } else {
            assertException(ex, tx::get);
        }
        verifyTransaction(success);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testTransact(boolean success) {
        final IllegalArgumentException ex = success ? null : new IllegalArgumentException("Everything.");
        final Supplier<String> supplier;
        if (success) {
            supplier = () -> "OK";
        } else {
            supplier = () -> { throw ex; };
        }
        if (success) {
            assertEquals("OK", transactor.transact(TXNAME, supplier));
        } else {
            assertException(ex, () -> transactor.transact(TXNAME, supplier));
        }
        verifyTransaction(success);
    }

    private void assertException(Exception ex, Executable exec) {
        assertThat(assertThrows(ex.getClass(), exec)).isSameAs(ex);
    }

    private void verifyTransaction(boolean success) {
        DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
        txDef.setName(TXNAME);
        txDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        verify(mockPtm).getTransaction(txDef);

        verify(mockPtm, times(success ? 1 : 0)).commit(txStatus);
        verify(mockPtm, times(success ? 0 : 1)).rollback(txStatus);
    }
}
