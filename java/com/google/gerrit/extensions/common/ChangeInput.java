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

package com.google.gerrit.extensions.common;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChangeInput {
  public String project;
  public String branch;
  public String subject;

  public String topic;
  public ChangeStatus status;
  public Boolean isPrivate;
  public Boolean workInProgress;
  public String baseChange;
  public String baseCommit;
  public Boolean newBranch;
  public Map<String, String> validationOptions;
  public Map<String, String> customKeyedValues;
  public MergeInput merge;
  public ApplyPatchInput patch;

  public AccountInput author;

  @Nullable public List<ListChangesOption> responseFormatOptions;

  public ChangeInput() {}

  /**
   * Creates a new {@code ChangeInput} with the minimal attributes required for a successful
   * creation of a new change.
   *
   * @param project the project name for the new change
   * @param branch the branch name for the new change
   * @param subject the subject (commit message) for the new change
   */
  public ChangeInput(String project, String branch, String subject) {
    this.project = project;
    this.branch = branch;
    this.subject = subject;
  }

  /** Who to send email notifications to after change is created. */
  public NotifyHandling notify = NotifyHandling.ALL;

  public Map<RecipientType, NotifyInfo> notifyDetails;

  @Override
  public boolean equals(Object o) {
    if (o instanceof ChangeInput) {
      ChangeInput k = (ChangeInput) o;
      return Objects.equals(project, k.project)
          && Objects.equals(branch, k.branch)
          && Objects.equals(subject, k.subject)
          && Objects.equals(topic, k.topic)
          && Objects.equals(status, k.status)
          && Objects.equals(isPrivate, k.isPrivate)
          && Objects.equals(workInProgress, k.workInProgress)
          && Objects.equals(baseChange, k.baseChange)
          && Objects.equals(baseCommit, k.baseCommit)
          && Objects.equals(newBranch, k.newBranch)
          && Objects.equals(validationOptions, k.validationOptions)
          && Objects.equals(customKeyedValues, k.customKeyedValues)
          && Objects.equals(merge, k.merge)
          && Objects.equals(patch, k.patch)
          && Objects.equals(author, k.author)
          && Objects.equals(responseFormatOptions, k.responseFormatOptions)
          && Objects.equals(notify, k.notify)
          && Objects.equals(notifyDetails, k.notifyDetails);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        project,
        branch,
        subject,
        topic,
        status,
        isPrivate,
        workInProgress,
        baseChange,
        baseCommit,
        newBranch,
        validationOptions,
        customKeyedValues,
        merge,
        patch,
        author,
        responseFormatOptions,
        notify,
        notifyDetails);
  }
}
