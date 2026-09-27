// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.CommentsUtil.COMMENT_INFO_ORDER;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.CommentContext;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.FixSuggestion;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.ContextLineInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.comment.CommentContextCache;
import com.google.gerrit.server.comment.CommentContextKey;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CommentJson {

  private final Provider<AccountLoader.Factory> accountLoaderFactory;
  private final Provider<CommentContextCache> commentContextCache;

  private Project.NameKey project;
  private Change.Id changeId;

  private boolean fillAccounts = true;
  private boolean fillPatchSet;
  private boolean fillCommentContext;
  private int contextPadding;

  @Inject
  CommentJson(
      Provider<AccountLoader.Factory> accountLoaderFactory,
      Provider<CommentContextCache> commentContextCache) {
    this.accountLoaderFactory = accountLoaderFactory;
    this.commentContextCache = commentContextCache;
  }

  public CommentJson setFillAccounts(boolean fillAccounts) {
    this.fillAccounts = fillAccounts;
    return this;
  }

  public CommentJson setFillPatchSet(boolean fillPatchSet) {
    this.fillPatchSet = fillPatchSet;
    return this;
  }

  public CommentJson setFillCommentContext(boolean fillCommentContext) {
    this.fillCommentContext = fillCommentContext;
    return this;
  }

  public CommentJson setContextPadding(int contextPadding) {
    this.contextPadding = contextPadding;
    return this;
  }

  public CommentJson setProjectKey(Project.NameKey project) {
    this.project = project;
    return this;
  }

  public CommentJson setChangeId(Change.Id changeId) {
    this.changeId = changeId;
    return this;
  }

  public HumanCommentFormatter newHumanCommentFormatter() {
    return new HumanCommentFormatter();
  }

  private abstract class BaseCommentFormatter<F extends Comment, T extends CommentInfo> {
    public T format(F comment) throws PermissionBackendException {
      AccountLoader loader = fillAccounts ? accountLoaderFactory.get().create(true) : null;
      T info = toInfo(comment, loader);
      if (loader != null) {
        loader.fill();
      }
      return info;
    }

    public Map<String, List<T>> format(Iterable<F> comments) throws PermissionBackendException {
      AccountLoader loader = fillAccounts ? accountLoaderFactory.get().create(true) : null;

      Map<String, List<T>> out = new TreeMap<>();
      int estimatedSize = (comments instanceof Collection) ? ((Collection<?>) comments).size() : 16;
      List<T> allComments = fillCommentContext ? new ArrayList<>(estimatedSize) : null;

      for (F c : comments) {
        T o = toInfo(c, loader);
        out.computeIfAbsent(o.path, k -> new ArrayList<>()).add(o);
        if (fillCommentContext) {
          allComments.add(o);
        }
      }

      for (List<T> list : out.values()) {
        list.sort(COMMENT_INFO_ORDER);
      }

      if (loader != null) {
        loader.fill();
      }

      if (fillCommentContext && allComments != null && !allComments.isEmpty()) {
        addCommentContext(allComments);
      }
      for (List<T> list : out.values()) {
        for (T c : list) {
          c.path = null; // we don't need path since it exists in the map keys
        }
      }
      return out;
    }

    public ImmutableList<T> formatAsList(Iterable<F> comments) throws PermissionBackendException {
      AccountLoader loader = fillAccounts ? accountLoaderFactory.get().create(true) : null;

      int estimatedSize = (comments instanceof Collection) ? ((Collection<?>) comments).size() : 16;
      List<T> outList = new ArrayList<>(estimatedSize);
      for (F c : comments) {
        outList.add(toInfo(c, loader));
      }
      outList.sort(COMMENT_INFO_ORDER);

      if (loader != null) {
        loader.fill();
      }

      if (fillCommentContext && !outList.isEmpty()) {
        addCommentContext(outList);
      }

      return ImmutableList.copyOf(outList);
    }

    protected void addCommentContext(List<T> allComments) {
      if (allComments.isEmpty()) {
        return;
      }
      List<CommentContextKey> keys = new ArrayList<>(allComments.size());
      for (T c : allComments) {
        keys.add(createCommentContextKey(c));
      }
      ImmutableMap<CommentContextKey, CommentContext> allContext =
          commentContextCache.get().getAll(keys);
      for (int i = 0; i < allComments.size(); i++) {
        T c = allComments.get(i);
        CommentContextKey contextKey = keys.get(i);
        CommentContext commentContext = allContext.get(contextKey);
        if (commentContext != null) {
          c.contextLines = toContextLineInfoList(commentContext);
          c.sourceContentType = commentContext.contentType();
        }
      }
    }

    protected List<ContextLineInfo> toContextLineInfoList(CommentContext commentContext) {
      if (commentContext == null
          || commentContext.lines() == null
          || commentContext.lines().isEmpty()) {
        return new ArrayList<>();
      }
      List<ContextLineInfo> result = new ArrayList<>(commentContext.lines().size());
      for (Map.Entry<Integer, String> e : commentContext.lines().entrySet()) {
        result.add(new ContextLineInfo(e.getKey(), e.getValue()));
      }
      return result;
    }

    protected CommentContextKey createCommentContextKey(T r) {
      return CommentContextKey.builder()
          .project(project)
          .changeId(changeId)
          .id(Url.decode(r.id)) // We reverse the encoding done while filling comment info
          .path(r.path)
          .patchset(r.patchSet)
          .contextPadding(contextPadding)
          .build();
    }

    protected abstract T toInfo(F comment, AccountLoader loader);

    protected void fillCommentInfo(Comment c, CommentInfo r, AccountLoader loader) {
      if (fillPatchSet) {
        r.patchSet = c.key.patchSetId;
      }
      r.id = Url.encode(c.key.uuid);
      r.path = c.key.filename;
      if (c.side <= 0) {
        r.side = Side.PARENT;
        if (c.side < 0) {
          r.parent = -c.side;
        }
      }
      if (c.lineNbr > 0) {
        r.line = c.lineNbr;
      }
      r.inReplyTo = Url.encode(c.parentUuid);
      r.message = Strings.emptyToNull(c.message);
      r.updated = c.writtenOn;
      r.range = toRange(c.range);
      r.tag = c.tag;
      r.isAi = c.isAi;
      if (loader != null) {
        r.author = loader.get(c.author.getId());
      }
      r.commitId = c.getCommitId().getName();
      r.fixSuggestions = toFixSuggestionInfos(c.fixSuggestions);
    }

    protected Range toRange(Comment.Range commentRange) {
      Range range = null;
      if (commentRange != null) {
        range = new Range();
        range.startLine = commentRange.startLine;
        range.startCharacter = commentRange.startChar;
        range.endLine = commentRange.endLine;
        range.endCharacter = commentRange.endChar;
      }
      return range;
    }

    @Nullable
    private List<FixSuggestionInfo> toFixSuggestionInfos(
        @Nullable List<FixSuggestion> fixSuggestions) {
      if (fixSuggestions == null || fixSuggestions.isEmpty()) {
        return null;
      }

      List<FixSuggestionInfo> result = new ArrayList<>(fixSuggestions.size());
      for (FixSuggestion fixSuggestion : fixSuggestions) {
        result.add(toFixSuggestionInfo(fixSuggestion));
      }
      return result;
    }

    private FixSuggestionInfo toFixSuggestionInfo(FixSuggestion fixSuggestion) {
      FixSuggestionInfo fixSuggestionInfo = new FixSuggestionInfo();
      fixSuggestionInfo.fixId = fixSuggestion.fixId;
      fixSuggestionInfo.description = fixSuggestion.description;
      if (fixSuggestion.replacements != null) {
        List<FixReplacementInfo> replacements = new ArrayList<>(fixSuggestion.replacements.size());
        for (FixReplacement fixReplacement : fixSuggestion.replacements) {
          replacements.add(toFixReplacementInfo(fixReplacement));
        }
        fixSuggestionInfo.replacements = replacements;
      }
      return fixSuggestionInfo;
    }

    private FixReplacementInfo toFixReplacementInfo(FixReplacement fixReplacement) {
      FixReplacementInfo fixReplacementInfo = new FixReplacementInfo();
      fixReplacementInfo.path = fixReplacement.path;
      fixReplacementInfo.range = toRange(fixReplacement.range);
      fixReplacementInfo.replacement = fixReplacement.replacement;
      return fixReplacementInfo;
    }
  }

  public class HumanCommentFormatter extends BaseCommentFormatter<HumanComment, CommentInfo> {
    @Override
    protected CommentInfo toInfo(HumanComment c, AccountLoader loader) {
      CommentInfo ci = new CommentInfo();
      fillCommentInfo(c, ci, loader);
      ci.unresolved = c.unresolved;
      return ci;
    }

    private HumanCommentFormatter() {}
  }
}
