package com.google.gerrit.metrics.dropwizard;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.Counter;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Rate;
import com.google.gerrit.metrics.Timer;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

import com.codahale.metrics.MetricRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Connects Gerrit metric package onto DropWizard.
 *
 * @see <a href="http://www.dropwizard.io/">DropWizard</a>
 */
@Singleton
public class DropWizardMetricMaker extends MetricMaker {
  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(MetricRegistry.class).in(Scopes.SINGLETON);
      bind(MetricMaker.class).to(DropWizardMetricMaker.class);
    }
  }

  private final MetricRegistry registry;

  @Inject
  DropWizardMetricMaker(MetricRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Counter newCounter(final String name, Description desc) {
    checkArgument(!desc.isGauge(), "counters must not be gauge");
    checkArgument(desc.isCumulative(), "counters must be cumulative");
    checkNotDefined(name);

    final com.codahale.metrics.Counter cntr = registry.counter(name);
    return new Counter() {
      @Override
      public void incrementBy(long delta) {
        checkArgument(delta >= 0, "counter delta must be >= 0");
        cntr.inc(delta);
      }

      @Override
      public void remove() {
        registry.remove(name);
      }
    };
  }

  @Override
  public Rate newRate(final String name, Description desc) {
    checkArgument(!desc.isGauge(), "rate must not be gauge");
    checkArgument(desc.isCumulative(), "rate must be cumulative");
    checkNotDefined(name);

    final com.codahale.metrics.Meter cntr = registry.meter(name);
    return new Rate() {
      @Override
      public void incrementBy(long delta) {
        checkArgument(delta >= 0, "rate delta must be >= 0");
        cntr.mark(delta);
      }

      @Override
      public void remove() {
        registry.remove(name);
      }
    };
  }

  @Override
  public Timer newTimer(final String name, Description desc) {
    checkArgument(!desc.isGauge(), "timer must not be gauge");
    checkArgument(desc.isCumulative(), "timer must be cumulative");
    checkNotDefined(name);

    final TimeUnit unit = desc.getTimeUnit();
    final com.codahale.metrics.Timer time = registry.timer(name);
    return new Timer(unit) {
      @Override
      public void record(long value) {
        checkArgument(value >= 0, "timer delta must be >= 0");
        time.update(value, unit);
      }

      @Override
      public void remove() {
        registry.remove(name);
      }
    };
  }

  @SuppressWarnings("unused")
  @Override
  public <V> CallbackMetric<V> newCallbackMetric(String name,
      Class<V> valueClass, Description desc) {
    checkNotDefined(name);
    return new CallbackMetricImpl<V>(name, valueClass);
  }

  @Override
  public RegistrationHandle newTrigger(Set<CallbackMetric<?>> metrics,
      Runnable trigger) {
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
      } else {
        throw new IllegalArgumentException("unsupported value type " + valueClass.getName());
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
