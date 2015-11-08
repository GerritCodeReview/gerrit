package com.google.gerrit.server.metrics;

import static com.codahale.metrics.MetricRegistry.name;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;

import org.eclipse.jgit.lib.Config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class GerritGraphiteReporter implements MetricsReporter {
  private GraphiteReporter graphiteReporter = null;

  public GerritGraphiteReporter() {
  }

  public GerritGraphiteReporter(GraphiteReporter graphiteReporter) {
    this.graphiteReporter = graphiteReporter;
  }

  @Override
  public MetricsReporter setup(Config config, MetricRegistry registry) {
    String host =
        defVal(config.getString("metrics", getName(), "host"), "localhost");
    Graphite graphite = new Graphite(new InetSocketAddress(host,
        config.getInt("metrics", getName(), "port", 2003)));

    GraphiteReporter graphiteReporter = null;
    try {
      graphiteReporter = GraphiteReporter.forRegistry(registry)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .prefixedWith(defVal(config.getString("metrics", getName(), "prefix"),
              name("gerrit", InetAddress.getLocalHost().getHostName())))
          .filter(MetricFilter.ALL).build(graphite);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return new GerritGraphiteReporter(graphiteReporter);
  }

  public String defVal(String str, String defaultValue) {
    return str != null ? str : defaultValue;
  }

  @Override
  public void start() {
    graphiteReporter.start(1, TimeUnit.MINUTES);
  }

  @Override
  public void stop() {
    graphiteReporter.stop();
  }

  @Override
  public String getName() {
    return "graphite";
  }

}
