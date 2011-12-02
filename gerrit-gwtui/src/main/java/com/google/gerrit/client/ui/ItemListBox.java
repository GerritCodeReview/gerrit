// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gwt.user.client.ui.ListBox;

import java.util.ArrayList;
import java.util.List;

/** A generic ListBox which can take a list of items and
  * return the selected item.  Unless the getItem() method
  * is overriden, the item's toString() value will be used for
  * display.
  */
public class ItemListBox<I> extends ListBox {
  private List<I> items;

  public ItemListBox(List<I> items) {
    this.items = new ArrayList<I>(items.size());
    for (I i : items) {
      addItem(i);
    }
  }

  public void addItem(I i) {
    addItem(getItem(i));
    items.add(i);
  }

  public String getItem(I i) {
    return i.toString();
  }

  public I getSelectedItem() {
    return getItem(getSelectedIndex());
  }

  public I getItem(int i) {
    return items.get(i);
  }
}
