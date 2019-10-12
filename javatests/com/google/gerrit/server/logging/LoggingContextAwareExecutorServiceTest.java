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
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.server.PerformanceLogContextProvider;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LoggingContextAwareExecutorServiceTest {
  @Rule public final Expect expect = Expect.create();

  @Inject private PerformanceLogContextProvider performanceLogContextProvider;
  @Inject private ExtensionRegistry extensionRegistry;

  @Before
  public void setup() {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
  }

  @Test
  public void loggingContextPropagationToBackgroundThread() throws Exception {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    assertForceLogging(false);
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();

    try (TraceContext traceContext = TraceContext.open().forceLogging().addTag("foo", "bar");
        Registration registration =
            extensionRegistry.newRegistration().add(newTestPerformanceLogger());
        PerformanceLogContext performanceLogContext = performanceLogContextProvider.get()) {
      // Create a performance log record.
      TraceContext.newTimer("test").close();

      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
      assertForceLogging(true);
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();
      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(1);

      ExecutorService executor =
          new LoggingContextAwareExecutorService(Executors.newFixedThreadPool(1));
      executor
          .submit(
              () -> {
                // Verify that the tags and force logging flag have been propagated to the new
                // thread.
                SortedMap<String, SortedSet<Object>> threadTagMap =
                    LoggingContext.getInstance().getTags().asMap();
                expect.that(threadTagMap.keySet()).containsExactly("foo");
                expect.that(threadTagMap.get("foo")).containsExactly("bar");
                expect
                    .that(LoggingContext.getInstance().shouldForceLogging(null, null, false))
                    .isTrue();
                expect.that(LoggingContext.getInstance().isPerformanceLogging()).isTrue();
                expect.that(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(1);

                // Create another performance log record. We expect this to be visible in the outer
                // thread.
                TraceContext.newTimer("test2").close();
                expect.that(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(2);
              })
          .get();

      // Verify that logging context values in the outer thread are still set.
      tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
      assertForceLogging(true);
      assertThat(LoggingContext.getInstance().isPerformanceLogging()).isTrue();

      // The performance log record that was added in the inner thread is available in addition to
      // the performance log record that was created in the outer thread.
      assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).hasSize(2);
    }

    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    assertForceLogging(false);
    assertThat(LoggingContext.getInstance().isPerformanceLogging()).isFalse();
    assertThat(LoggingContext.getInstance().getPerformanceLogRecords()).isEmpty();
  }

  private PerformanceLogger newTestPerformanceLogger() {
    return new PerformanceLogger() {
      @Override
      public void log(String operation, long durationMs, Metadata metadata) {
        // do nothing
      }
    };
  }

  private void assertForceLogging(boolean expected) {
    assertThat(LoggingContext.getInstance().shouldForceLogging(null, null, false))
        .isEqualTo(expected);
  }
}
