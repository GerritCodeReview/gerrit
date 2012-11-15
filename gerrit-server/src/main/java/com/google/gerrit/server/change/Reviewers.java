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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.InvalidApiResourceException;
import com.google.gerrit.extensions.restapi.RestCollection;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.Inject;

public class Reviewers implements RestView<ChangeResource>,
    RestCollection<ChangeResource, ReviewerResource> {
  private final DynamicMap<RestView<ReviewerResource>> views;

  @Inject
  Reviewers(DynamicMap<RestView<ReviewerResource>> views) {
    this.views = views;
  }

  @Override
  public DynamicMap<RestView<ReviewerResource>> views() {
    return views;
  }

  @Override
  public RestView<ChangeResource> list() throws InvalidApiResourceException {
    throw new InvalidApiResourceException();
  }

  @Override
  public ReviewerResource parse(ChangeResource change, String id)
      throws InvalidApiResourceException, Exception {
    if (id.matches("^[0-9]+$")) {
      return new ReviewerResource(change, Account.Id.parse(id));
    }
    throw new InvalidApiResourceException(id);
  }
}
