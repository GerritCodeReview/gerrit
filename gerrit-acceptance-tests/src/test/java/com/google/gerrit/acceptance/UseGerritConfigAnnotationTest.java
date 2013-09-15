package com.google.gerrit.acceptance;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class UseGerritConfigAnnotationTest extends AbstractDaemonTest {

  @Inject
  @GerritServerConfig
  Config serverConfig;

  @Test
  @GerritConfig("[x]\ny=z")
  public void testServerConig() {
    String value = serverConfig.getString("x", null, "y");
    System.out.println("x.y = " + value);
  }
}
