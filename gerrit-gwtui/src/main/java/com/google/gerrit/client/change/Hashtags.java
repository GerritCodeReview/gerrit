// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.Iterator;

public class Hashtags extends Composite {

  interface Binder extends UiBinder<HTMLPanel, Hashtags> {}

  private static final int VISIBLE_LENGTH = 55;
  private static final Binder uiBinder = GWT.create(Binder.class);
  private static final String REMOVE;
  private static final String DATA_ID = "data-id";

  private PatchSet.Id psId;
  private boolean canEdit;

  static {
    REMOVE = DOM.createUniqueId().replace('-', '_');
    init(REMOVE);
  }

  private static native void init(String r) /*-{
    $wnd[r] = $entry(function(e) {
      @com.google.gerrit.client.change.Hashtags::onRemove(Lcom/google/gwt/dom/client/NativeEvent;)(e)
    });
  }-*/;

  private static void onRemove(NativeEvent event) {
    String hashtags = getDataId(event);
    if (hashtags != null) {
      final ChangeScreen screen = ChangeScreen.get(event);
      final PatchSet.Id psId = screen.getPatchSetId();
      final Project.NameKey project = null;
      ChangeApi.hashtags(psId.getParentKey().get(), Project.NameKey.asStringOrNull(project))
          .post(
              PostInput.create(null, hashtags),
              new GerritCallback<JavaScriptObject>() {
                @Override
                public void onSuccess(JavaScriptObject result) {
                  if (screen.isCurrentView()) {
                    Gerrit.display(PageLinks.toChange(psId));
                  }
                }
              });
    }
  }

  private static String getDataId(NativeEvent event) {
    Element e = event.getEventTarget().cast();
    while (e != null) {
      String v = e.getAttribute(DATA_ID);
      if (!v.isEmpty()) {
        return v;
      }
      e = e.getParentElement();
    }
    return null;
  }

  @UiField Element hashtagsText;
  @UiField Image addHashtagIcon;
  @UiField Element form;
  @UiField Element error;
  @UiField NpTextBox hashtagTextBox;

  private ChangeScreen.Style style;
  private Change.Id changeId;
  private Project.NameKey project;

  public Hashtags() {

    initWidget(uiBinder.createAndBindUi(this));

    hashtagTextBox.setVisibleLength(VISIBLE_LENGTH);
    hashtagTextBox.addKeyDownHandler(
        new KeyDownHandler() {
          @Override
          public void onKeyDown(KeyDownEvent e) {
            if (e.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
              onCancel(null);
            } else if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
              onAdd(null);
            }
          }
        });

    addHashtagIcon.addDomHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            onOpenForm();
          }
        },
        ClickEvent.getType());
  }

  void init(ChangeScreen.Style style) {
    this.style = style;
  }

  void set(ChangeInfo info, String revision) {
    psId = new PatchSet.Id(info.legacyId(), info.revisions().get(revision)._number());
    project = info.projectNameKey();

    canEdit = info.hasActions() && info.actions().containsKey("hashtags");
    this.changeId = info.legacyId();
    display(info);
    addHashtagIcon.setVisible(canEdit);
  }

  void onOpenForm() {
    UIObject.setVisible(form, true);
    UIObject.setVisible(error, false);
    addHashtagIcon.setVisible(false);
    hashtagTextBox.setFocus(true);
  }

  private void display(ChangeInfo info) {
    hashtagsText.setInnerSafeHtml(formatHashtags(info));
  }

  private void display(JsArrayString hashtags) {
    hashtagsText.setInnerSafeHtml(formatHashtags(hashtags));
  }

  private SafeHtmlBuilder formatHashtags(ChangeInfo info) {
    if (info.hashtags() != null) {
      return formatHashtags(info.hashtags());
    }
    return new SafeHtmlBuilder();
  }

  private SafeHtmlBuilder formatHashtags(JsArrayString hashtags) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    Iterator<String> itr = Natives.asList(hashtags).iterator();
    while (itr.hasNext()) {
      String hashtagName = itr.next();
      html.openSpan()
          .setAttribute(DATA_ID, hashtagName)
          .setStyleName(style.hashtagName())
          .openAnchor()
          .setAttribute("href", "#" + PageLinks.toChangeQuery("hashtag:\"" + hashtagName + "\""))
          .setAttribute("role", "listitem")
          .openSpan()
          .setStyleName(style.hashtagIcon())
          .append(new ImageResourceRenderer().render(Gerrit.RESOURCES.hashtag()))
          .closeSpan()
          .append(" ")
          .append(hashtagName)
          .closeAnchor();
      if (canEdit) {
        html.openElement("button")
            .setAttribute("title", "Remove hashtag")
            .setAttribute("onclick", REMOVE + "(event)")
            .append("×")
            .closeElement("button");
      }
      html.closeSpan();
      if (itr.hasNext()) {
        html.append(' ');
      }
    }
    return html;
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    addHashtagIcon.setVisible(true);
    UIObject.setVisible(form, false);
    hashtagTextBox.setFocus(false);
  }

  @UiHandler("add")
  void onAdd(@SuppressWarnings("unused") ClickEvent e) {
    String hashtag = hashtagTextBox.getText();
    if (!hashtag.isEmpty()) {
      addHashtag(hashtag);
    }
  }

  private void addHashtag(final String hashtags) {
    ChangeApi.hashtags(changeId.get(), Project.NameKey.asStringOrNull(project))
        .post(
            PostInput.create(hashtags, null),
            new GerritCallback<JsArrayString>() {
              @Override
              public void onSuccess(JsArrayString result) {
                Gerrit.display(
                    PageLinks.toChange(project, psId.getParentKey(), String.valueOf(psId.get())));
              }

              @Override
              public void onFailure(Throwable err) {
                UIObject.setVisible(error, true);
                error.setInnerText(
                    err instanceof StatusCodeException
                        ? ((StatusCodeException) err).getEncodedResponse()
                        : err.getMessage());
                hashtagTextBox.setEnabled(true);
              }
            });
  }

  public static class PostInput extends JavaScriptObject {
    public static PostInput create(String add, String remove) {
      PostInput input = createObject().cast();
      input.init(toJsArrayString(add), toJsArrayString(remove));
      return input;
    }

    private static JsArrayString toJsArrayString(String commaSeparated) {
      if (commaSeparated == null || commaSeparated.equals("")) {
        return null;
      }
      JsArrayString array = JsArrayString.createArray().cast();
      for (String hashtag : commaSeparated.split(",")) {
        array.push(hashtag.trim());
      }
      return array;
    }

    private native void init(JsArrayString add, JsArrayString remove) /*-{
      this.add = add;
      this.remove = remove;
    }-*/;

    protected PostInput() {}
  }
}
