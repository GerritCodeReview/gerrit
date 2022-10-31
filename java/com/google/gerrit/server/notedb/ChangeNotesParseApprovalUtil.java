// Copyright (C) 2022 The Android Open Source Project
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

import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_COPIED_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_LABEL;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.revwalk.FooterKey;

/**
 * Util to extract {@link com.google.gerrit.entities.PatchSetApproval} from {@link
 * ChangeNoteFooters#FOOTER_LABEL} or {@link ChangeNoteFooters#FOOTER_COPIED_LABEL}.
 */
public class ChangeNotesParseApprovalUtil {

  /**
   * {@link com.google.gerrit.entities.PatchSetApproval}, parsed from {@link
   * ChangeNoteFooters#FOOTER_LABEL} or {@link ChangeNoteFooters#FOOTER_COPIED_LABEL}.
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
      return new AutoValue_ChangeNotesParseApprovalUtil_ParsedPatchSetApproval.Builder();
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
   * Parses {@link ParsedPatchSetApproval} from {@link ChangeNoteFooters#FOOTER_LABEL} line.
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
   * Parses copied {@link ParsedPatchSetApproval} from {@link ChangeNoteFooters#FOOTER_COPIED_LABEL}
   * line.
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
   *
   * <p>Footer example for removal: Copied-Label: -<LABEL> <Gerrit Account>,<Gerrit Real Account>
   *
   * <ul>
   *   <li><Gerrit Real Account> is also optional, if it was not set.
   * </ul>
   */
  public static ParsedPatchSetApproval parseCopiedApproval(String labelLine)
      throws ConfigInvalidException {
    try {
      ParsedPatchSetApproval.Builder rawPatchSetApproval =
          ParsedPatchSetApproval.builder().footerLine(labelLine);

      boolean isRemoval = labelLine.startsWith("-");
      rawPatchSetApproval.isRemoval(isRemoval);
      int labelStart = isRemoval ? 1 : 0;
      int uuidStart = isRemoval ? -1 : labelLine.indexOf(", ");
      int tagStart = isRemoval ? -1 : labelLine.indexOf(":\"");

      checkFooter(!isRemoval || uuidStart == -1, FOOTER_LABEL, labelLine);

      // Weird tag that contains uuid delimiter. The uuid is actually not present.
      if (tagStart != -1 && uuidStart > tagStart) {
        uuidStart = -1;
      }

      int identitiesStart = labelLine.indexOf(' ', uuidStart != -1 ? uuidStart + 2 : 0);
      checkFooter(
          identitiesStart != -1 && identitiesStart < labelLine.length(),
          FOOTER_COPIED_LABEL,
          labelLine);

      String labelVoteStr =
          labelLine.substring(labelStart, uuidStart != -1 ? uuidStart : identitiesStart);
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
