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

package com.google.gerrit.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.gerrit.testing.TestActionRefUpdateContext.CallableWithException;
import com.google.gerrit.testing.TestActionRefUpdateContext.RunnableWithException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Stores information about each updated ref in tests, together with associated RefUpdateContext(s).
 *
 * <p>This is a {@link TestRule}, which clears the stored data after each test.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * class ...Test {
 *  \@Rule
 *  public RefUpdateContextCollector refContextCollector = new RefUpdateContextCollector();
 *  ...
 *  public void test() {
 *    // some actions
 *    assertThat(refContextCollector.getContextsByRef("refs/heads/main")).contains(...)
 *  }
 *  }
 * }</pre>
 */
public class RefUpdateContextCollector implements TestRule {
  private static ConcurrentLinkedQueue<Entry<String, ImmutableList<RefUpdateContext>>>
      touchedRefsWithContexts = null;

  private static ConcurrentLinkedQueue<String> allowedItems = new ConcurrentLinkedQueue<>();

  @CanIgnoreReturnValue
  public static <V, E extends Exception> V testRefModification(CallableWithException<V, E> c, String... refNames) throws E {
    allowedItems.addAll(Arrays.asList(refNames));
    try {
      return c.call();
    } finally {
      allowedItems.removeAll(Arrays.asList(refNames));
    }
  }

  public static <E extends Exception> void testRefModification(RunnableWithException<E> c, String... refNames) throws E {
    allowedItems.addAll(Arrays.asList(refNames));
    c.run();
    allowedItems.removeAll(Arrays.asList(refNames));
  }

  public static boolean isAllowedRefModification(String refName) {
    return allowedItems.contains(refName);

  }

  @Override
  public Statement apply(Statement statement, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          touchedRefsWithContexts = new ConcurrentLinkedQueue<>();
          statement.evaluate();
        } finally {
          touchedRefsWithContexts = null;
        }
      }
    };
  }

  public static boolean enabled() {
    return touchedRefsWithContexts != null;
  }

  public static void register(String refName, ImmutableList<RefUpdateContext> openedContexts) {
    if (touchedRefsWithContexts == null) {
      return;
    }
    touchedRefsWithContexts.add(new SimpleImmutableEntry<>(refName, openedContexts));
  }

  public ImmutableList<String> getRefsByUpdateType(RefUpdateType refUpdateType) {
    return touchedRefsWithContexts.stream()
        .filter(
            entry ->
                entry.getValue().stream()
                    .map(RefUpdateContext::getUpdateType)
                    .anyMatch(refUpdateType::equals))
        .map(Entry::getKey)
        .collect(toImmutableList());
  }

  public ImmutableList<Entry<String, ImmutableList<RefUpdateContext>>> getContextsByUpdateType(
      RefUpdateType refUpdateType) {
    return touchedRefsWithContexts.stream()
        .filter(
            entry ->
                entry.getValue().stream()
                    .map(RefUpdateContext::getUpdateType)
                    .anyMatch(refUpdateType::equals))
        .collect(toImmutableList());
  }

  public void clear() {
    touchedRefsWithContexts.clear();
  }
}
