// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;

public class ChangeNoteUtil {

  static final String GERRIT_USER_TEMPLATE = "Gerrit User %d";

  private static final Gson gson = OutputFormat.JSON_COMPACT.newGson();

  private final ChangeNoteJson changeNoteJson;
  private final String serverId;

  @Inject
  public ChangeNoteUtil(ChangeNoteJson changeNoteJson, @GerritServerId String serverId) {
    this.serverId = serverId;
    this.changeNoteJson = changeNoteJson;
  }

  public ChangeNoteJson getChangeNoteJson() {
    return changeNoteJson;
  }

  /**
   * Generates a user identifier that contains the account ID, but not the user's name or email
   * address.
   *
   * @return The passed in {@link StringBuilder} instance to which the identifier has been appended.
   */
  StringBuilder appendAccountIdIdentString(StringBuilder stringBuilder, Account.Id accountId) {
    return stringBuilder
        .append(getAccountIdAsUsername(accountId))
        .append(" <")
        .append(getAccountIdAsEmailAddress(accountId))
        .append('>');
  }

  public static String formatAccountIdentString(Account.Id account, String accountIdAsEmail) {
    return String.format(
        "%s <%s>", ChangeNoteUtil.getAccountIdAsUsername(account), accountIdAsEmail);
  }

  /**
   * Returns a {@link PersonIdent} that contains the account ID, but not the user's name or email
   * address.
   */
  public PersonIdent newAccountIdIdent(
      Account.Id accountId, Instant when, PersonIdent serverIdent) {
    return new PersonIdent(
        getAccountIdAsUsername(accountId),
        getAccountIdAsEmailAddress(accountId),
        when,
        serverIdent.getZoneId());
  }

  /** Returns the string {@code "Gerrit User " + accountId}, to pseudonymize user names. */
  public static String getAccountIdAsUsername(Account.Id accountId) {
    return String.format(GERRIT_USER_TEMPLATE, accountId.get());
  }

  public String getAccountIdAsEmailAddress(Account.Id accountId) {
    return accountId.get() + "@" + serverId;
  }

  public static Optional<CommitMessageRange> parseCommitMessageRange(RevCommit commit) {
    byte[] raw = commit.getRawBuffer();
    int size = raw.length;

    int subjectStart = RawParseUtils.commitMessage(raw, 0);
    if (subjectStart < 0 || subjectStart >= size) {
      return Optional.empty();
    }

    int subjectEnd = RawParseUtils.endOfParagraph(raw, subjectStart);
    if (subjectEnd == size) {
      return Optional.empty();
    }

    int changeMessageStart;

    if (raw[subjectEnd] == '\n') {
      changeMessageStart = subjectEnd + 2; // \n\n ends paragraph
    } else if (raw[subjectEnd] == '\r') {
      changeMessageStart = subjectEnd + 4; // \r\n\r\n ends paragraph
    } else {
      return Optional.empty();
    }

    int ptr = size - 1;
    int changeMessageEnd = -1;
    while (ptr > changeMessageStart) {
      ptr = RawParseUtils.prevLF(raw, ptr, '\r');
      if (ptr == -1) {
        break;
      }
      if (raw[ptr] == '\n') {
        changeMessageEnd = ptr - 1;
        break;
      } else if (raw[ptr] == '\r') {
        changeMessageEnd = ptr - 3;
        break;
      }
    }

    if (ptr <= changeMessageStart) {
      // Return with subject, ChangeMessage is empty
      return Optional.of(
          CommitMessageRange.builder()
              .subjectStart(subjectStart)
              .subjectEnd(subjectEnd)
              .changeMessageStart(changeMessageStart)
              .changeMessageEnd(changeMessageStart)
              .build());
    }

    CommitMessageRange range =
        CommitMessageRange.builder()
            .subjectStart(subjectStart)
            .subjectEnd(subjectEnd)
            .changeMessageStart(changeMessageStart)
            .changeMessageEnd(changeMessageEnd)
            .build();

    return Optional.of(range);
  }

  @AutoValue
  public abstract static class CommitMessageRange {

    public abstract int subjectStart();

    public abstract int subjectEnd();

    public abstract int changeMessageStart();

    public abstract int changeMessageEnd();

    public boolean hasChangeMessage() {
      return changeMessageStart() < changeMessageEnd();
    }

    public static Builder builder() {
      return new AutoValue_ChangeNoteUtil_CommitMessageRange.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

      abstract Builder subjectStart(int subjectStart);

      abstract Builder subjectEnd(int subjectEnd);

      abstract Builder changeMessageStart(int changeMessageStart);

      abstract Builder changeMessageEnd(int changeMessageEnd);

      abstract CommitMessageRange build();
    }
  }

  /** Helper class for JSON serialization. Timestamp is taken from the commit. */
  public static class AttentionStatusInNoteDb {

    final String personIdent;
    final AttentionSetUpdate.Operation operation;
    final String reason;

    AttentionStatusInNoteDb(
        String personIndent, AttentionSetUpdate.Operation operation, String reason) {
      this.personIdent = personIndent;
      this.operation = operation;
      this.reason = reason;
    }
  }

  /** The returned {@link Optional} holds the parsed entity or is empty if parsing failed. */
  static Optional<AttentionSetUpdate> attentionStatusFromJson(
      Instant timestamp, String attentionString, NoteDbUtil noteDbUtil) {
    AttentionStatusInNoteDb inNoteDb =
        gson.fromJson(attentionString, AttentionStatusInNoteDb.class);
    PersonIdent personIdent = RawParseUtils.parsePersonIdent(inNoteDb.personIdent);
    if (personIdent == null) {
      return Optional.empty();
    }
    Optional<Account.Id> account = noteDbUtil.parseIdent(personIdent);
    return account.map(
        id ->
            AttentionSetUpdate.createFromRead(timestamp, id, inNoteDb.operation, inNoteDb.reason));
  }

  String attentionSetUpdateToJson(AttentionSetUpdate attentionSetUpdate) {
    StringBuilder stringBuilder = new StringBuilder();
    appendAccountIdIdentString(stringBuilder, attentionSetUpdate.account());
    return gson.toJson(
        new AttentionStatusInNoteDb(
            stringBuilder.toString(), attentionSetUpdate.operation(), attentionSetUpdate.reason()));
  }
}
