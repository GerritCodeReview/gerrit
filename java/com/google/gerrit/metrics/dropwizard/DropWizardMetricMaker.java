// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.metrics.dropwizard;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.metrics.dropwizard.MetricResource.METRIC_KIND;
import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.FieldOrdering;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.Histogram0;
import com.google.gerrit.metrics.Histogram1;
import com.google.gerrit.metrics.Histogram2;
import com.google.gerrit.metrics.Histogram3;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.MetricsReservoirConfig;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.metrics.Timer3;
import com.google.gerrit.metrics.proc.JGitMetricModule;
import com.google.gerrit.metrics.proc.ProcMetricModule;
import com.google.gerrit.server.cache.CacheMetrics;
import com.google.gerrit.server.config.MetricsReservoirConfigImpl;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects Gerrit metric package onto DropWizard.
 *
 * @see <a href="http://www.dropwizard.io/">DropWizard</a>
 */
@Singleton
public class DropWizardMetricMaker extends MetricMaker {
  public static class ApiModule extends RestApiModule {
    private final Optional<MetricsReservoirConfig> metricsReservoirConfig;

    public ApiModule(MetricsReservoirConfig metricsReservoirConfig) {
      this.metricsReservoirConfig = Optional.of(metricsReservoirConfig);
    }

    public ApiModule() {
      this.metricsReservoirConfig = Optional.empty();
    }

    @Override
    protected void configure() {
      if (metricsReservoirConfig.isPresent()) {
        bind(MetricsReservoirConfig.class).toInstance(metricsReservoirConfig.get());
      } else {
        bind(MetricsReservoirConfig.class)
            .to(MetricsReservoirConfigImpl.class)
            .in(Scopes.SINGLETON);
      }
      bind(MetricRegistry.class).in(Scopes.SINGLETON);
      bind(DropWizardMetricMaker.class).in(Scopes.SINGLETON);
      bind(MetricMaker.class).to(DropWizardMetricMaker.class);

      install(new ProcMetricModule());
      install(new JGitMetricModule());
    }
  }

  public static class RestModule extends RestApiModule {
    @Override
    protected void configure() {
      DynamicMap.mapOf(binder(), METRIC_KIND);
      child(CONFIG_KIND, "metrics").to(MetricsCollection.class);
      get(METRIC_KIND).to(GetMetric.class);
      bind(CacheMetrics.class);
    }
  }

  private final MetricRegistry registry;
  private final Map<String, BucketedMetric> bucketed;
  private final Map<String, ImmutableMap<String, String>> descriptions;
  private final MetricsReservoirConfig reservoirConfig;

  @Inject
  DropWizardMetricMaker(MetricRegistry registry, MetricsReservoirConfig reservoirConfig) {
    this.registry = registry;
    this.bucketed = new ConcurrentHashMap<>();
    this.descriptions = new ConcurrentHashMap<>();
    this.reservoirConfig = reservoirConfig;
  }

  Iterable<String> getMetricNames() {
    return descriptions.keySet();
  }

  /** Get the underlying metric implementation. */
  public Metric getMetric(String name) {
    Metric m = bucketed.get(name);
    return m != null ? m : registry.getMetrics().get(name);
  }

  /** Lookup annotations from a metric's {@link Description}. */
  public ImmutableMap<String, String> getAnnotations(String name) {
    return descriptions.get(name);
  }

  @Override
  public synchronized Counter0 newCounter(String name, Description desc) {
    checkCounterDescription(name, desc);
    define(name, desc);
    return newCounterImpl(name, desc.isRate());
  }

  @Override
  public synchronized <F1> Counter1<F1> newCounter(
      String name, Description desc, Field<F1> field1) {
    checkCounterDescription(name, desc);
    CounterImpl1<F1> m = new CounterImpl1<>(this, name, desc, field1);
    define(name, desc);
    bucketed.put(name, m);
    return m.counter();
  }

