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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Branch.NameKey;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSet.Id;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeOp.Factory;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PublishComments;
import com.google.gerrit.server.project.CanSubmitResult;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ReviewCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewCommand.class);

  @Override
  protected final CmdLineParser newCmdLineParser() {
    final CmdLineParser parser = super.newCmdLineParser();
    for (ApproveOption c : optionList) {
      parser.addOption(c, c);
    }
    return parser;
  }

  private final Set<PatchSet.Id> patchSetIds = new HashSet<PatchSet.Id>();
  private final Collection<Review> reviews = new ArrayList<Review>();

  @Argument(index = 0, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET | JSON}", usage = "patch to review or json")
  void parseArgument(final String token) {
    if(json)
    {
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
      reviews.add(gson.fromJson(token, Review.class));
    }
    else
    {
      try {
        patchSetIds.addAll(parsePatchSetId(token));
      } catch (UnloggedFailure e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      } catch (OrmException e) {
        throw new IllegalArgumentException("database error", e);
      }

    }
  }

  @Option(name = "--project", aliases = "-p", usage = "project containing the patch set")
  private ProjectControl projectControl;

  @Option(name = "--message", aliases = "-m", usage = "cover message to publish on change", metaVar = "MESSAGE")
  private String changeComment;

  @Option(name = "--abandon", usage = "abandon the patch set")
  private boolean abandonChange;

  @Option(name = "--restore", usage = "restore an abandoned the patch set")
  private boolean restoreChange;

  @Option(name = "--submit", aliases = "-s", usage = "submit the patch set")
  private boolean submitChange;

  @Option(name = "--json", aliases = "-j", usage = "use json as argument")
  private boolean json;

  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private MergeQueue merger;

  @Inject
  private MergeOp.Factory opFactory;

  @Inject
  private ApprovalTypes approvalTypes;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  @Inject
  private AbandonedSender.Factory abandonedSenderFactory;

  @Inject
  private FunctionState.Factory functionStateFactory;

  @Inject
  private PublishComments.Factory publishCommentsFactory;

  @Inject
  private ChangeHookRunner hooks;

  private List<ApproveOption> optionList;

  private Set<PatchSet.Id> toSubmit = new HashSet<PatchSet.Id>();

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        initOptionList();
        parseCommandLine();

        if(json)
        {
          processJson();
        }
        else
        {

          if (abandonChange) {
            if (restoreChange) {
              throw error("abandon and restore actions are mutually exclusive");
            }
            if (submitChange) {
              throw error("abandon and submit actions are mutually exclusive");
            }
          }

          boolean ok = approvePatchSets();

          if (!ok) {
            throw new UnloggedFailure(1, "one or more approvals failed;"
                + " review output above");
          }

          if (!toSubmit.isEmpty()) {
            submitPatchSets();
          }
        }
      }
    });
  }

  boolean approvePatchSets() {
    boolean ok = true;
    for (final PatchSet.Id patchSetId : patchSetIds) {
      try {
        approveOne(patchSetId);
      } catch (UnloggedFailure e) {
        ok = false;
        writeError("error: " + e.getMessage() + "\n");
      } catch (Exception e) {
        ok = false;
        writeError("fatal: internal server error while approving "
            + patchSetId + "\n");
        log.error("internal error while approving " + patchSetId, e);
      }
    }
    return ok;
  }

  private static class InlineComment
  {
    String message;
    String file;
    int line;
  }
  private static class Review
  {
    String message;
    String commit;
    Collection<InlineComment> inlineComments;
  }

  private void processJson() {

    for(Review review: reviews)
    {
      Collection<PatchLineComment> patchLineComments =
        new ArrayList<PatchLineComment>();

      Set<PatchSet.Id> ids = Collections.emptySet();
      try {
        ids = parsePatchSetId(review.commit);
      } catch (UnloggedFailure e) {
        writeError("fatal: internal server error while parsing patch set id from "
            + review.commit + ", ignoring inline comments\n");
      } catch (OrmException e) {
        writeError("fatal: internal server error while parsing patch set id from "
            + review.commit + "\n");
      }

      for(InlineComment inlineComment: review.inlineComments)
      {
        patchLineComments.addAll(createPatchLineComments(ids, inlineComment));
      }

      try {
        for (final PatchLineComment c : patchLineComments) {
          c.setStatus(PatchLineComment.Status.PUBLISHED);
          c.updated();
        }
        db.patchComments().insert(patchLineComments);
      } catch (OrmException e) {
        throw new IllegalArgumentException("database error", e);
      }
      for(PatchSet.Id id : ids)
      {
        try {
          publishCommentsFactory.create(id, review.message, Collections.<ApprovalCategoryValue.Id>emptySet()).call();
        } catch (NoSuchChangeException e) {
          writeError("No such change" + id.toString());
        } catch (OrmException e) {
          throw new IllegalArgumentException("database error", e);
        }
      }
    }
  }

  private Collection<PatchLineComment> createPatchLineComments(Iterable<Id> ids, InlineComment inlineComment) {
    int side = 1;
    Collection<PatchLineComment> patchLineComments = new ArrayList<PatchLineComment>();
    for (PatchSet.Id id : ids) {
      PatchLineComment patchComment =
        createPatchLineComment(inlineComment.message, inlineComment.file, inlineComment.line, id, side);
      patchLineComments.add(patchComment);
    }
    return patchLineComments;
  }

  private PatchLineComment createPatchLineComment(String comment,
      String filename, int lineNumber, PatchSet.Id id, int side) {
    Patch.Key patchKey = new Patch.Key(id, filename);
    PatchLineComment.Key patchLineKey;
    String messageUUID = "";
    try {
      messageUUID = ChangeUtil.messageUUID(db);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
    patchLineKey = new PatchLineComment.Key(patchKey, messageUUID);

    PatchLineComment inlineComment =
        new PatchLineComment(patchLineKey, lineNumber,
            currentUser.getAccountId(), null);

    inlineComment.setSide((short) side);
    inlineComment.setMessage(comment);
    return inlineComment;
  }

  private void approveOne(final PatchSet.Id patchSetId)
      throws NoSuchChangeException, UnloggedFailure, OrmException,
             EmailException {

    final Change.Id changeId = patchSetId.getParentKey();
    ChangeControl changeControl = changeControlFactory.validateFor(changeId);

    if (changeComment == null) {
      changeComment = "";
    }

    Set<ApprovalCategoryValue.Id> aps = new HashSet<ApprovalCategoryValue.Id>();
    for (ApproveOption ao : optionList) {
      Short v = ao.value();
      if (v != null) {
        assertScoreIsAllowed(patchSetId, changeControl, ao, v);
        aps.add(new ApprovalCategoryValue.Id(ao.getCategoryId(), v));
      }
    }

    publishCommentsFactory.create(patchSetId, changeComment, aps).call();

    if (abandonChange) {
      if (changeControl.canAbandon()) {
        ChangeUtil.abandon(patchSetId, currentUser, changeComment, db,
          abandonedSenderFactory, hooks);
      } else {
        throw error("Not permitted to abandon change");
      }
    }

    if (restoreChange) {
      if (changeControl.canRestore()) {
        ChangeUtil.restore(patchSetId, currentUser, changeComment, db,
          abandonedSenderFactory, hooks);
      } else {
        throw error("Not permitted to restore change");
      }
      if (submitChange) {
        changeControl = changeControlFactory.validateFor(changeId);
      }
    }

    if (submitChange) {
      CanSubmitResult result =
          changeControl.canSubmit(patchSetId, db, approvalTypes,
              functionStateFactory);
      if (result == CanSubmitResult.OK) {
        toSubmit.add(patchSetId);
      } else {
        throw error(result.getMessage());
      }
    }
  }

  private Set<PatchSet.Id> parsePatchSetId(final String patchIdentity)
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

      final Set<PatchSet.Id> matches = new HashSet<PatchSet.Id>();
      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          matches.add(ps.getId());
        }
      }

      switch (matches.size()) {
        case 1:
          return matches;
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
      if (db.patchSets().get(patchSetId) == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      if (projectControl != null) {
        final Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change)) {
          throw error("change " + change.getId() + " not in project "
              + projectControl.getProject().getName());
        }
      }
      return Collections.singleton(patchSetId);
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

  private void assertScoreIsAllowed(final PatchSet.Id patchSetId,
      final ChangeControl changeControl, ApproveOption ao, Short v)
      throws UnloggedFailure {
    final PatchSetApproval psa =
        new PatchSetApproval(new PatchSetApproval.Key(patchSetId, currentUser
            .getAccountId(), ao.getCategoryId()), v);
    final FunctionState fs =
        functionStateFactory.create(changeControl.getChange(), patchSetId,
            Collections.<PatchSetApproval> emptyList());
    psa.setValue(v);
    fs.normalize(approvalTypes.getApprovalType(psa.getCategoryId()), psa);
    if (v != psa.getValue()) {
      throw error(ao.name() + "=" + ao.value() + " not permitted");
    }
  }

  private void initOptionList() {
    optionList = new ArrayList<ApproveOption>();

    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      String usage = "";
      final ApprovalCategory category = type.getCategory();
      usage = "score for " + category.getName() + "\n";

      for (ApprovalCategoryValue v : type.getValues()) {
        usage += v.format() + "\n";
      }

      final String name =
          "--" + category.getName().toLowerCase().replace(' ', '-');
      optionList.add(new ApproveOption(name, usage, type));
    }
  }

  private void writeError(final String msg) {
    try {
      err.write(msg.getBytes(ENC));
    } catch (IOException e) {
    }
  }

  void submitPatchSets() throws Failure {
    final Set<Branch.NameKey> toMerge = new HashSet<Branch.NameKey>();
    try {
      for (PatchSet.Id patchSetId : toSubmit) {
        ChangeUtil.submit(patchSetId, currentUser, db, opFactory,
            new MergeQueue() {
              @Override
              public void merge(MergeOp.Factory mof, Branch.NameKey branch) {
                toMerge.add(branch);
              }

              @Override
              public void schedule(Branch.NameKey branch) {
                toMerge.add(branch);
              }

              @Override
              public void recheckAfter(Branch.NameKey branch, long delay,
                  TimeUnit delayUnit) {
                toMerge.add(branch);
              }
            });
      }
      for (Branch.NameKey branch : toMerge) {
        merger.merge(opFactory, branch);
      }
    } catch (OrmException updateError) {
      throw new Failure(1, "one or more submits failed", updateError);
    }
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }
}
