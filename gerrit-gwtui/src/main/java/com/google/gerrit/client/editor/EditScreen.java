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
import com.google.gerrit.client.account.EditPreferences;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeEditApi;
import com.google.gerrit.client.diff.DiffApi;
import com.google.gerrit.client.diff.DiffInfo;
import com.google.gerrit.client.diff.Header;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.FileInfo;
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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.KeyMapType;
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ClosingEvent;
import com.google.gwt.user.client.Window.ClosingHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ImageResourceRenderer;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import java.util.List;
import net.codemirror.addon.AddonInjector;
import net.codemirror.addon.Addons;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.ChangesHandler;
import net.codemirror.lib.CodeMirror.CommandRunner;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.KeyMap;
import net.codemirror.lib.MergeView;
import net.codemirror.lib.Pos;
import net.codemirror.mode.ModeInfo;
import net.codemirror.mode.ModeInjector;
import net.codemirror.theme.ThemeLoader;

public class EditScreen extends Screen {
  interface Binder extends UiBinder<HTMLPanel, EditScreen> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String fullWidth();

    String base();

    String hideBase();
  }

  private final Project.NameKey projectKey;
  private final PatchSet.Id revision;
  private final String path;
  private final int startLine;
  private EditPreferences prefs;
  private EditPreferencesAction editPrefsAction;
  private MergeView mv;
  private CodeMirror cmBase;
  private CodeMirror cmEdit;
  private HttpResponse<NativeString> content;
  private HttpResponse<NativeString> baseContent;
  private EditFileInfo editFileInfo;
  private JsArray<DiffWebLinkInfo> diffLinks;

  @UiField Element header;
  @UiField Element project;
  @UiField Element filePath;
  @UiField FlowPanel linkPanel;
  @UiField Element cursLine;
  @UiField Element cursCol;
  @UiField Element dirty;
  @UiField CheckBox showBase;
  @UiField Button close;
  @UiField Button save;
  @UiField Element editor;
  @UiField Style style;

  private HandlerRegistration resizeHandler;
  private HandlerRegistration closeHandler;
  private int generation;

  public EditScreen(@Nullable Project.NameKey projectKey, Patch.Key patch, int startLine) {
    this.projectKey = projectKey;
    this.revision = patch.getParentKey();
    this.path = patch.get();
    this.startLine = startLine - 1;
    setRequiresSignIn(true);
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

    prefs = EditPreferences.create(Gerrit.getEditPreferences());

    CallbackGroup group1 = new CallbackGroup();
    final CallbackGroup group2 = new CallbackGroup();
    final CallbackGroup group3 = new CallbackGroup();

    CodeMirror.initLibrary(
        group1.add(
            new AsyncCallback<Void>() {
              final AsyncCallback<Void> themeCallback = group3.addEmpty();

              @Override
              public void onSuccess(Void result) {
                // Load theme after CM library to ensure theme can override CSS.
                ThemeLoader.loadTheme(prefs.theme(), themeCallback);
                group2.done();

                new AddonInjector()
                    .add(Addons.I.merge_bundled().getName())
                    .inject(
                        new AsyncCallback<Void>() {
                          @Override
                          public void onFailure(Throwable caught) {}

                          @Override
                          public void onSuccess(Void result) {
                            if (!prefs.showBase() || revision.get() > 0) {
                              group3.done();
                            }
                          }
                        });
              }

              @Override
              public void onFailure(Throwable caught) {}
            }));

    ChangeApi.detail(
        revision.getParentKey().get(),
        Project.NameKey.asStringOrNull(projectKey),
        group1.add(
            new AsyncCallback<ChangeInfo>() {
              @Override
              public void onSuccess(ChangeInfo c) {
                project.setInnerText(c.project());
                SafeHtml.setInnerHTML(filePath, Header.formatPath(path));
              }

              @Override
              public void onFailure(Throwable caught) {}
            }));

    if (revision.get() == 0) {
      ChangeEditApi.getMeta(
          revision,
          Project.NameKey.asStringOrNull(projectKey),
          path,
          group1.add(
              new AsyncCallback<EditFileInfo>() {
                @Override
                public void onSuccess(EditFileInfo editInfo) {
                  editFileInfo = editInfo;
                }

                @Override
                public void onFailure(Throwable e) {}
              }));

      if (prefs.showBase()) {
        ChangeEditApi.get(
            projectKey,
            revision,
            path,
            true /* base */,
            group1.addFinal(
                new HttpCallback<NativeString>() {
                  @Override
                  public void onSuccess(HttpResponse<NativeString> fc) {
                    baseContent = fc;
                    group3.done();
                  }

                  @Override
                  public void onFailure(Throwable e) {}
                }));
      } else {
        group1.done();
      }
    } else {
      // TODO(davido): We probably want to create dedicated GET EditScreenMeta
      // REST endpoint. Abuse GET diff for now, as it retrieves links we need.
      DiffApi.diff(revision, Project.NameKey.asStringOrNull(projectKey), path)
          .webLinksOnly()
          .get(
              group1.addFinal(
                  new AsyncCallback<DiffInfo>() {
                    @Override
                    public void onSuccess(DiffInfo diffInfo) {
                      diffLinks = diffInfo.webLinks();
                    }

                    @Override
                    public void onFailure(Throwable e) {}
                  }));
    }

    ChangeEditApi.get(
        projectKey,
        revision,
        path,
        group2.add(
            new HttpCallback<NativeString>() {
              final AsyncCallback<Void> modeCallback = group3.addEmpty();

              @Override
              public void onSuccess(HttpResponse<NativeString> fc) {
                content = fc;
                if (revision.get() > 0) {
                  baseContent = fc;
                }

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

    group3.addListener(
        new ScreenLoadCallback<Void>(this) {
          @Override
          protected void preDisplay(Void result) {
            initEditor();

            renderLinks(editFileInfo, diffLinks);
            editFileInfo = null;
            diffLinks = null;

            showBase.setValue(prefs.showBase(), true);
            cmBase.refresh();
          }
        });
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    KeyMap localKeyMap = KeyMap.create();
    localKeyMap.on("Ctrl-L", gotoLine()).on("Cmd-L", gotoLine()).on("Cmd-S", save());

    // TODO(davido): Find a better way to prevent key maps collisions
    if (prefs.keyMapType() != KeyMapType.EMACS) {
      localKeyMap.on("Ctrl-S", save());
    }

    cmBase.addKeyMap(localKeyMap);
    cmEdit.addKeyMap(localKeyMap);
  }

  private Runnable gotoLine() {
    return () -> cmEdit.execCommand("jumpToLine");
  }

  @Override
  public void onShowView() {
    super.onShowView();
    Window.enableScrolling(false);
    JumpKeys.enable(false);
    if (prefs.hideTopMenu()) {
      Gerrit.setHeaderVisible(false);
    }
    resizeHandler =
        Window.addResizeHandler(
            new ResizeHandler() {
              @Override
              public void onResize(ResizeEvent event) {
                adjustHeight();
              }
            });
    closeHandler =
        Window.addWindowClosingHandler(
            new ClosingHandler() {
              @Override
              public void onWindowClosing(ClosingEvent event) {
                if (!cmEdit.isClean(generation)) {
                  event.setMessage(EditConstants.I.closeUnsavedChanges());
                }
              }
            });

    generation = cmEdit.changeGeneration(true);
    setClean(true);
    cmEdit.on(
        new ChangesHandler() {
          @Override
          public void handle(CodeMirror cm) {
            setClean(cm.isClean(generation));
          }
        });

    adjustHeight();
    cmEdit.on("cursorActivity", updateCursorPosition());
    setShowTabs(prefs.showTabs());
    setLineLength(prefs.lineLength());
    cmEdit.refresh();
    cmEdit.focus();

    if (startLine > 0) {
      cmEdit.scrollToLine(startLine);
    }
    updateActiveLine();
    editPrefsAction = new EditPreferencesAction(this, prefs);
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (cmBase != null) {
      cmBase.getWrapperElement().removeFromParent();
    }
    if (cmEdit != null) {
      cmEdit.getWrapperElement().removeFromParent();
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

  CodeMirror getEditor() {
    return cmEdit;
  }

  @UiHandler("editSettings")
  void onEditSetting(@SuppressWarnings("unused") ClickEvent e) {
    editPrefsAction.show();
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    save().run();
  }

  @UiHandler("close")
  void onClose(@SuppressWarnings("unused") ClickEvent e) {
    if (cmEdit.isClean(generation) || Window.confirm(EditConstants.I.cancelUnsavedChanges())) {
      upToChange();
    }
  }

  private void displayBase() {
    cmBase.getWrapperElement().getParentElement().removeClassName(style.hideBase());
    cmEdit.getWrapperElement().getParentElement().removeClassName(style.fullWidth());
    mv.getGapElement().removeClassName(style.hideBase());
    setCmBaseValue();
    setLineLength(prefs.lineLength());
    cmBase.refresh();
  }

  @UiHandler("showBase")
  void onShowBase(ValueChangeEvent<Boolean> e) {
    boolean shouldShow = e.getValue();
    if (shouldShow) {
      if (baseContent == null) {
        ChangeEditApi.get(
            projectKey,
            revision,
            path,
            true /* base */,
            new HttpCallback<NativeString>() {
              @Override
              public void onSuccess(HttpResponse<NativeString> fc) {
                baseContent = fc;
                displayBase();
              }

              @Override
              public void onFailure(Throwable e) {}
            });
      } else {
        displayBase();
      }
    } else {
      cmBase.getWrapperElement().getParentElement().addClassName(style.hideBase());
      cmEdit.getWrapperElement().getParentElement().addClassName(style.fullWidth());
      mv.getGapElement().addClassName(style.hideBase());
    }
    mv.setShowDifferences(shouldShow);
  }

  void setOption(String option, String value) {
    cmBase.setOption(option, value);
    cmEdit.setOption(option, value);
  }

  void setOption(String option, boolean value) {
    cmBase.setOption(option, value);
    cmEdit.setOption(option, value);
  }

  void setOption(String option, double value) {
    cmBase.setOption(option, value);
    cmEdit.setOption(option, value);
  }

  void setTheme(Theme newTheme) {
    cmBase.operation(() -> cmBase.setOption("theme", newTheme.name().toLowerCase()));
    cmEdit.operation(() -> cmEdit.setOption("theme", newTheme.name().toLowerCase()));
  }

  void setLineLength(int length) {
    int adjustedLength = Patch.COMMIT_MSG.equals(path) ? 72 : length;
    cmBase.extras().lineLength(adjustedLength);
    cmEdit.extras().lineLength(adjustedLength);
  }

  void setIndentUnit(int indent) {
    cmEdit.setOption("indentUnit", Patch.COMMIT_MSG.equals(path) ? 2 : indent);
  }

  void setShowLineNumbers(boolean show) {
    cmBase.setOption("lineNumbers", show);
    cmEdit.setOption("lineNumbers", show);
  }

  void setShowWhitespaceErrors(boolean show) {
    cmBase.operation(() -> cmBase.setOption("showTrailingSpace", show));
    cmEdit.operation(() -> cmEdit.setOption("showTrailingSpace", show));
  }

  void setShowTabs(boolean show) {
    cmBase.extras().showTabs(show);
    cmEdit.extras().showTabs(show);
  }

  void adjustHeight() {
    int height = header.getOffsetHeight();
    int rest = Gerrit.getHeaderFooterHeight() + height + 5; // Estimate
    mv.getGapElement().getStyle().setHeight(Window.getClientHeight() - rest, Unit.PX);
    cmBase.adjustHeight(height);
    cmEdit.adjustHeight(height);
  }

  void setSyntaxHighlighting(boolean b) {
    ModeInfo modeInfo = ModeInfo.findMode(content.getContentType(), path);
    final String mode = modeInfo != null ? modeInfo.mime() : null;
    if (b && mode != null && !mode.isEmpty()) {
      injectMode(
          mode,
          new AsyncCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
              cmBase.setOption("mode", mode);
              cmEdit.setOption("mode", mode);
            }

            @Override
            public void onFailure(Throwable caught) {
              prefs.syntaxHighlighting(false);
            }
          });
    } else {
      cmBase.setOption("mode", (String) null);
      cmEdit.setOption("mode", (String) null);
    }
  }

  private void upToChange() {
    Gerrit.display(PageLinks.toChangeInEditMode(projectKey, revision.getParentKey()));
  }

  private void initEditor() {
    ModeInfo mode = null;
    String editContent = "";
    if (content != null && content.getResult() != null) {
      editContent = content.getResult().asString();
      if (prefs.syntaxHighlighting()) {
        mode = ModeInfo.findMode(content.getContentType(), path);
      }
    }
    mv =
        MergeView.create(
            editor,
            Configuration.create()
                .set("autoCloseBrackets", prefs.autoCloseBrackets())
                .set("cursorBlinkRate", prefs.cursorBlinkRate())
                .set("cursorHeight", 0.85)
                .set("indentUnit", prefs.indentUnit())
                .set("keyMap", prefs.keyMapType().name().toLowerCase())
                .set("lineNumbers", prefs.hideLineNumbers())
                .set("lineWrapping", false)
                .set("matchBrackets", prefs.matchBrackets())
                .set("mode", mode != null ? mode.mime() : null)
                .set("origLeft", editContent)
                .set("scrollbarStyle", "overlay")
                .set("showTrailingSpace", prefs.showWhitespaceErrors())
                .set("styleSelectedText", true)
                .set("tabSize", prefs.tabSize())
                .set("theme", prefs.theme().name().toLowerCase())
                .set("value", ""));

    cmBase = mv.leftOriginal();
    cmBase.getWrapperElement().addClassName(style.base());
    cmEdit = mv.editor();
    setCmBaseValue();
    cmEdit.setValue(editContent);

    CodeMirror.addCommand(
        "save",
        new CommandRunner() {
          @Override
          public void run(CodeMirror instance) {
            save().run();
          }
        });
  }

  private void renderLinks(EditFileInfo editInfo, JsArray<DiffWebLinkInfo> diffLinks) {
    renderLinksToDiff();

    if (editInfo != null) {
      renderLinks(Natives.asList(editInfo.webLinks()));
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
    sbs.setHTML(new ImageResourceRenderer().render(Gerrit.RESOURCES.sideBySideDiff()));
    sbs.setTargetHistoryToken(
        Dispatcher.toPatch(projectKey, "sidebyside", null, new Patch.Key(revision, path)));
    sbs.setTitle(PatchUtil.C.sideBySideDiff());
    linkPanel.add(sbs);

    InlineHyperlink unified = new InlineHyperlink();
    unified.setHTML(new ImageResourceRenderer().render(Gerrit.RESOURCES.unifiedDiff()));
    unified.setTargetHistoryToken(
        Dispatcher.toPatch(projectKey, "unified", null, new Patch.Key(revision, path)));
    unified.setTitle(PatchUtil.C.unifiedDiff());
    linkPanel.add(unified);
  }

  private Runnable updateCursorPosition() {
    return () -> {
      // The rendering of active lines has to be deferred. Reflow
      // caused by adding and removing styles chokes Firefox when arrow
      // key (or j/k) is held down. Performance on Chrome is fine
      // without the deferral.
      //
      Scheduler.get().scheduleDeferred(() -> cmEdit.operation(this::updateActiveLine));
    };
  }

  private void updateActiveLine() {
    Pos p = cmEdit.getCursor("end");
    cursLine.setInnerText(Integer.toString(p.line() + 1));
    cursCol.setInnerText(Integer.toString(p.ch() + 1));
    cmEdit.extras().activeLine(cmEdit.getLineHandleVisualStart(p.line()));
  }

  private void setClean(boolean clean) {
    save.setEnabled(!clean);
    close.setEnabled(true);
    dirty.getStyle().setVisibility(!clean ? VISIBLE : HIDDEN);
  }

  private Runnable save() {
    return () -> {
      if (!cmEdit.isClean(generation)) {
        close.setEnabled(false);
        String text = cmEdit.getValue();
        if (Patch.COMMIT_MSG.equals(path)) {
          String trimmed = text.trim() + "\r";
          if (!trimmed.equals(text)) {
            text = trimmed;
            cmEdit.setValue(text);
          }
        }
        final int g = cmEdit.changeGeneration(false);
        ChangeEditApi.put(
            revision.getParentKey().get(),
            Project.NameKey.asStringOrNull(projectKey),
            path,
            text,
            new GerritCallback<VoidResult>() {
              @Override
              public void onSuccess(VoidResult result) {
                generation = g;
                setClean(cmEdit.isClean(g));
              }

              @Override
              public void onFailure(final Throwable caught) {
                close.setEnabled(true);
              }
            });
      }
    };
  }

  private void injectMode(String type, AsyncCallback<Void> cb) {
    new ModeInjector().add(type).inject(cb);
  }

  private void setCmBaseValue() {
    cmBase.setValue(
        baseContent != null && baseContent.getResult() != null
            ? baseContent.getResult().asString()
            : "");
  }
}
