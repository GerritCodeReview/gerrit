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
import com.google.gwt.event.dom.client.KeyPressHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KeyCommandSet implements KeyPressHandler {
  private final Map<Integer, KeyCommand> map;
  private List<KeyCommandSet> sets;
  private String name;

  public KeyCommandSet() {
    this("");
  }

  public KeyCommandSet(String setName) {
    map = new HashMap<>();
    name = setName;
  }

  public String getName() {
    return name;
  }

  public void setName(String setName) {
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

  public void add(KeyCommand k) {
    assert !map.containsKey(k.keyMask)
        : "Key " + k.describeKeyStroke().asString() + " already registered";
    if (!map.containsKey(k.keyMask)) {
      map.put(k.keyMask, k);
    }
  }

  public void remove(KeyCommand k) {
    assert map.get(k.keyMask) == k;
    map.remove(k.keyMask);
  }

  public void add(KeyCommandSet set) {
    if (sets == null) {
      sets = new ArrayList<>();
    }
    assert !sets.contains(set);
    sets.add(set);
    for (KeyCommand k : set.map.values()) {
      add(k);
    }
  }

  public void remove(KeyCommandSet set) {
    assert sets != null;
    assert sets.contains(set);
    sets.remove(set);
    for (KeyCommand k : set.map.values()) {
      remove(k);
    }
  }

  public void filter(KeyCommandFilter filter) {
    if (sets != null) {
      for (KeyCommandSet s : sets) {
        s.filter(filter);
      }
    }
    for (Iterator<KeyCommand> i = map.values().iterator(); i.hasNext(); ) {
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
    return sets != null ? sets : Collections.<KeyCommandSet>emptyList();
  }

  @Override
  public void onKeyPress(KeyPressEvent event) {
    final KeyCommand k = map.get(toMask(event));
    if (k != null) {
      event.preventDefault();
      event.stopPropagation();
      k.onKeyPress(event);
    }
  }

  static int toMask(KeyPressEvent event) {
    int mask = event.getUnicodeCharCode();
    if (mask == 0) {
      mask = event.getNativeEvent().getKeyCode();
    }
    if (event.isControlKeyDown()) {
      mask |= KeyCommand.M_CTRL;
    }
    if (event.isMetaKeyDown()) {
      mask |= KeyCommand.M_META;
    }
    return mask;
  }
}
