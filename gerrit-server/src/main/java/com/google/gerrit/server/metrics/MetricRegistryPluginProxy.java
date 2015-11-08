package com.google.gerrit.server.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

public class MetricRegistryPluginProxy extends MetricRegistry {
  private final Object pluginOwner;
  private final MetricRegistry coreRegistry;
  private final Map<Object, String> pluginMetrics;

  public MetricRegistryPluginProxy(Object pluginOwner, MetricRegistry coreRegistry, Map<Object, String> pluginMetrics) {
    this.pluginOwner = pluginOwner;
    this.coreRegistry = coreRegistry;
    this.pluginMetrics = pluginMetrics;
  }

  @Override
  public <T extends Metric> T register(String name, T metric)
      throws IllegalArgumentException {
    pluginMetrics.put(pluginOwner, name);
    return coreRegistry.register(name, metric);
  }

  @Override
  public Counter counter(String name) {
    return coreRegistry.counter(name);
  }

  @Override
  public Histogram histogram(String name) {
    return coreRegistry.histogram(name);
  }

  @Override
  public Meter meter(String name) {
    return coreRegistry.meter(name);
  }

  @Override
  public Timer timer(String name) {
    return coreRegistry.timer(name);
  }

  @Override
  public boolean remove(String name) {
    return coreRegistry.remove(name);
  }

  @Override
  public void removeMatching(MetricFilter filter) {
    coreRegistry.removeMatching(filter);
  }

  @Override
  public void addListener(MetricRegistryListener listener) {
    coreRegistry.addListener(listener);
  }

  @Override
  public void removeListener(MetricRegistryListener listener) {
    coreRegistry.removeListener(listener);
  }

  @Override
  public SortedSet<String> getNames() {
    return coreRegistry.getNames();
  }

  @Override
  public SortedMap<String, Gauge> getGauges() {
    return coreRegistry.getGauges();
  }

  @Override
  public SortedMap<String, Gauge> getGauges(MetricFilter filter) {
    return coreRegistry.getGauges(filter);
  }

  @Override
  public SortedMap<String, Counter> getCounters() {
    return coreRegistry.getCounters();
  }

  @Override
  public SortedMap<String, Counter> getCounters(MetricFilter filter) {
    return coreRegistry.getCounters(filter);
  }

  @Override
  public SortedMap<String, Histogram> getHistograms() {
    return coreRegistry.getHistograms();
  }

  @Override
  public SortedMap<String, Histogram> getHistograms(MetricFilter filter) {
    return coreRegistry.getHistograms(filter);
  }

  @Override
  public SortedMap<String, Meter> getMeters() {
    return coreRegistry.getMeters();
  }

  @Override
  public SortedMap<String, Meter> getMeters(MetricFilter filter) {
    return coreRegistry.getMeters(filter);
  }

  @Override
  public SortedMap<String, Timer> getTimers() {
    return coreRegistry.getTimers();
  }

  @Override
  public SortedMap<String, Timer> getTimers(MetricFilter filter) {
    return coreRegistry.getTimers(filter);
  }

  @Override
  public Map<String, Metric> getMetrics() {
    return coreRegistry.getMetrics();
  }
}
