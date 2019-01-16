// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.Locale;

public class FooterPredicate extends ChangeIndexPredicate {
  private static String clean(String value) {
    int indexEquals = value.indexOf('=');
    int indexColon = value.indexOf(':');

    // footer key cannot contain '='
    if (indexEquals > 0 && (indexEquals < indexColon || indexColon < 0)) {
      value = value.substring(0, indexEquals) + ": " + value.substring(indexEquals + 1);
    }
    return value.toLowerCase(Locale.US);
  }

  FooterPredicate(String value) {
    super(ChangeField.FOOTER, clean(value));
  }

  @Override
  public boolean match(ChangeData cd) throws StorageException {
    return ChangeField.getFooters(cd).contains(value);
  }

  @Override
  public int getCost() {
    return 0;
  }
}
