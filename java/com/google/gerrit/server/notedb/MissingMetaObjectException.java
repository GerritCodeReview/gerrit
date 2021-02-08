package com.google.gerrit.server.notedb;

/** Separate exception type to throw if requested meta SHA1 is not available. */
public class MissingMetaObjectException extends RuntimeException {
  MissingMetaObjectException(String msg) {
    super(msg);
  }
}
