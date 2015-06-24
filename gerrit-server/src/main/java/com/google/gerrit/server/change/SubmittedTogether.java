// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

@Singleton
public class SubmittedTogether implements RestReadView<ChangeResource> {
  private final Provider<ReviewDb> dbProvider;
  private final MergeSuperSet mergeSuperSet;

  @Inject
  SubmittedTogether(Provider<ReviewDb> dbProvider,
      MergeSuperSet mergeSuperSet) {
    this.dbProvider = dbProvider;
    this.mergeSuperSet = mergeSuperSet;
  }

  @Override
  public Object apply(ChangeResource resource)
      throws AuthException, BadRequestException,
      ResourceConflictException, Exception {
    try {
      //TODO: think of return type, should be easily displayable on client side,
      // i.e. contain submittable, commit short message, project,branch
      ChangeSet cs = mergeSuperSet.completeChangeSet(dbProvider.get(),
          ChangeSet.create(resource.getChange()));
    } catch (OrmException | IOException e) {

    }

    return null;
  }
}
