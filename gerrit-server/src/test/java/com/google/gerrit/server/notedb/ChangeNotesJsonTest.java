package com.google.gerrit.server.notedb;

import org.junit.Before;

public class ChangeNotesJsonTest extends ChangeNotesTest {
  @Before
  public void setJson() {
    noteUtil.writeJson = true;
  }
}
