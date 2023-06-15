package com.google.gerrit.metrics.dropwizard;

import static com.google.common.truth.Truth.assertThat;

import com.codahale.metrics.MetricRegistry;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricsReservoirConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class BucketedCallbackTest {

  @Mock MetricsReservoirConfig reservoirConfigMock;

  MetricRegistry registry;

  DropWizardMetricMaker metrics;

  @Before
  public void setupMocks() {
    registry = new MetricRegistry();
    metrics = new DropWizardMetricMaker(registry, reservoirConfigMock);
  }

  @Test
  public void shouldCreateUnregisteredMetricWithFormattedName() {
    createBucketedCallback().getOrCreate("foo/bar");
    assertThat(registry.getNames()).containsExactly("name/foo-bar");
  }

  @Test
  public void shouldNotCreatePreviouslyRegisteredMetric() {
    BucketedCallback<Long> bc = createBucketedCallback();
    bc.getOrCreate("foo/bar");
    bc.getOrCreate("foo/bar");
    assertThat(registry.getNames()).containsExactly("name/foo-bar");
  }

  @Test
  public void shouldNotCreateNewMetricWhenFormattedNameMatchesPreviouslyRegisteredMetric() {
    BucketedCallback<Long> bc = createBucketedCallback();
    bc.getOrCreate("foo/bar");
    bc.getOrCreate("foo-bar");
    assertThat(registry.getNames()).containsExactly("name/foo-bar");
  }

  private BucketedCallback<Long> createBucketedCallback() {
    return new CallbackMetricImpl1<>(
        metrics,
        registry,
        "name",
        Long.class,
        new Description("description"),
        Field.ofString("string_field", (metadataBuilder, stringField) -> {}).build());
  }
}
