package com.google.gerrit.server.metrics;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.TimeUnit;

public class GerritConsoleReporter implements MetricsReporter {
  private ConsoleReporter consoleReporter = null;

  public GerritConsoleReporter() {
  }

  public GerritConsoleReporter(ConsoleReporter consoleReporter) {
    this.consoleReporter = consoleReporter;
  }

  @Override
  public MetricsReporter setup(Config config, MetricRegistry registry) {
    ConsoleReporter consoleReporter = ConsoleReporter.forRegistry(registry)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build();
    return new GerritConsoleReporter(consoleReporter);
  }

  public String defVal(String str, String defaultValue) {
    return str != null ? str : defaultValue;
  }

  @Override
  public void start() {
    consoleReporter.start(10, TimeUnit.SECONDS);
  }

  @Override
  public void stop() {
    consoleReporter.stop();
  }

  @Override
  public String getName() {
    return "console";
  }

}
