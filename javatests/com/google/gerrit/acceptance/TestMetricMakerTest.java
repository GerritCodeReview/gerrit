// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link TestMetricMaker}. */
public class TestMetricMakerTest {
  private TestMetricMaker testMetricMaker = new TestMetricMaker();

  @Before
  public void setUp() {
    testMetricMaker.reset();
  }

  @Test
  public void counter0() throws Exception {
    String counterName = "test_counter";
    Counter0 counter = testMetricMaker.newCounter(counterName, new Description("Test Counter"));
    assertThat(testMetricMaker.getCount(counterName)).isEqualTo(0);

    counter.increment();
    assertThat(testMetricMaker.getCount(counterName)).isEqualTo(1);

    counter.incrementBy(/* value= */ 3);
    assertThat(testMetricMaker.getCount(counterName)).isEqualTo(4);
  }

  @Test
  public void counter1_booleanField() throws Exception {
    String counterName = "test_counter";
    Counter1<Boolean> counter =
        testMetricMaker.newCounter(
            counterName,
            new Description("Test Counter"),
            Field.ofBoolean("boolean_field", (metadataBuilder, booleanField) -> {}).build());
    assertThat(testMetricMaker.getCount(counterName, true)).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, false)).isEqualTo(0);

    counter.increment(/* field1= */ true);
    assertThat(testMetricMaker.getCount(counterName, true)).isEqualTo(1);
    assertThat(testMetricMaker.getCount(counterName, false)).isEqualTo(0);

    counter.incrementBy(/* field1= */ true, /* value= */ 3);
    assertThat(testMetricMaker.getCount(counterName, true)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false)).isEqualTo(0);

    counter.increment(/* field1= */ false);
    assertThat(testMetricMaker.getCount(counterName, true)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false)).isEqualTo(1);

    counter.incrementBy(/* field1= */ false, /* value= */ 4);
    assertThat(testMetricMaker.getCount(counterName, true)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false)).isEqualTo(5);

    assertThat(testMetricMaker.getCount(counterName)).isEqualTo(0);
  }

  @Test
  public void counter1_stringField() throws Exception {
    String counterName = "test_counter";
    Counter1<String> counter =
        testMetricMaker.newCounter(
            counterName,
            new Description("Test Counter"),
            Field.ofString("string_field", (metadataBuilder, stringField) -> {}).build());
    assertThat(testMetricMaker.getCount(counterName, "foo")).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, "bar")).isEqualTo(0);

    counter.increment(/* field1= */ "foo");
    assertThat(testMetricMaker.getCount(counterName, "foo")).isEqualTo(1);
    assertThat(testMetricMaker.getCount(counterName, "bar")).isEqualTo(0);

    counter.incrementBy(/* field1= */ "foo", /* value= */ 3);
    assertThat(testMetricMaker.getCount(counterName, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, "bar")).isEqualTo(0);

    counter.increment(/* field1= */ "bar");
    assertThat(testMetricMaker.getCount(counterName, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, "bar")).isEqualTo(1);

    counter.incrementBy(/* field1= */ "bar", /* value= */ 4);
    assertThat(testMetricMaker.getCount(counterName, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, "bar")).isEqualTo(5);

    assertThat(testMetricMaker.getCount(counterName)).isEqualTo(0);
  }

  @Test
  public void counter2() throws Exception {
    String counterName = "test_counter";
    Counter2<Boolean, String> counter =
        testMetricMaker.newCounter(
            counterName,
            new Description("Test Counter"),
            Field.ofBoolean("boolean_field", (metadataBuilder, booleanField) -> {}).build(),
            Field.ofString("string_field", (metadataBuilder, stringField) -> {}).build());
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, false, "foo")).isEqualTo(0);

    counter.increment(/* field1= */ true, /* field2= */ "foo");
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(1);
    assertThat(testMetricMaker.getCount(counterName, false, "foo")).isEqualTo(0);

    counter.incrementBy(/* field1= */ true, /* field2= */ "foo", /* value= */ 3);
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo")).isEqualTo(0);

    counter.increment(/* field1= */ false, /* field2= */ "foo");
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo")).isEqualTo(1);

    counter.incrementBy(/* field1= */ false, /* field2= */ "foo", /* value= */ 4);
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo")).isEqualTo(5);

    counter.increment(/* field1= */ true, /* field2= */ "bar");
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, true, "bar")).isEqualTo(1);

    counter.incrementBy(/* field1= */ true, /* field2= */ "bar", /* value= */ 5);
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, true, "bar")).isEqualTo(6);

    assertThat(testMetricMaker.getCount(counterName)).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, true)).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, false)).isEqualTo(0);
  }

  @Test
  public void counter3() throws Exception {
    String counterName = "test_counter";
    Counter3<Boolean, String, Integer> counter =
        testMetricMaker.newCounter(
            counterName,
            new Description("Test Counter"),
            Field.ofBoolean("boolean_field", (metadataBuilder, booleanField) -> {}).build(),
            Field.ofString("string_field", (metadataBuilder, stringField) -> {}).build(),
            Field.ofInteger("integer_field", (metadataBuilder, stringField) -> {}).build());
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, false, "foo", 0)).isEqualTo(0);

    counter.increment(/* field1= */ true, /* field2= */ "foo", /* field3= */ 0);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(1);
    assertThat(testMetricMaker.getCount(counterName, false, "foo", 0)).isEqualTo(0);

    counter.incrementBy(/* field1= */ true, /* field2= */ "foo", /* field3= */ 0, /* value= */ 3);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo", 0)).isEqualTo(0);

    counter.increment(/* field1= */ false, /* field2= */ "foo", /* field3= */ 0);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo", 0)).isEqualTo(1);

    counter.incrementBy(/* field1= */ false, /* field2= */ "foo", /* field3= */ 0, /* value= */ 4);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo", 0)).isEqualTo(5);

    counter.increment(/* field1= */ true, /* field2= */ "bar", /* field3= */ 0);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, true, "bar", 0)).isEqualTo(1);

    counter.incrementBy(/* field1= */ true, /* field2= */ "bar", /* field3= */ 0, /* value= */ 5);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, true, "bar", 0)).isEqualTo(6);

    counter.increment(/* field1= */ false, /* field2= */ "foo", /* field3= */ 1);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo", 1)).isEqualTo(1);

    counter.incrementBy(/* field1= */ false, /* field2= */ "foo", /* field3= */ 1, /* value= */ 6);
    assertThat(testMetricMaker.getCount(counterName, true, "foo", 0)).isEqualTo(4);
    assertThat(testMetricMaker.getCount(counterName, false, "foo", 1)).isEqualTo(7);

    assertThat(testMetricMaker.getCount(counterName)).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, true)).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, false)).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, true, "foo")).isEqualTo(0);
    assertThat(testMetricMaker.getCount(counterName, false, "foo")).isEqualTo(0);
  }

  @Test
  public void callbackMetric() {
    String name = "some_name";
    Integer metricValue = 3;
    testMetricMaker.newCallbackMetric(
        name, Integer.class, new Description("some_description"), () -> metricValue);

    assertThat(testMetricMaker.getCallbackMetricValue(name)).isEqualTo(metricValue);
  }
}
