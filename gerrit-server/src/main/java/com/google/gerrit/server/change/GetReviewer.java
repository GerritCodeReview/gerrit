// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class GetReviewer implements RestReadView<ReviewerResource> {
  private final ReviewerJson json;

  @Inject
  GetReviewer(ReviewerJson json) {
    this.json = json;
  }

  @Override
  public List<ReviewerInfo> apply(ReviewerResource rsrc) throws OrmException {
    return json.format(rsrc);
  }
}
