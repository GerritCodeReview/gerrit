// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.index.change.ChangeField.EXACT_AUTHOR;
import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_EXACTAUTHOR;

import com.google.gerrit.server.index.change.ChangeField;
import java.util.Locale;

public class ExactAuthorPredicate extends ChangeIndexPredicate {
  public ExactAuthorPredicate(String value) {
    super(EXACT_AUTHOR, FIELD_EXACTAUTHOR, value.toLowerCase(Locale.US));
  }

  @Override
  public boolean match(ChangeData object) {
    return ChangeField.getAuthorNameAndEmail(object).contains(getValue());
  }

  @Override
  public int getCost() {
    return 1;
  }
}
