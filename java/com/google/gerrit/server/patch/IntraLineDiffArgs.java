// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Project;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class IntraLineDiffArgs {
  public static IntraLineDiffArgs create(
      Text aText,
      Text bText,
      List<Edit> edits,
      Set<Edit> editsDueToRebase,
      Project.NameKey project,
      ObjectId commit,
      String path) {
    return new AutoValue_IntraLineDiffArgs(
        aText, bText, deepCopyEdits(edits), deepCopyEdits(editsDueToRebase), project, commit, path);
  }

  private static ImmutableList<Edit> deepCopyEdits(List<Edit> edits) {
    return edits.stream().map(IntraLineDiffArgs::copy).collect(ImmutableList.toImmutableList());
  }

  private static ImmutableSet<Edit> deepCopyEdits(Set<Edit> edits) {
    return edits.stream().map(IntraLineDiffArgs::copy).collect(ImmutableSet.toImmutableSet());
  }

  private static Edit copy(Edit edit) {
    return new Edit(edit.getBeginA(), edit.getEndA(), edit.getBeginB(), edit.getEndB());
  }

  public abstract Text aText();

  public abstract Text bText();

  public abstract ImmutableList<Edit> edits();

  public abstract ImmutableSet<Edit> editsDueToRebase();

  public abstract Project.NameKey project();

  public abstract ObjectId commit();

  public abstract String path();
}
