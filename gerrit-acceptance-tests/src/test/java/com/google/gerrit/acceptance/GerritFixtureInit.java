package com.google.gerrit.acceptance;

public interface GerritFixtureInit {
  void setUp() throws Exception;
  void tearDown() throws Exception;
}
