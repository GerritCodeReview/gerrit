// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;

import java.util.Collections;
import java.util.List;

public class PatchSetDetail {
  protected PatchSet patchSet;
  protected PatchSetInfo info;
  protected List<Patch> patches;
  protected Project.NameKey project;
  protected List<UiCommandDetail> commands;

  public PatchSetDetail() {
  }

  public PatchSet getPatchSet() {
    return patchSet;
  }

  public void setPatchSet(final PatchSet ps) {
    patchSet = ps;
  }

  public PatchSetInfo getInfo() {
    return info;
  }

  public void setInfo(final PatchSetInfo i) {
    info = i;
  }

  public List<Patch> getPatches() {
    return patches;
  }

  public void setPatches(final List<Patch> p) {
    patches = p;
  }

  public Project.NameKey getProject() {
    return project;
  }

  public void setProject(final Project.NameKey p) {
    project = p;
  }

  public List<UiCommandDetail> getCommands() {
    if (commands != null) {
      return commands;
    }
    return Collections.emptyList();
  }

  public void setCommands(List<UiCommandDetail> cmds) {
    commands = cmds.isEmpty() ? null : cmds;
  }
}
