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

import static com.google.common.flogger.LazyArgs.lazy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.LazyArg;
import java.util.Optional;

/**
 * Utility to compute the caller of a class.
 *
 * <p>Based on {@link com.google.common.flogger.util.CallerFinder}.
 */
@AutoValue
public abstract class CallerFinder {
  public static Builder builder() {
    return new AutoValue_CallerFinder.Builder()
        .matchSubClasses(false)
        .matchInnerClasses(false)
        .skip(0);
  }

  /**
   * The target classes for which the caller should be found, in the order in which they should be
   * checked.
   *
   * @return the target classes for which the caller should be found
   */
  public abstract ImmutableList<Class<?>> targets();

  /**
   * Whether inner classes should be matched.
   *
   * @return whether inner classes should be matched
   */
  public abstract boolean matchSubClasses();

  /**
   * Whether sub classes of the target classes should be matched.
   *
   * @return whether sub classes of the target classes should be matched
   */
  public abstract boolean matchInnerClasses();

  /**
   * The minimum number of calls known to have occurred between the first call to the target class
   * and the call of {@link #findCaller()}. If in doubt, specify zero here to avoid accidentally
   * skipping past the caller.
   *
   * @return the number of stack elements to skip when computing the caller
   */
  public abstract int skip();

  @AutoValue.Builder
  public abstract static class Builder {
    abstract ImmutableList.Builder<Class<?>> targetsBuilder();

    public Builder addTarget(Class<?> target) {
      targetsBuilder().add(target);
      return this;
    }

    public abstract Builder matchSubClasses(boolean matchSubClasses);

    public abstract Builder matchInnerClasses(boolean matchInnerClasses);

    public abstract Builder skip(int skip);

    public abstract CallerFinder build();
  }

  public LazyArg<String> findCaller() {
    return lazy(
        () -> {
          for (Class<?> target : targets()) {
            // Skip one additional stack frame for this method.
            Optional<String> caller = findCallerOf(target, skip() + 1);
            if (caller.isPresent()) {
              return caller.get();
            }
          }
          return "n/a";
        });
  }

  private Optional<String> findCallerOf(Class<?> target, int skip) {
    // Skip one additional stack frame because we create the Throwable inside this method, not at
    // the point that this method was invoked.
    skip++;

    StackTraceElement[] stack = new Throwable().getStackTrace();

    // Note: To avoid having to reflect the getStackTraceDepth() method as well, we assume that we
    // will find the caller on the stack and simply catch an exception if we fail (which should
    // hardly ever happen).
    boolean foundCaller = false;
    try {
      for (int index = skip; ; index++) {
        StackTraceElement element = stack[index];
        if (isCaller(target, element.getClassName(), matchSubClasses())) {
          foundCaller = true;
        } else if (foundCaller) {
          return Optional.of(element.toString());
        }
      }
    } catch (Exception e) {
      // This should only happen if a) the caller was not found on the stack
      // (IndexOutOfBoundsException) b) a class that is mentioned in the stack was not found
      // (ClassNotFoundException), however we don't want anything to be thrown from here.
      return Optional.empty();
    }
  }

  private boolean isCaller(Class<?> target, String className, boolean matchSubClasses)
      throws ClassNotFoundException {
    if (matchSubClasses) {
      Class<?> clazz = Class.forName(className);
      while (clazz != null) {
        if (Object.class.getName().equals(clazz.getName())) {
          break;
        }

        if (isCaller(target, clazz.getName(), false)) {
          return true;
        }
        clazz = clazz.getSuperclass();
      }
    }

    if (matchInnerClasses()) {
      int i = className.indexOf('$');
      if (i > 0) {
        className = className.substring(0, i);
      }
    }

    if (target.getName().equals(className)) {
      return true;
    }

    return false;
  }
}
