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

package com.google.gerrit.reviewdb;

/**
 * An abstract class to be extended either by
 * the PatchSetApproval or ChangeSetApproval
 * */
public abstract class SetApproval<T> {

  public abstract Account.Id getAccountId();

  public abstract ApprovalCategory.Id getCategoryId();

  public abstract T getSetId();

  public abstract short getValue();

  public abstract void setValue(final short v);
}
