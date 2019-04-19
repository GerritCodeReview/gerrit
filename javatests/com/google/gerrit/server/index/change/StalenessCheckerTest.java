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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.server.index.change.StalenessChecker.refsAreStale;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.index.RefState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.StalenessChecker.RefStatePattern;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.util.stream.Stream;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class StalenessCheckerTest extends GerritBaseTests {
  private static final String SHA1 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
  private static final String SHA2 = "badc0feebadc0feebadc0feebadc0feebadc0fee";

  private static final Project.NameKey P1 = Project.nameKey("project1");
  private static final Project.NameKey P2 = Project.nameKey("project2");

  private static final Change.Id C = new Change.Id(1234);

  private GitRepositoryManager repoManager;
  private Repository r1;
  private Repository r2;
  private TestRepository<Repository> tr1;
  private TestRepository<Repository> tr2;

  @Before
  public void setUp() throws Exception {
    repoManager = new InMemoryRepositoryManager();
    r1 = repoManager.createRepository(P1);
    tr1 = new TestRepository<>(r1);
    r2 = repoManager.createRepository(P2);
    tr2 = new TestRepository<>(r2);
  }

  @Test
  public void parseStates() {
    assertInvalidState(null);
    assertInvalidState("");
    assertInvalidState("project1:refs/heads/foo");
    assertInvalidState("project1:refs/heads/foo:notasha");
    assertInvalidState("project1:refs/heads/foo:");

    assertThat(
            RefState.parseStates(
                byteArrays(
                    P1 + ":refs/heads/foo:" + SHA1,
                    P1 + ":refs/heads/bar:" + SHA2,
                    P2 + ":refs/heads/baz:" + SHA1)))
        .isEqualTo(
            ImmutableSetMultimap.of(
                P1, RefState.create("refs/heads/foo", SHA1),
                P1, RefState.create("refs/heads/bar", SHA2),
                P2, RefState.create("refs/heads/baz", SHA1)));
  }

  private static void assertInvalidState(String state) {
    try {
      RefState.parseStates(byteArrays(state));
      assert_().fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void refStateToByteArray() {
    assertThat(
            new String(
                RefState.create("refs/heads/foo", ObjectId.fromString(SHA1)).toByteArray(P1),
                UTF_8))
        .isEqualTo(P1 + ":refs/heads/foo:" + SHA1);
    assertThat(
            new String(RefState.create("refs/heads/foo", (ObjectId) null).toByteArray(P1), UTF_8))
        .isEqualTo(P1 + ":refs/heads/foo:" + ObjectId.zeroId().name());
  }

  @Test
  public void parsePatterns() {
    assertInvalidPattern(null);
    assertInvalidPattern("");
    assertInvalidPattern("project:");
    assertInvalidPattern("project:refs/heads/foo");
    assertInvalidPattern("project:refs/he*ds/bar");
    assertInvalidPattern("project:refs/(he)*ds/bar");
    assertInvalidPattern("project:invalidrefname");

    ListMultimap<Project.NameKey, RefStatePattern> r =
        StalenessChecker.parsePatterns(
            byteArrays(
                P1 + ":refs/heads/*",
                P2 + ":refs/heads/foo/*/bar",
                P2 + ":refs/heads/foo/*-baz/*/quux"));

    assertThat(r.keySet()).containsExactly(P1, P2);
    RefStatePattern p = r.get(P1).get(0);
    assertThat(p.pattern()).isEqualTo("refs/heads/*");
    assertThat(p.prefix()).isEqualTo("refs/heads/");
    assertThat(p.regex().pattern()).isEqualTo("^\\Qrefs/heads/\\E.*\\Q\\E$");
    assertThat(p.match("refs/heads/foo")).isTrue();
    assertThat(p.match("xrefs/heads/foo")).isFalse();
    assertThat(p.match("refs/tags/foo")).isFalse();

    p = r.get(P2).get(0);
    assertThat(p.pattern()).isEqualTo("refs/heads/foo/*/bar");
    assertThat(p.prefix()).isEqualTo("refs/heads/foo/");
    assertThat(p.regex().pattern()).isEqualTo("^\\Qrefs/heads/foo/\\E.*\\Q/bar\\E$");
    assertThat(p.match("refs/heads/foo//bar")).isTrue();
    assertThat(p.match("refs/heads/foo/x/bar")).isTrue();
    assertThat(p.match("refs/heads/foo/x/y/bar")).isTrue();
    assertThat(p.match("refs/heads/foo/x/baz")).isFalse();

    p = r.get(P2).get(1);
    assertThat(p.pattern()).isEqualTo("refs/heads/foo/*-baz/*/quux");
    assertThat(p.prefix()).isEqualTo("refs/heads/foo/");
    assertThat(p.regex().pattern()).isEqualTo("^\\Qrefs/heads/foo/\\E.*\\Q-baz/\\E.*\\Q/quux\\E$");
    assertThat(p.match("refs/heads/foo/-baz//quux")).isTrue();
    assertThat(p.match("refs/heads/foo/x-baz/x/quux")).isTrue();
    assertThat(p.match("refs/heads/foo/x/y-baz/x/y/quux")).isTrue();
    assertThat(p.match("refs/heads/foo/x-baz/x/y")).isFalse();
  }

  @Test
  public void refStatePatternToByteArray() {
    assertThat(new String(RefStatePattern.create("refs/*").toByteArray(P1), UTF_8))
        .isEqualTo(P1 + ":refs/*");
  }

  private static void assertInvalidPattern(String state) {
    try {
      StalenessChecker.parsePatterns(byteArrays(state));
      assert_().fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void isStaleRefStatesOnly() throws Exception {
    String ref1 = "refs/heads/foo";
    ObjectId id1 = tr1.update(ref1, tr1.commit().message("commit 1"));
    String ref2 = "refs/heads/bar";
    ObjectId id2 = tr2.update(ref2, tr2.commit().message("commit 2"));

    // Not stale.
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(
                    P1, RefState.create(ref1, id1.name()),
                    P2, RefState.create(ref2, id2.name())),
                ImmutableListMultimap.of()))
        .isFalse();

    // Wrong ref value.
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(
                    P1, RefState.create(ref1, SHA1),
                    P2, RefState.create(ref2, id2.name())),
                ImmutableListMultimap.of()))
        .isTrue();

    // Swapped repos.
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(
                    P1, RefState.create(ref1, id2.name()),
                    P2, RefState.create(ref2, id1.name())),
                ImmutableListMultimap.of()))
        .isTrue();

    // Two refs in same repo, not stale.
    String ref3 = "refs/heads/baz";
    ObjectId id3 = tr1.update(ref3, tr1.commit().message("commit 3"));
    tr1.update(ref3, id3);
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(
                    P1, RefState.create(ref1, id1.name()),
                    P1, RefState.create(ref3, id3.name())),
                ImmutableListMultimap.of()))
        .isFalse();

    // Ignore ref not mentioned.
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(P1, RefState.create(ref1, id1.name())),
                ImmutableListMultimap.of()))
        .isFalse();

    // One ref wrong.
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(
                    P1, RefState.create(ref1, id1.name()),
                    P1, RefState.create(ref3, SHA1)),
                ImmutableListMultimap.of()))
        .isTrue();
  }

  @Test
  public void isStaleWithRefStatePatterns() throws Exception {
    String ref1 = "refs/heads/foo";
    ObjectId id1 = tr1.update(ref1, tr1.commit().message("commit 1"));

    // ref1 is only ref matching pattern.
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(P1, RefState.create(ref1, id1.name())),
                ImmutableListMultimap.of(P1, RefStatePattern.create("refs/heads/*"))))
        .isFalse();

    // Now ref2 matches pattern, so stale unless ref2 is present in state map.
    String ref2 = "refs/heads/bar";
    ObjectId id2 = tr1.update(ref2, tr1.commit().message("commit 2"));
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(P1, RefState.create(ref1, id1.name())),
                ImmutableListMultimap.of(P1, RefStatePattern.create("refs/heads/*"))))
        .isTrue();
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(
                    P1, RefState.create(ref1, id1.name()),
                    P1, RefState.create(ref2, id2.name())),
                ImmutableListMultimap.of(P1, RefStatePattern.create("refs/heads/*"))))
        .isFalse();
  }

  @Test
  public void isStaleWithNonPrefixPattern() throws Exception {
    String ref1 = "refs/heads/foo";
    ObjectId id1 = tr1.update(ref1, tr1.commit().message("commit 1"));
    tr1.update("refs/heads/bar", tr1.commit().message("commit 2"));

    // ref1 is only ref matching pattern.
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(P1, RefState.create(ref1, id1.name())),
                ImmutableListMultimap.of(P1, RefStatePattern.create("refs/*/foo"))))
        .isFalse();

    // Now ref2 matches pattern, so stale unless ref2 is present in state map.
    String ref3 = "refs/other/foo";
    ObjectId id3 = tr1.update(ref3, tr1.commit().message("commit 3"));
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(P1, RefState.create(ref1, id1.name())),
                ImmutableListMultimap.of(P1, RefStatePattern.create("refs/*/foo"))))
        .isTrue();
    assertThat(
            refsAreStale(
                repoManager,
                C,
                ImmutableSetMultimap.of(
                    P1, RefState.create(ref1, id1.name()),
                    P1, RefState.create(ref3, id3.name())),
                ImmutableListMultimap.of(P1, RefStatePattern.create("refs/*/foo"))))
        .isFalse();
  }

  private static Iterable<byte[]> byteArrays(String... strs) {
    return Stream.of(strs).map(s -> s != null ? s.getBytes(UTF_8) : null).collect(toList());
  }
}
