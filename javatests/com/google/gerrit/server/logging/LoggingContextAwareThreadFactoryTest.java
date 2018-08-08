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

import com.google.common.truth.Expect;
import java.util.SortedMap;
import java.util.SortedSet;
import org.junit.Rule;
import org.junit.Test;

public class LoggingContextAwareThreadFactoryTest {
  @Rule public final Expect expect = Expect.create();

  @Test
  public void loggingContextPropagationToNewThread() throws Exception {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");

      Thread thread =
          new LoggingContextAwareThreadFactory(r -> new Thread(r, "test-thread"))
              .newThread(
                  () -> {
                    // Verify that the tags have been propagated to the new thread.
                    SortedMap<String, SortedSet<Object>> threadTagMap =
                        LoggingContext.getInstance().getTags().asMap();
                    expect.that(threadTagMap.keySet()).containsExactly("foo");
                    expect.that(threadTagMap.get("foo")).containsExactly("bar");
                  });

      // Execute in background.
      thread.start();
      thread.join();

      // Verify that tags in the outer thread are still set.
      tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
  }

  @Test
  public void loggingContextPropagationToSameThread() throws Exception {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    try (TraceContext traceContext = new TraceContext("foo", "bar")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");

      Thread thread =
          new LoggingContextAwareThreadFactory()
              .newThread(
                  () -> {
                    // Verify that the tags have been propagated to the new thread.
                    SortedMap<String, SortedSet<Object>> threadTagMap =
                        LoggingContext.getInstance().getTags().asMap();
                    expect.that(threadTagMap.keySet()).containsExactly("foo");
                    expect.that(threadTagMap.get("foo")).containsExactly("bar");
                  });

      // Execute in the same thread.
      thread.run();

      // Verify that tags in the outer thread are still set.
      tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
  }
}
