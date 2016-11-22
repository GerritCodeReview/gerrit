// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

public class StalenessChecker {
  public static SetMultimap<Project.NameKey, RefState> parseStates(
      Iterable<byte[]> states) {
    RefState.check(states != null, null);
    SetMultimap<Project.NameKey, RefState> result = HashMultimap.create();
    for (byte[] b : states) {
      RefState.check(b != null, null);
      String s = new String(b, UTF_8);
      List<String> parts = Splitter.on(':').splitToList(s);
      RefState.check(
          parts.size() == 3
              && !parts.get(0).isEmpty()
              && !parts.get(1).isEmpty(),
          s);
      result.put(
          new Project.NameKey(parts.get(0)),
          RefState.create(parts.get(1), parts.get(2)));
    }
    return result;
  }

  public static ListMultimap<Project.NameKey, RefStatePattern> parsePatterns(
      Iterable<byte[]> patterns) {
    RefStatePattern.check(patterns != null, null);
    ListMultimap<Project.NameKey, RefStatePattern> result =
        ArrayListMultimap.create();
    for (byte[] b : patterns) {
      RefStatePattern.check(b != null, null);
      String s = new String(b, UTF_8);
      List<String> parts = Splitter.on(':').splitToList(s);
      RefStatePattern.check(parts.size() == 2, s);
      result.put(
          new Project.NameKey(parts.get(0)),
          RefStatePattern.create(parts.get(1)));
    }
    return result;
  }

  @AutoValue
  public abstract static class RefState {
    static RefState create(String ref, String sha) {
      return new AutoValue_StalenessChecker_RefState(
          ref, ObjectId.fromString(sha));
    }

    static RefState create(String ref, @Nullable ObjectId id) {
      return new AutoValue_StalenessChecker_RefState(
          ref, firstNonNull(id, ObjectId.zeroId()));
    }

    static RefState of(Ref ref) {
      return new AutoValue_StalenessChecker_RefState(
          ref.getName(), ref.getObjectId());
    }

    byte[] toByteArray(Project.NameKey project) {
      byte[] a = (project.toString() + ':' + ref() + ':').getBytes(UTF_8);
      byte[] b = new byte[a.length + Constants.OBJECT_ID_STRING_LENGTH];
      System.arraycopy(a, 0, b, 0, a.length);
      id().copyTo(b, a.length);
      return b;
    }

    private static void check(boolean condition, String str) {
      checkArgument(condition, "invalid RefState: %s", str);
    }

    abstract String ref();
    abstract ObjectId id();
  }

  /**
   * Pattern for matching refs.
   * <p>
   * Similar to '*' syntax for native Git refspecs, but slightly more powerful:
   * the pattern may contain arbitrarily many asterisks. There must be at least
   * one '*' and the first one must immediately follow a '/'.
   */
  @AutoValue
  public abstract static class RefStatePattern {
    static RefStatePattern create(String pattern) {
      int star = pattern.indexOf('*');
      check(star > 0 && pattern.charAt(star - 1) == '/', pattern);
      String prefix = pattern.substring(0, star);
      check(Repository.isValidRefName(pattern.replace('*', 'x')), pattern);

      // Quote everything except the '*'s, which become ".*".
      String regex =
          StreamSupport.stream(Splitter.on('*').split(pattern).spliterator(), false)
              .map(Pattern::quote)
              .collect(joining(".*", "^", "$"));
      return new AutoValue_StalenessChecker_RefStatePattern(
          pattern, prefix, Pattern.compile(regex));
    }

    byte[] toByteArray(Project.NameKey project) {
      return (project.toString() + ':' + pattern()).getBytes(UTF_8);
    }

    private static void check(boolean condition, String str) {
      checkArgument(condition, "invalid RefStatePattern: %s", str);
    }

    abstract String pattern();
    abstract String prefix();
    abstract Pattern regex();

    boolean match(String refName) {
      return regex().matcher(refName).find();
    }
  }
}
