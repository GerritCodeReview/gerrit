package com.google.gerrit.acceptance.server.index;


import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@UseLocalDisk
public class IndexMetricIT extends AbstractDaemonTest {

  @Inject
  MetricMaker metricMaker;

  @Inject
  MetricRegistry metricRegistry;

  @Test
  public void checkIndexAccounts() {
    assertThat(getMetric("indexes/accounts").getValue()).isEqualTo(-1);
  }

  @Test
  public void checkIndexChanges() {
    assertThat(getMetric("indexes/changes").getValue()).isEqualTo(-1);
  }

  @Test
  public void checkIndexGroups() {
    assertThat(getMetric("indexes/groups").getValue()).isEqualTo(-1);
  }

  @Test
  public void checkIndexProjects() {
    assertThat(getMetric("indexes/projects").getValue()).isEqualTo(-1);
  }
  
  private <V> Gauge<V> getMetric(String name) {
    System.out.println(metricRegistry.getMetrics());
    @SuppressWarnings("unchecked")
    Gauge<V> gauge = (Gauge<V>) metricRegistry.getMetrics().get(name);
    assertWithMessage(name).that(gauge).isNotNull();
    return gauge;
  }
}
