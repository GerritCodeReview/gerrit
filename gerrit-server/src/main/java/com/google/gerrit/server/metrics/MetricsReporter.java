package com.google.gerrit.server.metrics;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

import com.codahale.metrics.MetricRegistry;

@ExtensionPoint
public interface MetricsReporter {
  public void setup(MetricRegistry registry);

  public void start();

  public void stop();
}
