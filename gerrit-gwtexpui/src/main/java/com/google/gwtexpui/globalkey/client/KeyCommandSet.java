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

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.EventHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KeyCommandSet implements KeyDownHandler, KeyPressHandler {
  private final Map<Integer, KeyCommand> map;
  private List<KeyCommandSet> sets;
  private String name;

  public KeyCommandSet() {
    this("");
  }

  public KeyCommandSet(final String setName) {
    map = new HashMap<>();
    name = setName;
  }

  public String getName() {
    return name;
  }

  public void setName(final String setName) {
    assert setName != null;
    name = setName;
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public void add(KeyCommand a, KeyCommand b) {
    add(a);
    add(b);
    pair(a, b);
  }

  public void pair(KeyCommand a, KeyCommand b) {
    a.sibling = b;
    b.sibling = a;
  }

  public void add(final KeyCommand k) {
    assert !map.containsKey(k.keyMask)
         : "Key " + k.describeKeyStroke().asString()
         + " already registered";
    if (!map.containsKey(k.keyMask)) {
      map.put(k.keyMask, k);
    }
  }

  public void remove(final KeyCommand k) {
    assert map.get(k.keyMask) == k;
    map.remove(k.keyMask);
  }

  public void add(final KeyCommandSet set) {
    if (sets == null) {
      sets = new ArrayList<>();
    }
    assert !sets.contains(set);
    sets.add(set);
    for (final KeyCommand k : set.map.values()) {
      add(k);
    }
  }

  public void remove(final KeyCommandSet set) {
    assert sets != null;
    assert sets.contains(set);
    sets.remove(set);
    for (final KeyCommand k : set.map.values()) {
      remove(k);
    }
  }

  public void filter(final KeyCommandFilter filter) {
    if (sets != null) {
      for (final KeyCommandSet s : sets) {
        s.filter(filter);
      }
    }
    for (final Iterator<KeyCommand> i = map.values().iterator(); i.hasNext();) {
      final KeyCommand kc = i.next();
      if (!filter.include(kc)) {
        i.remove();
      } else if (kc instanceof CompoundKeyCommand) {
        ((CompoundKeyCommand) kc).set.filter(filter);
      }
    }
  }

  public Collection<KeyCommand> getKeys() {
    return map.values();
  }

  public Collection<KeyCommandSet> getSets() {
    return sets != null ? sets : Collections.<KeyCommandSet> emptyList();
  }

  @Override
  public void onKeyDown(final KeyDownEvent event) {
    int mask = toMask(event);
    final KeyCommand k = map.get(mask);
    if (k != null) {
      // In most cases, Chrome won't fire keypress when Ctrl or Alt is in the
      // key combination, so handle the keydown instead.
      if ((mask & KeyCommand.M_CTRL) == KeyCommand.M_CTRL
          || (mask & KeyCommand.M_ALT) == KeyCommand.M_ALT) {
        k.onKeyDown(event);
      }
    }
  }

  @Override
  public void onKeyPress(final KeyPressEvent event) {
    final KeyCommand k = map.get(toMask(event));
    if (k != null) {
      event.preventDefault();
      event.stopPropagation();
      k.onKeyPress(event);
    }
  }

  private static int toMask(KeyEvent<? extends EventHandler> event, int code) {
    if (event.isControlKeyDown()) {
      code |= KeyCommand.M_CTRL;
    }
    if (event.isAltKeyDown()) {
      code |= KeyCommand.M_ALT;
    }
    if (event.isMetaKeyDown()) {
      code |= KeyCommand.M_META;
    }
    if (event.isShiftKeyDown()) {
      code |= KeyCommand.M_SHIFT;
    }
    return code;
  }

  static int toMask(KeyDownEvent event) {
    return toMask(event, event.getNativeEvent().getKeyCode());
  }

  static int toMask(KeyPressEvent event) {
    int code = event.getUnicodeCharCode();
    if (code == 0) {
      code = event.getNativeEvent().getKeyCode();
    }
    return toMask(event, code);
  }
}
