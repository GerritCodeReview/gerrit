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

  private final Duration METRIC_TRIGGER_TIMEOUT = Duration.ofSeconds(5);

  @Inject MetricMaker metricMaker;
  @Inject MetricRegistry metricRegistry;

  @Test
  public void checkProjectsIndexMetric() throws Exception {
    waitUntil(() -> (int) getMetric("indexes/projects").getValue() == 3, METRIC_TRIGGER_TIMEOUT);
    gApi.projects().create("some_project");
    waitUntil(() -> (int) getMetric("indexes/projects").getValue() == 4, METRIC_TRIGGER_TIMEOUT);
  }

  @Test
  public void checkChangesIndexMetric() throws Exception {
    waitUntil(() -> (int) getMetric("indexes/changes").getValue() == 0, METRIC_TRIGGER_TIMEOUT);
    createChange();
    waitUntil(() -> (int) getMetric("indexes/changes").getValue() == 1, METRIC_TRIGGER_TIMEOUT);
  }

  @Test
  public void checkAccountsIndexMetric() throws Exception {
    waitUntil(() -> (int) getMetric("indexes/accounts").getValue() == 2, METRIC_TRIGGER_TIMEOUT);
    gApi.accounts().create("some_account");
    waitUntil(() -> (int) getMetric("indexes/accounts").getValue() == 3, METRIC_TRIGGER_TIMEOUT);
  }

  @Test
  public void checkGroupsIndexMetric() throws Exception {
    waitUntil(() -> (int) getMetric("indexes/groups").getValue() == 3, METRIC_TRIGGER_TIMEOUT);
    gApi.groups().create("some_group");
    waitUntil(() -> (int) getMetric("indexes/groups").getValue() == 4, METRIC_TRIGGER_TIMEOUT);
  }

  private <V> Gauge<V> getMetric(String name) {
    @SuppressWarnings("unchecked")
    Gauge<V> gauge = (Gauge<V>) metricRegistry.getMetrics().get(name);
    assertWithMessage(name).that(gauge).isNotNull();
    return gauge;
  }
}
