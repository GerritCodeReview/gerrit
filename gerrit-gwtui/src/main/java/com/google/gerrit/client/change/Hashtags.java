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
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestBox.DefaultSuggestionDisplay;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

import java.util.Iterator;

public class Hashtags extends Composite {
  interface Binder extends UiBinder<HTMLPanel, Hashtags> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Element hashtagsText;
  @UiField Button openForm;
  @UiField Element form;
  @UiField Element error;
  @UiField(provided = true)
  SuggestBox suggestBox;

  private ChangeScreen2.Style style;

  private RestHashtagsSuggestOracle hashtagsSuggestOracle;
  private HintTextBox nameTxtBox;
  private Change.Id changeId;
  private boolean submitOnSelection;

  public Hashtags() {
    hashtagsSuggestOracle = new RestHashtagsSuggestOracle();
    nameTxtBox = new HintTextBox();
    suggestBox = new SuggestBox(hashtagsSuggestOracle, nameTxtBox);
    initWidget(uiBinder.createAndBindUi(this));

    nameTxtBox.setVisibleLength(55);
    nameTxtBox.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent e) {
        submitOnSelection = false;

        if (e.getNativeEvent().getKeyCode() == KeyCodes.KEY_ESCAPE) {
          onCancel(null);
        } else if (e.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
          if (((DefaultSuggestionDisplay) suggestBox.getSuggestionDisplay())
              .isSuggestionListShowing()) {
            submitOnSelection = true;
          } else {
            onAdd(null);
          }
        }
      }
    });
    suggestBox.addSelectionHandler(new SelectionHandler<Suggestion>() {
      @Override
      public void onSelection(SelectionEvent<Suggestion> event) {
        nameTxtBox.setFocus(true);
        if (submitOnSelection) {
          onAdd(null);
        }
      }
    });
  }

  void init(ChangeScreen2.Style style){
    this.style = style;
  }

  void set(ChangeInfo info) {
    this.changeId = info.legacy_id();
    display(info);
    hashtagsSuggestOracle.setChange(changeId);
    openForm.setVisible(Gerrit.isSignedIn());
  }

  @UiHandler("openForm")
  void onOpenForm(ClickEvent e) {
    onOpenForm();
  }

  void onOpenForm() {
    UIObject.setVisible(form, true);
    UIObject.setVisible(error, false);
    openForm.setVisible(false);
    suggestBox.setFocus(true);
  }

  private void display(ChangeInfo info) {
    SafeHtmlBuilder html = formatHashtags(info);
    hashtagsText.setInnerSafeHtml(html);
  }

  private SafeHtmlBuilder formatHashtags(ChangeInfo info) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    if (info.hashtags() != null) {
      Iterator<String> itr = Natives.asList(info.hashtags()).iterator();
      while (itr.hasNext()) {
        String hashtagName = itr.next();
        html.openSpan().setAttribute("role", "listitem")
            .setStyleName(style.hashtagName()).append(hashtagName);
        html.closeSpan();
        if (itr.hasNext()) {
          html.append(' ');
        }
      }
    }
    return html;
  }

  @UiHandler("cancel")
  void onCancel(ClickEvent e) {
    openForm.setVisible(true);
    UIObject.setVisible(form, false);
    suggestBox.setFocus(false);
  }

  @UiHandler("add")
  void onAdd(ClickEvent e) {
    String reviewer = suggestBox.getText();
    if (!reviewer.isEmpty()) {
      addHashtag(reviewer);
    }
  }

  private void addHashtag(final String hashtag) {
    ChangeApi.hashTags(changeId.get()).post(
        PostInput.create(hashtag),
        new GerritCallback<PostResult>() {
          public void onSuccess(PostResult result) {
            nameTxtBox.setEnabled(true);
            if (result.error() != null) {
              UIObject.setVisible(error, true);
              error.setInnerText(result.error());
            } else {
              UIObject.setVisible(error, false);
              error.setInnerText("");
              nameTxtBox.setText("");

              if (result.hashtags() != null
                  && result.hashtags().length() > 0) {
                  updateHashtagList();
              }
            }
          }

          @Override
          public void onFailure(Throwable err) {
            UIObject.setVisible(error, true);
            error.setInnerText(err instanceof StatusCodeException
                ? ((StatusCodeException) err).getEncodedResponse()
                : err.getMessage());
            nameTxtBox.setEnabled(true);
          }
        });

  }
  protected void updateHashtagList() {
    ChangeApi.detail(changeId.get(),
        new GerritCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo result) {
            display(result);
          }
        });
  }
  public static class PostInput extends JavaScriptObject {
    public static PostInput create(String hashtag) {
      PostInput input = createObject().cast();
      input.init(hashtag);
      return input;
    }

    private native void init(String hashtag) /*-{
      this.hashtag = hashtag;
    }-*/;

    protected PostInput() {
    }
  }

  public static class PostResult extends JavaScriptObject {
    public final native JsArrayString hashtags() /*-{ return this.hashtags; }-*/;
    public final native String error() /*-{ return this.error; }-*/;

    protected PostResult() {
    }
  }
}
