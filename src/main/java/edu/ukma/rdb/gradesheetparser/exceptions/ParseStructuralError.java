package edu.ukma.rdb.gradesheetparser.exceptions;

public class ParseStructuralError extends RuntimeException{
    public ParseStructuralError(String message) {
        super(message);
    }
}
