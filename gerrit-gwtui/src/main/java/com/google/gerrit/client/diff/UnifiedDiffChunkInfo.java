package com.google.gerrit.client.diff;

public class UnifiedDiffChunkInfo extends DiffChunkInfo {

  private int cmLine;

  UnifiedDiffChunkInfo(DisplaySide side,
      int start, int end, int cmLine, boolean edit) {
    super(side, start, end, edit);
    this.cmLine = cmLine;
  }

  int getCmLine() {
    return cmLine;
  }
}
