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
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.RefState;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.StalenessCheckResult;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Checker that compares values stored in the change index to metadata in NoteDb to detect index
 * documents that should have been updated (= stale).
 */
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

  @Inject
  StalenessChecker(
      ChangeIndexCollection indexes, GitRepositoryManager repoManager, IndexConfig indexConfig) {
    this.indexes = indexes;
    this.repoManager = repoManager;
    this.indexConfig = indexConfig;
  }

  /**
   * Returns a {@link StalenessCheckResult} with structured information about staleness of the
   * provided {@link com.google.gerrit.entities.Change.Id}.
   */
  public StalenessCheckResult check(Change.Id id) {
    ChangeIndex i = indexes.getSearchIndex();
    if (i == null) {
      return StalenessCheckResult
          .notStale(); // No index; caller couldn't do anything if it is stale.
    }
    if (!i.getSchema().hasField(ChangeField.REF_STATE)
        || !i.getSchema().hasField(ChangeField.REF_STATE_PATTERN)) {
      return StalenessCheckResult.notStale(); // Index version not new enough for this check.
    }

    Optional<ChangeData> result =
        i.get(id, IndexedChangeQuery.createOptions(indexConfig, 0, 1, FIELDS));
    if (!result.isPresent()) {
      return StalenessCheckResult.stale("Document %s missing from index", id);
    }
    ChangeData cd = result.get();
    return check(repoManager, id, parseStates(cd), parsePatterns(cd));
  }

  /**
   * Returns a {@link StalenessCheckResult} with structured information about staleness of the
   * provided change.
   */
  @UsedAt(UsedAt.Project.GOOGLE)
  public static StalenessCheckResult check(
      GitRepositoryManager repoManager,
      Change.Id id,
      SetMultimap<Project.NameKey, RefState> states,
      ListMultimap<Project.NameKey, RefStatePattern> patterns) {
    return refsAreStale(repoManager, id, states, patterns);
  }

  @VisibleForTesting
  static StalenessCheckResult refsAreStale(
      GitRepositoryManager repoManager,
      Change.Id id,
      SetMultimap<Project.NameKey, RefState> states,
      ListMultimap<Project.NameKey, RefStatePattern> patterns) {
    Set<Project.NameKey> projects = Sets.union(states.keySet(), patterns.keySet());

    for (Project.NameKey p : projects) {
      StalenessCheckResult result = refsAreStale(repoManager, id, p, states, patterns);
      if (result.isStale()) {
        return result;
      }
    }

    return StalenessCheckResult.notStale();
  }

  private SetMultimap<Project.NameKey, RefState> parseStates(ChangeData cd) {
    return RefState.parseStates(cd.getRefStates());
  }

  private ListMultimap<Project.NameKey, RefStatePattern> parsePatterns(ChangeData cd) {
    return parsePatterns(cd.getRefStatePatterns());
  }

  /**
   * Returns a map containing the parsed version of {@link RefStatePattern}. See {@link
   * RefStatePattern}.
   */
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
      result.put(Project.nameKey(Url.decode(parts.get(0))), RefStatePattern.create(parts.get(1)));
    }
    return result;
  }

  private static StalenessCheckResult refsAreStale(
      GitRepositoryManager repoManager,
      Change.Id id,
      Project.NameKey project,
      SetMultimap<Project.NameKey, RefState> allStates,
      ListMultimap<Project.NameKey, RefStatePattern> allPatterns) {
    try (Repository repo = repoManager.openRepository(project)) {
      Set<RefState> states = allStates.get(project);
      for (RefState state : states) {
        if (!state.match(repo)) {
          return StalenessCheckResult.stale(
              "Ref states don't match for document %s (%s != %s)",
              id, state, repo.exactRef(state.ref()));
        }
      }
      for (RefStatePattern pattern : allPatterns.get(project)) {
        if (!pattern.match(repo, states)) {
          return StalenessCheckResult.stale(
              "Ref patterns don't match for document %s. Pattern: %s States: %s",
              id, pattern, states);
        }
      }
      return StalenessCheckResult.notStale();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("error checking staleness of %s in %s", id, project);
      return StalenessCheckResult.stale("Exceptions while processing document %s", e.getMessage());
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
          Splitter.on('*')
              .splitToStream(pattern)
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
