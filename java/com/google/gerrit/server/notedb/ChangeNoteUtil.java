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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.RawParseUtils;

public class ChangeNoteUtil {

  public static final FooterKey FOOTER_ATTENTION = new FooterKey("Attention");
  public static final FooterKey FOOTER_ASSIGNEE = new FooterKey("Assignee");
  public static final FooterKey FOOTER_BRANCH = new FooterKey("Branch");
  public static final FooterKey FOOTER_CHANGE_ID = new FooterKey("Change-id");
  public static final FooterKey FOOTER_COMMIT = new FooterKey("Commit");
  public static final FooterKey FOOTER_CURRENT = new FooterKey("Current");
  public static final FooterKey FOOTER_GROUPS = new FooterKey("Groups");
  public static final FooterKey FOOTER_HASHTAGS = new FooterKey("Hashtags");
  public static final FooterKey FOOTER_LABEL = new FooterKey("Label");
  public static final FooterKey FOOTER_COPIED_LABEL = new FooterKey("Copied-Label");
  public static final FooterKey FOOTER_PATCH_SET = new FooterKey("Patch-set");
  public static final FooterKey FOOTER_PATCH_SET_DESCRIPTION =
      new FooterKey("Patch-set-description");
  public static final FooterKey FOOTER_PRIVATE = new FooterKey("Private");
  public static final FooterKey FOOTER_REAL_USER = new FooterKey("Real-user");
  public static final FooterKey FOOTER_STATUS = new FooterKey("Status");
  public static final FooterKey FOOTER_SUBJECT = new FooterKey("Subject");
  public static final FooterKey FOOTER_SUBMISSION_ID = new FooterKey("Submission-id");
  public static final FooterKey FOOTER_SUBMITTED_WITH = new FooterKey("Submitted-with");
  public static final FooterKey FOOTER_TOPIC = new FooterKey("Topic");
  public static final FooterKey FOOTER_TAG = new FooterKey("Tag");
  public static final FooterKey FOOTER_WORK_IN_PROGRESS = new FooterKey("Work-in-progress");
  public static final FooterKey FOOTER_REVERT_OF = new FooterKey("Revert-of");
  public static final FooterKey FOOTER_CHERRY_PICK_OF = new FooterKey("Cherry-pick-of");

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
      Instant timestamp, String attentionString) {
    AttentionStatusInNoteDb inNoteDb =
        gson.fromJson(attentionString, AttentionStatusInNoteDb.class);
    PersonIdent personIdent = RawParseUtils.parsePersonIdent(inNoteDb.personIdent);
    if (personIdent == null) {
      return Optional.empty();
    }
    Optional<Account.Id> account = NoteDbUtil.parseIdent(personIdent);
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

  /**
   * {@link com.google.gerrit.entities.PatchSetApproval}, parsed from {@link #FOOTER_LABEL} or
   * {@link #FOOTER_COPIED_LABEL}.
   *
   * <p>In comparison to {@link com.google.gerrit.entities.PatchSetApproval}, this entity represent
   * the raw fields, parsed from the NoteDB footer line, without any interpretation of the parsed
   * values. See {@link #parseApproval} and {@link #parseCopiedApproval} for the valid {@link
   * #footerLine} values.
   */
  @AutoValue
  public abstract static class ParsedPatchSetApproval {

    /** The original footer value, that this entity was parsed from. */
    public abstract String footerLine();

    public abstract boolean isRemoval();

    /** Either <LABEL>=VOTE or <LABEL> for {@link #isRemoval}. */
    public abstract String labelVote();

    public abstract Optional<String> uuid();

    public abstract Optional<String> accountIdent();

    public abstract Optional<String> realAccountIdent();

    public abstract Optional<String> tag();

    public static Builder builder() {
      return new AutoValue_ChangeNoteUtil_ParsedPatchSetApproval.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

      abstract Builder footerLine(String labelLine);

      abstract Builder isRemoval(boolean isRemoval);

      abstract Builder labelVote(String labelVote);

      abstract Builder uuid(Optional<String> uuid);

      abstract Builder accountIdent(Optional<String> accountIdent);

      abstract Builder realAccountIdent(Optional<String> realAccountIdent);

      abstract Builder tag(Optional<String> tag);

      abstract ParsedPatchSetApproval build();
    }
  }

  /**
   * Parses {@link ParsedPatchSetApproval} from {@link #FOOTER_LABEL} line.
   *
   * <p>Valid added approval footer examples:
   *
   * <ul>
   *   <li>Label: <LABEL>=VOTE
   *   <li>Label: <LABEL>=VOTE <Gerrit Account>
   *   <li>Label: <LABEL>=VOTE, <UUID>
   *   <li>Label: <LABEL>=VOTE, <UUID> <Gerrit Account>
   * </ul>
   *
   * <p>Valid removed approval footer examples:
   *
   * <ul>
   *   <li>-<LABEL>
   *   <li>-<LABEL> <Gerrit Account>
   * </ul>
   *
   * <p><UUID> is optional, since the approval might have been granted before {@link
   * com.google.gerrit.entities.PatchSetApproval.UUID} was introduced.
   *
   * <p><Gerrit Account> is only persisted in cases, when the account, that granted the vote does
   * not match the account, that issued {@link ChangeUpdate} (created this NoteDB commit).
   */
  public static ParsedPatchSetApproval parseApproval(String footerLine)
      throws ConfigInvalidException {
    try {
      ParsedPatchSetApproval.Builder rawPatchSetApproval =
          ParsedPatchSetApproval.builder().footerLine(footerLine);
      String labelVoteStr;
      boolean isRemoval = footerLine.startsWith("-");
      rawPatchSetApproval.isRemoval(isRemoval);
      int uuidStart = isRemoval ? -1 : footerLine.indexOf(", ");
      int reviewerStart = footerLine.indexOf(' ', uuidStart != -1 ? uuidStart + 2 : 0);
      int labelStart = isRemoval ? 1 : 0;
      checkFooter(!isRemoval || uuidStart == -1, FOOTER_LABEL, footerLine);

      if (uuidStart != -1) {
        String uuid =
            footerLine.substring(
                uuidStart + 2, reviewerStart > 0 ? reviewerStart : footerLine.length());
        checkFooter(!Strings.isNullOrEmpty(uuid), FOOTER_LABEL, footerLine);
        labelVoteStr = footerLine.substring(labelStart, uuidStart);
        rawPatchSetApproval.uuid(Optional.of(uuid));
      } else if (reviewerStart != -1) {
        labelVoteStr = footerLine.substring(labelStart, reviewerStart);
      } else {
        labelVoteStr = footerLine.substring(labelStart);
      }
      rawPatchSetApproval.labelVote(labelVoteStr);

      if (reviewerStart > 0) {
        String ident = footerLine.substring(reviewerStart + 1);
        rawPatchSetApproval.accountIdent(Optional.of(ident));
      }
      return rawPatchSetApproval.build();
    } catch (StringIndexOutOfBoundsException ex) {
      throw parseException(FOOTER_LABEL, footerLine, ex);
    }
  }

  /**
   * Parses copied {@link ParsedPatchSetApproval} from {@link #FOOTER_COPIED_LABEL} line.
   *
   * <p>Footer example: Copied-Label: <LABEL>=VOTE, <UUID> <Gerrit Account>,<Gerrit Real Account>
   * :"<TAG>"
   *
   * <ul>
   *   <li>":<"TAG>"" is optional.
   *   <li><Gerrit Real Account> is also optional, if it was not set.
   *   <li><UUID> is optional, since the approval might have been granted before {@link
   *       com.google.gerrit.entities.PatchSetApproval.UUID} was introduced.
   *   <li>The label, vote, and the Gerrit account are mandatory (unlike FOOTER_LABEL where Gerrit
   *       Account is also optional since by default it's the committer).
   * </ul>
   */
  public static ParsedPatchSetApproval parseCopiedApproval(String labelLine)
      throws ConfigInvalidException {
    try {
      // Copied approvals can't be explicitly removed. They are removed the same way as non-copied
      // approvals.
      checkFooter(!labelLine.startsWith("-"), FOOTER_COPIED_LABEL, labelLine);
      ParsedPatchSetApproval.Builder rawPatchSetApproval =
          ParsedPatchSetApproval.builder().footerLine(labelLine).isRemoval(false);

      int tagStart = labelLine.indexOf(":\"");
      int uuidStart = labelLine.indexOf(", ");

      // Weird tag that contains uuid delimiter. The uuid is actually not present.
      if (tagStart != -1 && uuidStart > tagStart) {
        uuidStart = -1;
      }

      int identitiesStart = labelLine.indexOf(' ', uuidStart != -1 ? uuidStart + 2 : 0);
      checkFooter(
          identitiesStart != -1 && identitiesStart < labelLine.length(),
          FOOTER_COPIED_LABEL,
          labelLine);

      String labelVoteStr = labelLine.substring(0, uuidStart != -1 ? uuidStart : identitiesStart);
      rawPatchSetApproval.labelVote(labelVoteStr);
      if (uuidStart != -1) {
        String uuid = labelLine.substring(uuidStart + 2, identitiesStart);
        checkFooter(!Strings.isNullOrEmpty(uuid), FOOTER_COPIED_LABEL, labelLine);
        rawPatchSetApproval.uuid(Optional.of(uuid));
      }
      // The first account is the accountId, and second (if applicable) is the realAccountId.
      List<String> identities =
          Splitter.on(',')
              .splitToList(
                  labelLine.substring(
                      identitiesStart + 1, tagStart == -1 ? labelLine.length() : tagStart));
      checkFooter(identities.size() >= 1, FOOTER_COPIED_LABEL, labelLine);

      rawPatchSetApproval.accountIdent(Optional.of(identities.get(0)));

      if (identities.size() > 1) {
        rawPatchSetApproval.realAccountIdent(Optional.of(identities.get(1)));
      }

      if (tagStart != -1) {
        // tagStart+2 skips ":\"" to parse the actual tag. Tags are in brackets.
        // line.length()-1 skips the last ".
        String tag = labelLine.substring(tagStart + 2, labelLine.length() - 1);
        rawPatchSetApproval.tag(Optional.of(tag));
      }
      return rawPatchSetApproval.build();
    } catch (StringIndexOutOfBoundsException ex) {
      throw parseException(FOOTER_COPIED_LABEL, labelLine, ex);
    }
  }

  private static void checkFooter(boolean expr, FooterKey footer, String actual)
      throws ConfigInvalidException {
    if (!expr) {
      throw parseException(footer, actual, /*cause=*/ null);
    }
  }

  private static ConfigInvalidException parseException(
      FooterKey footer, String actual, Throwable cause) {
    return new ConfigInvalidException(
        String.format("invalid %s: %s", footer.getName(), actual), cause);
  }
}
