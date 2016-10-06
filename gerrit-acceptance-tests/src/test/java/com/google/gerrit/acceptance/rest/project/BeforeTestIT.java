package com.google.gerrit.acceptance.rest.project;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.testutil.ConfigSuite;

import org.eclipse.jgit.lib.Config;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Created by hanwen on 10/6/16.
 */
@RunWith(ConfigSuite.class)
public class BeforeTestIT {
  @BeforeClass
  public static void mySetup() {
    System.err.println("mysetup called.");
  }

  @ConfigSuite.Parameter
  public Config baseConfig;

  @Test
  public void testBla() {
    System.err.println("testBla called.");

  }
}
