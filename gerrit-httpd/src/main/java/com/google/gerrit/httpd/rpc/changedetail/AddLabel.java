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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.errors.InvalidLabelException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.BaseServiceImplementation.Failure;
import com.google.gerrit.reviewdb.ChangeLabel;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

public class AddLabel extends Handler<VoidResult> {

  interface Factory {
    AddLabel create(@Assisted ChangeLabel newChangeLabel);
  }

  final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  final ChangeLabel newChangeLabel;

  @Inject
  AddLabel(final ChangeControl.Factory changeControlFactory, final ReviewDb db,
      @Assisted final ChangeLabel newChangeLabel) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.newChangeLabel = newChangeLabel;
  }

  @Override
  public VoidResult call() throws Exception {
    try {
      final ChangeControl changeControl =
          changeControlFactory.controlFor(newChangeLabel.getChangeId());
      if (changeControl.canEditChangeLabels()) {
        if (ChangeLabel.LabelKey.isValid(newChangeLabel.getLabel().get())) {
          db.changeLabels().insert(Collections.singletonList(newChangeLabel));
          return VoidResult.INSTANCE;
        } else {
          throw new Failure(new InvalidLabelException());
        }
      } else {
        throw new Failure(new PermissionDeniedException(
            "User does not have permission to add labels."));
      }
    } catch (NoSuchChangeException e) {
      throw new Failure(e);
    }
  }
}
