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

import com.google.gwt.event.shared.HandlerRegistration;


public class GlobalKey {
  private static KeyCommandSet keyApplication;
  static KeyCommandSet keys;

  private static void init() {
    if (keys == null) {
      keys = new KeyCommandSet();
      DocWidget.get().addKeyPressHandler(keys);

      keyApplication = new KeyCommandSet(Util.C.applicationSection());
      keyApplication.add(new ShowHelpCommand());
      keys.add(keyApplication);
    }
  }

  public static HandlerRegistration addApplication(final KeyCommand key) {
    init();
    keys.add(key);
    keyApplication.add(key);

    return new HandlerRegistration() {
      @Override
      public void removeHandler() {
        keys.remove(key);
        keyApplication.add(key);
      }
    };
  }

  public static HandlerRegistration add(final KeyCommandSet set) {
    init();
    keys.add(set);

    return new HandlerRegistration() {
      @Override
      public void removeHandler() {
        keys.remove(set);
      }
    };
  }

  public static void filter(final KeyCommandFilter filter) {
    keys.filter(filter);
  }

  private GlobalKey() {
  }
}
