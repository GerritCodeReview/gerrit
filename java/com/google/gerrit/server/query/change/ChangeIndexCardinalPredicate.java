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
import com.google.gerrit.index.query.HasCardinality;

public class ChangeIndexCardinalPredicate extends ChangeIndexPredicate implements HasCardinality {
  protected final int cardinality;

  protected ChangeIndexCardinalPredicate(
      SchemaField<ChangeData, ?> def, String value, int cardinality) {
    super(def, value);
    this.cardinality = cardinality;
  }

  protected ChangeIndexCardinalPredicate(
      SchemaField<ChangeData, ?> def, String name, String value, int cardinality) {
    super(def, name, value);
    this.cardinality = cardinality;
  }

  @Override
  public int getCardinality() {
    return cardinality;
  }
}
