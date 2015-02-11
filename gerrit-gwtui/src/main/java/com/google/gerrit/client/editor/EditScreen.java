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

package com.google.gerrit.client.editor;

import static com.google.gwt.dom.client.Style.Visibility.HIDDEN;
import static com.google.gwt.dom.client.Style.Visibility.VISIBLE;

import com.google.gerrit.client.DiffWebLinkInfo;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.JumpKeys;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.account.DiffPreferences;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeEditApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.diff.DiffApi;
import com.google.gerrit.client.diff.DiffInfo;
import com.google.gerrit.client.diff.FileInfo;
import com.google.gerrit.client.diff.Header;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.HttpCallback;
import com.google.gerrit.client.rpc.HttpResponse;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ClosingEvent;
import com.google.gwt.user.client.Window.ClosingHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.SafeHtml;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.ChangesHandler;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.Pos;
import net.codemirror.mode.ModeInfo;
import net.codemirror.mode.ModeInjector;
import net.codemirror.theme.ThemeLoader;

import java.util.List;

public class EditScreen extends Screen {
  interface Binder extends UiBinder<HTMLPanel, EditScreen> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;
  private final int startLine;
  private DiffPreferences prefs;
  private CodeMirror cm;
  private HttpResponse<NativeString> content;
  private EditFileInfo editFileInfo;
  private JsArray<DiffWebLinkInfo> diffLinks;

  @UiField Element header;
  @UiField Element project;
  @UiField Element filePath;
  @UiField FlowPanel linkPanel;
  @UiField Element cursLine;
  @UiField Element cursCol;
  @UiField Element dirty;
  @UiField Button close;
  @UiField Button save;
  @UiField Element editor;

  private HandlerRegistration resizeHandler;
  private HandlerRegistration closeHandler;
  private int generation;

