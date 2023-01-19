// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.restapi.account;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.accounts.DeletedDraftCommentInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.CommentJson;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public class DeleteDraftCommentsUtil {
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeQueryBuilder queryBuilder;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeJson.Factory changeJsonFactory;
  private final Provider<CommentJson> commentJsonProvider;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;

  @Inject
  public DeleteDraftCommentsUtil(
      BatchUpdate.Factory batchUpdateFactory,
      ChangeQueryBuilder queryBuilder,
      Provider<InternalChangeQuery> queryProvider,
      ChangeData.Factory changeDataFactory,
      ChangeJson.Factory changeJsonFactory,
      Provider<CommentJson> commentJsonProvider,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.queryBuilder = queryBuilder;
    this.queryProvider = queryProvider;
    this.changeDataFactory = changeDataFactory;
    this.changeJsonFactory = changeJsonFactory;
    this.commentJsonProvider = commentJsonProvider;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
  }

  public ImmutableList<DeletedDraftCommentInfo> deleteDraftComments(
      IdentifiedUser user, String query) throws RestApiException, UpdateException {
    CommentJson.HumanCommentFormatter humanCommentFormatter =
        commentJsonProvider.get().newHumanCommentFormatter();
    Account.Id accountId = user.getAccountId();
    Instant now = TimeUtil.now();
    Map<Project.NameKey, BatchUpdate> updates = new LinkedHashMap<>();
    List<Op> ops = new ArrayList<>();
    for (ChangeData cd :
        queryProvider
            .get()
            // Don't attempt to mutate any changes the user can't currently see.
            .enforceVisibility(true)
            .query(predicate(accountId, query))) {
      BatchUpdate update =
          updates.computeIfAbsent(cd.project(), p -> batchUpdateFactory.create(p, user, now));
      Op op = new Op(humanCommentFormatter, accountId);
      update.addOp(cd.getId(), op);
      ops.add(op);
    }
    // Currently there's no way to let some updates succeed even if others fail. Even if there were,
    // all updates from this operation only happen in All-Users and thus are fully atomic, so
    // allowing partial failure would have little value.
    BatchUpdate.execute(updates.values(), ImmutableList.of(), false);
    return ops.stream().map(Op::getResult).filter(Objects::nonNull).collect(toImmutableList());
  }

  private Predicate<ChangeData> predicate(Account.Id accountId, String query)
      throws BadRequestException {
    Predicate<ChangeData> hasDraft = ChangePredicates.draftBy(commentsUtil, accountId);
    if (CharMatcher.whitespace().trimFrom(Strings.nullToEmpty(query)).isEmpty()) {
      return hasDraft;
    }
    try {
      return Predicate.and(hasDraft, queryBuilder.parse(query));
    } catch (QueryParseException e) {
      throw new BadRequestException("Invalid query: " + e.getMessage(), e);
    }
  }

  private class Op implements BatchUpdateOp {
    private final CommentJson.HumanCommentFormatter humanCommentFormatter;
    private final Account.Id accountId;
    private DeletedDraftCommentInfo result;

    Op(CommentJson.HumanCommentFormatter humanCommentFormatter, Account.Id accountId) {
      this.humanCommentFormatter = humanCommentFormatter;
      this.accountId = accountId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws PermissionBackendException {
      ImmutableList.Builder<CommentInfo> comments = ImmutableList.builder();
      boolean dirty = false;
      for (HumanComment c : commentsUtil.draftByChangeAuthor(ctx.getNotes(), accountId)) {
        dirty = true;
        PatchSet.Id psId = PatchSet.id(ctx.getChange().getId(), c.key.patchSetId);
        commentsUtil.setCommentCommitId(c, ctx.getChange(), psUtil.get(ctx.getNotes(), psId));
        commentsUtil.deleteHumanComments(ctx.getUpdate(psId), Collections.singleton(c));
        comments.add(humanCommentFormatter.format(c));
      }
      if (dirty) {
        result = new DeletedDraftCommentInfo();
        result.change =
            changeJsonFactory.noOptions().format(changeDataFactory.create(ctx.getNotes()));
        result.deleted = comments.build();
      }
      return dirty;
    }

    @Nullable
    DeletedDraftCommentInfo getResult() {
      return result;
    }
  }
}
