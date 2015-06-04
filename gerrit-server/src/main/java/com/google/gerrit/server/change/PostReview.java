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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.PatchLineCommentsUtil.setCommentRevId;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput.NotifyHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.LabelVote;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class PostReview implements RestModifyView<RevisionResource, ReviewInput> {
  private static final Logger log = LoggerFactory.getLogger(PostReview.class);

  static class Output {
    Map<String, Short> labels;
  }

  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangesCollection changes;
  private final ChangeData.Factory changeDataFactory;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final PatchLineCommentsUtil plcUtil;
  private final PatchListCache patchListCache;
  private final AccountsCollection accounts;
  private final EmailReviewComments.Factory email;
  private final ChangeHooks hooks;

  @Inject
  PostReview(Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      ChangesCollection changes,
      ChangeData.Factory changeDataFactory,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      PatchLineCommentsUtil plcUtil,
      PatchListCache patchListCache,
      AccountsCollection accounts,
      EmailReviewComments.Factory email,
      ChangeHooks hooks) {
    this.db = db;
    this.batchUpdateFactory = batchUpdateFactory;
    this.changes = changes;
    this.changeDataFactory = changeDataFactory;
    this.plcUtil = plcUtil;
    this.patchListCache = patchListCache;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.accounts = accounts;
    this.email = email;
    this.hooks = hooks;
  }

  @Override
  public Output apply(RevisionResource revision, ReviewInput input)
      throws RestApiException, UpdateException, OrmException {
    return apply(revision, input, TimeUtil.nowTs());
  }

  public Output apply(RevisionResource revision, ReviewInput input,
      Timestamp ts) throws RestApiException, UpdateException, OrmException {
    if (revision.getEdit().isPresent()) {
      throw new ResourceConflictException("cannot post review on edit");
    }
    if (input.onBehalfOf != null) {
      revision = onBehalfOf(revision, input);
    }
    if (input.labels != null) {
      checkLabels(revision, input.strictLabels, input.labels);
    }
    if (input.comments != null) {
      checkComments(revision, input.comments);
    }
    if (input.notify == null) {
      log.warn("notify = null; assuming notify = NONE");
      input.notify = NotifyHandling.NONE;
    }

    try (BatchUpdate bu = batchUpdateFactory.create(db.get(),
          revision.getChange().getProject(), revision.getUser(), ts)) {
      bu.addOp(
          revision.getChange().getId(),
          new Op(revision.getPatchSet().getId(), input));
      bu.execute();
    }
    Output output = new Output();
    output.labels = input.labels;
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

  private void checkComments(RevisionResource revision, Map<String, List<CommentInput>> in)
      throws BadRequestException, OrmException {
    Iterator<Map.Entry<String, List<CommentInput>>> mapItr =
        in.entrySet().iterator();
    Set<String> filePaths =
        Sets.newHashSet(changeDataFactory.create(
            db.get(), revision.getControl()).filePaths(
                revision.getPatchSet()));
    while (mapItr.hasNext()) {
      Map.Entry<String, List<CommentInput>> ent = mapItr.next();
      String path = ent.getKey();
      if (!filePaths.contains(path) && !Patch.COMMIT_MSG.equals(path)) {
        throw new BadRequestException(String.format(
            "file %s not found in revision %s",
            path, revision.getChange().currentPatchSetId()));
      }

      List<CommentInput> list = ent.getValue();
      if (list == null) {
        mapItr.remove();
        continue;
      }

      Iterator<CommentInput> listItr = list.iterator();
      while (listItr.hasNext()) {
        CommentInput c = listItr.next();
        if (c == null) {
          listItr.remove();
          continue;
        }
        if (c.line != null && c.line < 0) {
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

  /**
   * Used to compare PatchLineComments with CommentInput comments.
   */
  @AutoValue
  abstract static class CommentSetEntry {
    private static CommentSetEntry create(Patch.Key key,
        Integer line, Side side, HashCode message, CommentRange range) {
      return new AutoValue_PostReview_CommentSetEntry(key, line, side, message,
          range);
    }

    public static CommentSetEntry create(PatchLineComment comment) {
      return create(comment.getKey().getParentKey(),
          comment.getLine(),
          Side.fromShort(comment.getSide()),
          Hashing.sha1().hashString(comment.getMessage(), UTF_8),
          comment.getRange());
    }

    abstract Patch.Key key();
    @Nullable abstract Integer line();
    abstract Side side();
    abstract HashCode message();
    @Nullable abstract CommentRange range();
  }

  private class Op extends BatchUpdate.Op {
    private final PatchSet.Id psId;
    private final ReviewInput in;

    private IdentifiedUser user;
    private Change change;
    private PatchSet ps;
    private ChangeMessage message;
    private List<PatchLineComment> comments = new ArrayList<>();
    private List<String> labelDelta = new ArrayList<>();
    private Map<String, Short> categories = new HashMap<>();

    private Op(PatchSet.Id psId, ReviewInput in) {
      this.psId = psId;
      this.in = in;
    }

    @Override
    public void updateChange(ChangeContext ctx)
        throws OrmException, ResourceConflictException {
      user = ctx.getUser().asIdentifiedUser();
      change = ctx.getChange();
      if (change.getLastUpdatedOn().before(ctx.getWhen())) {
        change.setLastUpdatedOn(ctx.getWhen());
      }
      ps = ctx.getDb().patchSets().get(psId);
      ctx.getChangeUpdate().setPatchSetId(psId);
      boolean dirty = false;
      dirty |= insertComments(ctx);
      dirty |= updateLabels(ctx);
      dirty |= insertMessage(ctx);
      if (dirty) {
        ctx.getDb().changes().update(Collections.singleton(change));
      }
    }

    @Override
    public void postUpdate(Context ctx) {
      if (message == null) {
        return;
      }
      if (in.notify.compareTo(NotifyHandling.NONE) > 0) {
        email.create(
            in.notify,
            change,
            ps,
            user.getAccountId(),
            message,
            comments).sendAsync();
      }
      try {
        hooks.doCommentAddedHook(change, user.getAccount(), ps,
            message.getMessage(), categories, ctx.getDb());
      } catch (OrmException e) {
        log.warn("ChangeHook.doCommentAddedHook delivery failed", e);
      }
    }

    private boolean insertComments(ChangeContext ctx) throws OrmException {
      Map<String, List<CommentInput>> map = in.comments;
      if (map == null) {
        map = Collections.emptyMap();
      }

      Map<String, PatchLineComment> drafts = Collections.emptyMap();
      if (!map.isEmpty() || in.drafts != DraftHandling.KEEP) {
        if (in.drafts == DraftHandling.PUBLISH_ALL_REVISIONS) {
          drafts = changeDrafts(ctx);
        } else {
          drafts = patchSetDrafts(ctx);
        }
      }

      List<PatchLineComment> del = Lists.newArrayList();
      List<PatchLineComment> ups = Lists.newArrayList();

      Set<CommentSetEntry> existingIds = in.omitDuplicateComments
          ? readExistingComments(ctx)
          : Collections.<CommentSetEntry>emptySet();

      for (Map.Entry<String, List<CommentInput>> ent : map.entrySet()) {
        String path = ent.getKey();
        for (CommentInput c : ent.getValue()) {
          String parent = Url.decode(c.inReplyTo);
          PatchLineComment e = drafts.remove(Url.decode(c.id));
          if (e == null) {
            e = new PatchLineComment(
                new PatchLineComment.Key(new Patch.Key(psId, path), null),
                c.line != null ? c.line : 0,
                user.getAccountId(),
                parent, ctx.getWhen());
          } else if (parent != null) {
            e.setParentUuid(parent);
          }
          e.setStatus(PatchLineComment.Status.PUBLISHED);
          e.setWrittenOn(ctx.getWhen());
          e.setSide(c.side == Side.PARENT ? (short) 0 : (short) 1);
          setCommentRevId(e, patchListCache, ctx.getChange(), ps);
          e.setMessage(c.message);
          if (c.range != null) {
            e.setRange(new CommentRange(
                c.range.startLine,
                c.range.startCharacter,
                c.range.endLine,
                c.range.endCharacter));
            e.setLine(c.range.endLine);
          }
          if (existingIds.contains(CommentSetEntry.create(e))) {
            continue;
          }
          if (e.getKey().get() == null) {
            e.getKey().set(ChangeUtil.messageUUID(ctx.getDb()));
          }
          ups.add(e);
        }
      }

      switch (firstNonNull(in.drafts, DraftHandling.DELETE)) {
        case KEEP:
        default:
          break;
        case DELETE:
          del.addAll(drafts.values());
          break;
        case PUBLISH:
        case PUBLISH_ALL_REVISIONS:
          for (PatchLineComment e : drafts.values()) {
            e.setStatus(PatchLineComment.Status.PUBLISHED);
            e.setWrittenOn(ctx.getWhen());
            setCommentRevId(e, patchListCache, ctx.getChange(), ps);
            ups.add(e);
          }
          break;
      }
      plcUtil.deleteComments(ctx.getDb(), ctx.getChangeUpdate(), del);
      plcUtil.upsertComments(ctx.getDb(), ctx.getChangeUpdate(), ups);
      comments.addAll(ups);
      return !del.isEmpty() || !ups.isEmpty();
    }

    private Set<CommentSetEntry> readExistingComments(ChangeContext ctx)
        throws OrmException {
      Set<CommentSetEntry> r = new HashSet<>();
      for (PatchLineComment c : plcUtil.publishedByChange(ctx.getDb(),
            ctx.getChangeNotes())) {
        r.add(CommentSetEntry.create(c));
      }
      return r;
    }

    private Map<String, PatchLineComment> changeDrafts(ChangeContext ctx)
        throws OrmException {
      Map<String, PatchLineComment> drafts = Maps.newHashMap();
      for (PatchLineComment c : plcUtil.draftByChangeAuthor(
          ctx.getDb(), ctx.getChangeNotes(), user.getAccountId())) {
        drafts.put(c.getKey().get(), c);
      }
      return drafts;
    }

    private Map<String, PatchLineComment> patchSetDrafts(ChangeContext ctx)
        throws OrmException {
      Map<String, PatchLineComment> drafts = Maps.newHashMap();
      for (PatchLineComment c : plcUtil.draftByPatchSetAuthor(ctx.getDb(),
          psId, user.getAccountId(), ctx.getChangeNotes())) {
        drafts.put(c.getKey().get(), c);
      }
      return drafts;
    }

    private Map<String, Short> getAllLabels(Collection<PatchSetApproval> patchsetApprovals) {
      Map<String, Short> labels = new LinkedTreeMap<>();
      for (PatchSetApproval psa : patchsetApprovals) {
        labels.put(psa.getLabel(), psa.getValue());
      }
      return labels;
    }

    private boolean updateLabels(ChangeContext ctx)
        throws OrmException, ResourceConflictException {
      Map<String, Short> inLabels = MoreObjects.firstNonNull(in.labels,
          Collections.<String, Short> emptyMap());

      List<PatchSetApproval> del = Lists.newArrayList();
      List<PatchSetApproval> ups = Lists.newArrayList();
      Map<String, PatchSetApproval> current = scanLabels(ctx, del);
      Map<String, Short> allLabels = Collections.emptyMap();
      if (current != null) {
        allLabels = getAllLabels(current.values());
        allLabels.putAll(inLabels);
      }

      ChangeUpdate update = ctx.getChangeUpdate();
      LabelTypes labelTypes = ctx.getChangeControl().getLabelTypes();
      for (Map.Entry<String, Short> ent : allLabels.entrySet()) {
        String name = ent.getKey();
        LabelType lt = checkNotNull(labelTypes.byLabel(name), name);

        PatchSetApproval c = current.remove(lt.getName());
        String normName = lt.getName();
        if (ent.getValue() == null || ent.getValue() == 0) {
          // User requested delete of this label.
          if (c != null) {
            if (c.getValue() != 0) {
              addLabelDelta(normName, (short) 0);
            }
            del.add(c);
          }
          categories.put(ent.getKey(), (short) 0);
          update.putApproval(ent.getKey(), (short) 0);
        } else if (c != null && c.getValue() != ent.getValue()) {
          c.setValue(ent.getValue());
          c.setGranted(ctx.getWhen());
          ups.add(c);
          addLabelDelta(normName, c.getValue());
          categories.put(normName, c.getValue());
          update.putApproval(ent.getKey(), ent.getValue());
        } else if (c != null && c.getValue() == ent.getValue()) {
          current.put(normName, c);
          categories.put(normName, c.getValue());
          update.putApproval(normName, c.getValue());
        } else if (c == null) {
          c = new PatchSetApproval(new PatchSetApproval.Key(
                  psId,
                  user.getAccountId(),
                  lt.getLabelId()),
              ent.getValue(), TimeUtil.nowTs());
          c.setGranted(ctx.getWhen());
          ups.add(c);
          addLabelDelta(normName, c.getValue());
          categories.put(normName, c.getValue());
          update.putReviewer(user.getAccountId(), REVIEWER);
          update.putApproval(ent.getKey(), ent.getValue());
        }
      }

      if (!del.isEmpty() || !ups.isEmpty()) {
        if (ctx.getChange().getStatus().isClosed()) {
          throw new ResourceConflictException("change is closed");
        }
      }
      forceCallerAsReviewer(ctx, current, ups, del);
      ctx.getDb().patchSetApprovals().delete(del);
      ctx.getDb().patchSetApprovals().upsert(ups);
      return !del.isEmpty() || !ups.isEmpty();
    }

    private void forceCallerAsReviewer(ChangeContext ctx,
        Map<String, PatchSetApproval> current, List<PatchSetApproval> ups,
        List<PatchSetApproval> del) {
      if (current.isEmpty() && ups.isEmpty()) {
        // TODO Find another way to link reviewers to changes.
        if (del.isEmpty()) {
          // If no existing label is being set to 0, hack in the caller
          // as a reviewer by picking the first server-wide LabelType.
          PatchSetApproval c = new PatchSetApproval(new PatchSetApproval.Key(
              psId,
              user.getAccountId(),
              ctx.getChangeControl().getLabelTypes().getLabelTypes().get(0)
                  .getLabelId()),
              (short) 0, TimeUtil.nowTs());
          c.setGranted(ctx.getWhen());
          ups.add(c);
        } else {
          // Pick a random label that is about to be deleted and keep it.
          Iterator<PatchSetApproval> i = del.iterator();
          PatchSetApproval c = i.next();
          c.setValue((short) 0);
          c.setGranted(ctx.getWhen());
          i.remove();
          ups.add(c);
        }
      }
    }

    private Map<String, PatchSetApproval> scanLabels(ChangeContext ctx,
        List<PatchSetApproval> del) throws OrmException {
      LabelTypes labelTypes = ctx.getChangeControl().getLabelTypes();
      Map<String, PatchSetApproval> current = Maps.newHashMap();

      for (PatchSetApproval a : approvalsUtil.byPatchSetUser(
          ctx.getDb(), ctx.getChangeControl(), psId, user.getAccountId())) {
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

    private boolean insertMessage(ChangeContext ctx)
        throws OrmException {
      String msg = Strings.nullToEmpty(in.message).trim();

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
          new ChangeMessage.Key(
            psId.getParentKey(), ChangeUtil.messageUUID(ctx.getDb())),
          user.getAccountId(),
          ctx.getWhen(),
          psId);
      message.setMessage(String.format(
          "Patch Set %d:%s",
          psId.get(),
          buf.toString()));
      cmUtil.addChangeMessage(ctx.getDb(), ctx.getChangeUpdate(), message);
      return true;
    }

    private void addLabelDelta(String name, short value) {
      labelDelta.add(LabelVote.create(name, value).format());
    }
  }
}
