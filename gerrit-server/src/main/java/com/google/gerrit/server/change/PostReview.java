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

import com.google.common.base.Objects;
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
import com.google.gerrit.extensions.restapi.RestModifyView;
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
import com.google.gerrit.server.change.PostReview.Input;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.util.Url;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PostReview implements RestModifyView<RevisionResource, Input> {
  private static final Logger log = LoggerFactory.getLogger(PostReview.class);

  public static class Input {
    @DefaultInput
    public String message;

    public Map<String, Short> labels;
    Map<String, List<Comment>> comments;

    /**
     * If true require all labels to be within the user's permitted ranges based
     * on access controls, attempting to use a label not granted to the user
     * will fail the entire modify operation early. If false the operation will
     * execute anyway, but the proposed labels given by the user will be
     * modified to be the "best" value allowed by the access controls.
     */
    public boolean strictLabels = true;

    /**
     * How to process draft comments already in the database that were not also
     * described in this input request.
     */
    public DraftHandling drafts = DraftHandling.DELETE;
  }

  public static enum DraftHandling {
    DELETE, PUBLISH, KEEP;
  }

  static class Comment {
    String id;
    GetDraft.Side side;
    int line;
    String inReplyTo;
    String message;
  }

  static class Output {
    Map<String, Short> labels;
  }

  private final ReviewDb db;
  private final ApprovalTypes approvalTypes;
  private final EmailReviewComments.Factory email;
  @Deprecated private final ChangeHooks hooks;

  private Change change;
  private ChangeMessage message;
  private Timestamp timestamp;
  private List<PatchLineComment> comments = Lists.newArrayList();
  private List<String> labelDelta = Lists.newArrayList();
  @Deprecated private Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> categories
    = Maps.newHashMap();

  @Inject
  PostReview(ReviewDb db,
      ApprovalTypes approvalTypes,
      EmailReviewComments.Factory email,
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
      throws AuthException, BadRequestException, OrmException {
    if (input.labels != null) {
      checkLabels(revision, input.strictLabels, input.labels);
    }
    if (input.comments != null) {
      checkComments(input.comments);
    }

    db.changes().beginTransaction(revision.getChange().getId());
    try {
      change = db.changes().get(revision.getChange().getId());
      ChangeUtil.updated(change);
      timestamp = change.getLastUpdatedOn();

      insertComments(revision, input.comments, input.drafts);
      if (change.getStatus().isOpen() && input.labels != null) {
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

    Output output = new Output();
    output.labels = input.labels;
    return output;
  }

  private void checkLabels(RevisionResource revision, boolean strict,
      Map<String, Short> labels) throws BadRequestException, AuthException {
    ChangeControl ctl = revision.getControl();
    Iterator<Map.Entry<String, Short>> itr = labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();

      // TODO Support more generic label assignments.
      ApprovalType at = approvalTypes.byLabel(ent.getKey());
      if (at == null) {
        if (strict) {
          throw new BadRequestException(String.format(
              "label \"%s\" is not a configured ApprovalCategory",
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

      if (at.getValue(ent.getValue()) == null) {
        if (strict) {
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
        if (strict) {
          throw new AuthException(String.format(
              "Applying label \"%s\": %d is restricted",
              ent.getKey(), ent.getValue()));
        } else if (range == null || range.isEmpty()) {
          ent.setValue((short) 0);
        } else {
          ent.setValue((short) range.squash(ent.getValue()));
        }
      }
    }
  }

  private void checkComments(Map<String, List<Comment>> in)
      throws BadRequestException {
    Iterator<Map.Entry<String, List<Comment>>> mapItr =
        in.entrySet().iterator();
    while (mapItr.hasNext()) {
      Map.Entry<String, List<Comment>> ent = mapItr.next();
      String path = ent.getKey();
      List<Comment> list = ent.getValue();
      if (list == null) {
        mapItr.remove();
        continue;
      }

      Iterator<Comment> listItr = list.iterator();
      while (listItr.hasNext()) {
        Comment c = listItr.next();
        if (c.line < 0) {
          throw new BadRequestException(String.format(
              "negative line number %d not allowed on %s",
              c.line, path));
        }
        c.message = Strings.emptyToNull(c.message).trim();
        if (c.message.isEmpty()) {
          listItr.remove();
        }
      }
      if (list.isEmpty()) {
        mapItr.remove();
      }
    }
  }

  private void insertComments(RevisionResource rsrc,
      Map<String, List<Comment>> in, DraftHandling draftsHandling)
      throws OrmException {
    if (in == null) {
      in = Collections.emptyMap();
    }

    Map<String, PatchLineComment> drafts = Collections.emptyMap();
    if (!in.isEmpty() || draftsHandling != DraftHandling.KEEP) {
      drafts = scanDraftComments(rsrc);
    }

    List<PatchLineComment> del = Lists.newArrayList();
    List<PatchLineComment> ins = Lists.newArrayList();
    List<PatchLineComment> upd = Lists.newArrayList();

    for (Map.Entry<String, List<Comment>> ent : in.entrySet()) {
      String path = ent.getKey();
      for (Comment c : ent.getValue()) {
        String parent = Url.decode(c.inReplyTo);
        PatchLineComment e = drafts.remove(c.id);
        boolean create = e == null;
        if (create) {
          e = new PatchLineComment(
              new PatchLineComment.Key(
                  new Patch.Key(rsrc.getPatchSet().getId(), path),
                  ChangeUtil.messageUUID(db)),
              c.line,
              rsrc.getAuthorId(),
              parent);
        } else if (parent != null) {
          e.setParentUuid(parent);
        }
        e.setStatus(PatchLineComment.Status.PUBLISHED);
        e.setWrittenOn(timestamp);
        e.setSide(c.side == GetDraft.Side.PARENT ? (short) 0 : (short) 1);
        e.setMessage(c.message);
        (create ? ins : upd).add(e);
      }
    }

    switch (Objects.firstNonNull(draftsHandling, DraftHandling.DELETE)) {
      case KEEP:
      default:
        break;
      case DELETE:
        del.addAll(drafts.values());
        break;
      case PUBLISH:
        for (PatchLineComment e : drafts.values()) {
          e.setStatus(PatchLineComment.Status.PUBLISHED);
          e.setWrittenOn(timestamp);
          upd.add(e);
        }
        break;
    }
    db.patchComments().delete(del);
    db.patchComments().insert(ins);
    db.patchComments().update(upd);
    comments.addAll(ins);
    comments.addAll(upd);
  }

  private Map<String, PatchLineComment> scanDraftComments(
      RevisionResource rsrc) throws OrmException {
    Map<String, PatchLineComment> drafts = Maps.newHashMap();
    for (PatchLineComment c : db.patchComments().draftByPatchSetAuthor(
          rsrc.getPatchSet().getId(),
          rsrc.getAuthorId())) {
      drafts.put(c.getKey().get(), c);
    }
    return drafts;
  }

  private void updateLabels(RevisionResource rsrc, Map<String, Short> labels)
      throws OrmException {
    List<PatchSetApproval> del = Lists.newArrayList();
    List<PatchSetApproval> ins = Lists.newArrayList();
    List<PatchSetApproval> upd = Lists.newArrayList();
    Map<String, PatchSetApproval> current = scanLabels(rsrc, del);
    for (Map.Entry<String, Short> ent : labels.entrySet()) {
      // TODO Support arbitrary label names.
      ApprovalType at = approvalTypes.byLabel(ent.getKey());
      String name = at.getCategory().getLabelName();
      PatchSetApproval c = current.get(name);

      if (ent.getValue() == null) {
        if (c != null) {
          // User requested delete of this label.
          del.add(c);
          labelDelta.add("-" + name);
        }
      } else if (c != null && c.getValue() != ent.getValue()) {
        c.setValue(ent.getValue());
        c.setGranted(timestamp);
        c.cache(change);
        upd.add(c);
        labelDelta.add(format(name, c.getValue()));
        categories.put(
            at.getCategory().getId(),
            at.getValue(c.getValue()).getId());
      } else if (c == null) {
        c = new PatchSetApproval(new PatchSetApproval.Key(
                rsrc.getPatchSet().getId(),
                rsrc.getAuthorId(),
                at.getCategory().getId()),
            ent.getValue());
        c.setGranted(timestamp);
        c.cache(change);
        ins.add(c);
        labelDelta.add(format(name, c.getValue()));
        categories.put(
            at.getCategory().getId(),
            at.getValue(c.getValue()).getId());
      }
    }

    db.patchSetApprovals().delete(del);
    db.patchSetApprovals().insert(ins);
    db.patchSetApprovals().update(upd);
  }

  private Map<String, PatchSetApproval> scanLabels(RevisionResource rsrc,
      List<PatchSetApproval> del) throws OrmException {
    Map<String, PatchSetApproval> current = Maps.newHashMap();
    for (PatchSetApproval a : db.patchSetApprovals().byPatchSetUser(
          rsrc.getPatchSet().getId(), rsrc.getAuthorId())) {
      if (ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
        continue;
      }

      ApprovalType at = approvalTypes.byId(a.getCategoryId());
      if (at != null) {
        current.put(at.getCategory().getLabelName(), a);
      } else {
        del.add(a);
      }
    }
    return current;
  }

  private static String format(String name, short value) {
    StringBuilder sb = new StringBuilder(name.length() + 2);
    sb.append(name);
    if (value >= 0) {
      sb.append('+');
    }
    sb.append(value);
    return sb.toString();
  }

  private void insertMessage(RevisionResource rsrc, String msg)
      throws OrmException {
    msg = Strings.nullToEmpty(msg).trim();

    StringBuilder buf = new StringBuilder();
    buf.append(String.format(
        "Patch Set %d:",
        rsrc.getPatchSet().getPatchSetId()));
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
        rsrc.getAuthorId(),
        timestamp,
        rsrc.getPatchSet().getId());
    message.setMessage(buf.toString());
    db.changeMessages().insert(Collections.singleton(message));
  }

  @Deprecated
  private void fireCommentAddedHook(RevisionResource rsrc) {
    IdentifiedUser user = (IdentifiedUser) rsrc.getControl().getCurrentUser();
    try {
      hooks.doCommentAddedHook(change,
          user.getAccount(),
          rsrc.getPatchSet(),
          message.getMessage(),
          categories, db);
    } catch (OrmException e) {
      log.warn("ChangeHook.doCommentAddedHook delivery failed", e);
    }
  }
}
