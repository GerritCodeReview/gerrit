package com.google.gerrit.exceptions;

/** An exception thrown when the set topic is illegal. */
public class IllegalTopicException extends Exception {
  private static final long serialVersionUID = 1L;

  public IllegalTopicException(String message) {
    super(message);
  }

  public IllegalTopicException(String message, Throwable cause) {
    super(message, cause);
  }
}
