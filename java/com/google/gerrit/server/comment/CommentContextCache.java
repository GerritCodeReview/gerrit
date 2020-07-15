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
// limitations under the License

package com.google.gerrit.server.comment;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.LabeledContextLineInfo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface CommentContextCache {
  List<LabeledContextLineInfo> get(Project.NameKey project, Change.Id changeId, CommentInfo comment);

  Map<CommentInfo, List<LabeledContextLineInfo>> getAll(
      Project.NameKey project, Change.Id changeId, Collection<CommentInfo> comments);
}
