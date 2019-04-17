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

import com.google.common.base.Supplier;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.data.ChangeAttribute;

public abstract class ChangeEvent extends RefEvent {
  public Supplier<ChangeAttribute> change;
  public Project.NameKey project;
  public String refName;
  public Change.Key changeKey;

  protected ChangeEvent(String type, Change change) {
    super(type);
    this.project = change.getProject();
    this.refName = RefNames.fullName(change.getDest().branch());
    this.changeKey = change.getKey();
  }

  @Override
  public Project.NameKey getProjectNameKey() {
    return project;
  }

  @Override
  public String getRefName() {
    return refName;
  }

  public Change.Key getChangeKey() {
    return changeKey;
  }
}