  @Override
  public synchronized <F1, F2> Counter2<F1, F2> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    checkCounterDescription(name, desc);
    CounterImplN m = new CounterImplN(this, name, desc, field1, field2);
    define(name, desc);
    bucketed.put(name, m);
    return m.counter2();
  }

  @Override
  public synchronized <F1, F2, F3> Counter3<F1, F2, F3> newCounter(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    checkCounterDescription(name, desc);
    CounterImplN m = new CounterImplN(this, name, desc, field1, field2, field3);
    define(name, desc);
    bucketed.put(name, m);
    return m.counter3();
  }

  private static void checkCounterDescription(String name, Description desc) {
    checkMetricName(name);
    checkArgument(!desc.isConstant(), "counter must not be constant");
    checkArgument(!desc.isGauge(), "counter must not be gauge");
  }

  CounterImpl newCounterImpl(String name, boolean isRate) {
    if (isRate) {
      final com.codahale.metrics.Meter m = registry.meter(name);
      return new CounterImpl(name, m) {
        @Override
        public void incrementBy(long delta) {
          checkArgument(delta >= 0, "counter delta must be >= 0");
          m.mark(delta);
        }
      };
    }
    final com.codahale.metrics.Counter m = registry.counter(name);
    return new CounterImpl(name, m) {
      @Override
      public void incrementBy(long delta) {
        checkArgument(delta >= 0, "counter delta must be >= 0");
        m.inc(delta);
      }
    };
  }

  @Override
  public synchronized Timer0 newTimer(String name, Description desc) {
    checkTimerDescription(name, desc);
    define(name, desc);
    return newTimerImpl(name);
  }

  @Override
  public synchronized <F1> Timer1<F1> newTimer(String name, Description desc, Field<F1> field1) {
    checkTimerDescription(name, desc);
    TimerImpl1<F1> m = new TimerImpl1<>(this, name, desc, field1);
    define(name, desc);
    bucketed.put(name, m);
    return m.timer();
  }

  @Override
  public synchronized <F1, F2> Timer2<F1, F2> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    checkTimerDescription(name, desc);
    TimerImplN m = new TimerImplN(this, name, desc, field1, field2);
    define(name, desc);
    bucketed.put(name, m);
    return m.timer2();
  }

  @Override
  public synchronized <F1, F2, F3> Timer3<F1, F2, F3> newTimer(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    checkTimerDescription(name, desc);
    TimerImplN m = new TimerImplN(this, name, desc, field1, field2, field3);
    define(name, desc);
    bucketed.put(name, m);
    return m.timer3();
  }

  private static void checkTimerDescription(String name, Description desc) {
    checkMetricName(name);
    checkArgument(!desc.isConstant(), "timer must not be constant");
    checkArgument(!desc.isGauge(), "timer must not be a gauge");
    checkArgument(!desc.isRate(), "timer must not be a rate");
    checkArgument(desc.isCumulative(), "timer must be cumulative");
    checkArgument(desc.getTimeUnit() != null, "timer must have a unit");
  }

  TimerImpl newTimerImpl(String name) {
    return new TimerImpl(
        name,
        registry.timer(name, () -> new Timer(DropWizardReservoirProvider.get(reservoirConfig))));
  }

  @Override
  public synchronized Histogram0 newHistogram(String name, Description desc) {
    checkHistogramDescription(name, desc);
    define(name, desc);
    return newHistogramImpl(name);
  }

  @Override
  public synchronized <F1> Histogram1<F1> newHistogram(
      String name, Description desc, Field<F1> field1) {
    checkHistogramDescription(name, desc);
    HistogramImpl1<F1> m = new HistogramImpl1<>(this, name, desc, field1);
    define(name, desc);
    bucketed.put(name, m);
    return m.histogram1();
  }

  @Override
  public synchronized <F1, F2> Histogram2<F1, F2> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2) {
    checkHistogramDescription(name, desc);
    HistogramImplN m = new HistogramImplN(this, name, desc, field1, field2);
    define(name, desc);
    bucketed.put(name, m);
    return m.histogram2();
  }

  @Override
  public synchronized <F1, F2, F3> Histogram3<F1, F2, F3> newHistogram(
      String name, Description desc, Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    checkHistogramDescription(name, desc);
    HistogramImplN m = new HistogramImplN(this, name, desc, field1, field2, field3);
    define(name, desc);
    bucketed.put(name, m);
    return m.histogram3();
  }

  private static void checkHistogramDescription(String name, Description desc) {
    checkMetricName(name);
    checkArgument(!desc.isConstant(), "histogram must not be constant");
    checkArgument(!desc.isGauge(), "histogram must not be a gauge");
    checkArgument(!desc.isRate(), "histogram must not be a rate");
    checkArgument(desc.isCumulative(), "histogram must be cumulative");
  }

  HistogramImpl newHistogramImpl(String name) {
    return new HistogramImpl(
        name,
        registry.histogram(
            name, () -> new Histogram(DropWizardReservoirProvider.get(reservoirConfig))));
  }

  @Override
  public <V> CallbackMetric0<V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc) {
    checkMetricName(name);
    define(name, desc);
    return new CallbackMetricImpl0<>(this, registry, name, valueClass);
  }

  @Override
  public <F1, V> CallbackMetric1<F1, V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc, Field<F1> field1) {
    checkMetricName(name);
    CallbackMetricImpl1<F1, V> m =
        new CallbackMetricImpl1<>(this, registry, name, valueClass, desc, field1);
    define(name, desc);
    bucketed.put(name, m);
    return m.create();
  }

  @Override
  public synchronized RegistrationHandle newTrigger(
      Set<CallbackMetric<?>> metrics, Runnable trigger) {
    ImmutableSet<CallbackMetricGlue> all =
        FluentIterable.from(metrics).transform(m -> (CallbackMetricGlue) m).toSet();

    trigger = new CallbackGroup(trigger, all);
    for (CallbackMetricGlue m : all) {
      m.register(trigger);
    }
    trigger.run();

    return () -> all.forEach(CallbackMetricGlue::remove);
  }

  synchronized void remove(String name) {
    bucketed.remove(name);
    descriptions.remove(name);
  }

  private synchronized void define(String name, Description desc) {
    if (descriptions.containsKey(name)) {
      ImmutableMap<String, String> annotations = descriptions.get(name);
      if (!desc.getAnnotations()
          .get(Description.DESCRIPTION)
          .equals(annotations.get(Description.DESCRIPTION))) {
        throw new IllegalStateException(String.format("metric '%s' already defined", name));
      }
    } else {
      descriptions.put(name, desc.getAnnotations());
    }
  }

  private static final Pattern METRIC_NAME_PATTERN =
      Pattern.compile("[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*");
  private static final Pattern INVALID_CHAR_PATTERN = Pattern.compile("[^\\w-/]");
  private static final String REPLACEMENT_PREFIX = "_0x";

  private static void checkMetricName(String name) {
    checkArgument(
        METRIC_NAME_PATTERN.matcher(name).matches(),
        "invalid metric name '%s': must match pattern '%s'",
        name,
        METRIC_NAME_PATTERN.pattern());
  }

  /**
   * Ensures that the sanitized metric name doesn't contain invalid characters and removes the risk
   * of collision (between the sanitized metric names). Modifications to the input metric name:
   *
   * <ul>
   *   <li/>leading <code>/</code> is replaced with <code>_</code>
   *   <li/>doubled (or repeated more times) <code>/</code> are reduced to a single <code>/</code>
   *   <li/>ending <code>/</code> is removed
   *   <li/>all characters that are not <code>/a-zA-Z0-9_-</code> are replaced with <code>
   *       _0x[HEX CODE]_</code> (code is capitalized)
   *   <li/>the replacement prefix <code>_0x</code> is prepended with another replacement prefix
   * </ul>
   *
   * @param name name of the metric to sanitize
   * @return sanitized metric name
   */
  @Override
  public String sanitizeMetricName(String name) {
    if (METRIC_NAME_PATTERN.matcher(name).matches() && !name.contains(REPLACEMENT_PREFIX)) {
      return name;
    }

    StringBuilder sanitizedName =
        new StringBuilder(name.substring(0, 1).replaceFirst("[^\\w-]", "_"));
    if (name.length() == 1) {
      return sanitizedName.toString();
    }

    String slashSanitizedName = name.substring(1).replaceAll("/[/]+", "/");
    if (slashSanitizedName.endsWith("/")) {
      slashSanitizedName = slashSanitizedName.substring(0, slashSanitizedName.length() - 1);
    }

    String replacementPrefixSanitizedName =
        slashSanitizedName.replaceAll(REPLACEMENT_PREFIX, REPLACEMENT_PREFIX + REPLACEMENT_PREFIX);

    for (int i = 0; i < replacementPrefixSanitizedName.length(); i++) {
      Character c = replacementPrefixSanitizedName.charAt(i);
      Matcher matcher = INVALID_CHAR_PATTERN.matcher(c.toString());
      if (matcher.matches()) {
        sanitizedName.append(REPLACEMENT_PREFIX);
        sanitizedName.append(Integer.toHexString(c).toUpperCase());
        sanitizedName.append('_');
      } else {
        sanitizedName.append(c);
      }
    }

    return sanitizedName.toString();
  }

  static String name(Description.FieldOrdering ordering, String codeName, String fieldValues) {
    if (ordering == FieldOrdering.PREFIX_FIELDS_BASENAME) {
      int s = codeName.lastIndexOf('/');
      if (s > 0) {
        String prefix = codeName.substring(0, s);
        String metric = codeName.substring(s + 1);
        return prefix + '/' + fieldValues + '/' + metric;
      }
    }
    return codeName + '/' + fieldValues;
  }

  abstract class CounterImpl extends Counter0 {
    private final String name;
    final Metric metric;

    CounterImpl(String name, Metric metric) {
      this.name = name;
      this.metric = metric;
    }

    @Override
    public void remove() {
      descriptions.remove(name);
      registry.remove(name);
    }
  }

  class TimerImpl extends Timer0 {
    final com.codahale.metrics.Timer metric;

    private TimerImpl(String name, com.codahale.metrics.Timer metric) {
      super(name);
      this.metric = metric;
    }

    @Override
    protected void doRecord(long value, TimeUnit unit) {
      checkArgument(value >= 0, "timer delta must be >= 0");
      metric.update(value, unit);
    }

    @Override
    public void remove() {
      descriptions.remove(name);
      registry.remove(name);
    }
  }

  class HistogramImpl extends Histogram0 {
    private final String name;
    final com.codahale.metrics.Histogram metric;

    private HistogramImpl(String name, com.codahale.metrics.Histogram metric) {
      this.name = name;
      this.metric = metric;
    }

    @Override
    public void record(long value) {
      metric.update(value);
    }

    @Override
    public void remove() {
      descriptions.remove(name);
      registry.remove(name);
    }
  }
}
