// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.gerrit.common.ChangeHookRunner.ChangeAbandonedEvent;
import com.google.gerrit.common.ChangeHookRunner.ChangeMergedEvent;
import com.google.gerrit.common.ChangeHookRunner.CommentAddedEvent;
import com.google.gerrit.common.ChangeHookRunner.PatchSetCreatedEvent;

public interface ChangeListener {
    public void onPatchsetCreated(final PatchSetCreatedEvent patchSetCreatedEvent);

    public void onCommentAdded(final CommentAddedEvent commentAddedEvent);

    public void onChangeMerged(final ChangeMergedEvent changeMergedEvent);

    public void onChangeAbandoned(final ChangeAbandonedEvent changeAbandonedEvent);
}