  public EditScreen(PatchSet.Id base, Patch.Key patch, int startLine) {
    this.base = base;
    this.revision = patch.getParentKey();
    this.path = patch.get();
    this.startLine = startLine - 1;
    prefs = DiffPreferences.create(Gerrit.getAccountDiffPreference());
    add(uiBinder.createAndBindUi(this));
    addDomHandler(GlobalKey.STOP_PROPAGATION, KeyPressEvent.getType());
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setHeaderVisible(false);
    setWindowTitle(FileInfo.getFileName(path));
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    CallbackGroup group1 = new CallbackGroup();
    final CallbackGroup group2 = new CallbackGroup();
    final CallbackGroup group3 = new CallbackGroup();

    CodeMirror.initLibrary(group1.add(new AsyncCallback<Void>() {
      final AsyncCallback<Void> themeCallback = group3.addEmpty();

      @Override
      public void onSuccess(Void result) {
        // Load theme after CM library to ensure theme can override CSS.
        ThemeLoader.loadTheme(prefs.theme(), themeCallback);

        group2.done();
        group3.done();
      }

      @Override
      public void onFailure(Throwable caught) {
      }
    }));

    ChangeApi.detail(revision.getParentKey().get(),
        group1.add(new AsyncCallback<ChangeInfo>() {
          @Override
          public void onSuccess(ChangeInfo c) {
            project.setInnerText(c.project());
            SafeHtml.setInnerHTML(filePath, Header.formatPath(path, null, null));
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        }));


    if (revision.get() == 0) {
      ChangeEditApi.getMeta(revision, path,
          group1.add(new AsyncCallback<EditFileInfo>() {
            @Override
            public void onSuccess(EditFileInfo editInfo) {
              editFileInfo = editInfo;
            }

            @Override
            public void onFailure(Throwable e) {
            }
          }));
    } else {
      // TODO(davido): We probably want to create dedicated GET EditScreenMeta
      // REST endpoint. Abuse GET diff for now, as it retrieves links we need.
      DiffApi.diff(revision, path)
        .base(base)
        .webLinksOnly()
        .get(group1.add(new AsyncCallback<DiffInfo>() {
          @Override
          public void onSuccess(DiffInfo diffInfo) {
            diffLinks = diffInfo.web_links();
          }

          @Override
          public void onFailure(Throwable e) {
          }
      }));
    }

    ChangeEditApi.get(revision, path,
        group2.add(new HttpCallback<NativeString>() {
          final AsyncCallback<Void> modeCallback = group3.addEmpty();

          @Override
          public void onSuccess(HttpResponse<NativeString> fc) {
            content = fc;
            if (prefs.syntaxHighlighting()) {
              injectMode(fc.getContentType(), modeCallback);
            } else {
              modeCallback.onSuccess(null);
            }
          }

          @Override
          public void onFailure(Throwable e) {
            // "Not Found" means it's a new file.
            if (RestApi.isNotFound(e)) {
              content = null;
              modeCallback.onSuccess(null);
            } else {
              GerritCallback.showFailure(e);
            }
          }
        }));

    group3.addListener(new ScreenLoadCallback<Void>(this) {
      @Override
      protected void preDisplay(Void result) {
        initEditor(content);
        content = null;

        renderLinks(editFileInfo, diffLinks);
        editFileInfo = null;
        diffLinks = null;
      }
    });
    group1.done();
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    cm.addKeyMap(KeyMap.create()
        .on("Ctrl-L", gotoLine())
        .on("Cmd-L", gotoLine()));
  }

  private Runnable gotoLine() {
    return new Runnable() {
      @Override
      public void run() {
        String n = Window.prompt(EditConstants.I.gotoLineNumber(), "");
        if (n != null) {
          try {
            int line = Integer.parseInt(n);
            line--;
            if (line >= 0) {
              cm.scrollToLine(line);
            }
          } catch (NumberFormatException e) {
            // ignore non valid numbers
            // We don't want to popup another ugly dialog just to say
            // "The number you've provided is invalid, try again"
          }
        }
      }
    };
  }

  @Override
  public void onShowView() {
    super.onShowView();
    Window.enableScrolling(false);
    JumpKeys.enable(false);
    if (prefs.hideTopMenu()) {
      Gerrit.setHeaderVisible(false);
    }
    resizeHandler = Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        cm.adjustHeight(header.getOffsetHeight());
      }
    });
    closeHandler = Window.addWindowClosingHandler(new ClosingHandler() {
      @Override
      public void onWindowClosing(ClosingEvent event) {
        if (!cm.isClean(generation)) {
          event.setMessage(EditConstants.I.closeUnsavedChanges());
        }
      }
    });

    generation = cm.changeGeneration(true);
    setClean(true);
    cm.on(new ChangesHandler() {
      @Override
      public void handle(CodeMirror cm) {
        setClean(cm.isClean(generation));
      }
    });

    cm.adjustHeight(header.getOffsetHeight());
    cm.on("cursorActivity", updateCursorPosition());
    cm.extras().showTabs(prefs.showTabs());
    cm.extras().lineLength(
        Patch.COMMIT_MSG.equals(path) ? 72 : prefs.lineLength());
    cm.refresh();
    cm.focus();

    if (startLine > 0) {
      cm.scrollToLine(startLine);
    }
    updateActiveLine();
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (cm != null) {
      cm.getWrapperElement().removeFromParent();
    }
    if (resizeHandler != null) {
      resizeHandler.removeHandler();
    }
    if (closeHandler != null) {
      closeHandler.removeHandler();
    }
    Window.enableScrolling(true);
    Gerrit.setHeaderVisible(true);
    JumpKeys.enable(true);
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    save().run();
  }

  @UiHandler("close")
  void onClose(@SuppressWarnings("unused") ClickEvent e) {
    if (cm.isClean(generation)
        || Window.confirm(EditConstants.I.cancelUnsavedChanges())) {
      upToChange();
    }
  }

  private void upToChange() {
    Gerrit.display(PageLinks.toChangeInEditMode(revision.getParentKey()));
  }

  private void initEditor(HttpResponse<NativeString> file) {
    ModeInfo mode = null;
    String content = "";
    if (file != null) {
      content = file.getResult().asString();
      if (prefs.syntaxHighlighting()) {
        mode = ModeInfo.findMode(file.getContentType(), path);
      }
    }
    cm = CodeMirror.create(editor, Configuration.create()
        .set("value", content)
        .set("readOnly", false)
        .set("cursorBlinkRate", 0)
        .set("cursorHeight", 0.85)
        .set("lineNumbers", true)
        .set("tabSize", prefs.tabSize())
        .set("lineWrapping", false)
        .set("scrollbarStyle", "overlay")
        .set("styleSelectedText", true)
        .set("showTrailingSpace", true)
        .set("keyMap", "default")
        .set("theme", prefs.theme().name().toLowerCase())
        .set("mode", mode != null ? mode.mode() : null));
    cm.addKeyMap(KeyMap.create()
        .on("Cmd-S", save())
        .on("Ctrl-S", save()));
  }

  private void renderLinks(EditFileInfo editInfo,
      JsArray<DiffWebLinkInfo> diffLinks) {
    renderLinksToDiff();

    if (editInfo != null) {
      renderLinks(Natives.asList(editInfo.web_links()));
    } else if (diffLinks != null) {
      renderLinks(Natives.asList(diffLinks));
    }
  }

  private void renderLinks(List<DiffWebLinkInfo> links) {
    if (links != null) {
      for (DiffWebLinkInfo webLink : links) {
        linkPanel.add(webLink.toAnchor());
      }
    }
  }

  private void renderLinksToDiff() {
    InlineHyperlink sbs = new InlineHyperlink();
    sbs.setHTML(new ImageResourceRenderer()
        .render(Gerrit.RESOURCES.sideBySideDiff()));
    sbs.setTargetHistoryToken(
        Dispatcher.toPatch("sidebyside", base, new Patch.Key(revision, path)));
    sbs.setTitle(PatchUtil.C.sideBySideDiff());
    linkPanel.add(sbs);

    InlineHyperlink unified = new InlineHyperlink();
    unified.setHTML(new ImageResourceRenderer()
        .render(Gerrit.RESOURCES.unifiedDiff()));
    sbs.setTargetHistoryToken(
        Dispatcher.toPatch("unified", base, new Patch.Key(revision, path)));
    unified.setTitle(PatchUtil.C.unifiedDiff());
    linkPanel.add(unified);
  }

  private Runnable updateCursorPosition() {
    return new Runnable() {
      @Override
      public void run() {
        // The rendering of active lines has to be deferred. Reflow
        // caused by adding and removing styles chokes Firefox when arrow
        // key (or j/k) is held down. Performance on Chrome is fine
        // without the deferral.
        //
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
          @Override
          public void execute() {
            cm.operation(new Runnable() {
              @Override
              public void run() {
                updateActiveLine();
              }
            });
          }
        });
      }
    };
  }

  private void updateActiveLine() {
    Pos p = cm.getCursor("end");
    cursLine.setInnerText(Integer.toString(p.line() + 1));
    cursCol.setInnerText(Integer.toString(p.ch() + 1));
    cm.extras().activeLine(cm.getLineHandleVisualStart(p.line()));
  }

  private void setClean(boolean clean) {
    save.setEnabled(!clean);
    dirty.getStyle().setVisibility(!clean ? VISIBLE : HIDDEN);
  }

  private Runnable save() {
    return new Runnable() {
      @Override
      public void run() {
        if (!cm.isClean(generation)) {
          String text = cm.getValue();
          final int g = cm.changeGeneration(false);
          ChangeEditApi.put(revision.getParentKey().get(), path, text,
              new GerritCallback<VoidResult>() {
                @Override
                public void onSuccess(VoidResult result) {
                  generation = g;
                  setClean(cm.isClean(g));
                }
              });
        }
      }
    };
  }

  private void injectMode(String type, AsyncCallback<Void> cb) {
    new ModeInjector().add(type).inject(cb);
  }
}
