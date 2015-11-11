// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.common.data.SubmitWholeTopic;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ProjectUtil;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Singleton
public class Submit implements RestModifyView<RevisionResource, SubmitInput>,
    UiAction<RevisionResource> {
  private static final Logger log = LoggerFactory.getLogger(Submit.class);

  private static final String DEFAULT_TOOLTIP =
      "Submit patch set ${patchSet} into ${branch}";
  private static final String DEFAULT_TOOLTIP_ANCESTORS =
      "Submit patch set ${patchSet} and ancestors (${submitSize} changes " +
      "altogether) into ${branch}";
  private static final String DEFAULT_TOPIC_TOOLTIP =
      "Submit all ${topicSize} changes of the same topic " +
      "(${submitSize} changes including ancestors and other " +
      "changes related by topic)";
  private static final String BLOCKED_SUBMIT_TOOLTIP =
      "This change depends on other changes which are not ready";
  private static final String BLOCKED_HIDDEN_SUBMIT_TOOLTIP =
      "This change depends on other hidden changes which are not ready";
  private static final String CLICK_FAILURE_TOOLTIP =
      "Clicking the button would fail";
  private static final String CLICK_FAILURE_OTHER_TOOLTIP =
      "Clicking the button would fail for other changes";

  public static class Output {
    transient Change change;

    private Output(Change c) {
      change = c;
    }
  }

  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeMessagesUtil cmUtil;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final Provider<MergeOp> mergeOpProvider;
  private final MergeSuperSet mergeSuperSet;
  private final AccountsCollection accounts;
  private final ChangesCollection changes;
  private final String label;
  private final String labelWithParents;
  private final ParameterizedString titlePattern;
  private final ParameterizedString titlePatternWithAncestors;
  private final String submitTopicLabel;
  private final ParameterizedString submitTopicTooltip;
  private final boolean submitWholeTopic;
  private final SubmitWholeTopic.Mode submitWholeTopicMode;
  private final Provider<InternalChangeQuery> queryProvider;

  @Inject
  Submit(Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      ChangeData.Factory changeDataFactory,
      ChangeMessagesUtil cmUtil,
      ChangeControl.GenericFactory changeControlFactory,
      Provider<MergeOp> mergeOpProvider,
      MergeSuperSet mergeSuperSet,
      AccountsCollection accounts,
      ChangesCollection changes,
      @GerritServerConfig Config cfg,
      Provider<InternalChangeQuery> queryProvider) {
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.changeDataFactory = changeDataFactory;
    this.cmUtil = cmUtil;
    this.changeControlFactory = changeControlFactory;
    this.mergeOpProvider = mergeOpProvider;
    this.mergeSuperSet = mergeSuperSet;
    this.accounts = accounts;
    this.changes = changes;
    this.label = MoreObjects.firstNonNull(
        Strings.emptyToNull(cfg.getString("change", null, "submitLabel")),
        "Submit");
    this.labelWithParents = MoreObjects.firstNonNull(
        Strings.emptyToNull(
            cfg.getString("change", null, "submitLabelWithParents")),
        "Submit including parents");
    this.titlePattern = new ParameterizedString(MoreObjects.firstNonNull(
        cfg.getString("change", null, "submitTooltip"),
        DEFAULT_TOOLTIP));
    this.titlePatternWithAncestors = new ParameterizedString(
        MoreObjects.firstNonNull(
            cfg.getString("change", null, "submitTooltipAncestors"),
            DEFAULT_TOOLTIP_ANCESTORS));
    submitWholeTopic = wholeTopicEnabled(cfg);
    submitWholeTopicMode = wholeTopic(cfg);
    this.submitTopicLabel = MoreObjects.firstNonNull(
        Strings.emptyToNull(cfg.getString("change", null, "submitTopicLabel")),
        "Submit whole topic");
    this.submitTopicTooltip = new ParameterizedString(MoreObjects.firstNonNull(
        cfg.getString("change", null, "submitTopicTooltip"),
        DEFAULT_TOPIC_TOOLTIP));
    this.queryProvider = queryProvider;
  }

  @Override
  public Output apply(RevisionResource rsrc, SubmitInput input)
      throws AuthException, ResourceConflictException,
      RepositoryNotFoundException, IOException, OrmException,
      UnprocessableEntityException {
    input.onBehalfOf = Strings.emptyToNull(input.onBehalfOf);
    if (input.onBehalfOf != null) {
      rsrc = onBehalfOf(rsrc, input);
    }
    ChangeControl control = rsrc.getControl();
    IdentifiedUser caller = control.getUser().asIdentifiedUser();
    Change change = rsrc.getChange();
    if (input.onBehalfOf == null && !control.canSubmit()) {
      throw new AuthException("submit not permitted");
    } else if (!change.getStatus().isOpen()) {
      throw new ResourceConflictException("change is " + status(change));
    } else if (!ProjectUtil.branchExists(repoManager, change.getDest())) {
      throw new ResourceConflictException(String.format(
          "destination branch \"%s\" not found.",
          change.getDest().get()));
    } else if (!rsrc.getPatchSet().getId().equals(change.currentPatchSetId())) {
      // TODO Allow submitting non-current revision by changing the current.
      throw new ResourceConflictException(String.format(
          "revision %s is not current revision",
          rsrc.getPatchSet().getRevision().get()));
    }

    boolean shouldSubmitWholeTopic =
        input.submitWholeTopic != null ? input.submitWholeTopic
            : submitWholeTopicMode == SubmitWholeTopic.Mode.AUTO;
    try {
      ReviewDb db = dbProvider.get();
      mergeOpProvider.get().merge(db, change, caller, true,
          shouldSubmitWholeTopic);
      change = db.changes().get(change.getId());
    } catch (NoSuchChangeException e) {
      throw new OrmException("Submission failed", e);
    }

    if (change == null) {
      throw new ResourceConflictException("change is deleted");
    }
    switch (change.getStatus()) {
      case MERGED:
        return new Output(change);
      case NEW:
        ChangeMessage msg = getConflictMessage(rsrc);
        if (msg != null) {
          throw new ResourceConflictException(msg.getMessage());
        }
        //$FALL-THROUGH$
      default:
        throw new ResourceConflictException("change is " + status(change));
    }
  }

  /**
   * @param cs set of changes to be submitted at once
   * @param identifiedUser the user who is checking to submit
   * @return a reason why any of the changes is not submittable or null
   */
  private String problemsForSubmittingChangeset(
      ChangeSet cs, IdentifiedUser identifiedUser) {
    try {
      @SuppressWarnings("resource")
      ReviewDb db = dbProvider.get();
      for (PatchSet.Id psId : cs.patchIds()) {
        ChangeControl changeControl = changeControlFactory
            .controlFor(psId.getParentKey(), identifiedUser);
        ChangeData c = changeDataFactory.create(db, changeControl);

        if (!changeControl.isVisible(db)) {
          return BLOCKED_HIDDEN_SUBMIT_TOOLTIP;
        }
        if (!changeControl.canSubmit()) {
          return BLOCKED_SUBMIT_TOOLTIP;
        }
        // Recheck mergeability rather than using value stored in the index,
        // which may be stale.
        // TODO(dborowitz): This is ugly; consider providing a way to not read
        // stored fields from the index in the first place.
        c.setMergeable(null);
        Boolean mergeable = c.isMergeable();
        if (mergeable == null) {
          log.error("Ephemeral error checking if change is submittable");
          return CLICK_FAILURE_TOOLTIP;
        }
        if (!mergeable) {
          return CLICK_FAILURE_OTHER_TOOLTIP;
        }
        MergeOp.checkSubmitRule(c);
      }
    } catch (ResourceConflictException e) {
      return BLOCKED_SUBMIT_TOOLTIP;
    } catch (NoSuchChangeException | OrmException e) {
      log.error("Error checking if change is submittable", e);
      throw new OrmRuntimeException("Could not determine problems for the change", e);
    }
    return null;
  }

  /**
   * Check if there are any problems with the given change. It doesn't take
   * any problems of related changes into account.
   * <p>
   * @param cd the change to check for submittability
   * @return if the change has any problems for submission
   */
  public boolean submittable(ChangeData cd) {
    try {
      MergeOp.checkSubmitRule(cd);
      return true;
    } catch (ResourceConflictException | OrmException e) {
      return false;
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    PatchSet.Id current = resource.getChange().currentPatchSetId();
    String topic = resource.getChange().getTopic();
    boolean visible = !resource.getPatchSet().isDraft()
        && resource.getChange().getStatus().isOpen()
        && resource.getPatchSet().getId().equals(current)
        && resource.getControl().canSubmit();
    ReviewDb db = dbProvider.get();
    ChangeData cd = changeDataFactory.create(db, resource.getControl());

    try {
      MergeOp.checkSubmitRule(cd);
    } catch (ResourceConflictException e) {
      visible = false;
    } catch (OrmException e) {
      log.error("Error checking if change is submittable", e);
      throw new OrmRuntimeException("Could not determine problems for the change", e);
    }

    if (!visible) {
      return new UiAction.Description()
        .setLabel("")
        .setTitle("")
        .setVisible(false);
    }

    Boolean enabled;
    try {
      enabled = cd.isMergeable();
    } catch (OrmException e) {
      throw new OrmRuntimeException("Could not determine mergeability", e);
    }

    ChangeSet cs;
    try {
      cs = mergeSuperSet.completeChangeSet(db, cd.change(), submitWholeTopic);
    } catch (OrmException | IOException e) {
      throw new OrmRuntimeException("Could not determine complete set of " +
          "changes to be submitted", e);
    }

    int topicSize = 0;
    if (!Strings.isNullOrEmpty(topic)) {
      topicSize = getChangesByTopic(topic).size();
    }
    boolean treatWithTopic = submitWholeTopicMode != SubmitWholeTopic.Mode.OFF
        && !Strings.isNullOrEmpty(topic)
        && topicSize > 1;

    String submitProblems = problemsForSubmittingChangeset(cs,
        resource.getUser());
    if (submitProblems != null) {
      String actionLabel;
      if (submitWholeTopicMode == SubmitWholeTopic.Mode.DIALOG) {
        actionLabel = label;
      } else {
        actionLabel = treatWithTopic
            ? submitTopicLabel : (cs.size() > 1) ? labelWithParents : label;
      }
      return new UiAction.Description()
        .setLabel(actionLabel)
        .setTitle(submitProblems)
        .setVisible(true)
        .setEnabled(submitWholeTopicMode == SubmitWholeTopic.Mode.DIALOG);
    }

    if (treatWithTopic) {
      Map<String, String> params = ImmutableMap.of(
          "topicSize", String.valueOf(topicSize),
          "submitSize", String.valueOf(cs.size()));
      String actionLabel = submitWholeTopicMode == SubmitWholeTopic.Mode.DIALOG
          ? label : submitTopicLabel;
      return new UiAction.Description()
          .setLabel(actionLabel)
          .setTitle(Strings.emptyToNull(
              submitTopicTooltip.replace(params)))
          .setVisible(true)
          .setEnabled(Boolean.TRUE.equals(enabled));
    } else {
      RevId revId = resource.getPatchSet().getRevision();
      Map<String, String> params = ImmutableMap.of(
          "patchSet", String.valueOf(resource.getPatchSet().getPatchSetId()),
          "branch", resource.getChange().getDest().getShortName(),
          "commit", ObjectId.fromString(revId.get()).abbreviate(7).name(),
          "submitSize", String.valueOf(cs.size()));
      ParameterizedString tp = cs.size() > 1 ? titlePatternWithAncestors :
          titlePattern;
      return new UiAction.Description()
        .setLabel(cs.size() > 1 ? labelWithParents : label)
        .setTitle(Strings.emptyToNull(tp.replace(params)))
        .setVisible(true)
        .setEnabled(Boolean.TRUE.equals(enabled));
    }
  }

  /**
   * If the merge was attempted and it failed the system usually writes a
   * comment as a ChangeMessage and sets status to NEW. Find the relevant
   * message and return it.
   */
  public ChangeMessage getConflictMessage(RevisionResource rsrc)
      throws OrmException {
    return FluentIterable.from(cmUtil.byPatchSet(dbProvider.get(), rsrc.getNotes(),
        rsrc.getPatchSet().getId()))
        .filter(new Predicate<ChangeMessage>() {
          @Override
          public boolean apply(ChangeMessage input) {
            return input.getAuthor() == null;
          }
        })
        .last()
        .orNull();
  }

  static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
  }

  private RevisionResource onBehalfOf(RevisionResource rsrc, SubmitInput in)
      throws AuthException, UnprocessableEntityException, OrmException {
    ChangeControl caller = rsrc.getControl();
    if (!caller.canSubmit()) {
      throw new AuthException("submit not permitted");
    }
    if (!caller.canSubmitAs()) {
      throw new AuthException("submit on behalf of not permitted");
    }
    IdentifiedUser targetUser = accounts.parseId(in.onBehalfOf);
    if (targetUser == null) {
      throw new UnprocessableEntityException(String.format(
          "Account Not Found: %s", in.onBehalfOf));
    }
    ChangeControl target = caller.forUser(targetUser);
    if (!target.getRefControl().isVisible()) {
      throw new UnprocessableEntityException(String.format(
          "on_behalf_of account %s cannot see destination ref",
          targetUser.getAccountId()));
    }
    return new RevisionResource(changes.parse(target), rsrc.getPatchSet());
  }

  public static SubmitWholeTopic.Mode wholeTopic(Config config) {
    String value = config.getString("change", null, "submitWholeTopic");
    if (Strings.isNullOrEmpty(value)) {
      return SubmitWholeTopic.Mode.OFF;
    }
    try {
      return StringUtils.toBoolean(value) ? SubmitWholeTopic.Mode.AUTO
          : SubmitWholeTopic.Mode.OFF;
    } catch (IllegalArgumentException e) {
      // ignore
    }
    return SubmitWholeTopic.Mode.valueOf(value.toUpperCase());
  }

  public static boolean wholeTopicEnabled(Config config) {
    return wholeTopic(config) != SubmitWholeTopic.Mode.OFF;
  }

  private List<ChangeData> getChangesByTopic(String topic) {
    try {
      return queryProvider.get().byTopicOpen(topic);
    } catch (OrmException e) {
      throw new OrmRuntimeException(e);
    }
  }

  public static class CurrentRevision implements
      RestModifyView<ChangeResource, SubmitInput> {
    private final Provider<ReviewDb> dbProvider;
    private final Submit submit;
    private final ChangeJson.Factory json;

    @Inject
    CurrentRevision(Provider<ReviewDb> dbProvider,
        Submit submit,
        ChangeJson.Factory json) {
      this.dbProvider = dbProvider;
      this.submit = submit;
      this.json = json;
    }

    @Override
    public ChangeInfo apply(ChangeResource rsrc, SubmitInput input)
        throws AuthException, ResourceConflictException,
        RepositoryNotFoundException, IOException, OrmException,
        UnprocessableEntityException {
      PatchSet ps = dbProvider.get().patchSets()
        .get(rsrc.getChange().currentPatchSetId());
      if (ps == null) {
        throw new ResourceConflictException("current revision is missing");
      } else if (!rsrc.getControl().isPatchVisible(ps, dbProvider.get())) {
        throw new AuthException("current revision not accessible");
      }

      Output out = submit.apply(new RevisionResource(rsrc, ps), input);
      return json.create(ChangeJson.NO_OPTIONS).format(out.change);
    }
  }
}
