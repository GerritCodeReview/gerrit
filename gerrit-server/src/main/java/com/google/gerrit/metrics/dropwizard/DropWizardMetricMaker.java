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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.Counter;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final Map<String, ImmutableMap<String, String>> descriptions;

  @Inject
  DropWizardMetricMaker(MetricRegistry registry) {
    this.registry = registry;
    this.descriptions = new HashMap<>();
  }

  Map<String, Metric> getMetricMap() {
    return registry.getMetrics();
  }

  /** Get the underlying metric implementation. */
  public Metric getMetric(String name) {
    return registry.getMetrics().get(name);
  }

  /** Lookup annotations from a metric's {@link Description}.  */
  public ImmutableMap<String, String> getAnnotations(String name) {
    return descriptions.get(name);
  }

  @Override
  public synchronized Counter newCounter(String name, Description desc) {
    checkArgument(!desc.isGauge(), "counters must not be gauge");
    checkNotDefined(name);
    descriptions.put(name, desc.getAnnotations());

    if (desc.isRate()) {
      final com.codahale.metrics.Meter metric = registry.meter(name);
      return new CounterImpl(name) {
        @Override
        public void incrementBy(long delta) {
          checkArgument(delta >= 0, "counter delta must be >= 0");
          metric.mark(delta);
        }
      };
    } else {
      final com.codahale.metrics.Counter metric = registry.counter(name);
      return new CounterImpl(name) {
        @Override
        public void incrementBy(long delta) {
          checkArgument(delta >= 0, "counter delta must be >= 0");
          metric.inc(delta);
        }
      };
    }
  }

  @Override
  public synchronized Timer newTimer(final String name, Description desc) {
    checkArgument(!desc.isGauge(), "timer must not be a gauge");
    checkArgument(!desc.isRate(), "timer must not be a rate");
    checkArgument(desc.isCumulative(), "timer must be cumulative");
    checkArgument(desc.getTimeUnit() != null, "timer must have a unit");
    checkNotDefined(name);
    descriptions.put(name, desc.getAnnotations());

    final com.codahale.metrics.Timer metric = registry.timer(name);
    return new Timer() {
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
    };
  }

  @SuppressWarnings("unused")
  @Override
  public <V> CallbackMetric<V> newCallbackMetric(String name,
      Class<V> valueClass, Description desc) {
    checkNotDefined(name);
    descriptions.put(name, desc.getAnnotations());
    return new CallbackMetricImpl<V>(name, valueClass);
  }

  @Override
  public synchronized RegistrationHandle newTrigger(
      Set<CallbackMetric<?>> metrics, Runnable trigger) {
    for (CallbackMetric<?> m : metrics) {
      checkNotDefined(((CallbackMetricImpl<?>) m).name);
    }

    final List<String> names = new ArrayList<>(metrics.size());
    for (CallbackMetric<?> m : metrics) {
      CallbackMetricImpl<?> metric = (CallbackMetricImpl<?>) m;
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

  private void checkNotDefined(String name) {
    if (registry.getNames().contains(name)) {
      throw new IllegalStateException(String.format(
          "metric %s already defined", name));
    }
  }

  private abstract class CounterImpl extends Counter {
    private final String name;

    CounterImpl(String name) {
      this.name = name;
    }

    @Override
    public void remove() {
      descriptions.remove(name);
      registry.remove(name);
    }
  }

  private static class CallbackMetricImpl<V> extends CallbackMetric<V> {
    private final String name;
    private V value;

    @SuppressWarnings("unchecked")
    CallbackMetricImpl(String name, Class<V> valueClass) {
      this.name = name;

      if (valueClass == Integer.class) {
        value = (V) Integer.valueOf(0);
      } else if (valueClass == Long.class) {
        value = (V) Long.valueOf(0);
      } else if (valueClass == Double.class) {
        value = (V) Double.valueOf(0);
      } else if (valueClass == Float.class) {
        value = (V) Float.valueOf(0);
      } else if (valueClass == String.class) {
        value = (V) "";
      } else if (valueClass == Boolean.class) {
        value = (V) Boolean.FALSE;
      } else {
        throw new IllegalArgumentException("unsupported value type "
            + valueClass.getName());
      }
    }

    @Override
    public void set(V value) {
      this.value = value;
    }

    @Override
    public void remove() {
      // Triggers register and remove the metric.
    }

    com.codahale.metrics.Gauge<V> gauge(final Runnable trigger) {
      return new com.codahale.metrics.Gauge<V>() {
        @Override
        public V getValue() {
          trigger.run();
          return value;
        }
      };
    }
  }
}
