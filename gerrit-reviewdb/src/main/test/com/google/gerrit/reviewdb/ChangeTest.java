package com.google.gerrit.reviewdb;

import junit.framework.TestCase;

public class ChangeTest extends TestCase {

  public void testSortKeyDesc(){
    Change c = new Change();

    c.setSortKey("000d4ad500003a36");
    assertEquals("fff2b52affffc5c9",c.sortKeyDesc.toLowerCase());

    c.setSortKey("0000000000000000");
    assertEquals("ffffffffffffffff",c.sortKeyDesc.toLowerCase());
  }

}
