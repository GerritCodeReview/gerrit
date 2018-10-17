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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.RefState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.UsedAt;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class StalenessChecker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final ImmutableSet<String> FIELDS =
      ImmutableSet.of(
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

  public boolean isStale(Change.Id id) throws IOException, OrmException {
    ChangeIndex i = indexes.getSearchIndex();
    if (i == null) {
      return false; // No index; caller couldn't do anything if it is stale.
    }
    if (!i.getSchema().hasField(ChangeField.REF_STATE)
        || !i.getSchema().hasField(ChangeField.REF_STATE_PATTERN)) {
      return false; // Index version not new enough for this check.
    }

    Optional<ChangeData> result =
        i.get(id, IndexedChangeQuery.createOptions(indexConfig, 0, 1, FIELDS));
    if (!result.isPresent()) {
      return true; // Not in index, but caller wants it to be.
    }
    ChangeData cd = result.get();
    return isStale(
        repoManager,
        id,
        cd.change(),
        ChangeNotes.readOneReviewDbChange(db.get(), id),
        parseStates(cd),
        parsePatterns(cd));
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  public static boolean isStale(
      GitRepositoryManager repoManager,
      Change.Id id,
      Change indexChange,
      @Nullable Change reviewDbChange,
      SetMultimap<Project.NameKey, RefState> states,
      ListMultimap<Project.NameKey, RefStatePattern> patterns) {
    return reviewDbChangeIsStale(indexChange, reviewDbChange)
        || refsAreStale(repoManager, id, states, patterns);
  }

  @VisibleForTesting
  static boolean refsAreStale(
      GitRepositoryManager repoManager,
      Change.Id id,
      SetMultimap<Project.NameKey, RefState> states,
      ListMultimap<Project.NameKey, RefStatePattern> patterns) {
    Set<Project.NameKey> projects = Sets.union(states.keySet(), patterns.keySet());

    for (Project.NameKey p : projects) {
      if (refsAreStale(repoManager, id, p, states, patterns)) {
        return true;
      }
    }

    return false;
  }

  @VisibleForTesting
  static boolean reviewDbChangeIsStale(Change indexChange, @Nullable Change reviewDbChange) {
    requireNonNull(indexChange);
    PrimaryStorage storageFromIndex = PrimaryStorage.of(indexChange);
    PrimaryStorage storageFromReviewDb = PrimaryStorage.of(reviewDbChange);
    if (reviewDbChange == null) {
      if (storageFromIndex == PrimaryStorage.REVIEW_DB) {
        return true; // Index says it should have been in ReviewDb, but it wasn't.
      }
      return false; // Not in ReviewDb, but that's ok.
    }
    checkArgument(
        indexChange.getId().equals(reviewDbChange.getId()),
        "mismatched change ID: %s != %s",
        indexChange.getId(),
        reviewDbChange.getId());
    if (storageFromIndex != storageFromReviewDb) {
      return true; // Primary storage differs, definitely stale.
    }
    if (storageFromReviewDb != PrimaryStorage.REVIEW_DB) {
      return false; // Not a ReviewDb change, don't check rowVersion.
    }
    return reviewDbChange.getRowVersion() != indexChange.getRowVersion();
  }

  private SetMultimap<Project.NameKey, RefState> parseStates(ChangeData cd) {
    return RefState.parseStates(cd.getRefStates());
  }

  private ListMultimap<Project.NameKey, RefStatePattern> parsePatterns(ChangeData cd) {
    return parsePatterns(cd.getRefStatePatterns());
  }

  public static ListMultimap<Project.NameKey, RefStatePattern> parsePatterns(
      Iterable<byte[]> patterns) {
    RefStatePattern.check(patterns != null, null);
    ListMultimap<Project.NameKey, RefStatePattern> result =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (byte[] b : patterns) {
      RefStatePattern.check(b != null, null);
      String s = new String(b, UTF_8);
      List<String> parts = Splitter.on(':').splitToList(s);
      RefStatePattern.check(parts.size() == 2, s);
      result.put(
          new Project.NameKey(Url.decode(parts.get(0))), RefStatePattern.create(parts.get(1)));
    }
    return result;
  }

  private static boolean refsAreStale(
      GitRepositoryManager repoManager,
      Change.Id id,
      Project.NameKey project,
      SetMultimap<Project.NameKey, RefState> allStates,
      ListMultimap<Project.NameKey, RefStatePattern> allPatterns) {
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
      logger.atWarning().withCause(e).log("error checking staleness of %s in %s", id, project);
      return true;
    }
  }

  /**
   * Pattern for matching refs.
   *
   * <p>Similar to '*' syntax for native Git refspecs, but slightly more powerful: the pattern may
   * contain arbitrarily many asterisks. There must be at least one '*' and the first one must
   * immediately follow a '/'.
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
          Streams.stream(Splitter.on('*').split(pattern))
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

    private boolean match(Repository repo, Set<RefState> expected) throws IOException {
      for (Ref r : repo.getRefDatabase().getRefsByPrefix(prefix())) {
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
