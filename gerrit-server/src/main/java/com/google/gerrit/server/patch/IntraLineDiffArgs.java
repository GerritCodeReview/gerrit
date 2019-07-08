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
import com.google.gerrit.reviewdb.client.Project;
import java.util.List;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class IntraLineDiffArgs {
  public static IntraLineDiffArgs create(
      Text aText,
      Text bText,
      List<Edit> edits,
      Project.NameKey project,
      ObjectId commit,
      String path) {
    return new AutoValue_IntraLineDiffArgs(aText, bText, edits, project, commit, path);
  }

  public abstract Text aText();

  public abstract Text bText();

  public abstract List<Edit> edits();

  public abstract Project.NameKey project();

  public abstract ObjectId commit();

  public abstract String path();
}
