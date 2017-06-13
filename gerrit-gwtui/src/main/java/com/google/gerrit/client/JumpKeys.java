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

package com.google.gerrit.client;

import com.google.gerrit.common.PageLinks;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.CompoundKeyCommand;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

public class JumpKeys {
  private static HandlerRegistration activeHandler;
  private static KeyCommandSet keys;
  private static Widget bodyWidget;

  public static void enable(boolean enable) {
    if (enable && activeHandler == null) {
      activeHandler = GlobalKey.add(bodyWidget, keys);
    } else if (!enable && activeHandler != null) {
      activeHandler.removeHandler();
      activeHandler = null;
    }
  }

  static void register(Widget body) {
    final KeyCommandSet jumps = new KeyCommandSet();

    jumps.add(
        new KeyCommand(0, 'o', Gerrit.C.jumpAllOpen()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            Gerrit.display(PageLinks.toChangeQuery("status:open"));
          }
        });
    jumps.add(
        new KeyCommand(0, 'm', Gerrit.C.jumpAllMerged()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            Gerrit.display(PageLinks.toChangeQuery("status:merged"));
          }
        });
    jumps.add(
        new KeyCommand(0, 'a', Gerrit.C.jumpAllAbandoned()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            Gerrit.display(PageLinks.toChangeQuery("status:abandoned"));
          }
        });

    if (Gerrit.isSignedIn()) {
      jumps.add(
          new KeyCommand(0, 'i', Gerrit.C.jumpMine()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              Gerrit.display(PageLinks.MINE);
            }
          });
      jumps.add(
          new KeyCommand(0, 'd', Gerrit.C.jumpMineDrafts()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              Gerrit.display(PageLinks.toChangeQuery("owner:self is:draft"));
            }
          });
      jumps.add(
          new KeyCommand(0, 'c', Gerrit.C.jumpMineDraftComments()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              Gerrit.display(PageLinks.toChangeQuery("has:draft"));
            }
          });
      jumps.add(
          new KeyCommand(0, 'w', Gerrit.C.jumpMineWatched()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              Gerrit.display(PageLinks.toChangeQuery("is:watched status:open"));
            }
          });
      jumps.add(
          new KeyCommand(0, 's', Gerrit.C.jumpMineStarred()) {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              Gerrit.display(PageLinks.toChangeQuery("is:starred"));
            }
          });
    }

    keys = new KeyCommandSet(Gerrit.C.sectionJumping());
    keys.add(new CompoundKeyCommand(0, 'g', "", jumps));
    bodyWidget = body;
    activeHandler = GlobalKey.add(body, keys);
  }

  private JumpKeys() {}
}
