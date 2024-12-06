package com.google.gerrit.acceptance.server.index;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.WaitUtil.waitUntil;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;

@UseLocalDisk
public class IndexMetricIT extends AbstractDaemonTest {

  @Inject MetricMaker metricMaker;

  @Inject MetricRegistry metricRegistry;

  @Test
  public void checkProjectsIndexMetric() throws Exception {
    gApi.projects().create("some_project");
    waitUntil(() -> (int) getMetric("indexes/projects").getValue() == 4, Duration.ofSeconds(10));
  }

  @Test
  public void checkChangeIndexMetric() throws Exception {
    createChange();
    waitUntil(() -> (int) getMetric("indexes/changes").getValue() == 1, Duration.ofSeconds(10));
  }

  @Test
  public void checkAccountIndexMetric() throws Exception {
    waitUntil(() -> (int) getMetric("indexes/accounts").getValue() == 2, Duration.ofSeconds(10));
  }

  @Test
  public void checkGroupIndexMetric() throws Exception {
    waitUntil(() -> (int) getMetric("indexes/groups").getValue() == 3, Duration.ofSeconds(10));
  }

  private <V> Gauge<V> getMetric(String name) {
    System.out.println(metricRegistry.getMetrics());
    @SuppressWarnings("unchecked")
    Gauge<V> gauge = (Gauge<V>) metricRegistry.getMetrics().get(name);
    assertWithMessage(name).that(gauge).isNotNull();
    return gauge;
  }
}
