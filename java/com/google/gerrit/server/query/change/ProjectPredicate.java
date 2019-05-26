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

package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.index.change.ChangeField;

public class ProjectPredicate extends ChangeIndexPredicate {
  public ProjectPredicate(String id) {
    super(ChangeField.PROJECT, id);
  }

  protected Project.NameKey getValueKey() {
    return Project.nameKey(getValue());
  }

  @Override
  public boolean match(ChangeData object) {
    Change change = object.change();
    if (change == null) {
      return false;
    }

    Project.NameKey p = change.getDest().project();
    return p.equals(getValueKey());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
