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

package com.google.gwtexpui.globalkey.client;

import com.google.gwt.event.dom.client.KeyPressEvent;

public final class CompoundKeyCommand extends KeyCommand {
  final KeyCommandSet set;

  public CompoundKeyCommand(int mask, char key, String help, KeyCommandSet s) {
    super(mask, key, help);
    set = s;
  }

  public CompoundKeyCommand(int mask, int key, String help, KeyCommandSet s) {
    super(mask, key, help);
    set = s;
  }

  public KeyCommandSet getSet() {
    return set;
  }

  @Override
  public void onKeyPress(final KeyPressEvent event) {
    GlobalKey.temporaryWithTimeout(set);
  }
}
