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

package com.google.gerrit.git.testing;

import static com.google.common.truth.Truth.assertAbout;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

/** Subject over JGit {@link RevCommit}s. */
public class CommitSubject extends Subject {

  /**
   * Constructs a new subject.
   *
   * @param commit the commit.
   * @return a new subject over the commit.
   */
  public static CommitSubject assertThat(RevCommit commit) {
    return assertAbout(CommitSubject::new).that(commit);
  }

  /**
   * Performs some common assertions over a single commit.
   *
   * @param commit the commit.
   * @param expectedCommitMessage exact expected commit message.
   * @param expectedCommitTimestamp expected commit timestamp, to the tolerance specified in {@link
   *     #hasCommitTimestamp(Timestamp)}.
   * @param expectedSha1 expected commit SHA-1.
   */
  public static void assertCommit(
      RevCommit commit,
      String expectedCommitMessage,
      Timestamp expectedCommitTimestamp,
      ObjectId expectedSha1) {
    CommitSubject commitSubject = assertThat(commit);
    commitSubject.hasCommitMessage(expectedCommitMessage);
    commitSubject.hasCommitTimestamp(expectedCommitTimestamp);
    commitSubject.hasSha1(expectedSha1);
  }

  private final RevCommit commit;

  private CommitSubject(FailureMetadata metadata, RevCommit commit) {
    super(metadata, commit);
    this.commit = commit;
  }

  /**
   * Asserts that the commit has the given commit message.
   *
   * @param expectedCommitMessage exact expected commit message.
   */
  public void hasCommitMessage(String expectedCommitMessage) {
    isNotNull();
    check("getFullMessage()").that(commit.getFullMessage()).isEqualTo(expectedCommitMessage);
  }

  /**
   * Asserts that the commit has the given commit message, up to skew of at most 1 second.
   *
   * @param expectedCommitTimestamp expected commit timestamp.
   */
  public void hasCommitTimestamp(Timestamp expectedCommitTimestamp) {
    isNotNull();
    long timestampDiffMs =
        Math.abs(commit.getCommitTime() * 1000L - expectedCommitTimestamp.getTime());
    check("commitTimestampDiff()").that(timestampDiffMs).isAtMost(SECONDS.toMillis(1));
  }

  /**
   * Asserts that the commit has the given SHA-1.
   *
   * @param expectedSha1 expected commit SHA-1.
   */
  public void hasSha1(ObjectId expectedSha1) {
    isNotNull();
    check("sha1()").that(commit).isEqualTo(expectedSha1);
  }
}
