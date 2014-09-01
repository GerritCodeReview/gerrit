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
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.Comment;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.Side;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PostReview implements RestModifyView<RevisionResource, ReviewInput> {
  private static final Logger log = LoggerFactory.getLogger(PostReview.class);

  static class Output {
    Map<String, Short> labels;
  }

  private final Provider<ReviewDb> db;
  private final ChangesCollection changes;
  private final ChangeUpdate.Factory updateFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeIndexer indexer;
  private final AccountsCollection accounts;
  private final EmailReviewComments.Factory email;
  @Deprecated private final ChangeHooks hooks;

  private Change change;
  private ChangeMessage message;
  private Timestamp timestamp;
  private List<PatchLineComment> comments = Lists.newArrayList();
  private List<String> labelDelta = Lists.newArrayList();
  private Map<String, Short> categories = Maps.newHashMap();

  @Inject
  PostReview(Provider<ReviewDb> db,
      ChangesCollection changes,
      ChangeUpdate.Factory updateFactory,
      ApprovalsUtil approvalsUtil,
      ChangeIndexer indexer,
      AccountsCollection accounts,
      EmailReviewComments.Factory email,
      ChangeHooks hooks) {
    this.db = db;
    this.changes = changes;
    this.updateFactory = updateFactory;
    this.approvalsUtil = approvalsUtil;
    this.indexer = indexer;
    this.accounts = accounts;
    this.email = email;
    this.hooks = hooks;
  }

  @Override
  public Output apply(RevisionResource revision, ReviewInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
      OrmException, IOException {
    if (input.onBehalfOf != null) {
      revision = onBehalfOf(revision, input);
    }
    if (input.labels != null) {
      checkLabels(revision, input.strictLabels, input.labels);
    }
    if (input.comments != null) {
      checkComments(input.comments);
    }
    if (input.notify == null) {
      log.warn("notify = null; assuming notify = NONE");
      input.notify = NotifyHandling.NONE;
    }

    ChangeUpdate update = null;
    db.get().changes().beginTransaction(revision.getChange().getId());
    boolean dirty = false;
    try {
      change = db.get().changes().get(revision.getChange().getId());
      ChangeUtil.updated(change);
      timestamp = change.getLastUpdatedOn();

      update = updateFactory.create(revision.getControl(), timestamp);
      dirty |= insertComments(revision, input.comments, input.drafts);
      dirty |= updateLabels(revision, update, input.labels);
      dirty |= insertMessage(revision, input.message);
      if (dirty) {
        db.get().changes().update(Collections.singleton(change));
        db.get().commit();
      }
    } finally {
      db.get().rollback();
    }
    if (update != null) {
      update.commit();
    }

    CheckedFuture<?, IOException> indexWrite;
    if (dirty) {
      indexWrite = indexer.indexAsync(change.getId());
    } else {
      indexWrite = Futures.<Void, IOException> immediateCheckedFuture(null);
    }
    if (message != null) {
      if (input.notify.compareTo(NotifyHandling.NONE) > 0) {
        email.create(
            input.notify,
            change,
            revision.getPatchSet(),
            revision.getAccountId(),
            message,
            comments).sendAsync();
      }
      fireCommentAddedHook(revision);
    }

    Output output = new Output();
    output.labels = input.labels;
    indexWrite.checkedGet();
    return output;
  }

  private RevisionResource onBehalfOf(RevisionResource rev, ReviewInput in)
      throws BadRequestException, AuthException, UnprocessableEntityException,
      OrmException {
    if (in.labels == null || in.labels.isEmpty()) {
      throw new AuthException(String.format(
          "label required to post review on behalf of \"%s\"",
          in.onBehalfOf));
    }

    ChangeControl caller = rev.getControl();
    Iterator<Map.Entry<String, Short>> itr = in.labels.entrySet().iterator();
    while (itr.hasNext()) {
      Map.Entry<String, Short> ent = itr.next();
      LabelType type = caller.getLabelTypes().byLabel(ent.getKey());
      if (type == null && in.strictLabels) {
        throw new BadRequestException(String.format(
            "label \"%s\" is not a configured label", ent.getKey()));
      } else if (type == null) {
        itr.remove();
        continue;
      }

      PermissionRange r = caller.getRange(Permission.forLabelAs(type.getName()));
      if (r == null || r.isEmpty() || !r.contains(ent.getValue())) {
        throw new AuthException(String.format(
            "not permitted to modify label \"%s\" on behalf of \"%s\"",
            ent.getKey(), in.onBehalfOf));
      }
    }
    if (in.labels.isEmpty()) {
      throw new AuthException(String.format(
          "label required to post review on behalf of \"%s\"",
          in.onBehalfOf));
    }

    ChangeControl target = caller.forUser(accounts.parse(in.onBehalfOf));
    return new RevisionResource(changes.parse(target), rev.getPatchSet());
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
        c.message = Strings.nullToEmpty(c.message).trim();
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
    List<PatchLineComment> ups = Lists.newArrayList();

    for (Map.Entry<String, List<Comment>> ent : in.entrySet()) {
      String path = ent.getKey();
      for (Comment c : ent.getValue()) {
        String parent = Url.decode(c.inReplyTo);
        PatchLineComment e = drafts.remove(Url.decode(c.id));
        if (e == null) {
          e = new PatchLineComment(
              new PatchLineComment.Key(
                  new Patch.Key(rsrc.getPatchSet().getId(), path),
                  ChangeUtil.messageUUID(db.get())),
              c.line,
              rsrc.getAccountId(),
              parent, timestamp);
        } else if (parent != null) {
          e.setParentUuid(parent);
        }
        e.setStatus(PatchLineComment.Status.PUBLISHED);
        e.setWrittenOn(timestamp);
        e.setSide(c.side == Side.PARENT ? (short) 0 : (short) 1);
        e.setMessage(c.message);
        if (c.range != null) {
          e.setRange(new CommentRange(
              c.range.startLine,
              c.range.startCharacter,
              c.range.endLine,
              c.range.endCharacter));
          e.setLine(c.range.endLine);
        }
        ups.add(e);
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
          ups.add(e);
        }
        break;
    }
    db.get().patchComments().delete(del);
    db.get().patchComments().upsert(ups);
    comments.addAll(ups);
    return !del.isEmpty() || !ups.isEmpty();
  }

  private Map<String, PatchLineComment> scanDraftComments(
      RevisionResource rsrc) throws OrmException {
    Map<String, PatchLineComment> drafts = Maps.newHashMap();
    for (PatchLineComment c : db.get().patchComments().draftByPatchSetAuthor(
          rsrc.getPatchSet().getId(),
          rsrc.getAccountId())) {
      drafts.put(c.getKey().get(), c);
    }
    return drafts;
  }

  private boolean updateLabels(RevisionResource rsrc, ChangeUpdate update,
      Map<String, Short> labels) throws OrmException {
    if (labels == null) {
      labels = Collections.emptyMap();
    }

    List<PatchSetApproval> del = Lists.newArrayList();
    List<PatchSetApproval> ups = Lists.newArrayList();
    Map<String, PatchSetApproval> current = scanLabels(rsrc, del);

    LabelTypes labelTypes = rsrc.getControl().getLabelTypes();
    for (Map.Entry<String, Short> ent : labels.entrySet()) {
      String name = ent.getKey();
      LabelType lt = checkNotNull(labelTypes.byLabel(name), name);
      if (change.getStatus().isClosed()) {
        // TODO Allow updating some labels even when closed.
        continue;
      }

      PatchSetApproval c = current.remove(lt.getName());
      String normName = lt.getName();
      if (ent.getValue() == null || ent.getValue() == 0) {
        // User requested delete of this label.
        if (c != null) {
          if (c.getValue() != 0) {
            addLabelDelta(normName, (short) 0);
          }
          del.add(c);
          update.putApproval(ent.getKey(), (short) 0);
        }
      } else if (c != null && c.getValue() != ent.getValue()) {
        c.setValue(ent.getValue());
        c.setGranted(timestamp);
        ups.add(c);
        addLabelDelta(normName, c.getValue());
        categories.put(normName, c.getValue());
        update.putApproval(ent.getKey(), ent.getValue());
      } else if (c != null && c.getValue() == ent.getValue()) {
        current.put(normName, c);
      } else if (c == null) {
        c = new PatchSetApproval(new PatchSetApproval.Key(
                rsrc.getPatchSet().getId(),
                rsrc.getAccountId(),
                lt.getLabelId()),
            ent.getValue(), TimeUtil.nowTs());
        c.setGranted(timestamp);
        ups.add(c);
        addLabelDelta(normName, c.getValue());
        categories.put(normName, c.getValue());
        update.putApproval(ent.getKey(), ent.getValue());
      }
    }

    forceCallerAsReviewer(rsrc, current, ups, del);
    db.get().patchSetApprovals().delete(del);
    db.get().patchSetApprovals().upsert(ups);
    return !del.isEmpty() || !ups.isEmpty();
  }

  private void forceCallerAsReviewer(RevisionResource rsrc,
      Map<String, PatchSetApproval> current, List<PatchSetApproval> ups,
      List<PatchSetApproval> del) {
    if (current.isEmpty() && ups.isEmpty()) {
      // TODO Find another way to link reviewers to changes.
      if (del.isEmpty()) {
        // If no existing label is being set to 0, hack in the caller
        // as a reviewer by picking the first server-wide LabelType.
        PatchSetApproval c = new PatchSetApproval(new PatchSetApproval.Key(
            rsrc.getPatchSet().getId(),
            rsrc.getAccountId(),
            rsrc.getControl().getLabelTypes().getLabelTypes().get(0)
                .getLabelId()),
            (short) 0, TimeUtil.nowTs());
        c.setGranted(timestamp);
        ups.add(c);
      } else {
        // Pick a random label that is about to be deleted and keep it.
        Iterator<PatchSetApproval> i = del.iterator();
        PatchSetApproval c = i.next();
        c.setValue((short) 0);
        c.setGranted(timestamp);
        i.remove();
        ups.add(c);
      }
    }
  }

  private Map<String, PatchSetApproval> scanLabels(RevisionResource rsrc,
      List<PatchSetApproval> del) throws OrmException {
    LabelTypes labelTypes = rsrc.getControl().getLabelTypes();
    Map<String, PatchSetApproval> current = Maps.newHashMap();

    for (PatchSetApproval a : approvalsUtil.byPatchSetUser(
        db.get(), rsrc.getNotes(), rsrc.getPatchSet().getId(),
        rsrc.getAccountId())) {
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

  private void addLabelDelta(String name, short value) {
    labelDelta.add(new LabelVote(name, value).format());
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
        new ChangeMessage.Key(change.getId(), ChangeUtil.messageUUID(db.get())),
        rsrc.getAccountId(),
        timestamp,
        rsrc.getPatchSet().getId());
    message.setMessage(String.format(
        "Patch Set %d:%s",
        rsrc.getPatchSet().getPatchSetId(),
        buf.toString()));
    db.get().changeMessages().insert(Collections.singleton(message));
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
          categories, db.get());
    } catch (OrmException e) {
      log.warn("ChangeHook.doCommentAddedHook delivery failed", e);
    }
  }
}
