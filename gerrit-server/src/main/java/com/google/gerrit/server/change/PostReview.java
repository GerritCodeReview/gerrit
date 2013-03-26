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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.Url;
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
     * modified to be the "best" value allowed by the access controls, or
     * ignored if the label does not exist.
     */
    public boolean strictLabels = true;

    /**
     * How to process draft comments already in the database that were not also
     * described in this input request.
     */
    public DraftHandling drafts = DraftHandling.DELETE;

    /** Who to send email notifications to after review is stored. */
    public NotifyHandling notify = NotifyHandling.ALL;
  }

  public static enum DraftHandling {
    DELETE, PUBLISH, KEEP;
  }

  public static enum NotifyHandling {
    NONE, OWNER, OWNER_REVIEWERS, ALL;
  }

  static class Comment {
    String id;
    CommentInfo.Side side;
    int line;
    String inReplyTo;
    String message;
  }

  static class Output {
    Map<String, Short> labels;
  }

  private final ReviewDb db;
  private final EmailReviewComments.Factory email;
  @Deprecated private final ChangeHooks hooks;

  private Change change;
  private ChangeMessage message;
  private Timestamp timestamp;
  private List<PatchLineComment> comments = Lists.newArrayList();
  private List<String> labelDelta = Lists.newArrayList();
  private Map<String, Short> categories = Maps.newHashMap();

  @Inject
  PostReview(ReviewDb db,
      EmailReviewComments.Factory email,
      ChangeHooks hooks) {
    this.db = db;
    this.email = email;
    this.hooks = hooks;
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
    if (input.notify == null) {
      input.notify = NotifyHandling.NONE;
    }

    db.changes().beginTransaction(revision.getChange().getId());
    try {
      change = db.changes().get(revision.getChange().getId());
      ChangeUtil.updated(change);
      timestamp = change.getLastUpdatedOn();

      boolean dirty = false;
      dirty |= insertComments(revision, input.comments, input.drafts);
      dirty |= updateLabels(revision, input.labels);
      dirty |= insertMessage(revision, input.message);
      if (dirty) {
        db.changes().update(Collections.singleton(change));
        db.commit();
      }
    } finally {
      db.rollback();
    }

    if (input.notify.compareTo(NotifyHandling.NONE) > 0 && message != null) {
      email.create(
          input.notify,
          change,
          revision.getPatchSet(),
          revision.getAccountId(),
          message,
          comments).sendAsync();
      fireCommentAddedHook(revision);
    }

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

      LabelType lt = revision.getControl().getLabelTypes()
          .byLabel(ent.getKey());
      if (lt == null) {
        if (strict) {
          throw new BadRequestException(String.format(
              "label \"%s\" is not a configured label", ent.getKey()));
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

      if (lt.getValue(ent.getValue()) == null) {
        if (strict) {
          throw new BadRequestException(String.format(
              "label \"%s\": %d is not a valid value",
              ent.getKey(), ent.getValue()));
        } else {
          itr.remove();
          continue;
        }
      }

      String name = lt.getName();
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

  private boolean insertComments(RevisionResource rsrc,
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
        PatchLineComment e = drafts.remove(Url.decode(c.id));
        boolean create = e == null;
        if (create) {
          e = new PatchLineComment(
              new PatchLineComment.Key(
                  new Patch.Key(rsrc.getPatchSet().getId(), path),
                  ChangeUtil.messageUUID(db)),
              c.line,
              rsrc.getAccountId(),
              parent);
        } else if (parent != null) {
          e.setParentUuid(parent);
        }
        e.setStatus(PatchLineComment.Status.PUBLISHED);
        e.setWrittenOn(timestamp);
        e.setSide(c.side == CommentInfo.Side.PARENT ? (short) 0 : (short) 1);
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
    return !del.isEmpty() || !ins.isEmpty() || !upd.isEmpty();
  }

  private Map<String, PatchLineComment> scanDraftComments(
      RevisionResource rsrc) throws OrmException {
    Map<String, PatchLineComment> drafts = Maps.newHashMap();
    for (PatchLineComment c : db.patchComments().draftByPatchSetAuthor(
          rsrc.getPatchSet().getId(),
          rsrc.getAccountId())) {
      drafts.put(c.getKey().get(), c);
    }
    return drafts;
  }

  private boolean updateLabels(RevisionResource rsrc, Map<String, Short> labels)
      throws OrmException {
    if (labels == null) {
      labels = Collections.emptyMap();
    }

    List<PatchSetApproval> del = Lists.newArrayList();
    List<PatchSetApproval> ins = Lists.newArrayList();
    List<PatchSetApproval> upd = Lists.newArrayList();
    Map<String, PatchSetApproval> current = scanLabels(rsrc, del);

    LabelTypes labelTypes = rsrc.getControl().getLabelTypes();
    for (Map.Entry<String, Short> ent : labels.entrySet()) {
      String name = ent.getKey();
      LabelType lt = checkNotNull(labelTypes.byLabel(name), name);
      if (change.getStatus().isClosed()) {
        // TODO Allow updating some labels even when closed.
        continue;
      }

      PatchSetApproval c = current.remove(name);
      if (ent.getValue() == null || ent.getValue() == 0) {
        // User requested delete of this label.
        if (c != null) {
          if (c.getValue() != 0) {
            labelDelta.add("-" + name);
          }
          del.add(c);
        }
      } else if (c != null && c.getValue() != ent.getValue()) {
        c.setValue(ent.getValue());
        c.setGranted(timestamp);
        c.cache(change);
        upd.add(c);
        labelDelta.add(format(name, c.getValue()));
        categories.put(name, c.getValue());
      } else if (c != null && c.getValue() == ent.getValue()) {
        current.put(name, c);
      } else if (c == null) {
        c = new PatchSetApproval(new PatchSetApproval.Key(
                rsrc.getPatchSet().getId(),
                rsrc.getAccountId(),
                lt.getLabelId()),
            ent.getValue());
        c.setGranted(timestamp);
        c.cache(change);
        ins.add(c);
        labelDelta.add(format(name, c.getValue()));
        categories.put(name, c.getValue());
      }
    }

    forceCallerAsReviewer(rsrc, current, ins, upd, del);
    db.patchSetApprovals().delete(del);
    db.patchSetApprovals().insert(ins);
    db.patchSetApprovals().update(upd);
    return !del.isEmpty() || !ins.isEmpty() || !upd.isEmpty();
  }

  private void forceCallerAsReviewer(RevisionResource rsrc,
      Map<String, PatchSetApproval> current, List<PatchSetApproval> ins,
      List<PatchSetApproval> upd, List<PatchSetApproval> del) {
    if (current.isEmpty() && ins.isEmpty() && upd.isEmpty()) {
      // TODO Find another way to link reviewers to changes.
      if (del.isEmpty()) {
        // If no existing label is being set to 0, hack in the caller
        // as a reviewer by picking the first server-wide LabelType.
        PatchSetApproval c = new PatchSetApproval(new PatchSetApproval.Key(
            rsrc.getPatchSet().getId(),
            rsrc.getAccountId(),
            rsrc.getControl().getLabelTypes().getLabelTypes().get(0)
                .getLabelId()),
            (short) 0);
        c.setGranted(timestamp);
        c.cache(change);
        ins.add(c);
      } else {
        // Pick a random label that is about to be deleted and keep it.
        Iterator<PatchSetApproval> i = del.iterator();
        PatchSetApproval c = i.next();
        c.setValue((short) 0);
        c.setGranted(timestamp);
        c.cache(change);
        i.remove();
        upd.add(c);
      }
    }
  }

  private Map<String, PatchSetApproval> scanLabels(RevisionResource rsrc,
      List<PatchSetApproval> del) throws OrmException {
    LabelTypes labelTypes = rsrc.getControl().getLabelTypes();
    Map<String, PatchSetApproval> current = Maps.newHashMap();
    for (PatchSetApproval a : db.patchSetApprovals().byPatchSetUser(
          rsrc.getPatchSet().getId(), rsrc.getAccountId())) {
      if (a.isSubmit()) {
        continue;
      }

      LabelType lt = labelTypes.byLabel(a.getLabelId());
      if (lt != null) {
        current.put(lt.getName(), a);
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

  private boolean insertMessage(RevisionResource rsrc, String msg)
      throws OrmException {
    msg = Strings.nullToEmpty(msg).trim();

    StringBuilder buf = new StringBuilder();
    for (String d : labelDelta) {
      buf.append(" ").append(d);
    }
    if (comments.size() == 1) {
      buf.append("\n\n(1 comment)");
    } else if (comments.size() > 1) {
      buf.append(String.format("\n\n(%d comments)", comments.size()));
    }
    if (!msg.isEmpty()) {
      buf.append("\n\n").append(msg);
    }
    if (buf.length() == 0) {
      return false;
    }

    message = new ChangeMessage(
        new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db)),
        rsrc.getAccountId(),
        timestamp,
        rsrc.getPatchSet().getId());
    message.setMessage(String.format(
        "Patch Set %d:%s",
        rsrc.getPatchSet().getPatchSetId(),
        buf.toString()));
    db.changeMessages().insert(Collections.singleton(message));
    return true;
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
