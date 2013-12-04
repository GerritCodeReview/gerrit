// Copyright (C) 2014 Digia Plc and/or its subsidiary(-ies).
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
package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractKeyNavigation {
  public enum Action { NEXT, PREV, OPEN };

  protected final KeyCommandSet keysNavigation;
  protected final KeyCommandSet keysAction;
  private HandlerRegistration regNavigation;
  private HandlerRegistration regAction;
  private final KeyCommandSet keysOpenByEnter;
  private HandlerRegistration regOpenByEnter;
  protected final Widget parent;
  private Map<Action, String> help;
  protected static boolean initialized = false;

  protected void onNext() {};
  protected void onPrev() {};
  protected void onOpen() {};

  public AbstractKeyNavigation(final Widget parent) {
    this.parent = parent;
    help = new HashMap<AbstractKeyNavigation.Action, String>();
    keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysOpenByEnter = new KeyCommandSet(Gerrit.C.sectionNavigation());
  }

  public void setKeyHelp(final Action action, final String text) {
    help.put(action, text);
  }

  public void initializeKeys() {
      keysNavigation.add(new NextKeyCommand(0, 'j', help.get(Action.NEXT)));
      keysNavigation.add(new PrevKeyCommand(0, 'k', help.get(Action.PREV)));
      keysAction.add(new OpenKeyCommand(0, 'o', help.get(Action.OPEN)));
      keysOpenByEnter.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER,
          help.get(Action.OPEN)));
  }

  public void addNavigationKey(final KeyCommand keyCommand) {
    keysNavigation.add(keyCommand);
  }

  public void setRegisterKeys(final boolean on) {
    if (on) {
      if (regNavigation == null) {
        regNavigation = GlobalKey.add(parent, keysNavigation);
      }
      if (regAction == null) {
        regAction = GlobalKey.add(parent, keysAction);
      }
      if (regOpenByEnter == null) {
        regOpenByEnter = GlobalKey.add(parent, keysOpenByEnter);
      }
    } else {
      if (regNavigation != null) {
        regNavigation.removeHandler();
        regNavigation = null;
      }

      if (regAction != null) {
        regAction.removeHandler();
        regAction = null;
      }

      if (regOpenByEnter != null) {
        regOpenByEnter.removeHandler();
        regOpenByEnter = null;
      }
    }
  }

  public void setRegisterEnter(final boolean on) {
    if (on) {
      if (keysOpenByEnter != null && regOpenByEnter == null) {
        regOpenByEnter = GlobalKey.add(parent, keysOpenByEnter);
      }
    } else {
      if (regOpenByEnter != null) {
        regOpenByEnter.removeHandler();
        regOpenByEnter = null;
      }
    }
  }

  private class PrevKeyCommand extends KeyCommand {
    public PrevKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onPrev();
    }
  }

  private class NextKeyCommand extends KeyCommand {
    public NextKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onNext();
    }
  }

  private class OpenKeyCommand extends KeyCommand {
    public OpenKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onOpen();
    }
  }

  protected class NoOpKeyCommand extends NeedsSignInKeyCommand {
    public NoOpKeyCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
    }
  }
}

