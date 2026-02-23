// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.util;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Optional;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JujutsuChangeIdUtilTest {

  private static final String VALID_JJ_ID = "mlqnqnkrxpuvuuxzlzoltostwlwyskpx";

  private InMemoryRepository repo;

  @Before
  public void setUp() {
    repo = new InMemoryRepository(new DfsRepositoryDescription("test"));
  }

  @After
  public void tearDown() {
    repo.close();
  }

  @Test
  public void isJujutsuChangeId_validId_returnsTrue() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId(VALID_JJ_ID)).isTrue();
  }

  @Test
  public void isJujutsuChangeId_null_returnsFalse() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId(null)).isFalse();
  }

  @Test
  public void isJujutsuChangeId_tooShort_returnsFalse() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId("abcdefghijklmnopqrstuvwxyzabcde")).isFalse();
  }

  @Test
  public void isJujutsuChangeId_tooLong_returnsFalse() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId("abcdefghijklmnopqrstuvwxyzabcdefg"))
        .isFalse();
  }

  @Test
  public void isJujutsuChangeId_containsUpperCase_returnsFalse() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId("Mlqnqnkrxpuvuuxzlzoltostwlwyskpx")).isFalse();
  }

  @Test
  public void isJujutsuChangeId_containsDigit_returnsFalse() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId("1lqnqnkrxpuvuuxzlzoltostwlwyskpx")).isFalse();
  }

  @Test
  public void isJujutsuChangeId_gerritStyleId_returnsFalse() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId("I1234567890abcdef1234567890abcdef12345678"))
        .isFalse();
  }

  @Test
  public void isJujutsuChangeId_empty_returnsFalse() {
    assertThat(JujutsuChangeIdUtil.isJujutsuChangeId("")).isFalse();
  }

  @Test
  public void getChangeIdFromCommitHeader_withJjHeader_returnsId() throws Exception {
    RevCommit commit = buildCommit("change-id", VALID_JJ_ID, "Some commit message");
    assertThat(JujutsuChangeIdUtil.getChangeIdFromCommitHeader(commit))
        .isEqualTo(Optional.of(VALID_JJ_ID));
  }

  @Test
  public void getChangeIdFromCommitHeader_noExtraHeader_returnsEmpty() throws Exception {
    RevCommit commit = buildCommit(null, null, "Commit without JJ header");
    assertThat(JujutsuChangeIdUtil.getChangeIdFromCommitHeader(commit)).isEmpty();
  }

  @Test
  public void getChangeIdFromCommitHeader_unrelatedExtraHeader_returnsEmpty() throws Exception {
    RevCommit commit = buildCommit("gpgsig", "fakesig", "Commit with unrelated header");
    assertThat(JujutsuChangeIdUtil.getChangeIdFromCommitHeader(commit)).isEmpty();
  }

  @Test
  public void getChangeIdFromCommitHeader_jjIdInMessageFooter_returnsEmpty() throws Exception {
    // JJ id in the commit message body — not in the header section — must NOT be found.
    RevCommit commit = buildCommit(null, null, "Commit message\n\nchange-id " + VALID_JJ_ID);
    assertThat(JujutsuChangeIdUtil.getChangeIdFromCommitHeader(commit)).isEmpty();
  }

  @Test
  public void hasJujutsuChangeId_withJjHeader_returnsTrue() throws Exception {
    RevCommit commit = buildCommit("change-id", VALID_JJ_ID, "Some commit");
    assertThat(JujutsuChangeIdUtil.hasJujutsuChangeId(commit)).isTrue();
  }

  @Test
  public void hasJujutsuChangeId_noJjHeader_returnsFalse() throws Exception {
    RevCommit commit = buildCommit(null, null, "Some commit");
    assertThat(JujutsuChangeIdUtil.hasJujutsuChangeId(commit)).isFalse();
  }

  /**
   * Inserts a raw commit object into the in-memory repo and returns the parsed {@link RevCommit}.
   */
  private RevCommit buildCommit(String extraHeaderName, String extraHeaderValue, String message)
      throws Exception {
    String emptyTreeSha = "4b825dc642cb6eb9a060e54bf8d69288fbee4904";
    PersonIdent ident = new PersonIdent("Test User", "test@example.com", 0L, 0);
    StringBuilder raw = new StringBuilder();
    raw.append("tree ").append(emptyTreeSha).append("\n");
    raw.append("author ").append(ident.toExternalString()).append("\n");
    raw.append("committer ").append(ident.toExternalString()).append("\n");
    if (extraHeaderName != null) {
      raw.append(extraHeaderName).append(" ").append(extraHeaderValue).append("\n");
    }
    raw.append("\n");
    raw.append(message).append("\n");

    byte[] bytes = raw.toString().getBytes(UTF_8);
    ObjectId id;
    try (ObjectInserter ins = repo.newObjectInserter()) {
      id = ins.insert(Constants.OBJ_COMMIT, bytes);
      ins.flush();
    }
    try (RevWalk rw = new RevWalk(repo)) {
      return rw.parseCommit(id);
    }
  }
}
