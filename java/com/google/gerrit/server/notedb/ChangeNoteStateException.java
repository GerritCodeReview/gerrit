package com.google.gerrit.server.notedb;

import java.io.IOException;

public class ChangeNoteStateException extends IOException {
  private static final long serialVersionUID = 1L;

  ChangeNoteStateException(String message) {
    super(message);
  }
}
