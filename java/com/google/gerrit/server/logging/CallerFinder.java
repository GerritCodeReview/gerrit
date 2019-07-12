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
 * Utility to compute the caller of a method.
 *
 * <p>In the logs we see for each entry from where it was triggered (class/method/line) but in case
 * the logging is done in a utility method or inside of a module this doesn't tell us from where the
 * action was actually triggered. To get this information we could included the stacktrace into the
 * logs (by calling {@link
 * com.google.common.flogger.LoggingApi#withStackTrace(com.google.common.flogger.StackSize)} but
 * sometimes there are too many uninteresting stacks so that this would blow up the logs too much.
 * In this case CallerFinder can be used to find the first interesting caller from the current
 * stacktrace by specifying the class that interesting callers invoke as target.
 *
 * <p>Example:
 *
 * <p>Index queries are executed by the {@code query(List<String>, List<Predicate<T>>)} method in
 * {@link com.google.gerrit.index.query.QueryProcessor}. At this place the index query is logged but
 * from the log we want to see which code triggered this index query.
 *
 * <p>E.g. the stacktrace could look like this:
 *
 * <pre>
 * GroupQueryProcessor(QueryProcessor<T>).query(List<String>, List<Predicate<T>>) line: 216
 * GroupQueryProcessor(QueryProcessor<T>).query(List<Predicate<T>>) line: 188
 * GroupQueryProcessor(QueryProcessor<T>).query(Predicate<T>) line: 171
 * InternalGroupQuery(InternalQuery<T>).query(Predicate<T>) line: 81
 * InternalGroupQuery.getOnlyGroup(Predicate<InternalGroup>, String) line: 67
 * InternalGroupQuery.byName(NameKey) line: 50
 * GroupCacheImpl$ByNameLoader.load(String) line: 166
 * GroupCacheImpl$ByNameLoader.load(Object) line: 1
 * LocalCache$LoadingValueReference<K,V>.loadFuture(K, CacheLoader<? super K,V>) line: 3527
 * ...
 * </pre>
 *
 * <p>The first interesting caller is {@code GroupCacheImpl$ByNameLoader.load(String) line: 166}. To
 * find this caller from the stacktrace we could specify {@link
 * com.google.gerrit.server.query.group.InternalGroupQuery} as a target since we know that all
 * internal group queries go through this class:
 *
 * <pre>
 * CallerFinder.builder()
 *   .addTarget(InternalGroupQuery.class)
 *   .build();
 * </pre>
 *
 * <p>Since in some places {@link com.google.gerrit.server.query.group.GroupQueryProcessor} may also
 * be used directly we can add it as a secondary target to catch these callers as well:
 *
 * <pre>
 * CallerFinder.builder()
 *   .addTarget(InternalGroupQuery.class)
 *   .addTarget(GroupQueryProcessor.class)
 *   .build();
 * </pre>
 *
 * <p>However since {@link com.google.gerrit.index.query.QueryProcessor} is also responsible to
 * execute other index queries (for changes, accounts, projects) we would need to add the classes
 * for them as targets too. Since there are common base classes we can simply specify the base
 * classes and request matching of subclasses:
 *
 * <pre>
 * CallerFinder.builder()
 *   .addTarget(InternalQuery.class)
 *   .addTarget(QueryProcessor.class)
 *   .matchSubClasses(true)
 *   .build();
 * </pre>
 *
 * <p>Another special case is if the entry point is always an inner class of a known interface. E.g.
 * {@link com.google.gerrit.server.permissions.PermissionBackend} is the entry point for all
 * permission checks but they are done through inner classes, e.g. {@link
 * com.google.gerrit.server.permissions.PermissionBackend.ForProject}. In this case matching of
 * inner classes must be enabled as well:
 *
 * <pre>
 * CallerFinder.builder()
 *   .addTarget(PermissionBackend.class)
 *   .matchSubClasses(true)
 *   .matchInnerClasses(true)
 *   .build();
 * </pre>
 *
 * <p>Finding the interesting caller requires specifying the entry point class as target. This may
 * easily break when code is refactored and hence should be used only with care. It's recommended to
 * use this only when the corresponding code is relatively stable and logging the caller information
 * brings some significant benefit.
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

  /**
   * Packages that should be ignored and not be considered as caller once a target has been found.
   *
   * @return the ignored packages
   */
  public abstract ImmutableList<String> ignoredPackages();

  /**
   * Classes that should be ignored and not be considered as caller once a target has been found.
   *
   * @return the qualified names of the ignored classes
   */
  public abstract ImmutableList<String> ignoredClasses();

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

    abstract ImmutableList.Builder<String> ignoredPackagesBuilder();

    public Builder addIgnoredPackage(String ignoredPackage) {
      ignoredPackagesBuilder().add(ignoredPackage);
      return this;
    }

    abstract ImmutableList.Builder<String> ignoredClassesBuilder();

    public Builder addIgnoredClass(Class<?> ignoredClass) {
      ignoredClassesBuilder().add(ignoredClass.getName());
      return this;
    }

    public abstract CallerFinder build();
  }

  public LazyArg<String> findCaller() {
    return lazy(
        () ->
            targets()
                .stream()
                .map(t -> findCallerOf(t, skip() + 1))
                .filter(Optional::isPresent)
                .findFirst()
                .map(Optional::get)
                .orElse("unknown"));
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
        } else if (foundCaller
            && !ignoredPackages().contains(getPackageName(element))
            && !ignoredClasses().contains(element.getClassName())) {
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

  private static String getPackageName(StackTraceElement element) {
    String className = element.getClassName();
    return className.substring(0, className.lastIndexOf("."));
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
