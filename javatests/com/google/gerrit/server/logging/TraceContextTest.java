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

import com.google.gerrit.server.util.RequestId;
import java.util.SortedMap;
import java.util.SortedSet;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TraceContextTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @After
  public void cleanup() {
    LoggingContext.getInstance().clearTags();
    LoggingContext.getInstance().forceLogging(false);
  }

  @Test
  public void openContext() {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
  }

  @Test
  public void openNestedContexts() {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");

      try (TraceContext traceContext2 = new TraceContext("abc", "xyz")) {
        tagMap = LoggingContext.getInstance().getTags().asMap();
        assertThat(tagMap.keySet()).containsExactly("abc", "foo");
        assertThat(tagMap.get("abc")).containsExactly("xyz");
        assertThat(tagMap.get("foo")).containsExactly("bar");
      }

      tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
  }

  @Test
  public void openNestedContextsWithSameTagName() {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");

      try (TraceContext traceContext2 = new TraceContext("foo", "baz")) {
        tagMap = LoggingContext.getInstance().getTags().asMap();
        assertThat(tagMap.keySet()).containsExactly("foo");
        assertThat(tagMap.get("foo")).containsExactly("bar", "baz");
      }

      tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
  }

  @Test
  public void openNestedContextsWithSameTagNameAndValue() {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");

      try (TraceContext traceContext2 = new TraceContext("foo", "bar")) {
        tagMap = LoggingContext.getInstance().getTags().asMap();
        assertThat(tagMap.keySet()).containsExactly("foo");
        assertThat(tagMap.get("foo")).containsExactly("bar");
      }

      tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
  }

  @Test
  public void openContextWithRequestId() {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    try (TraceContext traceContext = new TraceContext(RequestId.Id.RECEIVE_ID, "foo")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("RECEIVE_ID");
      assertThat(tagMap.get("RECEIVE_ID")).containsExactly("foo");
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
  }

  @Test
  public void cannotOpenContextWithNullRequestId() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("request ID is required");
    try (TraceContext traceContext = new TraceContext((RequestId.Id) null, "foo")) {}
  }

  @Test
  public void cannotOpenContextWithNullTagName() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("tag name is required");
    try (TraceContext traceContext = new TraceContext((String) null, "foo")) {}
  }

  @Test
  public void cannotOpenContextWithNullTagValue() {
    exception.expect(NullPointerException.class);
    exception.expectMessage("tag value is required");
    try (TraceContext traceContext = new TraceContext("foo", null)) {}
  }

  @Test
  public void openContextWithForceLogging() {
    assertForceLogging(false);
    try (TraceContext traceContext = new TraceContext(true, "foo", "bar")) {
      assertForceLogging(true);
    }
    assertForceLogging(false);
  }

  @Test
  public void openNestedContextsWithForceLogging() {
    assertForceLogging(false);
    try (TraceContext traceContext = new TraceContext(true, "foo", "bar")) {
      assertForceLogging(true);

      try (TraceContext traceContext2 = new TraceContext("abc", "xyz")) {
        // force logging is still enabled since outer trace context forced logging
        assertForceLogging(true);

        try (TraceContext traceContext3 = new TraceContext(true, "tag", "value")) {
          assertForceLogging(true);
        }

        assertForceLogging(true);
      }

      assertForceLogging(true);
    }
    assertForceLogging(false);
  }

  private void assertForceLogging(boolean expected) {
    assertThat(LoggingContext.getInstance().shouldForceLogging(null, null, false))
        .isEqualTo(expected);
  }
}
