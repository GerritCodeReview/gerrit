package com.google.gerrit.server.metrics;

import com.codahale.metrics.MetricRegistry;

import org.eclipse.jgit.lib.Config;

public interface MetricsReporter {
  public MetricsReporter setup(Config config, MetricRegistry registry);

  public void start();

  public void stop();

  public String getName();
}
