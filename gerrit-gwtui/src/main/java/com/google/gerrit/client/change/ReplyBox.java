// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import static com.google.gwt.event.dom.client.KeyCodes.KEY_ENTER;
import static com.google.gwt.event.dom.client.KeyCodes.KEY_MAC_ENTER;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.ReviewInput;
import com.google.gerrit.client.changes.ReviewInput.DraftHandling;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.info.ChangeInfo.LabelInfo;
import com.google.gerrit.client.info.ChangeInfo.MessageInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReplyBox extends Composite {
  interface Binder extends UiBinder<HTMLPanel, ReplyBox> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Styles extends CssResource {
    String label_name();

    String label_value();

    String label_help();
  }

  private final CommentLinkProcessor clp;
  private final Project.NameKey project;
  private final PatchSet.Id psId;
  private final String revision;
  private ReviewInput in = ReviewInput.create();
  private int labelHelpColumn;
  private LocalComments lc;

  @UiField Styles style;
  @UiField TextArea message;
  @UiField Element labelsParent;
  @UiField Grid labelsTable;
  @UiField Button post;
  @UiField Button cancel;
  @UiField ScrollPanel commentsPanel;
  @UiField FlowPanel comments;

  ReplyBox(
      CommentLinkProcessor clp,
      @Nullable Project.NameKey project,
      PatchSet.Id psId,
      String revision,
      NativeMap<LabelInfo> all,
      NativeMap<JsArrayString> permitted) {
    this.clp = clp;
    this.project = project;
    this.psId = psId;
    this.revision = revision;
    this.lc = new LocalComments(project, psId.getParentKey());
    initWidget(uiBinder.createAndBindUi(this));

    List<String> names = new ArrayList<>(permitted.keySet());
    if (names.isEmpty()) {
      UIObject.setVisible(labelsParent, false);
    } else {
      Collections.sort(names);
      renderLabels(names, all, permitted);
    }

    addDomHandler(
        new KeyDownHandler() {
          @Override
          public void onKeyDown(KeyDownEvent e) {
            e.stopPropagation();
            if ((e.getNativeKeyCode() == KEY_ENTER || e.getNativeKeyCode() == KEY_MAC_ENTER)
                && (e.isControlKeyDown() || e.isMetaKeyDown())) {
              e.preventDefault();
              if (post.isEnabled()) {
                onPost(null);
              }
            }
          }
        },
        KeyDownEvent.getType());
    addDomHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent e) {
            e.stopPropagation();
          }
        },
        KeyPressEvent.getType());
  }

  @Override
  protected void onLoad() {
    commentsPanel.setVisible(false);
    post.setEnabled(false);
    if (lc.hasReplyComment()) {
      message.setText(lc.getReplyComment());
      lc.removeReplyComment();
    }
    ChangeApi.drafts(Project.NameKey.asStringOrNull(project), psId.getParentKey().get())
        .get(
            new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
              @Override
              public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
                displayComments(result);
                post.setEnabled(true);
              }

              @Override
              public void onFailure(Throwable caught) {
                post.setEnabled(true);
              }
            });

    Scheduler.get()
        .scheduleDeferred(
            new ScheduledCommand() {
              @Override
              public void execute() {
                message.setFocus(true);
              }
            });
    Scheduler.get()
        .scheduleFixedDelay(
            new RepeatingCommand() {
              @Override
              public boolean execute() {
                String t = message.getText();
                if (t != null) {
                  message.setCursorPos(t.length());
                }
                return false;
              }
            },
            0);
  }

  @UiHandler("post")
  void onPost(@SuppressWarnings("unused") ClickEvent e) {
    postReview();
  }

  void quickApprove(ReviewInput quickApproveInput) {
    in.mergeLabels(quickApproveInput);
    postReview();
  }

  boolean hasMessage() {
    return !message.getText().trim().isEmpty();
  }

  private void postReview() {
    in.message(message.getText().trim());
    // Don't send any comments in the request; just publish everything, even if
    // e.g. a draft was modified in another tab since we last looked it up.
    in.drafts(DraftHandling.PUBLISH_ALL_REVISIONS);
    in.prePost();
    ChangeApi.revision(Project.NameKey.asStringOrNull(project), psId.getParentKey().get(), revision)
        .view("review")
        .post(
            in,
            new GerritCallback<ReviewInput>() {
              @Override
              public void onSuccess(ReviewInput result) {
                Gerrit.display(PageLinks.toChange(psId));
              }

              @Override
              public void onFailure(final Throwable caught) {
                if (RestApi.isNotSignedIn(caught)) {
                  lc.setReplyComment(message.getText());
                }
                super.onFailure(caught);
              }
            });
    hide();
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    message.setText("");
    hide();
  }

  void replyTo(MessageInfo msg) {
    if (msg.message() != null) {
      String t = message.getText();
      String m = quote(removePatchSetHeaderLine(msg.message()));
      if (t == null || t.isEmpty()) {
        t = m;
      } else if (t.endsWith("\n\n")) {
        t += m;
      } else if (t.endsWith("\n")) {
        t += "\n" + m;
      } else {
        t += "\n\n" + m;
      }
      message.setText(t);
    }
  }

  private static String removePatchSetHeaderLine(String msg) {
    msg = msg.trim();
    if (msg.startsWith("Patch Set ")) {
      int i = msg.indexOf('\n');
      if (i > 0) {
        msg = msg.substring(i + 1).trim();
      }
    }
    return msg;
  }

  public static String quote(String msg) {
    msg = msg.trim();
    StringBuilder quotedMsg = new StringBuilder();
    for (String line : msg.split("\\n")) {
      line = line.trim();
      while (line.length() > 67) {
        int i = line.lastIndexOf(' ', 67);
        if (i < 50) {
          i = line.indexOf(' ', 67);
        }
        if (i > 0) {
          quotedMsg.append(" > ").append(line.substring(0, i)).append("\n");
          line = line.substring(i + 1);
        } else {
          break;
        }
      }
      quotedMsg.append(" > ").append(line).append("\n");
    }
    quotedMsg.append("\n");
    return quotedMsg.toString();
  }

  private void hide() {
    for (Widget w = getParent(); w != null; w = w.getParent()) {
      if (w instanceof PopupPanel) {
        ((PopupPanel) w).hide();
        break;
      }
    }
  }

  private void renderLabels(
      List<String> names, NativeMap<LabelInfo> all, NativeMap<JsArrayString> permitted) {
    TreeSet<Short> values = new TreeSet<>();
    List<LabelAndValues> labels = new ArrayList<>(permitted.size());
    for (String id : names) {
      JsArrayString p = permitted.get(id);
      if (p != null) {
        if (!all.containsKey(id)) {
          continue;
        }
        Set<Short> a = new TreeSet<>();
        for (int i = 0; i < p.length(); i++) {
          a.add(LabelInfo.parseValue(p.get(i)));
        }
        labels.add(new LabelAndValues(all.get(id), a));
        values.addAll(a);
      }
    }
    List<Short> columns = new ArrayList<>(values);

    labelsTable.resize(1 + labels.size(), 2 + values.size());
    for (int c = 0; c < columns.size(); c++) {
      labelsTable.setText(0, 1 + c, LabelValue.formatValue(columns.get(c)));
      labelsTable.getCellFormatter().setStyleName(0, 1 + c, style.label_value());
    }

    List<LabelAndValues> checkboxes = new ArrayList<>(labels.size());
    int row = 1;
    for (LabelAndValues lv : labels) {
      if (isCheckBox(lv.info.valueSet())) {
        checkboxes.add(lv);
      } else {
        renderRadio(row++, columns, lv);
      }
    }
    for (LabelAndValues lv : checkboxes) {
      renderCheckBox(row++, lv);
    }
  }

  private Short normalizeDefaultValue(Short defaultValue, Set<Short> permittedValues) {
    Short pmin = Collections.min(permittedValues);
    Short pmax = Collections.max(permittedValues);
    Short dv = defaultValue;
    if (dv > pmax) {
      dv = pmax;
    } else if (dv < pmin) {
      dv = pmin;
    }
    return dv;
  }

  private void renderRadio(int row, List<Short> columns, LabelAndValues lv) {
    String id = lv.info.name();
    Short dv = normalizeDefaultValue(lv.info.defaultValue(), lv.permitted);

    labelHelpColumn = 1 + columns.size();
    labelsTable.setText(row, 0, id);

    CellFormatter fmt = labelsTable.getCellFormatter();
    fmt.setStyleName(row, 0, style.label_name());
    fmt.setStyleName(row, labelHelpColumn, style.label_help());

    ApprovalInfo self =
        Gerrit.isSignedIn() ? lv.info.forUser(Gerrit.getUserAccount().getId().get()) : null;

    final LabelRadioGroup group = new LabelRadioGroup(row, id, lv.permitted.size());
    for (int i = 0; i < columns.size(); i++) {
      Short v = columns.get(i);
      if (lv.permitted.contains(v)) {
        String text = lv.info.valueText(LabelValue.formatValue(v));
        LabelRadioButton b = new LabelRadioButton(group, text, v);
        if ((self != null && v == self.value()) || (self == null && v.equals(dv))) {
          b.setValue(true);
          group.select(b);
          in.label(group.label, v);
          labelsTable.setText(row, labelHelpColumn, b.text);
        }
        group.buttons.add(b);
        labelsTable.setWidget(row, 1 + i, b);
      }
    }
  }

  private void renderCheckBox(int row, LabelAndValues lv) {
    ApprovalInfo self =
        Gerrit.isSignedIn() ? lv.info.forUser(Gerrit.getUserAccount().getId().get()) : null;

    final String id = lv.info.name();
    final CheckBox b = new CheckBox();
    b.setText(id);
    b.setEnabled(lv.permitted.contains((short) 1));
    if (self != null && self.value() == 1) {
      b.setValue(true);
    }
    b.addValueChangeHandler(
        new ValueChangeHandler<Boolean>() {
          @Override
          public void onValueChange(ValueChangeEvent<Boolean> event) {
            in.label(id, event.getValue() ? (short) 1 : (short) 0);
          }
        });
    b.setStyleName(style.label_name());
    labelsTable.setWidget(row, 0, b);

    CellFormatter fmt = labelsTable.getCellFormatter();
    fmt.setStyleName(row, labelHelpColumn, style.label_help());
    labelsTable.setText(row, labelHelpColumn, lv.info.valueText("+1"));
  }

  private static boolean isCheckBox(Set<Short> values) {
    return values.size() == 2 && values.contains((short) 0) && values.contains((short) 1);
  }

  private void displayComments(NativeMap<JsArray<CommentInfo>> m) {
    comments.clear();

    JsArray<CommentInfo> l = m.get(Patch.COMMIT_MSG);
    if (l != null) {
      comments.add(
          new FileComments(
              clp, project, psId, Util.C.commitMessage(), copyPath(Patch.COMMIT_MSG, l)));
    }
    l = m.get(Patch.MERGE_LIST);
    if (l != null) {
      comments.add(
          new FileComments(
              clp, project, psId, Util.C.commitMessage(), copyPath(Patch.MERGE_LIST, l)));
    }

    List<String> paths = new ArrayList<>(m.keySet());
    Collections.sort(paths);

    for (String path : paths) {
      if (!Patch.isMagic(path)) {
        comments.add(new FileComments(clp, project, psId, path, copyPath(path, m.get(path))));
      }
    }

    commentsPanel.setVisible(comments.getWidgetCount() > 0);
  }

  private static List<CommentInfo> copyPath(String path, JsArray<CommentInfo> l) {
    for (int i = 0; i < l.length(); i++) {
      l.get(i).path(path);
    }
    return Natives.asList(l);
  }

  private static class LabelAndValues {
    final LabelInfo info;
    final Set<Short> permitted;

    LabelAndValues(LabelInfo info, Set<Short> permitted) {
      this.info = info;
      this.permitted = permitted;
    }
  }

  private class LabelRadioGroup {
    final int row;
    final String label;
    final List<LabelRadioButton> buttons;
    LabelRadioButton selected;

    LabelRadioGroup(int row, String label, int cnt) {
      this.row = row;
      this.label = label;
      this.buttons = new ArrayList<>(cnt);
    }

    void select(LabelRadioButton b) {
      selected = b;
      labelsTable.setText(row, labelHelpColumn, b.text);
    }
  }

  private class LabelRadioButton extends RadioButton
      implements ValueChangeHandler<Boolean>, ClickHandler, MouseOverHandler, MouseOutHandler {
    private final LabelRadioGroup group;
    private final String text;
    private final short value;

    LabelRadioButton(LabelRadioGroup group, String text, short value) {
      super(group.label);
      this.group = group;
      this.text = text;
      this.value = value;
      addValueChangeHandler(this);
      addClickHandler(this);
      addMouseOverHandler(this);
      addMouseOutHandler(this);
    }

    @Override
    public void onValueChange(ValueChangeEvent<Boolean> event) {
      if (event.getValue()) {
        select();
      }
    }

    @Override
    public void onClick(ClickEvent event) {
      select();
    }

    void select() {
      group.select(this);
      in.label(group.label, value);
    }

    @Override
    public void onMouseOver(MouseOverEvent event) {
      labelsTable.setText(group.row, labelHelpColumn, text);
    }

    @Override
    public void onMouseOut(MouseOutEvent event) {
      LabelRadioButton b = group.selected;
      String s = b != null ? b.text : "";
      labelsTable.setText(group.row, labelHelpColumn, s);
    }
  }
}
