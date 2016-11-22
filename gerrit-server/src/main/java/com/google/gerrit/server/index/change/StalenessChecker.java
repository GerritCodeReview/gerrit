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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

@Singleton
class StalenessChecker {
  private static final Logger log =
      LoggerFactory.getLogger(StalenessChecker.class);

  private final ImmutableSet<String> FIELDS = ImmutableSet.of(
      ChangeField.CHANGE.getName(),
      ChangeField.REF_STATE.getName(),
      ChangeField.REF_STATE_PATTERN.getName());

  private final ChangeIndexCollection indexes;
  private final GitRepositoryManager repoManager;
  private final IndexConfig indexConfig;
  private final Provider<ReviewDb> db;

  @Inject
  StalenessChecker(
      ChangeIndexCollection indexes,
      GitRepositoryManager repoManager,
      IndexConfig indexConfig,
      Provider<ReviewDb> db) {
    this.indexes = indexes;
    this.repoManager = repoManager;
    this.indexConfig = indexConfig;
    this.db = db;
  }

  boolean isStale(Change.Id id) throws IOException, OrmException {
    ChangeIndex i = indexes.getSearchIndex();
    if (i == null) {
      return false; // No index; caller couldn't do anything if it is stale.
    }
    if (!i.getSchema().hasField(ChangeField.REF_STATE)
        || !i.getSchema().hasField(ChangeField.REF_STATE_PATTERN)) {
      return false; // Index version not new enough for this check.
    }

    Optional<ChangeData> result = i.get(
        id, IndexedChangeQuery.createOptions(indexConfig, 0, 1, FIELDS));
    if (!result.isPresent()) {
      return true; // Not in index, but caller wants it to be.
    }
    ChangeData cd = result.get();
    if (reviewDbChangeIsStale(
        cd.change(),
        ChangeNotes.readOneReviewDbChange(db.get(), cd.getId()))) {
      return true;
    }

    return isStale(repoManager, id, parseStates(cd), parsePatterns(cd));
  }

  @VisibleForTesting
  static boolean isStale(GitRepositoryManager repoManager,
      Change.Id id,
      SetMultimap<Project.NameKey, RefState> states,
      Multimap<Project.NameKey, RefStatePattern> patterns) {
    Set<Project.NameKey> projects =
        Sets.union(states.keySet(), patterns.keySet());

    for (Project.NameKey p : projects) {
      if (isStale(repoManager, id, p, states, patterns)) {
        return true;
      }
    }

    return false;
  }

  @VisibleForTesting
  static boolean reviewDbChangeIsStale(
      Change indexChange, @Nullable Change reviewDbChange) {
    if (reviewDbChange == null) {
      return false; // Nothing the caller can do.
    }
    checkArgument(indexChange.getId().equals(reviewDbChange.getId()),
        "mismatched change ID: %s != %s",
        indexChange.getId(), reviewDbChange.getId());
    if (PrimaryStorage.of(reviewDbChange) != PrimaryStorage.REVIEW_DB) {
      return false; // Not a ReviewDb change, don't check rowVersion.
    }
    return reviewDbChange.getRowVersion() != indexChange.getRowVersion();
  }

  private SetMultimap<Project.NameKey, RefState> parseStates(ChangeData cd) {
    return parseStates(cd.getRefStates());
  }

  @VisibleForTesting
  static SetMultimap<Project.NameKey, RefState> parseStates(
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

  private Multimap<Project.NameKey, RefStatePattern> parsePatterns(
      ChangeData cd) {
    return parsePatterns(cd.getRefStatePatterns());
  }

  @VisibleForTesting
  static ListMultimap<Project.NameKey, RefStatePattern> parsePatterns(
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

  private static boolean isStale(GitRepositoryManager repoManager,
      Change.Id id, Project.NameKey project,
      SetMultimap<Project.NameKey, RefState> allStates,
      Multimap<Project.NameKey, RefStatePattern> allPatterns) {
    try (Repository repo = repoManager.openRepository(project)) {
      Set<RefState> states = allStates.get(project);
      for (RefState state : states) {
        if (!state.match(repo)) {
          return true;
        }
      }
      for (RefStatePattern pattern : allPatterns.get(project)) {
        if (!pattern.match(repo, states)) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      log.warn(
          String.format("error checking staleness of %s in %s", id, project),
          e);
      return true;
    }
  }

  @AutoValue
  abstract static class RefState {
    static RefState create(String ref, String sha) {
      return new AutoValue_StalenessChecker_RefState(
          ref, ObjectId.fromString(sha));
    }

    static RefState of(Ref ref) {
      return new AutoValue_StalenessChecker_RefState(
          ref.getName(), ref.getObjectId());
    }

    static byte[] toByteArray(Project.NameKey project, Ref ref) {
      return toByteArray(project, ref.getName(), ref.getObjectId());
    }

    static byte[] toByteArray(Project.NameKey project, String name,
        @Nullable ObjectId id) {
      byte[] a = (project.toString() + ':' + name + ':').getBytes(UTF_8);
      byte[] b = new byte[a.length + Constants.OBJECT_ID_STRING_LENGTH];
      System.arraycopy(a, 0, b, 0, a.length);
      firstNonNull(id, ObjectId.zeroId()).copyTo(b, a.length);
      return b;
    }

    private static void check(boolean condition, String str) {
      checkArgument(condition, "invalid RefState: %s", str);
    }

    abstract String ref();
    abstract ObjectId id();

    private boolean match(Repository repo) throws IOException {
      Ref ref = repo.exactRef(ref());
      ObjectId expected = ref != null ? ref.getObjectId() : ObjectId.zeroId();
      return id().equals(expected);
    }
  }

  /**
   * Pattern for matching refs.
   * <p>
   * Similar to '*' syntax for native Git refspecs, but slightly more powerful:
   * the pattern may contain arbitrarily many asterisks. There must be at least
   * one '*' and the first one must immediately follow a '/'.
   */
  @AutoValue
  abstract static class RefStatePattern {
    static RefStatePattern create(String pat) {
      int star = pat.indexOf('*');
      check(star > 0 && pat.charAt(star - 1) == '/', pat);
      String prefix = pat.substring(0, star);
      check(Repository.isValidRefName(pat.replace('*', 'x')), pat);

      // Quote everything except the '*'s, which become ".*".
      String regex =
          StreamSupport.stream(Splitter.on('*').split(pat).spliterator(), false)
              .map(Pattern::quote)
              .collect(joining(".*", "^", "$"));
      return new AutoValue_StalenessChecker_RefStatePattern(
          prefix, Pattern.compile(regex));
    }

    static byte[] toByteArray(Project.NameKey project, String pat) {
      return (project.toString() + ':' + pat).getBytes(UTF_8);
    }

    private static void check(boolean condition, String str) {
      checkArgument(condition, "invalid RefStatePattern: %s", str);
    }

    abstract String prefix();
    abstract Pattern pattern();

    boolean match(String refName) {
      return pattern().matcher(refName).find();
    }

    private boolean match(Repository repo, Set<RefState> expected)
        throws IOException {
      for (Ref r : repo.getRefDatabase().getRefs(prefix()).values()) {
        if (!match(r.getName())) {
          continue;
        }
        if (!expected.contains(RefState.of(r))) {
          return false;
        }
      }
      return true;
    }
  }
}
