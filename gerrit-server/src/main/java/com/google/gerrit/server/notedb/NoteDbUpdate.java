// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;

/**
 * A delta to be applied to a change.
 * <p>
 * To add to the update, callers should "get" the ChangeUpdate and the
 * ChangeDraftUpdate instances and make modifications directly to those.
 * However, to commit, callers should use NoteDbUpdate.commit() to ensure that
 * if there are things to commit from both Updates that they will both be saved.
 */
public class NoteDbUpdate {
  public interface Factory {
    NoteDbUpdate create(ChangeControl ctl);
    NoteDbUpdate create(ChangeControl ctl, Date when);
    @VisibleForTesting
    NoteDbUpdate create(ChangeControl ctl, Date when,
        Comparator<String> labelNameComparator);
  }

  private final ChangeUpdate changeUpdate;
  private final ChangeDraftUpdate draftUpdate;

  @AssistedInject
  private NoteDbUpdate(ChangeUpdate.Factory updateFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      @Assisted ChangeControl ctl) {
    changeUpdate = updateFactory.create(ctl);
    draftUpdate = draftUpdateFactory.create(ctl);
  }

  @AssistedInject
  private NoteDbUpdate(ChangeUpdate.Factory updateFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      @Assisted ChangeControl ctl,
      @Assisted Date when) {
    changeUpdate = updateFactory.create(ctl, when);
    draftUpdate = draftUpdateFactory.create(ctl, when);
  }

  @AssistedInject
  private NoteDbUpdate(ChangeUpdate.Factory updateFactory,
      ChangeDraftUpdate.Factory draftUpdateFactory,
      @Assisted ChangeControl ctl,
      @Assisted Date when,
      @Assisted Comparator<String> labelNameComparator) {
    changeUpdate = updateFactory.create(ctl, when, labelNameComparator);
    draftUpdate = draftUpdateFactory.create(ctl, when);
  }

  public ChangeUpdate getChangeUpdate() {
    return changeUpdate;
  }

  public ChangeDraftUpdate getChangeDraftUpdate() {
    return draftUpdate;
  }

  public void commit() throws IOException {
    changeUpdate.commit();
    draftUpdate.commit();
  }
}
