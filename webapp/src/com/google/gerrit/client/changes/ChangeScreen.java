// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.ChangeInfo;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.ui.Screen;


public class ChangeScreen extends Screen {
  private Change.Id changeId;

  public ChangeScreen(final Change.Id toShow) {
    changeId = toShow;
  }

  public ChangeScreen(final ChangeInfo c) {
    this(c.getId());
  }

  @Override
  public Object getScreenCacheToken() {
    return getClass();
  }

  @Override
  public Screen recycleThis(final Screen newScreen) {
    changeId = ((ChangeScreen) newScreen).changeId;
    return this;
  }

  @Override
  public void onLoad() {
    setTitleText("Change " + changeId.get());
  }
}
