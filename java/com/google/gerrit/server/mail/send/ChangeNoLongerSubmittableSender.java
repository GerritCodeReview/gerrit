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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.exceptions.EmailException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Sender to send an email after a change update saying that the change is no longer submittable.
 *
 * <p>An email is only sent when the change was submittable, but due to the update it is no longer
 * submittable.
 *
 * <p>The email contains the old and new submit requirements from before and after the update.
 *
 * <p>The email is only sent to the change owner and uploader.
 *
 * <p>Sending this email requires to evaluate the submit requirements for the pre-update change
 * state, which is expensive. This is why callers should trigger this email only asynchronously
 * through {@link com.google.gerrit.server.util.ChangeNoLongerSubmittableEmail}.
 *
 * <p>Sending this email also requires the submit requirements results for the post-update change
 * state, but callers are expected to have these already available and pass them it (callers need to
 * (re)index the change after performing the update which triggers an evaluation of the submit
 * requirements and then callers can pass in these results).
 */
public class ChangeNoLongerSubmittableSender extends ReplyToChangeSender {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** The kind of update kind that triggered the email sending. */
  public enum UpdateKind {
    // The current user posted a reply on the change.
    REPLY_POSTED;
  }

  public interface Factory {
    /**
     * Creates a sender.
     *
     * @param projectName the name of the project
     * @param changeId the ID of the change for which the email should be sent
     * @param updateKind the kind of update kind that triggered the email sending
     * @param preUpdateMetaId the SHA1 to which the notes branch pointed before the update
     * @param postUpdateSubmitRequirementResults the new submit requirement results from after the
     *     update, callers are expected to have these already available and should pass them in to
     *     avoid an expensive recomputation
     */
    ChangeNoLongerSubmittableSender create(
        Project.NameKey projectName,
        Change.Id changeId,
        UpdateKind updateKind,
        ObjectId preUpdateMetaId,
        Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults);
  }

  private final UpdateKind updateKind;
  private final Map<SubmitRequirement, SubmitRequirementResult> preUpdateSubmitRequirementResults;
  private final Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults;

  @Inject
  public ChangeNoLongerSubmittableSender(
      EmailArguments args,
      @Assisted Project.NameKey projectName,
      @Assisted Change.Id changeId,
      @Assisted UpdateKind updateKind,
      @Assisted ObjectId preUpdateMetaId,
      @Assisted
          Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults) {
    super(args, "changeNoLongerSubmittable", newChangeData(args, projectName, changeId));
    this.updateKind = updateKind;

    // triggers an (expensive) evaluation of the submit requirements
    this.preUpdateSubmitRequirementResults =
        newChangeData(args, projectName, changeId, preUpdateMetaId)
            .submitRequirementsIncludingLegacy();

    this.postUpdateSubmitRequirementResults = postUpdateSubmitRequirementResults;
  }

  @Override
  protected boolean shouldSendMessage() {
    if (!isChangeNoLongerSubmittable()) {
      logger.atFine().log("skip sending email for change %s", change.getId());
      return false;
    }

    return super.shouldSendMessage();
  }

  /**
   * Checks whether the change is no longer submittable.
   *
   * @return {@code true} if the change has been submittable before the update and is no longer
   *     submittable after the update has been applied, otherwise {@code false}
   */
  private boolean isChangeNoLongerSubmittable() {
    boolean isSubmittablePreUpdate =
        preUpdateSubmitRequirementResults.values().stream()
            .allMatch(SubmitRequirementResult::fulfilled);
    logger.atFine().log(
        "the submitability of change %s before the update is %s",
        change.getId(), isSubmittablePreUpdate);
    if (!isSubmittablePreUpdate) {
      return false;
    }

    boolean isSubmittablePostUpdate =
        postUpdateSubmitRequirementResults.values().stream()
            .allMatch(SubmitRequirementResult::fulfilled);
    logger.atFine().log(
        "the submitability of change %s after the update is %s",
        change.getId(), isSubmittablePostUpdate);
    return !isSubmittablePostUpdate;
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
        "oldSubmitRequirements", formatSubmitRequirments(preUpdateSubmitRequirementResults));
    soyContextEmailData.put(
        "newSubmitRequirements", formatSubmitRequirments(postUpdateSubmitRequirementResults));
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
