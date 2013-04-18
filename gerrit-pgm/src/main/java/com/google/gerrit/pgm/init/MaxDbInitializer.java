package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.InitUtil.username;

public class MaxDbInitializer implements DatabaseConfigInitializer {

  @Override
  public void initConfig(Section databaseSection) {
    final String defPort = "(maxdb default)";
    databaseSection.string("Server hostname", "hostname", "localhost");
    databaseSection.string("Server port", "port", defPort, true);
    databaseSection.string("Database name", "database", "reviewdb");
    databaseSection.string("Database username", "username", username());
    databaseSection.password("username", "password");
  }
}
