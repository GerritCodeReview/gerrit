// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.events;

import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.data.PatchSetAttribute;

public class ChangeAbandonedEvent extends ChangeEvent {
  public final String type = "change-abandoned";
  public ChangeAttribute change;
  public PatchSetAttribute patchSet;
  public AccountAttribute abandoner;
  public String reason;

  @Override
  public String getType() {
    return type;
  }

  @Override
  public Project.NameKey getProjectNameKey() {
    return new Project.NameKey(change.project);
  }

  @Override
  public Change.Key getChangeKey() {
    return new Change.Key(change.id);
  }

  @Override
  public String getRefName() {
    return R_HEADS + change.branch;
  }
}
