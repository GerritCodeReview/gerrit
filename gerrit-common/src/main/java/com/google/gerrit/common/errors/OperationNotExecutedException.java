package com.google.gerrit.common.errors;

/** Error indicating the operation execution is not allowed. */
public class OperationNotExecutedException extends Exception {
  private static final long serialVersionUID = 1L;

  public static final String MESSAGE = "Operation was not executed. ";

  public OperationNotExecutedException() {
    super(MESSAGE);
  }

  public OperationNotExecutedException(final String why) {
    super(MESSAGE + why);
  }
}
