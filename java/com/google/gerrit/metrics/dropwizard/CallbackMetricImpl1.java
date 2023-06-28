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

import com.codahale.metrics.MetricRegistry;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optimized version of {@link BucketedCallback} for single dimension. */
class CallbackMetricImpl1<F1, V> extends BucketedCallback<V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern SUBMETRIC_NAME_PATTERN =
      Pattern.compile("[a-zA-Z0-9_-]+([a-zA-Z0-9_-]+)*");
  private static final Pattern INVALID_CHAR_PATTERN = Pattern.compile("[^\\w-]");
  private static final String REPLACMENT_PREFIX = "_0x";

  CallbackMetricImpl1(
      DropWizardMetricMaker metrics,
      MetricRegistry registry,
      String name,
      Class<V> valueClass,
      Description desc,
      Field<F1> field1) {
    super(metrics, registry, name, valueClass, desc, field1);
  }

  CallbackMetric1<F1, V> create() {
    return new Impl1();
  }

  private final class Impl1 extends CallbackMetric1<F1, V> implements CallbackMetricGlue {
    @Override
    public void beginSet() {
      doBeginSet();
    }

    @Override
    public void set(F1 field1, V value) {
      try {
        BucketedCallback<V>.ValueGauge cell = getOrCreate(field1);
        cell.value = value;
        cell.set = true;
      } catch (IllegalArgumentException e) {
        logger.atWarning().withCause(e).atMostEvery(1, TimeUnit.HOURS).log(
            "Unable to register duplicate metric: %s", submetric(field1));
      }
    }

    @Override
    public void prune() {
      doPrune();
    }

    @Override
    public void endSet() {
      doEndSet();
    }

    @Override
    public void forceCreate(F1 field1) {
      getOrCreate(field1);
    }

    @Override
    public void register(Runnable t) {
      trigger = t;
    }

    @Override
    public void remove() {
      doRemove();
    }
  }

  /**
   * Ensures that the name obtained from value doesn't contain invalid characters and removes the
   * risk of collision (between the sanitized names). All characters that are not <code>a-zA-Z0-9_-
   * </code> are replaced with <code>_0x[HEX CODE]_</code> (code is capitalized). And if replacement
   * prefix (<code>_0x</code>) is in the value it is prepended with another replacement prefix.
   *
   * @param field1 value to sanitize
   * @return sanitized value
   */
  @Override
  String name(Object field1) {
    @SuppressWarnings("unchecked")
    Function<Object, String> fmt = (Function<Object, String>) fields[0].formatter();
    String value = fmt.apply(field1);
    if (SUBMETRIC_NAME_PATTERN.matcher(value).matches() && !value.contains(REPLACMENT_PREFIX)) {
      return value;
    }

    String replacmentPrefixSanitizedName =
        value.replaceAll(REPLACMENT_PREFIX, REPLACMENT_PREFIX + REPLACMENT_PREFIX);
    StringBuilder sanitizedName = new StringBuilder();
    for (int i = 0; i < replacmentPrefixSanitizedName.length(); i++) {
      Character c = replacmentPrefixSanitizedName.charAt(i);
      Matcher matcher = INVALID_CHAR_PATTERN.matcher(c.toString());
      if (matcher.matches()) {
        sanitizedName.append(REPLACMENT_PREFIX);
        sanitizedName.append(Integer.toHexString(c).toUpperCase());
        sanitizedName.append('_');
      } else {
        sanitizedName.append(c);
      }
    }

    return sanitizedName.toString();
  }
}
