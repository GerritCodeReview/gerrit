// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;
import java.util.Collections;
import java.util.List;

class EqualsPathPredicate extends ChangeIndexPredicate {
  private final String value;

  EqualsPathPredicate(String fieldName, String value) {
    super(ChangeField.PATH, fieldName, value);
    this.value = value;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    List<String> files = object.currentFilePaths();
    return files != null && Collections.binarySearch(files, value) >= 0;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
