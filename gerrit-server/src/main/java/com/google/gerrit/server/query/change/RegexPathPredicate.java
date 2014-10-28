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

import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.RegexPredicate;
import com.google.gerrit.server.util.RegexListSearcher;
import com.google.gwtorm.server.OrmException;

import java.util.List;

class RegexPathPredicate extends RegexPredicate<ChangeData> {
  RegexPathPredicate(String re) {
    super(ChangeField.PATH, re);
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    List<String> files = object.currentFilePaths();
    if (files != null) {
      return RegexListSearcher.ofStrings(getValue()).hasMatch(files);
    } else {
      // The ChangeData can't do expensive lookups right now. Bypass
      // them and include the result anyway. We might be able to do
      // a narrow later on to a smaller set.
      //
      return true;
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}
