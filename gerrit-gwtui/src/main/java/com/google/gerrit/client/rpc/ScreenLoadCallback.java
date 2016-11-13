// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.NotFoundScreen;
import com.google.gerrit.client.NotSignedInDialog;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.errors.NoSuchEntityException;

/** Callback switching {@link NoSuchEntityException} to {@link NotFoundScreen} */
public abstract class ScreenLoadCallback<T> extends GerritCallback<T> {
  private final Screen screen;

  public ScreenLoadCallback(final Screen s) {
    screen = s;
  }

  @Override
  public final void onSuccess(final T result) {
    if (screen.isAttached()) {
      preDisplay(result);
      screen.display();
      postDisplay();
    }
  }

  protected abstract void preDisplay(T result);

  protected void postDisplay() {}

  @Override
  public void onFailure(final Throwable caught) {
    if (isSigninFailure(caught)) {
      new NotSignedInDialog().center();
    } else if (isNoSuchEntity(caught)) {
      Gerrit.display(screen.getToken(), new NotFoundScreen());
    } else {
      super.onFailure(caught);
    }
  }
}
