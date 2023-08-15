package uk.ac.sanger.sccp.stan.service;

import graphql.*;
import graphql.language.SourceLocation;

import java.util.*;

/**
 * @author dr6
 */
public class ValidationException extends RuntimeException implements GraphQLError {
    private final Collection<?> problems;

    public ValidationException(Collection<?> problems) {
        this("The request could not be validated.", problems);
    }

    public ValidationException(String message, Collection<?> problems) {
        super(message);
        this.problems = problems;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return Collections.emptyList();
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorType.ValidationError;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("problems", getProblems());
    }

    public Collection<?> getProblems() {
        return this.problems;
    }
}
