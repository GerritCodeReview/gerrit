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

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PutReview.Input;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class PutReview implements RestModifyView<RevisionResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(PutReview.class);

  static class Input {
    @DefaultInput
    String message;

    boolean strictLabels = true;
    Map<String, Integer> labels;
    Map<String, List<Comment>> comments;
  }

  static enum Side {
    PARENT, REVISION;
  }

  static class Comment {
    String id;
    Side side;
    int line;
    String message;
  }

  private final ReviewDb db;
  private final ApprovalTypes approvalTypes;
  private final SendReviewComments.Factory email;
  @Deprecated private final ChangeHooks hooks;

  private Change change;
  private ChangeMessage message;
  private List<PatchLineComment> comments = Lists.newArrayList();
  private List<String> labelDelta = Lists.newArrayList();
  @Deprecated private Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> categories
    = Maps.newHashMap();

  @Inject
  PutReview(ReviewDb db,
      ApprovalTypes approvalTypes,
      SendReviewComments.Factory email,
      ChangeHooks hooks) {
    this.db = db;
    this.approvalTypes = approvalTypes;
    this.email = email;
    this.hooks = hooks;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(RevisionResource revision, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    checkLabels(revision, input);

    db.changes().beginTransaction(revision.getChange().getId());
    try {
      change = db.changes().get(revision.getChange().getId());
      ChangeUtil.updated(change);
      insertComments(revision, input.comments);
      if (change.getStatus().isOpen()) {
        // TODO Allow updating some labels even when closed.
        updateLabels(revision, input.labels);
      }
      insertMessage(revision, input.message);
      db.changes().update(Collections.singleton(change));
      db.commit();
    } finally {
      db.rollback();
    }

    email.create(
        change,
        revision.getPatchSet(),
        revision.getAuthorId(),
        message,
        comments).sendAsync();
    fireCommentAddedHook(revision);
    return input;
  }

  private void checkLabels(RevisionResource revision, Input input)
      throws BadRequestException, AuthException {
    if (input.labels != null) {
      ChangeControl ctl = revision.getControl();
      Iterator<Map.Entry<String, Integer>> itr =
          input.labels.entrySet().iterator();
      while (itr.hasNext()) {
        Map.Entry<String, Integer> ent = itr.next();

        // TODO Support more generic label assignments.
        ApprovalType at = approvalTypes.byLabel(ent.getKey());
        if (at == null) {
          if (input.strictLabels) {
            throw new BadRequestException(String.format(
                "label \"%s\" is not a valid ApprovalCategory",
                ent.getKey()));
          } else {
            itr.remove();
            continue;
          }
        }

        if (ent.getValue() == null || ent.getValue() == 0) {
          // Always permit 0, even if it is not within range.
          // Later null/0 will be deleted and revoke the label.
          continue;
        }

        if (!at.getValuesAsList().contains(ent.getValue())) {
          if (input.strictLabels) {
            throw new BadRequestException(String.format(
                "label \"%s\": %d is not a valid value",
                ent.getKey(), ent.getValue()));
          } else {
            itr.remove();
            continue;
          }
        }

        String name = at.getCategory().getLabelName();
        PermissionRange range = ctl.getRange(Permission.forLabel(name));
        if (range == null || !range.contains(ent.getValue())) {
          if (input.strictLabels) {
            throw new AuthException(String.format(
                "Applying label \"%s\": %d is restricted",
                ent.getKey(), ent.getValue()));
          } else if (range == null || range.isEmpty()) {
            itr.remove();
            continue;
          } else {
            ent.setValue(range.squash(ent.getValue()));
          }
        }
      }
    }
  }

  private void insertComments(RevisionResource revision,
      Map<String, List<Comment>> comments) throws OrmException {
    if (comments == null) {
      return;
    }

    Account.Id authorId = revision.getAuthorId();
    Map<String, PatchLineComment> have = Maps.newHashMap();
    for (PatchLineComment c : db.patchComments().draftByPatchSetAuthor(
        revision.getPatchSet().getId(), authorId)) {
      have.put(c.getKey().get(), c);
    }

    List<PatchLineComment> del = Lists.newArrayList();
    List<PatchLineComment> ins = Lists.newArrayList();
    List<PatchLineComment> upd = Lists.newArrayList();

    for (Map.Entry<String, List<Comment>> ent : comments.entrySet()) {
      String path = ent.getKey();
      for (Comment c : ent.getValue()) {
        c.message = Strings.nullToEmpty(c.message).trim();

        PatchLineComment r = have.remove(c.id);
        if (c.message.isEmpty()) {
          if (r != null) {
            del.add(r);
          }
          continue;
        }

        boolean create = r == null;
        if (create) {
          r = new PatchLineComment(
              new PatchLineComment.Key(
                  new Patch.Key(revision.getPatchSet().getId(), path),
                  ChangeUtil.messageUUID(db)),
              c.line,
              authorId,
              null);
        }
        r.setStatus(PatchLineComment.Status.PUBLISHED);
        r.setSide(c.side == Side.PARENT ? (short) 0 : (short) 1);
        r.setMessage(c.message);
        (create ? ins : upd).add(r);
      }
    }
    del.addAll(have.values());

    db.patchComments().delete(del);
    db.patchComments().insert(ins);
    db.patchComments().update(upd);

    this.comments.addAll(ins);
    this.comments.addAll(upd);
  }

  private void updateLabels(RevisionResource revision,
      Map<String, Integer> labels) throws OrmException {
    List<PatchSetApproval> del = Lists.newArrayList();
    List<PatchSetApproval> ins = Lists.newArrayList();
    List<PatchSetApproval> upd = Lists.newArrayList();
    Map<String, PatchSetApproval> have = Maps.newHashMap();

    for (PatchSetApproval a : db.patchSetApprovals().byPatchSetUser(
        revision.getPatchSet().getId(),
        revision.getAuthorId()).toList()) {
      ApprovalType at = approvalTypes.byId(a.getCategoryId());
      if (at != null) {
        have.put(at.getCategory().getLabelName(), a);
      } else {
        del.add(a);
      }
    }

    for (Map.Entry<String, Integer> ent : labels.entrySet()) {
      // TODO Support arbitrary label names.
      ApprovalType at = approvalTypes.byLabel(ent.getKey());
      String name = at.getCategory().getLabelName();

      PatchSetApproval h = have.get(name);
      if (ent.getValue() == null || ent.getValue() == 0) {
        // User requested delete of this label.
        if (h != null) {
          del.add(h);
        }
        continue;
      }

      if (h != null && h.getValue() != ent.getValue().shortValue()) {
        h.setValue(ent.getValue().shortValue());
        h.setGranted();
        h.cache(change);
        upd.add(h);
        labelDelta.add(format(name, h.getValue()));
        categories.put(
            at.getCategory().getId(),
            at.getValue(h.getValue()).getId());
      } else if (h == null) {
        h = new PatchSetApproval(
            new PatchSetApproval.Key(
                revision.getPatchSet().getId(),
                revision.getAuthorId(),
                at.getCategory().getId()),
            ent.getValue().shortValue());
        h.cache(change);
        ins.add(h);
        labelDelta.add(format(name, h.getValue()));
        categories.put(
            at.getCategory().getId(),
            at.getValue(h.getValue()).getId());
      }
    }

    db.patchSetApprovals().delete(del);
    db.patchSetApprovals().insert(ins);
    db.patchSetApprovals().update(upd);
  }

  private static String format(String name, short value) {
    if (value < 0) {
      return String.format("%s%d", name, value);
    } else if (value == 0) {
      return "-" + name;
    } else {
      return String.format("%s+%d", name, value);
    }
  }

  private void insertMessage(RevisionResource revision, String msg)
      throws OrmException {
    msg = Strings.nullToEmpty(msg).trim();

    StringBuilder buf = new StringBuilder();
    buf.append(String.format(
        "Patch Set %d:",
        revision.getPatchSet().getPatchSetId()));
    for (String d : labelDelta) {
      buf.append(" ").append(d);
    }
    if (comments.size() == 1) {
      buf.append("\n\n(1 inline comment)");
    } else if (comments.size() > 1) {
      buf.append(String.format("\n\n(%d inline comments)", comments.size()));
    }
    if (!msg.isEmpty()) {
      buf.append("\n\n").append(msg);
    }

    message = new ChangeMessage(
        new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db)),
        revision.getAuthorId(),
        revision.getPatchSet().getId());
    message.setMessage(buf.toString());
    db.changeMessages().insert(Collections.singleton(message));
  }

  @Deprecated
  private void fireCommentAddedHook(RevisionResource revision) throws OrmException {
    IdentifiedUser user =
        (IdentifiedUser) revision.getControl().getCurrentUser();
    try {
      hooks.doCommentAddedHook(change,
          user.getAccount(),
          revision.getPatchSet(),
          message.getMessage(),
          categories, db);
    } catch (OrmException e) {
      log.warn("ChangeHook.doCommentAddedHook delivery failed", e);
    }
  }
}
