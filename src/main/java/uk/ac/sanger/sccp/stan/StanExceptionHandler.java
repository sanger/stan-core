package uk.ac.sanger.sccp.stan;

import graphql.ExceptionWhileDataFetching;
import graphql.execution.*;
import graphql.language.SourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Exception handler that adds validation problems when logging a ValidationError.
 * @author dr6
 */
public class StanExceptionHandler implements DataFetcherExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(StanExceptionHandler.class);

    @Override
    public DataFetcherExceptionHandlerResult onException(DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable exception = handlerParameters.getException();
        SourceLocation sourceLocation = handlerParameters.getSourceLocation();
        var path = handlerParameters.getPath();

        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(path, exception, sourceLocation);
        String message = error.getMessage();
        if (exception instanceof ValidationException) {
            message += "\nProblems: "+((ValidationException) exception).getProblems();
        }
        log.warn(message, exception);

        return DataFetcherExceptionHandlerResult.newResult().error(error).build();
    }
}
