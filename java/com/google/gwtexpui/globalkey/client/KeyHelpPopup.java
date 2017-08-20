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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeyHelpPopup extends PopupPanel implements KeyPressHandler, KeyDownHandler {
  private final FocusPanel focus;

  public KeyHelpPopup() {
    super(true /* autohide */, true /* modal */);
    setStyleName(KeyResources.I.css().helpPopup());

    final Anchor closer = new Anchor(KeyConstants.I.closeButton());
    closer.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            hide();
          }
        });

    final Grid header = new Grid(1, 3);
    header.setStyleName(KeyResources.I.css().helpHeader());
    header.setText(0, 0, KeyConstants.I.keyboardShortcuts());
    header.setWidget(0, 2, closer);

    final CellFormatter fmt = header.getCellFormatter();
    fmt.addStyleName(0, 1, KeyResources.I.css().helpHeaderGlue());
    fmt.setHorizontalAlignment(0, 2, HasHorizontalAlignment.ALIGN_RIGHT);

    final Grid lists = new Grid(0, 7);
    lists.setStyleName(KeyResources.I.css().helpTable());
    populate(lists);
    lists.getCellFormatter().addStyleName(0, 3, KeyResources.I.css().helpTableGlue());

    final FlowPanel body = new FlowPanel();
    body.add(header);
    body.getElement().appendChild(DOM.createElement("hr"));
    body.add(lists);

    focus = new FocusPanel(body);
    focus.getElement().getStyle().setProperty("outline", "0px");
    focus.getElement().setAttribute("hideFocus", "true");
    focus.addKeyPressHandler(this);
    focus.addKeyDownHandler(this);
    add(focus);
  }

  @Override
  public void setVisible(boolean show) {
    super.setVisible(show);
    if (show) {
      focus.setFocus(true);
    }
  }

  @Override
  public void onKeyPress(KeyPressEvent event) {
    if (KeyCommandSet.toMask(event) == ShowHelpCommand.INSTANCE.keyMask) {
      // Block the '?' key from triggering us to show right after
      // we just hide ourselves.
      //
      event.stopPropagation();
      event.preventDefault();
    }
    hide();
  }

  @Override
  public void onKeyDown(KeyDownEvent event) {
    if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
      hide();
    }
  }

  private void populate(Grid lists) {
    int[] end = new int[5];
    int column = 0;
    for (KeyCommandSet set : combinedSetsByName()) {
      int row = end[column];
      row = formatGroup(lists, row, column, set);
      end[column] = row;
      if (column == 0) {
        column = 4;
      } else {
        column = 0;
      }
    }
  }

  /**
   * @return an ordered collection of KeyCommandSet, combining sets which share the same name, so
   *     that each set name appears at most once.
   */
  private static Collection<KeyCommandSet> combinedSetsByName() {
    LinkedHashMap<String, KeyCommandSet> byName = new LinkedHashMap<>();
    for (KeyCommandSet set : GlobalKey.active.all.getSets()) {
      KeyCommandSet v = byName.get(set.getName());
      if (v == null) {
        v = new KeyCommandSet(set.getName());
        byName.put(v.getName(), v);
      }
      v.add(set);
    }
    return byName.values();
  }

  private int formatGroup(Grid lists, int row, int col, KeyCommandSet set) {
    if (set.isEmpty()) {
      return row;
    }

    if (lists.getRowCount() < row + 1) {
      lists.resizeRows(row + 1);
    }
    lists.setText(row, col + 2, set.getName());
    lists.getCellFormatter().addStyleName(row, col + 2, KeyResources.I.css().helpGroup());
    row++;

    return formatKeys(lists, row, col, set, null);
  }

  private int formatKeys(final Grid lists, int row, int col, KeyCommandSet set, SafeHtml prefix) {
    final CellFormatter fmt = lists.getCellFormatter();
    final List<KeyCommand> keys = sort(set);
    if (lists.getRowCount() < row + keys.size()) {
      lists.resizeRows(row + keys.size());
    }

    Map<KeyCommand, Integer> rows = new HashMap<>();
    FORMAT_KEYS:
    for (int i = 0; i < keys.size(); i++) {
      final KeyCommand k = keys.get(i);
      if (rows.containsKey(k)) {
        continue;
      }

      if (k instanceof CompoundKeyCommand) {
        final SafeHtmlBuilder b = new SafeHtmlBuilder();
        b.append(k.describeKeyStroke());
        row = formatKeys(lists, row, col, ((CompoundKeyCommand) k).getSet(), b);
        continue;
      }

      for (int prior = 0; prior < i; prior++) {
        if (KeyCommand.same(keys.get(prior), k)) {
          final int r = rows.get(keys.get(prior));
          final SafeHtmlBuilder b = new SafeHtmlBuilder();
          b.append(SafeHtml.get(lists, r, col + 0));
          b.append(" ");
          b.append(KeyConstants.I.orOtherKey());
          b.append(" ");
          if (prefix != null) {
            b.append(prefix);
            b.append(" ");
            b.append(KeyConstants.I.thenOtherKey());
            b.append(" ");
          }
          b.append(k.describeKeyStroke());
          SafeHtml.set(lists, r, col + 0, b);
          rows.put(k, r);
          continue FORMAT_KEYS;
        }
      }

      SafeHtmlBuilder b = new SafeHtmlBuilder();
      String t = k.getHelpText();
      if (prefix != null) {
        b.append(prefix);
        b.append(" ");
        b.append(KeyConstants.I.thenOtherKey());
        b.append(" ");
      }
      b.append(k.describeKeyStroke());
      if (k.sibling != null) {
        b.append(" / ").append(k.sibling.describeKeyStroke());
        t += " / " + k.sibling.getHelpText();
        rows.put(k.sibling, row);
      }
      SafeHtml.set(lists, row, col + 0, b);
      lists.setText(row, col + 1, ":");
      lists.setText(row, col + 2, t);
      rows.put(k, row);

      fmt.addStyleName(row, col + 0, KeyResources.I.css().helpKeyStroke());
      fmt.addStyleName(row, col + 1, KeyResources.I.css().helpSeparator());
      row++;
    }

    return row;
  }

  private List<KeyCommand> sort(KeyCommandSet set) {
    final List<KeyCommand> keys = new ArrayList<>(set.getKeys());
    Collections.sort(
        keys,
        new Comparator<KeyCommand>() {
          @Override
          public int compare(KeyCommand arg0, KeyCommand arg1) {
            return arg0.getHelpText().compareTo(arg1.getHelpText());
          }
        });
    return keys;
  }
}
