package uk.ac.sanger.sccp.stan;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.function.Supplier;

/**
 * Helper to perform explicit transactions
 * @author dr6
 */
@Component
public class Transactor {
    private final PlatformTransactionManager platformTransactionManager;

    @Autowired
    public Transactor(PlatformTransactionManager platformTransactionManager) {
        this.platformTransactionManager = platformTransactionManager;
    }

    public <T> DataFetcher<T> dataFetcher(String transactionName, DataFetcher<T> dataFetcher) {
        return new TransactionDataFetcher<>(platformTransactionManager, transactionName, dataFetcher);
    }

    public <T> Supplier<T> supplier(String transactionName, Supplier<T> supplier) {
        return new TransactingSupplier<>(platformTransactionManager, transactionName, supplier);
    }

    public <T> T transact(String transactionName, Supplier<T> supplier) {
        return transactSupplier(platformTransactionManager, transactionName, supplier);
    }

    private static class TransactingSupplier<T> implements Supplier<T> {
        private final PlatformTransactionManager platformTransactionManager;
        private final String transactionName;
        private final Supplier<T> supplier;

        public TransactingSupplier(PlatformTransactionManager platformTransactionManager,
                                   String transactionName, Supplier<T> supplier) {
            this.platformTransactionManager = platformTransactionManager;
            this.transactionName = transactionName;
            this.supplier = supplier;
        }

        @Override
        public T get() {
            return transactSupplier(platformTransactionManager, transactionName, supplier);
        }
    }

    private static class TransactionDataFetcher<T> implements DataFetcher<T> {
        private final PlatformTransactionManager platformTransactionManager;
        private final String transactionName;
        private final DataFetcher<T> dataFetcher;

        private TransactionDataFetcher(PlatformTransactionManager platformTransactionManager, String transactionName,
                                       DataFetcher<T> dataFetcher) {
            this.platformTransactionManager = platformTransactionManager;
            this.transactionName = transactionName;
            this.dataFetcher = dataFetcher;
        }

        @Override
        public T get(DataFetchingEnvironment dfe) throws Exception {
            return transactDataFetcher(platformTransactionManager, transactionName, dataFetcher, dfe);
        }
    }

    private static <T> T transactSupplier(PlatformTransactionManager platformTransactionManager,
                                  String transactionName, Supplier<T> supplier) {
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setName(transactionName);
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);
        boolean success = false;
        try {
            T value = supplier.get();
            success = true;
            return value;
        } finally {
            if (success) {
                platformTransactionManager.commit(status);
            } else {
                platformTransactionManager.rollback(status);
            }
        }
    }

    private static <T> T transactDataFetcher(PlatformTransactionManager platformTransactionManager,
                                             String transactionName, DataFetcher<T> dataFetcher, DataFetchingEnvironment dfe)
            throws Exception {
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setName(transactionName);
        transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = platformTransactionManager.getTransaction(transactionDefinition);
        boolean success = false;
        try {
            T value = dataFetcher.get(dfe);
            success = true;
            return value;
        } finally {
            if (success) {
                platformTransactionManager.commit(status);
            } else {
                platformTransactionManager.rollback(status);
            }
        }
    }
}
