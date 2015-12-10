// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Let users know that a review has been removed. */
public class DeleteReviewerSender extends NewChangeSender {
  public static interface Factory {
    DeleteReviewerSender create(Change.Id id);
  }

  @Inject
  public DeleteReviewerSender(EmailArguments ea, @Assisted Change.Id id)
      throws OrmException {
    super(ea, newChangeData(ea, id));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccExistingReviewers();
  }
}
