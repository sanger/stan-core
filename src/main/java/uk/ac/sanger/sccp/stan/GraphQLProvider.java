package uk.ac.sanger.sccp.stan;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * @author dr6
 */
@Component
public class GraphQLProvider {

    private GraphQL graphQL;

    final PlatformTransactionManager transactionManager;
    final GraphQLDataFetchers graphQLDataFetchers;
    final GraphQLMutation graphQLMutation;

    @Autowired
    public GraphQLProvider(PlatformTransactionManager transactionManager,
                           GraphQLDataFetchers graphQLDataFetchers, GraphQLMutation graphQLMutation) {
        this.transactionManager = transactionManager;
        this.graphQLDataFetchers = graphQLDataFetchers;
        this.graphQLMutation = graphQLMutation;
    }

    @Bean
    public GraphQL graphQL() {
        return graphQL;
    }

    @PostConstruct
    public void init() throws IOException {
        //noinspection UnstableApiUsage
        URL url = Resources.getResource("schema.graphqls");
        //noinspection UnstableApiUsage
        String sdl = Resources.toString(url, Charsets.UTF_8);
        GraphQLSchema graphQLSchema = buildSchema(sdl);
        this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    private GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        RuntimeWiring runtimeWiring = buildWiring();
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("user", graphQLDataFetchers.getUser())
                        .dataFetcher("tissueTypes", graphQLDataFetchers.getTissueTypes())
                        .dataFetcher("hmdmcs", graphQLDataFetchers.getHmdmcs())
                        .dataFetcher("labwareTypes", graphQLDataFetchers.getLabwareTypes())
                        .dataFetcher("mediums", graphQLDataFetchers.getMediums())
                        .dataFetcher("fixatives", graphQLDataFetchers.getFixatives())
                        .dataFetcher("mouldSizes", graphQLDataFetchers.getMouldSizes())
                        .dataFetcher("labware", graphQLDataFetchers.findLabwareByBarcode())
                )
                .type(newTypeWiring("Mutation")
                        .dataFetcher("login", graphQLMutation.logIn())
                        .dataFetcher("logout", graphQLMutation.logOut())
                        .dataFetcher("register", transact(graphQLMutation.register()))
                        .dataFetcher("plan", transact(graphQLMutation.recordPlan()))
                )
                .build();
    }

    private <T> DataFetcher<T> transact(DataFetcher<T> dataFetcher) {
        return dfe -> {
            DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
            transactionDefinition.setName("Mutation transaction");
            transactionDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            TransactionStatus status = transactionManager.getTransaction(transactionDefinition);
            boolean success = false;
            try {
                T value = dataFetcher.get(dfe);
                success = true;
                return value;
            } finally {
                if (success) {
                    transactionManager.commit(status);
                } else {
                    transactionManager.rollback(status);
                }
            }
        };
    }
}