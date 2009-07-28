package com.google.gerrit.server.patchsetinfo;

import java.io.IOException;

public final class PatchSetInfoNotAvailableException extends IOException {
  
  public PatchSetInfoNotAvailableException(Exception cause) {
    super(cause);
  }

}
