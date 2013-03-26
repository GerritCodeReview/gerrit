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

package com.google.gerrit.server.change;

import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

public class GetReview implements RestReadView<RevisionResource> {
  private final ChangeJson json;

  @Inject
  GetReview(ChangeJson json) {
    this.json = json
        .addOption(ListChangesOption.DETAILED_LABELS)
        .addOption(ListChangesOption.DETAILED_ACCOUNTS);
  }

  @Override
  public Object apply(RevisionResource resource) throws OrmException {
    return json.format(resource);
  }
}
