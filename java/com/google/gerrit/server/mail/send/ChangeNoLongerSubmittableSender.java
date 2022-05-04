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

package com.google.gerrit.server.mail.send;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Map;

/**
 * Sender to send an email after a change update saying that the change is no longer submittable.
 *
 * <p>The email contains the old and new submit requirements from before and after the update.
 *
 * <p>The email is only sent to the change owner and uploader.
 */
public class ChangeNoLongerSubmittableSender extends ReplyToChangeSender {
  /** The kind of update kind that triggered the email sending. */
  public enum UpdateKind {
    // The current user uploaded a new patch set.
    PATCH_SET_UPLOADED,

    // The current user posted a reply on the change.
    REPLY_POSTED;
  }

  public interface Factory {
    /**
     * Creates a sender.
     *
     * @param updateKind the kind of update kind that triggered the email sending
     * @param preUpdateChangeData the old change data that contains the change state from before the
     *     update
     * @param postUpdateChangeData the updated change data that contains the change state from after
     *     the update
     */
    ChangeNoLongerSubmittableSender create(
        UpdateKind updateKind,
        @Assisted("preUpdateChangeData") ChangeData preUpdateChangeData,
        @Assisted("postUpdateChangeData") ChangeData postUpdateChangeData);
  }

  private final UpdateKind updateKind;
  private final ChangeData preUpdateChangeData;

  @Inject
  public ChangeNoLongerSubmittableSender(
      EmailArguments args,
      @Assisted UpdateKind updateKind,
      @Assisted("preUpdateChangeData") ChangeData preUpdateChangeData,
      @Assisted("postUpdateChangeData") ChangeData postUpdateChangeData) {
    super(args, "changeNoLongerSubmittable", postUpdateChangeData);
    this.updateKind = updateKind;
    this.preUpdateChangeData = preUpdateChangeData;
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("ChangeNoLongerSubmittable"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("ChangeNoLongerSubmittableHtml"));
    }
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();

    soyContextEmailData.put("updateKind", updateKind.name());
    soyContextEmailData.put(
        "oldSubmitRequirements",
        formatSubmitRequirments(preUpdateChangeData.submitRequirementsIncludingLegacy()));
    soyContextEmailData.put(
        "newSubmitRequirements",
        formatSubmitRequirments(changeData.submitRequirementsIncludingLegacy()));
  }

  private static ImmutableList<String> formatSubmitRequirments(
      Map<SubmitRequirement, SubmitRequirementResult> submitRequirementResults) {
    return submitRequirementResults.entrySet().stream()
        .map(
            e -> {
              if (e.getValue().errorMessage().isPresent()) {
                return String.format(
                    "%s: %s (%s)",
                    e.getKey().name(),
                    e.getValue().status().name(),
                    e.getValue().errorMessage().get());
              }
              return String.format("%s: %s", e.getKey().name(), e.getValue().status().name());
            })
        .sorted()
        .collect(toImmutableList());
  }
}
