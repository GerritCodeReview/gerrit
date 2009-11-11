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
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.CompoundKeyCommand;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

class JumpKeys {
  static void register(final Widget body) {
    final KeyCommandSet jumps = new KeyCommandSet();

    jumps.add(new KeyCommand(0, 'o', Gerrit.C.jumpAllOpen()) {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        Gerrit.display(PageLinks.ALL_OPEN, true);
      }
    });
    jumps.add(new KeyCommand(0, 'm', Gerrit.C.jumpAllMerged()) {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        Gerrit.display(PageLinks.ALL_MERGED, true);
      }
    });

    if (Gerrit.isSignedIn()) {
      jumps.add(new KeyCommand(0, 'i', Gerrit.C.jumpMine()) {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          Gerrit.display(PageLinks.MINE, true);
        }
      });
      jumps.add(new KeyCommand(0, 'd', Gerrit.C.jumpMineDrafts()) {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          Gerrit.display(PageLinks.MINE_DRAFTS, true);
        }
      });
      jumps.add(new KeyCommand(0, 's', Gerrit.C.jumpMineStarred()) {
        @Override
        public void onKeyPress(final KeyPressEvent event) {
          Gerrit.display(PageLinks.MINE_STARRED, true);
        }
      });
    }

    final KeyCommandSet jumping = new KeyCommandSet(Gerrit.C.sectionJumping());
    jumping.add(new CompoundKeyCommand(0, 'g', "", jumps));
    GlobalKey.add(body, jumping);
  }

  private JumpKeys() {
  }
}
