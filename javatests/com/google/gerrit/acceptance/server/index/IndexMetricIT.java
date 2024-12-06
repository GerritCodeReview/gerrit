package com.google.gerrit.acceptance.server.index;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.inject.Inject;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class IndexMetricIT extends AbstractDaemonTest {

  @Inject
  private TestMetricMaker testMetricMaker;

  @Test
  public void checkIndexChange() {
    testMetricMaker.reset();
    assertThat(testMetricMaker.getCount("indexes/account")).isEqualTo(1);
  }
}
