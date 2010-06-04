package com.google.gerrit.server;

import junit.framework.TestCase;

public class ChangeUtilTest extends TestCase {

  public void testInvertSortKey() {
    assertEquals("FFFFFFFFFFFFFFFF", ChangeUtil.invertSortKey(
        "0000000000000000").toUpperCase());

    assertEquals("0000000000000001", ChangeUtil.invertSortKey(
        "FFFFFFFFFFFFFFFE").toUpperCase());

    assertEquals("0001600000000000", ChangeUtil.invertSortKey(
        "FFFE9FFFFFFFFFFF").toUpperCase());

    assertEquals("/", ChangeUtil.invertSortKey("z").toUpperCase());

    assertEquals("Z", ChangeUtil.invertSortKey("/").toUpperCase());
  }

}
