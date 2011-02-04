// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.query;

import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Search;
import com.google.gerrit.server.OwnerControl;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;

/** Access control management for saved searches. */
public class SearchControl {
  public static class Factory {
    private final OwnerControl.Factory ownerControlFactory;

    @Inject
    Factory(final OwnerControl.Factory ownerControlFactory) {
      this.ownerControlFactory = ownerControlFactory;
    }

    public SearchControl controlFor(final Search.Key key)
        throws NoSuchEntityException, NoSuchProjectException,
        NoSuchGroupException {
      return new SearchControl(ownerControlFactory.controlFor(key.getOwnerId()));
    }

    public SearchControl validateFor(final Search.Key key)
        throws NoSuchEntityException, NoSuchProjectException,
        NoSuchGroupException {
      final SearchControl c = controlFor(key);
      if (!c.isVisible()) {
        throw new NoSuchEntityException();
      }
      return c;
    }
  }

  private OwnerControl oc;

  SearchControl(OwnerControl c) {
    this.oc = c;
  }

  /** Can this user see this search? */
  public boolean isVisible() {
    return oc.isVisible();
  }

  public boolean isOwner() {
    return oc.isOwner();
  }

  public boolean canEdit() {
    return oc.canEdit();
  }
}
