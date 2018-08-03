// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.util.RequestId;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import org.junit.After;
import org.junit.Test;

public class TraceContextTest {
  @After
  public void cleanup() {
    LoggingContext.getInstance().clearTags();
  }

  @Test
  public void openContext() {
    assertTags(ImmutableMap.of());
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));
    }
    assertTags(ImmutableMap.of());
  }

  @Test
  public void openNestedContexts() {
    assertTags(ImmutableMap.of());
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));

      try (TraceContext traceContext2 = new TraceContext("abc", "xyz")) {
        assertTags(ImmutableMap.of("abc", ImmutableSet.of("xyz"), "foo", ImmutableSet.of("bar")));
      }

      assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));
    }
    assertTags(ImmutableMap.of());
  }

  @Test
  public void openNestedContextsWithSameTagName() {
    assertTags(ImmutableMap.of());
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));

      try (TraceContext traceContext2 = new TraceContext("foo", "baz")) {
        assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar", "baz")));
      }

      assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));
    }
    assertTags(ImmutableMap.of());
  }

  @Test
  public void openNestedContextsWithSameTagNameAndValue() {
    assertTags(ImmutableMap.of());
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));

      try (TraceContext traceContext2 = new TraceContext("foo", "bar")) {
        assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));
      }

      assertTags(ImmutableMap.of("foo", ImmutableSet.of("bar")));
    }
    assertTags(ImmutableMap.of());
  }

  @Test
  public void openContextWithRequestId() {
    assertTags(ImmutableMap.of());
    try (TraceContext traceContext = new TraceContext(RequestId.Type.RECEIVE_ID, "foo")) {
      assertTags(ImmutableMap.of("RECEIVE_ID", ImmutableSet.of("foo")));
    }
    assertTags(ImmutableMap.of());
  }

  @Test
  public void cannotOpenContextWithInvalidTag() {
    assertNullPointerException(
        "tag name is required", () -> new TraceContext((String) null, "foo"));
    assertNullPointerException("tag value is required", () -> new TraceContext("foo", null));

    assertNullPointerException(
        "request ID is required", () -> new TraceContext((RequestId.Type) null, "foo"));
    assertNullPointerException(
        "tag value is required", () -> new TraceContext(RequestId.Type.RECEIVE_ID, null));
  }

  private void assertTags(ImmutableMap<String, ImmutableSet<String>> expectedTagMap) {
    SortedMap<String, SortedSet<Object>> actualTagMap =
        LoggingContext.getInstance().getTags().asMap();
    assertThat(actualTagMap.keySet()).containsExactlyElementsIn(expectedTagMap.keySet());
    for (Map.Entry<String, ImmutableSet<String>> expectedEntry : expectedTagMap.entrySet()) {
      assertThat(actualTagMap.get(expectedEntry.getKey()))
          .containsExactlyElementsIn(expectedEntry.getValue());
    }
  }

  private void assertNullPointerException(String expectedMessage, Runnable r) {
    try {
      r.run();
      assert_().fail("expected NullPointerException");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isEqualTo(expectedMessage);
    }
  }
}
