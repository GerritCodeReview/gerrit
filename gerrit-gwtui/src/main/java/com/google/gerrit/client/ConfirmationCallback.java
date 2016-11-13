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

package com.google.gerrit.client;

/**
 * Interface that a caller must implement to react on the result of a {@link ConfirmationDialog}.
 */
public abstract class ConfirmationCallback {

  /**
   * Called when the {@link ConfirmationDialog} is finished with OK. To be overwritten by
   * subclasses.
   */
  public abstract void onOk();

  /**
   * Called when the {@link ConfirmationDialog} is finished with Cancel. To be overwritten by
   * subclasses.
   */
  public void onCancel() {}
}
