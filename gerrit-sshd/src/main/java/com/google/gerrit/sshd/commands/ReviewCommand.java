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

package com.google.gerrit.sshd.commands;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.common.data.ReviewResult.Error.Type;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.Abandon;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.Restore;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.changedetail.DeleteDraftPatchSet;
import com.google.gerrit.server.changedetail.PublishDraft;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CommandMetaData(name = "review", descr = "Verify, approve and/or submit one or more patch sets")
public class ReviewCommand extends SshCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewCommand.class);

  @Override
  protected final CmdLineParser newCmdLineParser(Object options) {
    final CmdLineParser parser = super.newCmdLineParser(options);
    for (ApproveOption c : optionList) {
      parser.addOption(c, c);
    }
    return parser;
  }

  private final Set<PatchSet> patchSets = new HashSet<PatchSet>();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "list of commits or patch sets to review")
  void addPatchSetId(final String token) {
    try {
      patchSets.add(parsePatchSet(token));
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
  }

  @Option(name = "--project", aliases = "-p", usage = "project containing the specified patch set(s)")
  private ProjectControl projectControl;

  @Option(name = "--message", aliases = "-m", usage = "cover message to publish on change(s)", metaVar = "MESSAGE")
  private String changeComment;

  @Option(name = "--abandon", usage = "abandon the specified change(s)")
  private boolean abandonChange;

  @Option(name = "--restore", usage = "restore the specified abandoned change(s)")
  private boolean restoreChange;

  @Option(name = "--submit", aliases = "-s", usage = "submit the specified patch set(s)")
  private boolean submitChange;

  @Option(name = "--publish", usage = "publish the specified draft patch set(s)")
  private boolean publishPatchSet;

  @Option(name = "--delete", usage = "delete the specified draft patch set(s)")
  private boolean deleteDraftPatchSet;

  @Option(name = "--label", aliases = "-l", usage = "custom label(s) to assign", metaVar = "LABEL=VALUE")
  void addLabel(final String token) {
    List<String> parts = ImmutableList.copyOf(Splitter.on('=').split(token));
    if (parts.size() != 2) {
      throw new IllegalArgumentException("invalid custom label " + token);
    }
    short value;
    try {
      value = Short.parseShort(parts.get(1));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid custom label value "
          + parts.get(1));
    }
    customLabels.put(parts.get(0), value);
  }

  @Inject
  private ReviewDb db;

  @Inject
  private DeleteDraftPatchSet.Factory deleteDraftPatchSetFactory;

  @Inject
  private ProjectControl.Factory projectControlFactory;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  @Inject
  private Provider<Abandon> abandonProvider;

  @Inject
  private Provider<PostReview> reviewProvider;

  @Inject
  private PublishDraft.Factory publishDraftFactory;

  @Inject
  private Provider<Restore> restoreProvider;

  @Inject
  private Provider<Submit> submitProvider;

  private List<ApproveOption> optionList;
  private Map<String, Short> customLabels;

  @Override
  protected void run() throws UnloggedFailure {
    if (abandonChange) {
      if (restoreChange) {
        throw error("abandon and restore actions are mutually exclusive");
      }
      if (submitChange) {
        throw error("abandon and submit actions are mutually exclusive");
      }
      if (publishPatchSet) {
        throw error("abandon and publish actions are mutually exclusive");
      }
      if (deleteDraftPatchSet) {
        throw error("abandon and delete actions are mutually exclusive");
      }
    }
    if (publishPatchSet) {
      if (restoreChange) {
        throw error("publish and restore actions are mutually exclusive");
      }
      if (submitChange) {
        throw error("publish and submit actions are mutually exclusive");
      }
      if (deleteDraftPatchSet) {
        throw error("publish and delete actions are mutually exclusive");
      }
    }

    boolean ok = true;
    for (final PatchSet patchSet : patchSets) {
      try {
        approveOne(patchSet);
      } catch (UnloggedFailure e) {
        ok = false;
        writeError("error: " + e.getMessage() + "\n");
      } catch (NoSuchChangeException e) {
        ok = false;
        writeError("no such change " + patchSet.getId().getParentKey().get());
      } catch (Exception e) {
        ok = false;
        writeError("fatal: internal server error while approving "
            + patchSet.getId() + "\n");
        log.error("internal error while approving " + patchSet.getId(), e);
      }
    }

    if (!ok) {
      throw new UnloggedFailure(1, "one or more approvals failed;"
          + " review output above");
    }
  }

  private void applyReview(final ChangeControl ctl, final PatchSet patchSet,
      final PostReview.Input review) throws Exception {
    reviewProvider.get().apply(new RevisionResource(
        new ChangeResource(ctl), patchSet), review);
  }

  private void approveOne(final PatchSet patchSet) throws Exception {

    if (changeComment == null) {
      changeComment = "";
    }

    PostReview.Input review = new PostReview.Input();
    review.message = Strings.emptyToNull(changeComment);
    review.labels = Maps.newTreeMap();
    review.drafts = PostReview.DraftHandling.PUBLISH;
    review.strictLabels = false;
    for (ApproveOption ao : optionList) {
      Short v = ao.value();
      if (v != null) {
        review.labels.put(ao.getLabelName(), v);
      }
    }
    review.labels.putAll(customLabels);

    // If review labels are being applied, the comment will be included
    // on the review note. We don't need to add it again on the abandon
    // or restore comment.
    if (!review.labels.isEmpty() && (abandonChange || restoreChange)) {
      changeComment = null;
    }

    try {
      ChangeControl ctl =
          changeControlFactory.controlFor(patchSet.getId().getParentKey());

      if (abandonChange) {
        final Abandon abandon = abandonProvider.get();
        final Abandon.Input input = new Abandon.Input();
        input.message = changeComment;
        applyReview(ctl, patchSet, review);
        try {
          abandon.apply(new ChangeResource(ctl), input);
        } catch (AuthException e) {
          writeError("error: " + parseError(Type.ABANDON_NOT_PERMITTED) + "\n");
        } catch (ResourceConflictException e) {
          writeError("error: " + parseError(Type.CHANGE_IS_CLOSED) + "\n");
        }
      } else if (restoreChange) {
        final Restore restore = restoreProvider.get();
        final Restore.Input input = new Restore.Input();
        input.message = changeComment;
        try {
          restore.apply(new ChangeResource(ctl), input);
          applyReview(ctl, patchSet, review);
        } catch (AuthException e) {
          writeError("error: " + parseError(Type.RESTORE_NOT_PERMITTED) + "\n");
        } catch (ResourceConflictException e) {
          writeError("error: " + parseError(Type.CHANGE_NOT_ABANDONED) + "\n");
        }
      } else {
        applyReview(ctl, patchSet, review);
      }

      if (submitChange) {
        Submit submit = submitProvider.get();
        Submit.Input input = new Submit.Input();
        input.waitForMerge = true;
        submit.apply(new RevisionResource(
            new ChangeResource(ctl), patchSet),
          input);
      }
    } catch (InvalidChangeOperationException e) {
      throw error(e.getMessage());
    } catch (IllegalStateException e) {
      throw error(e.getMessage());
    } catch (AuthException e) {
      throw error(e.getMessage());
    } catch (BadRequestException e) {
      throw error(e.getMessage());
    } catch (ResourceConflictException e) {
      throw error(e.getMessage());
    }

    if (publishPatchSet) {
      final ReviewResult result =
          publishDraftFactory.create(patchSet.getId()).call();
      handleReviewResultErrors(result);
    } else if (deleteDraftPatchSet) {
      final ReviewResult result =
          deleteDraftPatchSetFactory.create(patchSet.getId()).call();
      handleReviewResultErrors(result);
    }
  }

  private void handleReviewResultErrors(final ReviewResult result) {
    for (ReviewResult.Error resultError : result.getErrors()) {
      String errMsg = "error: (change " + result.getChangeId() + ") ";
      errMsg += parseError(resultError.getType());
      if (resultError.getMessage() != null) {
        errMsg += ": " + resultError.getMessage();
      }
      writeError(errMsg);
    }
  }

  private String parseError(Type type) {
    switch (type) {
      case ABANDON_NOT_PERMITTED:
        return "not permitted to abandon change";
      case RESTORE_NOT_PERMITTED:
        return "not permitted to restore change";
      case SUBMIT_NOT_PERMITTED:
        return "not permitted to submit change";
      case SUBMIT_NOT_READY:
        return "approvals or dependencies lacking";
      case CHANGE_IS_CLOSED:
        return "change is closed";
      case CHANGE_NOT_ABANDONED:
        return "change is not abandoned";
      case PUBLISH_NOT_PERMITTED:
        return "not permitted to publish change";
      case DELETE_NOT_PERMITTED:
        return "not permitted to delete change/patch set";
      case RULE_ERROR:
        return "rule error";
      case NOT_A_DRAFT:
        return "change/patch set is not a draft";
      case GIT_ERROR:
        return "error writing change to git repository";
      case DEST_BRANCH_NOT_FOUND:
        return "destination branch not found";
      default:
        return "failure in review";
    }
  }

  private PatchSet parsePatchSet(final String patchIdentity)
      throws UnloggedFailure, OrmException {
    // By commit?
    //
    if (patchIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      final RevId id = new RevId(patchIdentity);
      final ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      final Set<PatchSet> matches = new HashSet<PatchSet>();
      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          matches.add(ps);
        }
      }

      switch (matches.size()) {
        case 1:
          return matches.iterator().next();
        case 0:
          throw error("\"" + patchIdentity + "\" no such patch set");
        default:
          throw error("\"" + patchIdentity + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      final PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        throw error("\"" + patchIdentity + "\" is not a valid patch set");
      }
      final PatchSet patchSet = db.patchSets().get(patchSetId);
      if (patchSet == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      if (projectControl != null) {
        final Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change)) {
          throw error("change " + change.getId() + " not in project "
              + projectControl.getProject().getName());
        }
      }
      return patchSet;
    }

    throw error("\"" + patchIdentity + "\" is not a valid patch set");
  }

  private boolean inProject(final Change change) {
    if (projectControl == null) {
      // No --project option, so they want every project.
      return true;
    }
    return projectControl.getProject().getNameKey().equals(change.getProject());
  }

  @Override
  protected void parseCommandLine() throws UnloggedFailure {
    optionList = new ArrayList<ApproveOption>();
    customLabels = Maps.newHashMap();

    ProjectControl allProjectsControl;
    try {
      allProjectsControl = projectControlFactory.controlFor(allProjects);
    } catch (NoSuchProjectException e) {
      throw new UnloggedFailure("missing " + allProjects.get());
    }

    for (LabelType type : allProjectsControl.getLabelTypes().getLabelTypes()) {
      String usage = "";
      usage = "score for " + type.getName() + "\n";

      for (LabelValue v : type.getValues()) {
        usage += v.format() + "\n";
      }

      final String name = "--" + type.getName().toLowerCase();
      optionList.add(new ApproveOption(name, usage, type));
    }

    super.parseCommandLine();
  }

  private void writeError(final String msg) {
    try {
      err.write(msg.getBytes(ENC));
    } catch (IOException e) {
    }
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }
}
