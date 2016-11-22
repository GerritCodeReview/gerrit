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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.change.StalenessChecker.RefState;
import com.google.gerrit.server.index.change.StalenessChecker.RefStatePattern;
import com.google.gerrit.testutil.GerritBaseTests;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import java.util.stream.Stream;

public class StalenessCheckerTest extends GerritBaseTests {
  private static final String SHA1 = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
  private static final String SHA2 = "badc0feebadc0feebadc0feebadc0feebadc0fee";

  private static final Project.NameKey P1 = new Project.NameKey("project1");
  private static final Project.NameKey P2 = new Project.NameKey("project2");

  @Test
  public void parseStates() {
    assertInvalidState(null);
    assertInvalidState("");
    assertInvalidState("project1:refs/heads/foo");
    assertInvalidState("project1:refs/heads/foo:notasha");
    assertInvalidState("project1:refs/heads/foo:");

    assertThat(
            StalenessChecker.parseStates(
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
      StalenessChecker.parseStates(byteArrays(state));
      assert_().fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void refStateToByteArray() {
    assertThat(
            new String(
                RefState.create("refs/heads/foo", ObjectId.fromString(SHA1))
                    .toByteArray(P1),
                UTF_8))
        .isEqualTo(P1 + ":refs/heads/foo:" + SHA1);
    assertThat(
            new String(
                RefState.create("refs/heads/foo", (ObjectId) null)
                    .toByteArray(P1),
                UTF_8))
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
    assertThat(p.regex().pattern())
        .isEqualTo("^\\Qrefs/heads/foo/\\E.*\\Q/bar\\E$");
    assertThat(p.match("refs/heads/foo//bar")).isTrue();
    assertThat(p.match("refs/heads/foo/x/bar")).isTrue();
    assertThat(p.match("refs/heads/foo/x/y/bar")).isTrue();
    assertThat(p.match("refs/heads/foo/x/baz")).isFalse();

    p = r.get(P2).get(1);
    assertThat(p.pattern()).isEqualTo("refs/heads/foo/*-baz/*/quux");
    assertThat(p.prefix()).isEqualTo("refs/heads/foo/");
    assertThat(p.regex().pattern())
        .isEqualTo("^\\Qrefs/heads/foo/\\E.*\\Q-baz/\\E.*\\Q/quux\\E$");
    assertThat(p.match("refs/heads/foo/-baz//quux")).isTrue();
    assertThat(p.match("refs/heads/foo/x-baz/x/quux")).isTrue();
    assertThat(p.match("refs/heads/foo/x/y-baz/x/y/quux")).isTrue();
    assertThat(p.match("refs/heads/foo/x-baz/x/y")).isFalse();
  }

  @Test
  public void refStatePatternToByteArray() {
    assertThat(
            new String(RefStatePattern.create("refs/*").toByteArray(P1), UTF_8))
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

  private static Iterable<byte[]> byteArrays(String... strs) {
    return Stream.of(strs).map(s -> s != null ? s.getBytes(UTF_8) : null)
        .collect(toList());
  }
}
