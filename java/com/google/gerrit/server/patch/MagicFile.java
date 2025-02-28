// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.gerrit.git.ObjectIds;
import java.io.IOException;
import java.text.SimpleDateFormat;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Representation of a magic file which appears as a file with content to Gerrit users. */
@AutoValue
public abstract class MagicFile {

  public static MagicFile forCommitMessage(ObjectReader reader, AnyObjectId commitId)
      throws IOException {
    try (RevWalk rw = new RevWalk(reader)) {
      RevCommit c;
      if (commitId instanceof RevCommit) {
        c = (RevCommit) commitId;
      } else {
        c = rw.parseCommit(commitId);
      }

      String header = createCommitMessageHeader(reader, rw, c);
      String message = c.getFullMessage();
      return MagicFile.builder().generatedContent(header).modifiableContent(message).build();
    }
  }

  private static String createCommitMessageHeader(ObjectReader reader, RevWalk rw, RevCommit c)
      throws IOException {
    StringBuilder b = new StringBuilder();
    switch (c.getParentCount()) {
      case 0 -> {}
      case 1 -> {
        RevCommit p = c.getParent(0);
        rw.parseBody(p);
        b.append("Parent:     ");
        b.append(abbreviateName(p, reader));
        b.append(" (");
        b.append(p.getShortMessage());
        b.append(")\n");
      }
      default -> {
        for (int i = 0; i < c.getParentCount(); i++) {
          RevCommit p = c.getParent(i);
          rw.parseBody(p);
          b.append(i == 0 ? "Merge Of:   " : "            ");
          b.append(abbreviateName(p, reader));
          b.append(" (");
          b.append(p.getShortMessage());
          b.append(")\n");
        }
      }
    }
    appendPersonIdent(b, "Author", c.getAuthorIdent());
    appendPersonIdent(b, "Commit", c.getCommitterIdent());
    b.append("\n");
    return b.toString();
  }

  public static MagicFile forMergeList(
      ComparisonType comparisonType, ObjectReader reader, AnyObjectId commitId) throws IOException {
    try (RevWalk rw = new RevWalk(reader)) {
      RevCommit c = rw.parseCommit(commitId);
      StringBuilder b = new StringBuilder();
      switch (c.getParentCount()) {
        case 0 -> {}
        case 1 -> {}
        default -> {
          int uninterestingParent =
              comparisonType.isAgainstParent() ? comparisonType.getParentNum().get() : 1;

          b.append("Merge List:\n\n");
          for (RevCommit commit : MergeListBuilder.build(rw, c, uninterestingParent)) {
            b.append("* ");
            b.append(abbreviateName(commit, reader));
            b.append(" ");
            b.append(commit.getShortMessage());
            b.append("\n");
          }
        }
      }
      return MagicFile.builder().generatedContent(b.toString()).build();
    }
  }

  private static String abbreviateName(RevCommit p, ObjectReader reader) throws IOException {
    return ObjectIds.abbreviateName(p, 8, reader);
  }

  private static void appendPersonIdent(StringBuilder b, String field, PersonIdent person) {
    if (person != null) {
      b.append(field).append(":    ");
      if (person.getName() != null) {
        b.append(" ");
        b.append(person.getName());
      }
      if (person.getEmailAddress() != null) {
        b.append(" <");
        b.append(person.getEmailAddress());
        b.append(">");
      }
      b.append("\n");

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ZZZ");
      sdf.setTimeZone(person.getTimeZone());
      b.append(field).append("Date: ");
      b.append(sdf.format(person.getWhen()));
      b.append("\n");
    }
  }

  /** Generated part of the file. Any generated contents should go here. Can be empty. */
  public abstract String generatedContent();

  /**
   * Non-generated part of the file. This should correspond to some actual content derived from
   * somewhere else which can also be modified (e.g. by suggested fixes). Can be empty.
   */
  public abstract String modifiableContent();

  /** Whole content of the file as it appears to users. */
  public String getFileContent() {
    return generatedContent() + modifiableContent();
  }

  /** Returns the start line of the modifiable content. Assumes that line counting starts at 1. */
  public int getStartLineOfModifiableContent() {
    int numHeaderLines = CharMatcher.is('\n').countIn(generatedContent());
    // Lines start at 1 and not 0. -> Add 1.
    return 1 + numHeaderLines;
  }

  static Builder builder() {
    return new AutoValue_MagicFile.Builder().generatedContent("").modifiableContent("");
  }

  @AutoValue.Builder
  abstract static class Builder {

    /** See {@link #generatedContent()}. Use an empty string to denote no such content. */
    public abstract Builder generatedContent(String content);

    /** See {@link #modifiableContent()}. Use an empty string to denote no such content. */
    public abstract Builder modifiableContent(String content);

    abstract String generatedContent();

    abstract String modifiableContent();

    abstract MagicFile autoBuild();

    public MagicFile build() {
      // Normalize each content part to end with a newline character, which simplifies further
      // handling.
      if (!generatedContent().isEmpty() && !generatedContent().endsWith("\n")) {
        generatedContent(generatedContent() + "\n");
      }
      if (!modifiableContent().isEmpty() && !modifiableContent().endsWith("\n")) {
        modifiableContent(modifiableContent() + "\n");
      }
      return autoBuild();
    }
  }
}
