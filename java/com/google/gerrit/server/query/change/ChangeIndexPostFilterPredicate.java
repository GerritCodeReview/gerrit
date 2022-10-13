// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.index.SchemaFieldDefs.SchemaField;

/**
 * Predicate that is mapped to a field in the change index, with additional filtering done in the
 * {@code match} method.
 */
public abstract class ChangeIndexPostFilterPredicate extends ChangeIndexPredicate {
  protected ChangeIndexPostFilterPredicate(SchemaField<ChangeData, ?> def, String value) {
    super(def, value);
  }

  protected ChangeIndexPostFilterPredicate(
      SchemaField<ChangeData, ?> def, String name, String value) {
    super(def, name, value);
  }
}
