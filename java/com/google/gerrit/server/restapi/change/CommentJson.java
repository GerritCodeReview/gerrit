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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.CommentsUtil.COMMENT_INFO_ORDER;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.FixSuggestion;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.CommentContextLoader;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CommentJson {

  private final AccountLoader.Factory accountLoaderFactory;

  private boolean fillAccounts = true;
  private boolean fillPatchSet;
  @Nullable private CommentContextLoader commentContextLoader;

  @Inject
  CommentJson(AccountLoader.Factory accountLoaderFactory) {
    this.accountLoaderFactory = accountLoaderFactory;
  }

  CommentJson setFillAccounts(boolean fillAccounts) {
    this.fillAccounts = fillAccounts;
    return this;
  }

  CommentJson setFillPatchSet(boolean fillPatchSet) {
    this.fillPatchSet = fillPatchSet;
    return this;
  }

  CommentJson setCommentContextLoader(@Nullable CommentContextLoader commentContextLoader) {
    this.commentContextLoader = commentContextLoader;
    return this;
  }

  public HumanCommentFormatter newHumanCommentFormatter() {
    return new HumanCommentFormatter();
  }

  public RobotCommentFormatter newRobotCommentFormatter() {
    return new RobotCommentFormatter();
  }

  private abstract class BaseCommentFormatter<F extends Comment, T extends CommentInfo> {
    public T format(F comment) throws PermissionBackendException {
      AccountLoader loader = fillAccounts ? accountLoaderFactory.create(true) : null;
      T info = toInfo(comment, loader);
      if (loader != null) {
        loader.fill();
      }
      if (commentContextLoader != null) {
        commentContextLoader.fill();
      }
      return info;
    }

    public Map<String, List<T>> format(Iterable<F> comments) throws PermissionBackendException {
      AccountLoader loader = fillAccounts ? accountLoaderFactory.create(true) : null;

      Map<String, List<T>> out = new TreeMap<>();

      for (F c : comments) {
        T o = toInfo(c, loader);
        List<T> list = out.get(o.path);
        if (list == null) {
          list = new ArrayList<>();
          out.put(o.path, list);
        }
        o.path = null;
        list.add(o);
      }

      out.values().forEach(l -> l.sort(COMMENT_INFO_ORDER));

      if (loader != null) {
        loader.fill();
      }
      if (commentContextLoader != null) {
        commentContextLoader.fill();
      }
      return out;
    }

    public ImmutableList<T> formatAsList(Iterable<F> comments) throws PermissionBackendException {
      AccountLoader loader = fillAccounts ? accountLoaderFactory.create(true) : null;

      ImmutableList<T> out =
          Streams.stream(comments)
              .map(c -> toInfo(c, loader))
              .sorted(COMMENT_INFO_ORDER)
              .collect(toImmutableList());

      if (loader != null) {
        loader.fill();
      }
      if (commentContextLoader != null) {
        commentContextLoader.fill();
      }
      return out;
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
      r.unresolved = c.unresolved;
      if (loader != null) {
        r.author = loader.get(c.author.getId());
      }
      r.commitId = c.getCommitId().getName();
      if (commentContextLoader != null) {
        r.contextLines = commentContextLoader.getContext(r);
      }
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
  }

  public class HumanCommentFormatter extends BaseCommentFormatter<HumanComment, CommentInfo> {
    @Override
    protected CommentInfo toInfo(HumanComment c, AccountLoader loader) {
      CommentInfo ci = new CommentInfo();
      fillCommentInfo(c, ci, loader);
      return ci;
    }

    private HumanCommentFormatter() {}
  }

  class RobotCommentFormatter extends BaseCommentFormatter<RobotComment, RobotCommentInfo> {
    @Override
    protected RobotCommentInfo toInfo(RobotComment c, AccountLoader loader) {
      RobotCommentInfo rci = new RobotCommentInfo();
      rci.robotId = c.robotId;
      rci.robotRunId = c.robotRunId;
      rci.url = c.url;
      rci.properties = c.properties;
      rci.fixSuggestions = toFixSuggestionInfos(c.fixSuggestions);
      fillCommentInfo(c, rci, loader);
      return rci;
    }

    private List<FixSuggestionInfo> toFixSuggestionInfos(
        @Nullable List<FixSuggestion> fixSuggestions) {
      if (fixSuggestions == null || fixSuggestions.isEmpty()) {
        return null;
      }

      return fixSuggestions.stream().map(this::toFixSuggestionInfo).collect(toList());
    }

    private FixSuggestionInfo toFixSuggestionInfo(FixSuggestion fixSuggestion) {
      FixSuggestionInfo fixSuggestionInfo = new FixSuggestionInfo();
      fixSuggestionInfo.fixId = fixSuggestion.fixId;
      fixSuggestionInfo.description = fixSuggestion.description;
      fixSuggestionInfo.replacements =
          fixSuggestion.replacements.stream().map(this::toFixReplacementInfo).collect(toList());
      return fixSuggestionInfo;
    }

    private FixReplacementInfo toFixReplacementInfo(FixReplacement fixReplacement) {
      FixReplacementInfo fixReplacementInfo = new FixReplacementInfo();
      fixReplacementInfo.path = fixReplacement.path;
      fixReplacementInfo.range = toRange(fixReplacement.range);
      fixReplacementInfo.replacement = fixReplacement.replacement;
      return fixReplacementInfo;
    }

    private RobotCommentFormatter() {}
  }
}
