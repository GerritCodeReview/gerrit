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

package com.google.gerrit.client.admin;

import com.google.gwt.editor.client.IsEditor;
import com.google.gwt.editor.client.adapters.TakesValueEditor;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.IntegerBox;
import com.google.gwt.user.client.ui.ValueBoxBase.TextAlignment;
import com.google.gwt.user.client.ui.ValueListBox;
import java.io.IOException;

abstract class RangeBox extends Composite implements IsEditor<TakesValueEditor<Integer>> {
  static final RangeRenderer rangeRenderer = new RangeRenderer();

  private static class RangeRenderer implements Renderer<Integer> {
    @Override
    public String render(Integer object) {
      if (0 <= object) {
        return "+" + object;
      }
      return String.valueOf(object);
    }

    @Override
    public void render(Integer object, Appendable appendable) throws IOException {
      appendable.append(render(object));
    }
  }

  static class List extends RangeBox {
    final ValueListBox<Integer> list;

    List() {
      list = new ValueListBox<>(rangeRenderer);
      initWidget(list);
    }

    @Override
    void setEnabled(boolean on) {
      list.getElement().setPropertyBoolean("disabled", !on);
    }

    @Override
    public TakesValueEditor<Integer> asEditor() {
      return list.asEditor();
    }
  }

  static class Box extends RangeBox {
    private final IntegerBox box;

    Box() {
      box = new IntegerBox();
      box.setVisibleLength(10);
      box.setAlignment(TextAlignment.RIGHT);
      initWidget(box);
    }

    @Override
    void setEnabled(boolean on) {
      box.getElement().setPropertyBoolean("disabled", !on);
    }

    @Override
    public TakesValueEditor<Integer> asEditor() {
      return box.asEditor();
    }
  }

  abstract void setEnabled(boolean on);
}
