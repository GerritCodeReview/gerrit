// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.entities.converter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.CommentRange;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.FixSuggestion;
import com.google.gerrit.entities.HumanComment;
import java.time.Instant;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class HumanCommentProtoConverterTest {
  private static final ObjectId VALID_OBJECT_ID =
      ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");

  private final HumanCommentProtoConverter converter = HumanCommentProtoConverter.INSTANCE;

  @Test
  public void fileLevelCommentWithAllOptionalFields() {
    HumanComment orig =
        new HumanComment(
            new Comment.Key("uuid", "a.txt", 42),
            Account.id(314),
            Instant.ofEpochMilli(12345),
            (short) 1,
            "message",
            "server",
            /* unresolved= */ true,
            VALID_OBJECT_ID.getName(),
            "parent uuid",
            "tag",
            ImmutableList.of(
                new FixSuggestion(
                    "fixId",
                    "fixDesc",
                    ImmutableList.of(
                        new FixReplacement("fixPath", new Range(1, 2, 3, 4), "fixReplacement")))),
            Account.id(314));

    HumanComment res = converter.fromProto(converter.toProto(orig));

    assertThat(res).isEqualTo(orig);
  }

  @Test
  public void patchsetLevelComment() {
    HumanComment orig =
        new HumanComment(
            new Comment.Key("uuid", PATCHSET_LEVEL, 42),
            Account.id(314),
            Instant.ofEpochMilli(12345),
            (short) 1,
            "message",
            "server",
            /* unresolved= */ false);

    HumanComment res = converter.fromProto(converter.toProto(orig));

    assertThat(res).isEqualTo(orig);
  }

  @Test
  public void lineComment() {
    HumanComment orig =
        new HumanComment(
            new Comment.Key("uuid", "a.txt", 42),
            Account.id(314),
            Instant.ofEpochMilli(12345),
            (short) 1,
            "message",
            "server",
            /* unresolved= */ true);
    orig.setLineNbrAndRange(7, null);

    HumanComment res = converter.fromProto(converter.toProto(orig));

    assertThat(res).isEqualTo(orig);
  }

  @Test
  public void rangeComment() {
    HumanComment orig =
        new HumanComment(
            new Comment.Key("uuid", "a.txt", 42),
            Account.id(314),
            Instant.ofEpochMilli(12345),
            (short) 1,
            "message",
            "server",
            /* unresolved= */ true);
    orig.setRange(new CommentRange(2, 3, 5, 7));

    HumanComment res = converter.fromProto(converter.toProto(orig));

    assertThat(res).isEqualTo(orig);
  }

  @Test
  public void extensionRangeComment() {
    HumanComment orig =
        new HumanComment(
            new Comment.Key("uuid", "a.txt", 42),
            Account.id(314),
            Instant.ofEpochMilli(12345),
            (short) 1,
            "message",
            "server",
            /* unresolved= */ false);
    com.google.gerrit.extensions.client.Comment.Range range =
        new com.google.gerrit.extensions.client.Comment.Range();
    range.startLine = 2;
    range.startCharacter = 3;
    range.endLine = 5;
    range.endCharacter = 7;
    orig.setLineNbrAndRange(null, range);

    HumanComment res = converter.fromProto(converter.toProto(orig));

    assertThat(res).isEqualTo(orig);
  }
}
