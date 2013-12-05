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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo.ApprovalInfo;
import com.google.gerrit.client.changes.ChangeInfo.LabelInfo;
import com.google.gerrit.client.changes.ChangeInfo.MessageInfo;
import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.ReviewInput;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.RepeatingCommand;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLPanel;
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

class ReplyBox extends Composite {
  private static final String CODE_REVIEW = "Code-Review";

  interface Binder extends UiBinder<HTMLPanel, ReplyBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Styles extends CssResource {
    String label_name();
    String label_value();
  }

  private final CommentLinkProcessor clp;
  private final PatchSet.Id psId;
  private final String revision;
  private ReviewInput in = ReviewInput.create();
  private Runnable lgtm;

  @UiField Styles style;
  @UiField TextArea message;
  @UiField Element labelsParent;
  @UiField Grid labelsTable;
  @UiField Button post;
  @UiField CheckBox email;
  @UiField Button cancel;
  @UiField ScrollPanel commentsPanel;
  @UiField FlowPanel comments;

  ReplyBox(
      CommentLinkProcessor clp,
      PatchSet.Id psId,
      String revision,
      NativeMap<LabelInfo> all,
      NativeMap<JsArrayString> permitted) {
    this.clp = clp;
    this.psId = psId;
    this.revision = revision;
    initWidget(uiBinder.createAndBindUi(this));

    List<String> names = new ArrayList<String>(permitted.keySet());
    if (names.isEmpty()) {
      UIObject.setVisible(labelsParent, false);
    } else {
      Collections.sort(names);
      renderLabels(names, all, permitted);
    }

    addDomHandler(
      new KeyPressHandler() {
        @Override
        public void onKeyPress(KeyPressEvent e) {
          e.stopPropagation();
          if ((e.getCharCode() == '\n' || e.getCharCode() == KEY_ENTER)
              && e.isControlKeyDown()) {
            e.preventDefault();
            onPost(null);
          }
        }
      },
      KeyPressEvent.getType());
  }

