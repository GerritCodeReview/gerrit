// Copyright (C) 2019 The Android Open Source Project
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

/** Static JUnit utility methods. */
public class GerritJUnit {
  /**
   * Assert that an exception is thrown by a block of code.
   *
   * <p>This method is source-compatible with <a
   * href="https://junit.org/junit4/javadoc/latest/org/junit/Assert.html#assertThrows(java.lang.Class,%20org.junit.function.ThrowingRunnable)">JUnit
   * 4.13 beta</a>.
   *
   * <p>This construction is recommended by the Truth team for use in conjunction with asserting
   * over a {@code ThrowableSubject} on the return type:
   *
   * <pre>{@code
   * MyException e = assertThrows(MyException.class, () -> doSomething(foo));
   * assertThat(e).isInstanceOf(MySubException.class);
   * assertThat(e).hasMessageThat().contains("sub-exception occurred");
   * }</pre>
   *
   * @param throwableClass expected exception type.
   * @param runnable runnable containing arbitrary code.
   * @return exception that was thrown.
   */
  public static <T extends Throwable> T assertThrows(
      Class<T> throwableClass, ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      if (!throwableClass.isInstance(t)) {
        throw new AssertionError(
            "expected "
                + throwableClass.getName()
                + " but "
                + t.getClass().getName()
                + " was thrown",
            t);
      }
      @SuppressWarnings("unchecked")
      T toReturn = (T) t;
      return toReturn;
    }
    throw new AssertionError(
        "expected " + throwableClass.getName() + " but no exception was thrown");
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Throwable;
  }

  private GerritJUnit() {}
}
