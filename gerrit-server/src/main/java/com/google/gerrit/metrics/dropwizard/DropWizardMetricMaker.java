package com.google.gerrit.metrics.dropwizard;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.metrics.dropwizard.MetricResource.METRIC_KIND;
import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.metrics.Timer2;
import com.google.gerrit.metrics.Timer3;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Connects Gerrit metric package onto DropWizard.
 *
 * @see <a href="http://www.dropwizard.io/">DropWizard</a>
 */
@Singleton
public class DropWizardMetricMaker extends MetricMaker {
  public static class Module extends RestApiModule {
    @Override
    protected void configure() {
      bind(MetricRegistry.class).in(Scopes.SINGLETON);
      bind(DropWizardMetricMaker.class).in(Scopes.SINGLETON);
      bind(MetricMaker.class).to(DropWizardMetricMaker.class);

      DynamicMap.mapOf(binder(), METRIC_KIND);
      child(CONFIG_KIND, "metrics").to(MetricsCollection.class);
      get(METRIC_KIND).to(GetMetric.class);
    }
  }

  private final MetricRegistry registry;
  private final Map<String, BucketedMetric> bucketed;
  private final Map<String, ImmutableMap<String, String>> descriptions;

  @Inject
  DropWizardMetricMaker(MetricRegistry registry) {
    this.registry = registry;
    this.bucketed = new ConcurrentHashMap<>();
    this.descriptions = new ConcurrentHashMap<>();
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
    checkCounterDescription(desc);
    define(name, desc);
    return newCounterImpl(name, desc.isRate());
  }

  @Override
  public synchronized <F1> Counter1<F1> newCounter(
      String name, Description desc,
      Field<F1> field1) {
    checkCounterDescription(desc);
    CounterImpl1<F1> m = new CounterImpl1<>(this, name, desc, field1);
    define(name, desc);
    bucketed.put(name, m);
    return m.counter();
  }

  @Override
  public synchronized <F1, F2> Counter2<F1, F2> newCounter(
      String name, Description desc,
      Field<F1> field1, Field<F2> field2) {
    checkCounterDescription(desc);
    CounterImplN m = new CounterImplN(this, name, desc, field1, field2);
    define(name, desc);
    bucketed.put(name, m);
    return m.counter2();
  }

  @Override
  public synchronized <F1, F2, F3> Counter3<F1, F2, F3> newCounter(
      String name, Description desc,
      Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    checkCounterDescription(desc);
    CounterImplN m = new CounterImplN(this, name, desc, field1, field2, field3);
    define(name, desc);
    bucketed.put(name, m);
    return m.counter3();
  }

  private static void checkCounterDescription(Description desc) {
    checkArgument(!desc.isGauge(), "counters must not be gauge");
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
    } else {
      final com.codahale.metrics.Counter m = registry.counter(name);
      return new CounterImpl(name, m) {
        @Override
        public void incrementBy(long delta) {
          checkArgument(delta >= 0, "counter delta must be >= 0");
          m.inc(delta);
        }
      };
    }
  }

  @Override
  public synchronized Timer0 newTimer(String name, Description desc) {
    checkTimerDescription(desc);
    define(name, desc);
    return newTimerImpl(name);
  }

  @Override
  public synchronized <F1> Timer1<F1> newTimer(String name, Description desc, Field<F1> field1) {
    checkTimerDescription(desc);
    TimerImpl1<F1> m = new TimerImpl1<>(this, name, field1);
    define(name, desc);
    bucketed.put(name, m);
    return m.timer();
  }

  @Override
  public synchronized <F1, F2> Timer2<F1, F2> newTimer(String name, Description desc,
      Field<F1> field1, Field<F2> field2) {
    checkTimerDescription(desc);
    TimerImplN m = new TimerImplN(this, name, field1, field2);
    define(name, desc);
    bucketed.put(name, m);
    return m.timer2();
  }

  @Override
  public synchronized <F1, F2, F3> Timer3<F1, F2, F3> newTimer(
      String name, Description desc,
      Field<F1> field1, Field<F2> field2, Field<F3> field3) {
    checkTimerDescription(desc);
    TimerImplN m = new TimerImplN(this, name, field1, field2, field3);
    define(name, desc);
    bucketed.put(name, m);
    return m.timer3();
  }

  private static void checkTimerDescription(Description desc) {
    checkArgument(!desc.isGauge(), "timer must not be a gauge");
    checkArgument(!desc.isRate(), "timer must not be a rate");
    checkArgument(desc.isCumulative(), "timer must be cumulative");
    checkArgument(desc.getTimeUnit() != null, "timer must have a unit");
  }

  TimerImpl newTimerImpl(String name) {
    return new TimerImpl(name, registry.timer(name));
  }

  @Override
  public <V> CallbackMetric0<V> newCallbackMetric(
      String name, Class<V> valueClass, Description desc) {
    define(name, desc);
    return new CallbackMetricImpl0<>(name, valueClass);
  }

  @Override
  public synchronized RegistrationHandle newTrigger(
      Set<CallbackMetric<?>> metrics, Runnable trigger) {
    if (metrics.size() > 1) {
      trigger = new CallbackGroup(trigger);
    }

    for (CallbackMetric<?> m : metrics) {
      CallbackMetricImpl0<?> metric = (CallbackMetricImpl0<?>) m;
      if (registry.getMetrics().containsKey(metric.name)) {
        throw new IllegalStateException(String.format(
            "metric %s already configured", metric.name));
      }
    }

    final List<String> names = new ArrayList<>(metrics.size());
    for (CallbackMetric<?> m : metrics) {
      CallbackMetricImpl0<?> metric = (CallbackMetricImpl0<?>) m;
      registry.register(metric.name, metric.gauge(trigger));
      names.add(metric.name);
    }

    return new RegistrationHandle() {
      @Override
      public void remove() {
        for (String name : names) {
          descriptions.remove(name);
          registry.remove(name);
        }
      }
    };
  }

  synchronized void remove(String name) {
    bucketed.remove(name);
    descriptions.remove(name);
  }

  private synchronized void define(String name, Description desc) {
    if (descriptions.containsKey(name)) {
      throw new IllegalStateException(String.format(
          "metric %s already defined", name));
    }
    descriptions.put(name, desc.getAnnotations());
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
    private final String name;
    final com.codahale.metrics.Timer metric;

    private TimerImpl(String name, com.codahale.metrics.Timer metric) {
      this.name = name;
      this.metric = metric;
    }

    @Override
    public void record(long value, TimeUnit unit) {
      checkArgument(value >= 0, "timer delta must be >= 0");
      metric.update(value, unit);
    }

    @Override
    public void remove() {
      descriptions.remove(name);
      registry.remove(name);
    }
  }
}
