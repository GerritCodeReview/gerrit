// Copyright (C) 2009 The Android Open Source Project
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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.util.LabelVote;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

/** Send notice of new patch sets for reviewers. */
@AutoFactory
public class ReplacePatchSetChangeEmailDecoratorImpl
    implements ReplacePatchSetChangeEmailDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final EmailArguments args;
  protected OutgoingEmail email;
  protected ChangeEmail changeEmail;
  protected final Set<Account.Id> reviewers = new HashSet<>();
  protected final Set<Account.Id> extraCC = new HashSet<>();
  protected final ChangeKind changeKind;
  protected final Set<PatchSetApproval> outdatedApprovals = new HashSet<>();
  protected final Supplier<Map<SubmitRequirement, SubmitRequirementResult>>
      preUpdateSubmitRequirementResultsSupplier;
  protected final Map<SubmitRequirement, SubmitRequirementResult>
      postUpdateSubmitRequirementResults;

  public ReplacePatchSetChangeEmailDecoratorImpl(
      @Provided EmailArguments args,
      Project.NameKey project,
      Change.Id changeId,
      ChangeKind changeKind,
      ObjectId preUpdateMetaId,
      Map<SubmitRequirement, SubmitRequirementResult> postUpdateSubmitRequirementResults) {
    this.args = args;
    this.changeKind = changeKind;

    this.preUpdateSubmitRequirementResultsSupplier =
        Suppliers.memoize(
            () ->
                // Triggers an (expensive) evaluation of the submit requirements. This is OK since
                // all callers sent this email asynchronously, see EmailNewPatchSet.
                args.newChangeData(project, changeId, preUpdateMetaId)
                    .submitRequirementsIncludingLegacy());

    this.postUpdateSubmitRequirementResults = postUpdateSubmitRequirementResults;
  }

  @Override
  public boolean shouldSendMessage() {
    if (!isChangeNoLongerSubmittable() && changeKind.isTrivialRebase()) {
      logger.atFine().log(
          "skip email because new patch set is a trivial rebase that didn't make the change"
              + " non-submittable");
      return false;
    }
    return true;
  }

  @Override
  public void addReviewers(Collection<Account.Id> cc) {
    reviewers.addAll(cc);
  }

  @Override
  public void addExtraCC(Collection<Account.Id> cc) {
    extraCC.addAll(cc);
  }

  @Override
  public void addOutdatedApproval(@Nullable Collection<PatchSetApproval> outdatedApprovals) {
    if (outdatedApprovals != null) {
      this.outdatedApprovals.addAll(outdatedApprovals);
    }
  }

  @Override
  public void init(OutgoingEmail email, ChangeEmail changeEmail) {
    this.email = email;
    this.changeEmail = changeEmail;
    changeEmail.markAsReply();

    Account.Id fromId = email.getFrom();
    if (fromId != null) {
      // Don't call yourself a reviewer of your own patch set.
      //
      reviewers.remove(fromId);
    }
  }

  @Nullable
  protected ImmutableList<String> getReviewerNames() {
    List<String> names = new ArrayList<>();
    for (Account.Id id : reviewers) {
      if (id.equals(email.getFrom())) {
        continue;
      }
      names.add(email.getNameFor(id));
    }
    if (names.isEmpty()) {
      return null;
    }
    return names.stream().sorted().collect(toImmutableList());
  }

  protected ImmutableList<String> formatOutdatedApprovals() {
    return outdatedApprovals.stream()
        .map(
            outdatedApproval ->
                String.format(
                    "%s by %s",
                    LabelVote.create(outdatedApproval.label(), outdatedApproval.value()).format(),
                    email.getNameFor(outdatedApproval.accountId())))
        .sorted()
        .collect(toImmutableList());
  }

  @Override
  public void populateEmailContent() {
    changeEmail.addAuthors(RecipientType.TO);

    email.addSoyEmailDataParam("reviewerNames", getReviewerNames());
    email.addSoyEmailDataParam("outdatedApprovals", formatOutdatedApprovals());

    if (isChangeNoLongerSubmittable()) {
      email.addSoyParam("unsatisfiedSubmitRequirements", formatUnsatisfiedSubmitRequirements());
      email.addSoyParam(
          "oldSubmitRequirements",
          formatSubmitRequirements(preUpdateSubmitRequirementResultsSupplier.get()));
      email.addSoyParam(
          "newSubmitRequirements", formatSubmitRequirements(postUpdateSubmitRequirementResults));
    }

    if (args.settings.sendNewPatchsetEmails) {
      if (email.getNotify().handling().equals(NotifyHandling.ALL)
          || email.getNotify().handling().equals(NotifyHandling.OWNER_REVIEWERS)) {
        reviewers.stream().forEach(r -> email.addByAccountId(RecipientType.TO, r));
        extraCC.stream().forEach(cc -> email.addByAccountId(RecipientType.CC, cc));
      }
    }
    changeEmail.bccStarredBy();
    changeEmail.includeWatchers(
        NotifyType.NEW_PATCHSETS,
        !changeEmail.getChange().isWorkInProgress() && !changeEmail.getChange().isPrivate());

    email.appendText(email.textTemplate("ReplacePatchSet"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("ReplacePatchSetHtml"));
    }
  }

  /**
   * Checks whether the change is no longer submittable.
   *
   * @return {@code true} if the change has been submittable before the update and is no longer
   *     submittable after the update has been applied, otherwise {@code false}
   */
  private boolean isChangeNoLongerSubmittable() {
    boolean isSubmittablePreUpdate =
        preUpdateSubmitRequirementResultsSupplier.get().values().stream()
            .allMatch(SubmitRequirementResult::fulfilled);
    logger.atFine().log(
        "the submitability of change %s before the update is %s",
        changeEmail.getChange().getId(), isSubmittablePreUpdate);
    if (!isSubmittablePreUpdate) {
      return false;
    }

    boolean isSubmittablePostUpdate =
        postUpdateSubmitRequirementResults.values().stream()
            .allMatch(SubmitRequirementResult::fulfilled);
    logger.atFine().log(
        "the submitability of change %s after the update is %s",
        changeEmail.getChange().getId(), isSubmittablePostUpdate);
    return !isSubmittablePostUpdate;
  }

  private ImmutableList<String> formatUnsatisfiedSubmitRequirements() {
    return postUpdateSubmitRequirementResults.entrySet().stream()
        .filter(e -> SubmitRequirementResult.Status.UNSATISFIED.equals(e.getValue().status()))
        .map(Map.Entry::getKey)
        .map(SubmitRequirement::name)
        .sorted()
        .collect(toImmutableList());
  }

  private static ImmutableList<String> formatSubmitRequirements(
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
