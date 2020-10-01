// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.server.change.CommentThread;
import com.google.gerrit.server.change.CommentThreads;
import java.util.Collection;

/** A filter which only keeps comments which are part of an unresolved {@link CommentThread}. */
public class UnresolvedCommentFilter implements HumanCommentFilter {

  @Override
  public ImmutableList<HumanComment> filter(ImmutableList<HumanComment> comments) {
    ImmutableSet<CommentThread<HumanComment>> commentThreads =
        CommentThreads.forComments(comments).getThreads();

    return commentThreads.stream()
        .filter(CommentThread::unresolved)
        .map(CommentThread::comments)
        .flatMap(Collection::stream)
        .collect(toImmutableList());
  }
}
