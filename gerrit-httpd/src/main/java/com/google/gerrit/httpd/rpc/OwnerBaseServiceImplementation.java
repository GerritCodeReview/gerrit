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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.OwnerInfo;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Owner;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OwnerControl;
import com.google.gerrit.server.OwnerUtil;
import com.google.gerrit.server.account.NoSuchGroupException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class OwnerBaseServiceImplementation extends BaseServiceImplementation {
  private final OwnerControl.Factory ownerControlFactory;
  private final OwnerUtil ownerUtil;

  @Inject
  OwnerBaseServiceImplementation(final Provider<ReviewDb> schema,
      final Provider<IdentifiedUser> currentUser,
      final OwnerControl.Factory ownerControlFactory,
      final OwnerUtil ownerUtil) {
    super(schema, currentUser);
    this.ownerControlFactory = ownerControlFactory;
    this.ownerUtil = ownerUtil;
  }

  protected OwnerInfo getOwnerInfo(final Owner.Id id)
      throws OrmException, Failure {
    try {
      return ownerUtil.getOwnerInfo(id);
    } catch (NoSuchEntityException e) {
      throw new Failure(e);
    } catch (NoSuchAccountException e) {
      throw new Failure(e);
    } catch (NoSuchGroupException e) {
      throw new Failure(e);
    } catch (NoSuchProjectException e) {
      throw new Failure(e);
    }
  }

  protected OwnerControl validateOwnerControlFor(Owner.Id owner)
      throws Failure {
    try {
      return ownerControlFactory.validateFor(owner);
    } catch (NoSuchEntityException e) {
      throw new Failure(e);
    } catch (NoSuchGroupException e) {
      throw new Failure(e);
    } catch (NoSuchProjectException e) {
      throw new Failure(e);
    }
  }
}
