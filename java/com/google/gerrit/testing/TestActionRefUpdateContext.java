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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.server.update.context.RefUpdateContext;
import java.util.Optional;

/**
 * Marks ref updates as a test actions.
 *
 * <p>This class should be used in tests only to wrap a portion of test code which directly modifies
 * references. Usage:
 *
 * <pre>{@code
 * import static ...TestActionRefUpdateContext.openTestRefUpdateContext();
 *
 * try(RefUpdateContext ctx=openTestRefUpdateContext()) {
 *   // Some test code, which modifies a reference.
 * }
 * }</pre>
 *
 * or
 *
 * <pre>{@code
 * import static ...TestActionRefUpdateContext.testRefAction;
 *
 * testRefAction(() -> {doSomethingWithRef()});
 * T result = testRefAction(() -> { return doSomethingWithRef()});
 * }</pre>
 */
public final class TestActionRefUpdateContext extends RefUpdateContext {
  public static boolean isOpen() {
    return getCurrent().stream().anyMatch(ctx -> ctx instanceof TestActionRefUpdateContext);
  }

  public static TestActionRefUpdateContext openTestRefUpdateContext() {
    return open(new TestActionRefUpdateContext());
  }

  @CanIgnoreReturnValue
  public static <V, E extends Exception> V testRefAction(CallableWithException<V, E> c) throws E {
    try (RefUpdateContext ctx = openTestRefUpdateContext()) {
      return c.call();
    }
  }

  public static <E extends Exception> void testRefAction(RunnableWithException<E> c) throws E {
    try (RefUpdateContext ctx = openTestRefUpdateContext()) {
      c.run();
    }
  }

  public TestActionRefUpdateContext() {
    super(Optional.empty());
  }

  public interface CallableWithException<V, E extends Exception> {
    V call() throws E;
  }

  @FunctionalInterface
  public interface RunnableWithException<E extends Exception> {
    void run() throws E;
  }
}
