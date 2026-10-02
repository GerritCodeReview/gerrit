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

package com.google.gerrit.server.restapi.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PutDraftCommentTest {
  @Test
  public void withDefaultLocationCopiesAllFields() throws Exception {
    DraftInput in = new DraftInput();
    for (Field field : inputFields()) {
      field.set(in, nonDefaultValue(field));
    }
    in.path = null;
    HumanComment orig =
        new HumanComment(
            new Comment.Key("uuid", "file.txt", 1),
            Account.id(100),
            Instant.ofEpochMilli(1234),
            (short) 1,
            "message",
            "serverId",
            false);

    DraftInput copy = PutDraftComment.withDefaultLocation(in, orig);

    assertThat(copy).isNotSameInstanceAs(in);
    assertThat(in.path).isNull();
    assertThat(copy.path).isEqualTo("file.txt");
    for (Field field : inputFields()) {
      if (!field.getName().equals("path")) {
        assertWithMessage("field %s must be copied", field.getName())
            .that(field.get(copy))
            .isEqualTo(field.get(in));
      }
    }
  }

  private static ImmutableList<Field> inputFields() {
    ImmutableList.Builder<Field> fields = ImmutableList.builder();
    for (Field field : DraftInput.class.getFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        fields.add(field);
      }
    }
    return fields.build();
  }

  private static Object nonDefaultValue(Field field) {
    Class<?> type = field.getType();
    if (type == String.class) {
      return "value-of-" + field.getName();
    } else if (type == Integer.class) {
      return 42;
    } else if (type == Boolean.class) {
      return true;
    } else if (type == Side.class) {
      return Side.PARENT;
    } else if (type == Timestamp.class) {
      return new Timestamp(1234);
    } else if (type == com.google.gerrit.extensions.client.Comment.Range.class) {
      com.google.gerrit.extensions.client.Comment.Range range =
          new com.google.gerrit.extensions.client.Comment.Range();
      range.startLine = 1;
      range.endLine = 42;
      return range;
    } else if (type == List.class) {
      return ImmutableList.of(new FixSuggestionInfo());
    }
    throw new AssertionError(
        String.format("Unhandled type %s of field %s, extend this test", type, field.getName()));
  }
}