  @Override
  protected void onLoad() {
    commentsPanel.setVisible(false);
    post.setEnabled(false);
    CommentApi.drafts(psId, new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
        attachComments(result);
        displayComments(result);
        post.setEnabled(true);
      }

      @Override
      public void onFailure(Throwable caught) {
        post.setEnabled(true);
      }
    });

    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        Window.scrollTo(0, 0);
        message.setFocus(true);
      }});
    Scheduler.get().scheduleFixedDelay(new RepeatingCommand() {
      @Override
      public boolean execute() {
        String t = message.getText();
        if (t != null) {
          message.setCursorPos(t.length());
        }
        return false;
      }}, 0);
  }

  @UiHandler("message")
  void onMessageKey(KeyPressEvent event) {
    if (lgtm != null
        && event.getCharCode() == 'M'
        && message.getValue().equals("LGT")) {
      Scheduler.get().scheduleDeferred(new ScheduledCommand() {
        @Override
        public void execute() {
          lgtm.run();
        }
      });
    }
  }

  @UiHandler("email")
  void onEmail(ValueChangeEvent<Boolean> e) {
    if (e.getValue()) {
      in.notify(ReviewInput.NotifyHandling.ALL);
    } else {
      in.notify(ReviewInput.NotifyHandling.NONE);
    }
  }

  @UiHandler("post")
  void onPost(ClickEvent e) {
    in.message(message.getText().trim());
    in.prePost();
    ChangeApi.revision(psId.getParentKey().get(), revision)
      .view("review")
      .post(in, new GerritCallback<ReviewInput>() {
        @Override
        public void onSuccess(ReviewInput result) {
          Gerrit.display(PageLinks.toChange(
              psId.getParentKey(),
              String.valueOf(psId.get())));
        }
      });
    hide();
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    hide();
  }

  void replyTo(MessageInfo msg) {
    if (msg.message() != null) {
      String t = message.getText();
      String m = quote(msg);
      if (t == null || t.isEmpty()) {
        t = m;
      } else if (t.endsWith("\n\n")) {
        t += m;
      } else if (t.endsWith("\n")) {
        t += "\n" + m;
      } else {
        t += "\n\n" + m;
      }
      message.setText(t + "\n\n");
    }
  }

  private static String quote(MessageInfo msg) {
    String m = msg.message().trim();
    if (m.startsWith("Patch Set ")) {
      int i = m.indexOf('\n');
      if (i > 0) {
        m = m.substring(i + 1).trim();
      }
    }
    StringBuilder quotedMsg = new StringBuilder();
    for (String line : m.split("\\n")) {
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
    return quotedMsg.toString().substring(0, quotedMsg.length() - 1); // remove last '\n'
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
      List<String> names,
      NativeMap<LabelInfo> all,
      NativeMap<JsArrayString> permitted) {
    TreeSet<Short> values = new TreeSet<Short>();
    for (String id : names) {
      JsArrayString p = permitted.get(id);
      if (p != null) {
        for (int i = 0; i < p.length(); i++) {
          values.add(LabelInfo.parseValue(p.get(i)));
        }
      }
    }
    List<Short> columns = new ArrayList<Short>(values);

    labelsTable.resize(1 + permitted.size(), 1 + values.size());
    for (int c = 0; c < columns.size(); c++) {
      labelsTable.setText(0, 1 + c, LabelValue.formatValue(columns.get(c)));
      labelsTable.getCellFormatter().setStyleName(0, 1 + c, style.label_value());
    }

    List<String> checkboxes = new ArrayList<String>(permitted.size());
    int row = 1;
    for (String id : names) {
      Set<Short> vals = all.get(id).value_set();
      if (isCheckBox(vals)) {
        checkboxes.add(id);
      } else {
        renderRadio(row++, id, columns, vals, all.get(id));
      }
    }
    for (String id : checkboxes) {
      renderCheckBox(row++, id, all.get(id));
    }
  }

  private void renderRadio(int row, final String id,
      List<Short> columns,
      Set<Short> values,
      LabelInfo info) {
    labelsTable.setText(row, 0, id);
    labelsTable.getCellFormatter().setStyleName(row, 0, style.label_name());

    ApprovalInfo self = Gerrit.isSignedIn()
        ? info.for_user(Gerrit.getUserAccount().getId().get())
        : null;

    final List<RadioButton> group = new ArrayList<RadioButton>(values.size());
    for (int i = 0; i < columns.size(); i++) {
      final Short v = columns.get(i);
      if (values.contains(v)) {
        RadioButton b = new RadioButton(id);
        b.setTitle(info.value_text(LabelValue.formatValue(v)));
        if ((self != null && v == self.value()) || (self == null && v == 0)) {
          b.setValue(true);
        }
        b.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
          @Override
          public void onValueChange(ValueChangeEvent<Boolean> event) {
            if (event.getValue()) {
              in.label(id, v);
            }
          }
        });
        group.add(b);
        labelsTable.setWidget(row, 1 + i, b);
      }
    }

    if (CODE_REVIEW.equalsIgnoreCase(id) && !group.isEmpty()) {
      lgtm = new Runnable() {
        @Override
        public void run() {
          for (int i = 0; i < group.size() - 1; i++) {
            group.get(i).setValue(false, false);
          }
          group.get(group.size() - 1).setValue(true, true);
        }
      };
    }
  }

  private void renderCheckBox(int row, final String id, LabelInfo info) {
    ApprovalInfo self = Gerrit.isSignedIn()
        ? info.for_user(Gerrit.getUserAccount().getId().get())
        : null;

    final CheckBox b = new CheckBox();
    b.setText(id);
    b.setTitle(info.value_text("+1"));
    if (self != null && self.value() == 1) {
      b.setValue(true);
    }
    b.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
      @Override
      public void onValueChange(ValueChangeEvent<Boolean> event) {
        in.label(id, event.getValue() ? (short) 1 : (short) 0);
      }
    });
    b.setStyleName(style.label_name());
    labelsTable.setWidget(row, 0, b);

    if (CODE_REVIEW.equalsIgnoreCase(id)) {
      lgtm = new Runnable() {
        @Override
        public void run() {
          b.setValue(true, true);
        }
      };
    }
  }

  private static boolean isCheckBox(Set<Short> values) {
    return values.size() == 2
        && values.contains((short) 0)
        && values.contains((short) 1);
  }

  private void attachComments(NativeMap<JsArray<CommentInfo>> result) {
    in.drafts(ReviewInput.DraftHandling.KEEP);
    in.comments(result);
  }

  private void displayComments(NativeMap<JsArray<CommentInfo>> m) {
    comments.clear();

    JsArray<CommentInfo> l = m.get(Patch.COMMIT_MSG);
    if (l != null) {
      comments.add(new FileComments(clp, psId,
          Util.C.commitMessage(), copyPath(Patch.COMMIT_MSG, l)));
    }

    List<String> paths = new ArrayList<String>(m.keySet());
    Collections.sort(paths);

    for (String path : paths) {
      if (!path.equals(Patch.COMMIT_MSG)) {
        comments.add(new FileComments(clp, psId,
            path, copyPath(path, m.get(path))));
      }
    }

    commentsPanel.setVisible(comments.getWidgetCount() > 0);
  }

  private static List<CommentInfo> copyPath(String path, JsArray<CommentInfo> l) {
    for (int i = 0; i < l.length(); i++) {
      l.get(i).setPath(path);
    }
    return Natives.asList(l);
  }
}
