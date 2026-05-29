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

package com.google.gerrit.server.mail.send;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import java.time.Instant;
import java.util.Map;

/** Populates an email for change related notifications. */
public interface ChangeEmail extends OutgoingEmail.EmailDecorator {

  /** Implementations of params interface populate details specific to the notification type. */
  interface ChangeEmailDecorator {
    /**
     * Stores the reference to the {@link OutgoingEmail} and {@link ChangeEmail} for the subsequent
     * calls.
     *
     * <p>Both init and populateEmailContent can be called multiply times in case of retries. Init
     * is therefore responsible for clearing up any changes which are not idempotent and
     * initializing data for use in populateEmailContent.
     *
     * <p>Can be used to adjust any of the behaviour of the {@link
     * ChangeEmail#populateEmailContent}.
     */
    void init(OutgoingEmail email, ChangeEmail changeEmail) throws EmailException;

    /**
     * Populate headers, recipients and body of the email.
     *
     * <p>Method operates on the email provided in the init method.
     *
     * <p>By default, all the contents and parameters of the email should be set in this method.
     */
    void populateEmailContent() throws EmailException;

    /** If returns false email is not sent to any recipients. */
    default boolean shouldSendMessage() {
      return true;
    }
  }

  /** Mark the email as non-first in the thread to ensure correct headers will be set */
  void markAsReply();

  /** Get change for which the email is being sent. */
  Change getChange();

  /** Get ChangeData for the change corresponding to the email. */
  ChangeData getChangeData();

  /**
   * Get Timestamp of the event causing the email.
   *
   * <p>Provided by {@link #setChangeMessage(String, Instant)}.
   */
  @Nullable
  Instant getTimestamp();

  /** Specify PatchSet with which the notification is associated with. */
  void setPatchSet(PatchSet ps);

  /** Get PatchSet if provided. */
  @Nullable
  PatchSet getPatchSet();

  /** Specify PatchSet along with additional data. */
  void setPatchSet(PatchSet ps, PatchSetInfo psi);

  /** Specify the summary of what happened to the change. */
  void setChangeMessage(String cm, Instant t);

  /**
   * Specify if the email should only be sent to attention set.
   *
   * <p>Only affects users who have corresponding option enabled in the settings.
   */
  void setEmailOnlyAttentionSetIfEnabled(boolean value);

  /** Get the text of the "cover letter" (processed changeMessage). */
  String getCoverLetter();

  /** Get the patch list corresponding to patch set patchSetId of this change. */
  Map<String, FileDiffOutput> listModifiedFiles(int patchSetId);

  /** Get the patch list corresponding to this patch set. */
  Map<String, FileDiffOutput> listModifiedFiles();

  /** Get the number of added lines in a change. */
  int getInsertionsCount();

  /** Get the number of deleted lines in a change. */
  int getDeletionsCount();

  /** Get the project entity the change is in; null if its been deleted. */
  ProjectState getProjectState();

  /** TO or CC all vested parties (change owner, patch set uploader, author). */
  void addAuthors(RecipientType rt);

  /** BCC any user who has starred this change. */
  void bccStarredBy();

  /** Include users and groups that want notification of events. */
  void includeWatchers(NotifyType type);

  /** Include users and groups that want notification of events. */
  void includeWatchers(NotifyType type, boolean includeWatchersFromNotifyConfig);

  /** Any user who has published comments on this change. */
  void ccAllApprovals();

  /** Users who were added as reviewers to this change. */
  void ccExistingReviewers();

  /** Show patch set as unified difference. */
  String getUnifiedDiff();

  /**
   * Generate a list of maps representing each line of the unified diff. The line maps will have a
   * 'type' key which maps to one of 'common', 'add' or 'remove' and a 'text' key which maps to the
   * line's content.
   *
   * @param sourceDiff the unified diff that we're converting to the map.
   * @return map of 'type' to a line's content.
   */
  static ImmutableList<ImmutableMap<String, String>> getDiffTemplateData(String sourceDiff) {
    ImmutableList.Builder<ImmutableMap<String, String>> result = ImmutableList.builder();
    Splitter lineSplitter = Splitter.on(System.getProperty("line.separator"));
    for (String diffLine : lineSplitter.split(sourceDiff)) {
      ImmutableMap.Builder<String, String> lineData = ImmutableMap.builder();
      lineData.put("text", diffLine);

      // Skip empty lines and lines that look like diff headers.
      if (diffLine.isEmpty() || diffLine.startsWith("---") || diffLine.startsWith("+++")) {
        lineData.put("type", "common");
      } else {
        switch (diffLine.charAt(0)) {
          case '+' -> lineData.put("type", "add");
          case '-' -> lineData.put("type", "remove");
          default -> lineData.put("type", "common");
        }
      }
      result.add(lineData.build());
    }
    return result.build();
  }
}
