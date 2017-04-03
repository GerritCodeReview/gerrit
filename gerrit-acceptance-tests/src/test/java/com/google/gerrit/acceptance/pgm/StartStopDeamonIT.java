package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import org.junit.Test;
import org.junit.runner.Description;

public class StartStopDeamonIT extends AbstractDaemonTest {
  Description suiteDescription = Description.createSuiteDescription(StartStopDeamonIT.class);

  @Test
  public void startAndStopGerritWithoutThreadLeaksTest() throws Exception {
    final ThreadMXBean thbean = ManagementFactory.getThreadMXBean();
    for (int i = 0; i < 50; i++) {
      int startThreads = thbean.getThreadCount();
      beforeTest(suiteDescription);
      stopCommonServer();
      assertThat(Thread.activeCount()).isLessThan(startThreads);
    }
  }
}
