package uk.ac.sanger.sccp.stan;

import graphql.*;
import graphql.spring.web.servlet.GraphQLInvocation;
import graphql.spring.web.servlet.GraphQLInvocationData;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;
import uk.ac.sanger.sccp.stan.config.ApiKeyConfig;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author dr6
 */
@Component
@Primary
public class CustomGraphQLInvocation implements GraphQLInvocation {
    private final GraphQL graphQL;
    private final ApiKeyConfig apiKeyConfig;

    @Autowired
    public CustomGraphQLInvocation(GraphQL graphQL, ApiKeyConfig apiKeyConfig) {
        this.graphQL = graphQL;
        this.apiKeyConfig = apiKeyConfig;
    }

    @Override
    public CompletableFuture<ExecutionResult> invoke(GraphQLInvocationData invocationData, WebRequest request) {
        Map<String, Object> variables = invocationData.getVariables();
        StanRequestContext context = getStoreRequestContext(request, variables);

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(invocationData.getQuery())
                .operationName(invocationData.getOperationName())
                .variables(variables)
                .context(context)
                .build();
        return graphQL.executeAsync(executionInput);
    }

    private String getHeaderOrVariable(String name, WebRequest request, Map<String, ?> variables) {
        String value = request.getHeader(name);
        if (value!=null) {
            return value;
        }
        Object obj = variables.get(name);
        return (obj!=null ? obj.toString() : null);
    }

    @NotNull
    private StanRequestContext getStoreRequestContext(WebRequest request, Map<String, Object> variables) {
        String apiKey = getHeaderOrVariable(StanRequestContext.API_KEY_HEADER, request, variables);
        String username = (apiKey==null ? null : apiKeyConfig.getUsername(apiKey));
        return new StanRequestContext(apiKey, username);
    }
}
