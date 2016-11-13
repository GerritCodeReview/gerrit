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

import com.google.gerrit.client.AvatarImage;
import com.google.gerrit.client.DiffObject;
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GerritUiExtensionPoint;
import com.google.gerrit.client.NotFoundScreen;
import com.google.gerrit.client.api.ChangeGlue;
import com.google.gerrit.client.api.ExtensionPanel;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.changes.RevisionInfoCache;
import com.google.gerrit.client.changes.StarredChanges;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.diff.DiffApi;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.AccountInfo.AvatarInfo;
import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.CommitInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.LabelInfo;
import com.google.gerrit.client.info.ChangeInfo.MessageInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.info.FileInfo;
import com.google.gerrit.client.info.GpgKeyInfo;
import com.google.gerrit.client.info.PushCertificateInfo;
import com.google.gerrit.client.projects.ConfigInfoCache;
import com.google.gerrit.client.projects.ConfigInfoCache.Entry;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.BranchLink;
import com.google.gerrit.client.ui.ChangeLink;
import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.UserActivityMonitor;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ToggleButton;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import com.google.gwtorm.client.KeyUtil;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.codemirror.lib.CodeMirror;

public class ChangeScreen extends Screen {
  private static final Logger logger = Logger.getLogger(ChangeScreen.class.getName());

  interface Binder extends UiBinder<HTMLPanel, ChangeScreen> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Style extends CssResource {
    String avatar();

    String hashtagName();

    String hashtagIcon();

    String highlight();

    String labelName();

    String label_may();

    String label_need();

    String label_ok();

    String label_recommend();

    String label_dislike();

    String label_reject();

    String label_user();

    String pushCertStatus();

    String replyBox();

    String selected();

    String notCurrentPatchSet();
  }

  static ChangeScreen get(NativeEvent in) {
    Element e = in.getEventTarget().cast();
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof ChangeScreen) {
        return (ChangeScreen) l;
      }
    }
    return null;
  }

  private final Change.Id changeId;
  private DiffObject base;
  private String revision;
  private ChangeInfo changeInfo;
  private boolean hasDraftComments;
  private CommentLinkProcessor commentLinkProcessor;
  private EditInfo edit;
  private LocalComments lc;

  private List<HandlerRegistration> handlers = new ArrayList<>(4);
  private UpdateCheckTimer updateCheck;
  private Timestamp lastDisplayedUpdate;
  private UpdateAvailableBar updateAvailable;
  private boolean openReplyBox;
  private boolean loaded;
  private FileTable.Mode fileTableMode;

  @UiField HTMLPanel headerLine;
  @UiField SimplePanel headerExtension;
  @UiField SimplePanel headerExtensionMiddle;
  @UiField SimplePanel headerExtensionRight;
  @UiField Style style;
  @UiField ToggleButton star;
  @UiField Anchor permalink;

  @UiField Assignee assignee;
  @UiField Element assigneeRow;
  @UiField Element ccText;
  @UiField Reviewers reviewers;
  @UiField Hashtags hashtags;
  @UiField Element hashtagTableRow;

  @UiField FlowPanel ownerPanel;
  @UiField InlineHyperlink ownerLink;

  @UiField Element uploaderRow;
  @UiField FlowPanel uploaderPanel;
  @UiField InlineLabel uploaderName;

  @UiField Element statusText;
  @UiField Element privateText;
  @UiField Element wipText;
  @UiField Image projectSettings;
  @UiField AnchorElement projectSettingsLink;
  @UiField InlineHyperlink projectDashboard;
  @UiField InlineHyperlink branchLink;
  @UiField Element strategy;
  @UiField Element submitActionText;
  @UiField Element notMergeable;
  @UiField Topic topic;
  @UiField Element actionText;
  @UiField Element actionDate;
  @UiField SimplePanel changeExtension;
  @UiField SimplePanel relatedExtension;
  @UiField SimplePanel commitExtension;

  @UiField Actions actions;
  @UiField Labels labels;
  @UiField CommitBox commit;
  @UiField RelatedChanges related;
  @UiField FileTable files;
  @UiField ListBox diffBase;
  @UiField History history;
  @UiField SimplePanel historyExtensionRight;

  @UiField Button includedIn;
  @UiField Button patchSets;
  @UiField Element patchSetsText;
  @UiField Button download;
  @UiField Button reply;
  @UiField Button publishEdit;
  @UiField Button rebaseEdit;
  @UiField Button deleteEdit;
  @UiField Button publish;
  @UiField Button deleteRevision;
  @UiField Button openAll;
  @UiField Button editMode;
  @UiField Button reviewMode;
  @UiField Button addFile;
  @UiField Button deleteFile;
  @UiField Button renameFile;
  @UiField Button expandAll;
  @UiField Button collapseAll;
  @UiField Button hideTaggedComments;
  @UiField Button showTaggedComments;
  @UiField QuickApprove quickApprove;

  private ReplyAction replyAction;
  private IncludedInAction includedInAction;
  private PatchSetsAction patchSetsAction;
  private DownloadAction downloadAction;
  private AddFileAction addFileAction;
  private DeleteFileAction deleteFileAction;
  private RenameFileAction renameFileAction;

  public ChangeScreen(
      Change.Id changeId,
      DiffObject base,
      String revision,
      boolean openReplyBox,
      FileTable.Mode mode) {
    this.changeId = changeId;
    this.base = base;
    this.revision = normalize(revision);
    this.openReplyBox = openReplyBox;
    this.fileTableMode = mode;
    this.lc = new LocalComments(changeId);
    add(uiBinder.createAndBindUi(this));
  }

  PatchSet.Id getPatchSetId() {
    return new PatchSet.Id(changeInfo.legacyId(), changeInfo.revisions().get(revision)._number());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    CallbackGroup group = new CallbackGroup();
    if (Gerrit.isSignedIn()) {
      ChangeList.query(
          "change:" + changeId.get() + " has:draft",
          Collections.<ListChangesOption>emptySet(),
          group.add(
              new AsyncCallback<ChangeList>() {
                @Override
                public void onSuccess(ChangeList result) {
                  hasDraftComments = result.length() > 0;
                }

                @Override
                public void onFailure(Throwable caught) {}
              }));
      ChangeApi.editWithFiles(
          changeId.get(),
          group.add(
              new AsyncCallback<EditInfo>() {
                @Override
                public void onSuccess(EditInfo result) {
                  edit = result;
                }

                @Override
                public void onFailure(Throwable caught) {}
              }));
    }
    loadChangeInfo(
        true,
        group.addFinal(
            new GerritCallback<ChangeInfo>() {
              @Override
              public void onSuccess(ChangeInfo info) {
                info.init();
                initCurrentRevision(info);
                final RevisionInfo rev = info.revision(revision);
                CallbackGroup group = new CallbackGroup();
                loadCommit(rev, group);

                group.addListener(
                    new GerritCallback<Void>() {
                      @Override
                      public void onSuccess(Void result) {
                        if (base.isBase() && rev.isMerge()) {
                          base =
                              DiffObject.parse(
                                  info.legacyId(),
                                  Gerrit.getUserPreferences().defaultBaseForMerges().getBase());
                        }
                        loadConfigInfo(info, base);
                        JsArray<MessageInfo> mAr = info.messages();
                        for (int i = 0; i < mAr.length(); i++) {
                          if (mAr.get(i).tag() != null) {
                            hideTaggedComments.setVisible(true);
                            break;
                          }
                        }
                      }
                    });
                group.done();
              }
            }));
  }

  private RevisionInfo initCurrentRevision(ChangeInfo info) {
    info.revisions().copyKeysIntoChildren("name");
    if (edit != null) {
      edit.setName(edit.commit().commit());
      info.setEdit(edit);
      if (edit.hasFiles()) {
        edit.files().copyKeysIntoChildren("path");
      }
      info.revisions().put(edit.name(), RevisionInfo.fromEdit(edit));
      JsArray<RevisionInfo> list = info.revisions().values();

      // Edit is converted to a regular revision (with number = 0) and
      // added to the list of revisions. Additionally under certain
      // circumstances change edit is assigned to be the current revision
      // and is selected to be shown on the change screen.
      // We have two different strategies to assign edit to the current ps:
      // 1. revision == null: no revision is selected, so use the edit only
      //    if it is based on the latest patch set
      // 2. edit was selected explicitly from ps drop down:
      //    use the edit regardless of which patch set it is based on
      if (revision == null) {
        RevisionInfo.sortRevisionInfoByNumber(list);
        RevisionInfo rev = list.get(list.length() - 1);
        if (rev.isEdit()) {
          info.setCurrentRevision(rev.name());
        }
      } else if (revision.equals("edit") || revision.equals("0")) {
        for (int i = 0; i < list.length(); i++) {
          RevisionInfo r = list.get(i);
          if (r.isEdit()) {
            info.setCurrentRevision(r.name());
            break;
          }
        }
      }
    }
    return resolveRevisionToDisplay(info);
  }

  private void addExtensionPoints(ChangeInfo change, RevisionInfo rev, Entry result) {
    addExtensionPoint(GerritUiExtensionPoint.CHANGE_SCREEN_HEADER, headerExtension, change, rev);
    addExtensionPoint(
        GerritUiExtensionPoint.CHANGE_SCREEN_HEADER_RIGHT_OF_BUTTONS,
        headerExtensionMiddle,
        change,
        rev);
    addExtensionPoint(
        GerritUiExtensionPoint.CHANGE_SCREEN_HEADER_RIGHT_OF_POP_DOWNS,
        headerExtensionRight,
        change,
        rev);
    addExtensionPoint(
        GerritUiExtensionPoint.CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK,
        changeExtension,
        change,
        rev,
        result.getExtensionPanelNames(
            GerritUiExtensionPoint.CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK.toString()));
    addExtensionPoint(
        GerritUiExtensionPoint.CHANGE_SCREEN_BELOW_RELATED_INFO_BLOCK,
        relatedExtension,
        change,
        rev);
    addExtensionPoint(
        GerritUiExtensionPoint.CHANGE_SCREEN_BELOW_COMMIT_INFO_BLOCK, commitExtension, change, rev);
    addExtensionPoint(
        GerritUiExtensionPoint.CHANGE_SCREEN_HISTORY_RIGHT_OF_BUTTONS,
        historyExtensionRight,
        change,
        rev);
  }

  private void addExtensionPoint(
      GerritUiExtensionPoint extensionPoint,
      Panel p,
      ChangeInfo change,
      RevisionInfo rev,
      List<String> panelNames) {
    ExtensionPanel extensionPanel = new ExtensionPanel(extensionPoint, panelNames);
    extensionPanel.putObject(GerritUiExtensionPoint.Key.CHANGE_INFO, change);
    extensionPanel.putObject(GerritUiExtensionPoint.Key.REVISION_INFO, rev);
    p.add(extensionPanel);
  }

  private void addExtensionPoint(
      GerritUiExtensionPoint extensionPoint, Panel p, ChangeInfo change, RevisionInfo rev) {
    addExtensionPoint(extensionPoint, p, change, rev, Collections.emptyList());
  }

  private boolean enableSignedPush() {
    return Gerrit.info().receive().enableSignedPush();
  }

  void loadChangeInfo(boolean fg, AsyncCallback<ChangeInfo> cb) {
    RestApi call = ChangeApi.detail(changeId.get());
    EnumSet<ListChangesOption> opts =
        EnumSet.of(ListChangesOption.ALL_REVISIONS, ListChangesOption.CHANGE_ACTIONS);
    if (enableSignedPush()) {
      opts.add(ListChangesOption.PUSH_CERTIFICATES);
    }
    ChangeList.addOptions(call, opts);
    if (!fg) {
      call.background();
    }
    call.get(cb);
  }

  void loadRevisionInfo() {
    RestApi call = ChangeApi.actions(changeId.get(), revision);
    call.background();
    call.get(
        new GerritCallback<NativeMap<ActionInfo>>() {
          @Override
          public void onSuccess(NativeMap<ActionInfo> actionMap) {
            actionMap.copyKeysIntoChildren("id");
            renderRevisionInfo(changeInfo, actionMap);
          }
        });
  }

  @Override
  protected void onUnload() {
    if (replyAction != null) {
      replyAction.hide();
    }
    if (updateCheck != null) {
      updateCheck.cancel();
      updateCheck = null;
    }
    for (HandlerRegistration h : handlers) {
      h.removeHandler();
    }
    handlers.clear();
    super.onUnload();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setHeaderVisible(false);
    Resources.I.style().ensureInjected();
    star.setVisible(Gerrit.isSignedIn());
    labels.init(style);
    reviewers.init(style, ccText);
    hashtags.init(style);
  }

  private void initReplyButton(ChangeInfo info, String revision) {
    if (!info.revision(revision).isEdit()) {
      reply.setTitle(Gerrit.info().change().replyLabel());
      reply.setHTML(
          new SafeHtmlBuilder().openDiv().append(Gerrit.info().change().replyLabel()).closeDiv());
      if (hasDraftComments || lc.hasReplyComment()) {
        reply.setStyleName(style.highlight());
      }
      reply.setVisible(true);
    }
  }

  private void gotoSibling(int offset) {
    if (offset > 0
        && changeInfo.currentRevision() != null
        && changeInfo.currentRevision().equals(revision)) {
      return;
    }

    if (offset < 0 && changeInfo.revision(revision)._number() == 1) {
      return;
    }

    JsArray<RevisionInfo> revisions = changeInfo.revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(revisions);
    for (int i = 0; i < revisions.length(); i++) {
      if (revision.equals(revisions.get(i).name())) {
        if (0 <= i + offset && i + offset < revisions.length()) {
          Gerrit.display(
              PageLinks.toChange(
                  new PatchSet.Id(changeInfo.legacyId(), revisions.get(i + offset)._number())));
          return;
        }
        return;
      }
    }
  }

  private void initIncludedInAction(ChangeInfo info) {
    if (info.status() == Status.MERGED) {
      includedInAction = new IncludedInAction(info.legacyId(), style, headerLine, includedIn);
      includedIn.setVisible(true);
    }
  }

  private void updatePatchSetsTextStyle(boolean isPatchSetCurrent) {
    if (isPatchSetCurrent) {
      patchSetsText.removeClassName(style.notCurrentPatchSet());
    } else {
      patchSetsText.addClassName(style.notCurrentPatchSet());
    }
  }

  private void initRevisionsAction(
      ChangeInfo info, String revision, NativeMap<ActionInfo> actions) {
    int currentPatchSet;
    if (info.currentRevision() != null && info.revisions().containsKey(info.currentRevision())) {
      currentPatchSet = info.revision(info.currentRevision())._number();
    } else {
      JsArray<RevisionInfo> revList = info.revisions().values();
      RevisionInfo.sortRevisionInfoByNumber(revList);
      currentPatchSet = revList.get(revList.length() - 1)._number();
    }

    String currentlyViewedPatchSet;
    boolean isPatchSetCurrent = true;
    String revisionId = info.revision(revision).id();
    if (revisionId.equals("edit")) {
      currentlyViewedPatchSet =
          Resources.M.editPatchSet(RevisionInfo.findEditParent(info.revisions().values()));
      currentPatchSet = info.revisions().values().length() - 1;
    } else {
      currentlyViewedPatchSet = revisionId;
      if (!currentlyViewedPatchSet.equals(Integer.toString(currentPatchSet))) {
        isPatchSetCurrent = false;
      }
    }
    patchSetsText.setInnerText(Resources.M.patchSets(currentlyViewedPatchSet, currentPatchSet));
    updatePatchSetsTextStyle(isPatchSetCurrent);
    patchSetsAction =
        new PatchSetsAction(info.legacyId(), revision, edit, style, headerLine, patchSets);

    RevisionInfo revInfo = info.revision(revision);
    if (revInfo.draft()) {
      if (actions.containsKey("publish")) {
        publish.setVisible(true);
        publish.setTitle(actions.get("publish").title());
      }
      if (actions.containsKey("/")) {
        deleteRevision.setVisible(true);
        deleteRevision.setTitle(actions.get("/").title());
      }
    }
  }

  private void initDownloadAction(ChangeInfo info, String revision) {
    downloadAction = new DownloadAction(info, revision, style, headerLine, download);
  }

  private void initProjectLinks(ChangeInfo info) {
    projectSettingsLink.setHref("#" + PageLinks.toProject(info.projectNameKey()));
    projectSettings.addDomHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            if (Hyperlink.impl.handleAsClick((Event) event.getNativeEvent())) {
              event.stopPropagation();
              event.preventDefault();
              Gerrit.display(PageLinks.toProject(info.projectNameKey()));
            }
          }
        },
        ClickEvent.getType());
    projectDashboard.setText(info.project());
    projectDashboard.setTargetHistoryToken(
        PageLinks.toProjectDefaultDashboard(info.projectNameKey()));
  }

  private void initBranchLink(ChangeInfo info) {
    branchLink.setText(info.branch());
    branchLink.setTargetHistoryToken(
        PageLinks.toChangeQuery(
            BranchLink.query(info.projectNameKey(), info.status(), info.branch(), null)));
  }

  private void initEditMode(ChangeInfo info, String revision) {
    if (Gerrit.isSignedIn()) {
      RevisionInfo rev = info.revision(revision);
      if (info.status().isOpen()) {
        if (isEditModeEnabled(info, rev)) {
          editMode.setVisible(fileTableMode == FileTable.Mode.REVIEW);
          addFile.setVisible(!editMode.isVisible());
          deleteFile.setVisible(!editMode.isVisible());
          renameFile.setVisible(!editMode.isVisible());
          reviewMode.setVisible(!editMode.isVisible());
          addFileAction =
              new AddFileAction(changeId, info.revision(revision), style, addFile, files);
          deleteFileAction =
              new DeleteFileAction(changeId, info.revision(revision), style, addFile);
          renameFileAction =
              new RenameFileAction(changeId, info.revision(revision), style, addFile);
        } else {
          editMode.setVisible(false);
          addFile.setVisible(false);
          reviewMode.setVisible(false);
        }

        if (rev.isEdit()) {
          if (info.hasEditBasedOnCurrentPatchSet()) {
            publishEdit.setVisible(true);
          } else {
            rebaseEdit.setVisible(true);
          }
          deleteEdit.setVisible(true);
        }
      } else if (rev.isEdit()) {
        deleteEdit.setStyleName(style.highlight());
        deleteEdit.setVisible(true);
      }
    }
  }

  private boolean isEditModeEnabled(ChangeInfo info, RevisionInfo rev) {
    if (rev.isEdit()) {
      return true;
    }
    if (edit == null) {
      return revision.equals(info.currentRevision());
    }
    return rev._number() == RevisionInfo.findEditParent(info.revisions().values());
  }

  @UiHandler("publishEdit")
  void onPublishEdit(@SuppressWarnings("unused") ClickEvent e) {
    EditActions.publishEdit(changeId, publishEdit, rebaseEdit, deleteEdit);
  }

  @UiHandler("rebaseEdit")
  void onRebaseEdit(@SuppressWarnings("unused") ClickEvent e) {
    EditActions.rebaseEdit(changeId, publishEdit, rebaseEdit, deleteEdit);
  }

  @UiHandler("deleteEdit")
  void onDeleteEdit(@SuppressWarnings("unused") ClickEvent e) {
    if (Window.confirm(Resources.C.deleteChangeEdit())) {
      EditActions.deleteEdit(changeId, publishEdit, rebaseEdit, deleteEdit);
    }
  }

  @UiHandler("publish")
  void onPublish(@SuppressWarnings("unused") ClickEvent e) {
    ChangeActions.publish(changeId, revision, publish, deleteRevision);
  }

  @UiHandler("deleteRevision")
  void onDeleteRevision(@SuppressWarnings("unused") ClickEvent e) {
    if (Window.confirm(Resources.C.deleteDraftRevision())) {
      ChangeActions.delete(changeId, revision, publish, deleteRevision);
    }
  }

  @Override
  public void registerKeys() {
    super.registerKeys();

    KeyCommandSet keysNavigation = new KeyCommandSet(Gerrit.C.sectionNavigation());
    keysNavigation.add(
        new KeyCommand(0, 'u', Util.C.upToChangeList()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            Gerrit.displayLastChangeList();
          }
        });
    keysNavigation.add(
        new KeyCommand(0, 'R', Util.C.keyReloadChange()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            Gerrit.display(PageLinks.toChange(changeId));
          }
        });
    keysNavigation.add(
        new KeyCommand(0, 'n', Util.C.keyNextPatchSet()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            gotoSibling(1);
          }
        },
        new KeyCommand(0, 'p', Util.C.keyPreviousPatchSet()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            gotoSibling(-1);
          }
        });
    handlers.add(GlobalKey.add(this, keysNavigation));

    KeyCommandSet keysAction = new KeyCommandSet(Gerrit.C.sectionActions());
    keysAction.add(
        new KeyCommand(0, 'a', Util.C.keyPublishComments()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (Gerrit.isSignedIn()) {
              onReply(null);
            } else {
              Gerrit.doSignIn(getToken());
            }
          }
        });
    keysAction.add(
        new KeyCommand(0, 'x', Util.C.keyExpandAllMessages()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            onExpandAll(null);
          }
        });
    keysAction.add(
        new KeyCommand(0, 'z', Util.C.keyCollapseAllMessages()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            onCollapseAll(null);
          }
        });
    keysAction.add(
        new KeyCommand(0, 's', Util.C.changeTableStar()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (Gerrit.isSignedIn()) {
              star.setValue(!star.getValue(), true);
            } else {
              Gerrit.doSignIn(getToken());
            }
          }
        });
    keysAction.add(
        new KeyCommand(0, 'c', Util.C.keyAddReviewers()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (Gerrit.isSignedIn()) {
              reviewers.onOpenForm();
            } else {
              Gerrit.doSignIn(getToken());
            }
          }
        });
    keysAction.add(
        new KeyCommand(0, 't', Util.C.keyEditTopic()) {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (Gerrit.isSignedIn()) {
              // In Firefox this event is mistakenly called when F5 is pressed so
              // differentiate F5 from 't' by checking the charCode(F5=0, t=116).
              if (event.getNativeEvent().getCharCode() == 0) {
                Window.Location.reload();
                return;
              }
              if (topic.canEdit()) {
                topic.onEdit();
              }
            } else {
              Gerrit.doSignIn(getToken());
            }
          }
        });
    handlers.add(GlobalKey.add(this, keysAction));
    files.registerKeys();
  }

  @Override
  public void onShowView() {
    super.onShowView();
    commit.onShowView();
    related.setMaxHeight(commit.getElement().getParentElement().getOffsetHeight());

    if (openReplyBox) {
      onReply();
    } else {
      String prior = Gerrit.getPriorView();
      if (prior != null && prior.startsWith("/c/")) {
        scrollToPath(prior.substring(3));
      }
    }

    ChangeGlue.fireShowChange(changeInfo, changeInfo.revision(revision));
    CodeMirror.preload();
    startPoller();
  }

  private void scrollToPath(String token) {
    int s = token.indexOf('/');
    try {
      String c = token.substring(0, s);
      int editIndex = c.indexOf(",edit");
      if (editIndex > 0) {
        c = c.substring(0, editIndex);
      }
      if (s < 0 || !changeId.equals(Change.Id.parse(c))) {
        return; // Unrelated URL, do not scroll.
      }
    } catch (IllegalArgumentException e) {
      return;
    }

    s = token.indexOf('/', s + 1);
    if (s < 0) {
      return; // URL does not name a file.
    }

    int c = token.lastIndexOf(',');
    if (0 <= c) {
      token = token.substring(s + 1, c);
    } else {
      token = token.substring(s + 1);
    }

    if (!token.isEmpty()) {
      files.scrollToPath(KeyUtil.decode(token));
    }
  }

  @UiHandler("star")
  void onToggleStar(ValueChangeEvent<Boolean> e) {
    StarredChanges.toggleStar(changeId, e.getValue());
  }

  @UiHandler("includedIn")
  void onIncludedIn(@SuppressWarnings("unused") ClickEvent e) {
    includedInAction.show();
  }

  @UiHandler("download")
  void onDownload(@SuppressWarnings("unused") ClickEvent e) {
    downloadAction.show();
  }

  @UiHandler("patchSets")
  void onPatchSets(@SuppressWarnings("unused") ClickEvent e) {
    patchSetsAction.show();
  }

  @UiHandler("reply")
  void onReply(@SuppressWarnings("unused") ClickEvent e) {
    onReply();
  }

  @UiHandler("permalink")
  void onReload(ClickEvent e) {
    e.preventDefault();
    Gerrit.display(PageLinks.toChange(changeId));
  }

  private void onReply() {
    if (Gerrit.isSignedIn()) {
      replyAction.onReply(null);
    } else {
      Gerrit.doSignIn(getToken());
    }
  }

  @UiHandler("openAll")
  void onOpenAll(@SuppressWarnings("unused") ClickEvent e) {
    files.openAll();
  }

  @UiHandler("editMode")
  void onEditMode(@SuppressWarnings("unused") ClickEvent e) {
    fileTableMode = FileTable.Mode.EDIT;
    refreshFileTable();
    editMode.setVisible(false);
    addFile.setVisible(true);
    deleteFile.setVisible(true);
    renameFile.setVisible(true);
    reviewMode.setVisible(true);
  }

  @UiHandler("reviewMode")
  void onReviewMode(@SuppressWarnings("unused") ClickEvent e) {
    fileTableMode = FileTable.Mode.REVIEW;
    refreshFileTable();
    editMode.setVisible(true);
    addFile.setVisible(false);
    deleteFile.setVisible(false);
    renameFile.setVisible(false);
    reviewMode.setVisible(false);
  }

  @UiHandler("addFile")
  void onAddFile(@SuppressWarnings("unused") ClickEvent e) {
    addFileAction.onEdit();
  }

  @UiHandler("deleteFile")
  void onDeleteFile(@SuppressWarnings("unused") ClickEvent e) {
    deleteFileAction.onDelete();
  }

  @UiHandler("renameFile")
  void onRenameFile(@SuppressWarnings("unused") ClickEvent e) {
    renameFileAction.onRename();
  }

  private void refreshFileTable() {
    int idx = diffBase.getSelectedIndex();
    if (0 <= idx) {
      String n = diffBase.getValue(idx);
      loadConfigInfo(changeInfo, DiffObject.parse(changeInfo.legacyId(), n));
    }
  }

  @UiHandler("showTaggedComments")
  void onShowTaggedComments(@SuppressWarnings("unused") ClickEvent e) {
    showTaggedComments.setVisible(false);
    hideTaggedComments.setVisible(true);
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      Message m = ((Message) history.getWidget(i));
      m.setVisible(true);
    }
  }

  @UiHandler("hideTaggedComments")
  void onHideTaggedComments(@SuppressWarnings("unused") ClickEvent e) {
    hideTaggedComments.setVisible(false);
    showTaggedComments.setVisible(true);
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      Message m = ((Message) history.getWidget(i));
      if (m.getMessageInfo().tag() != null) {
        m.setVisible(false);
      }
    }
  }

  @UiHandler("expandAll")
  void onExpandAll(@SuppressWarnings("unused") ClickEvent e) {
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      ((Message) history.getWidget(i)).setOpen(true);
    }
    expandAll.setVisible(false);
    collapseAll.setVisible(true);
  }

  @UiHandler("collapseAll")
  void onCollapseAll(@SuppressWarnings("unused") ClickEvent e) {
    int n = history.getWidgetCount();
    for (int i = 0; i < n; i++) {
      ((Message) history.getWidget(i)).setOpen(false);
    }
    expandAll.setVisible(true);
    collapseAll.setVisible(false);
  }

  @UiHandler("diffBase")
  void onChangeRevision(@SuppressWarnings("unused") ChangeEvent e) {
    int idx = diffBase.getSelectedIndex();
    if (0 <= idx) {
      String n = diffBase.getValue(idx);
      loadConfigInfo(changeInfo, DiffObject.parse(changeInfo.legacyId(), n));
    }
  }

  private void loadConfigInfo(ChangeInfo info, DiffObject base) {
    final RevisionInfo rev = info.revision(revision);
    if (base.isAutoMerge() && !initCurrentRevision(info).isMerge()) {
      Gerrit.display(getToken(), new NotFoundScreen());
    }

    updateToken(info, base, rev);

    RevisionInfo baseRev = resolveRevisionOrPatchSetId(info, base.asString(), null);

    CallbackGroup group = new CallbackGroup();
    Timestamp lastReply = myLastReply(info);
    if (rev.isEdit()) {
      // Comments are filtered for the current revision. Use parent
      // patch set for edits, as edits themself can never have comments.
      RevisionInfo p = RevisionInfo.findEditParentRevision(info.revisions().values());
      List<NativeMap<JsArray<CommentInfo>>> comments = loadComments(p, group);
      loadFileList(base, baseRev, rev, lastReply, group, comments, null);
    } else {
      loadDiff(base, baseRev, rev, lastReply, group);
    }
    group.addListener(
        new AsyncCallback<Void>() {
          @Override
          public void onSuccess(Void result) {
            loadConfigInfo(info, rev);
          }

          @Override
          public void onFailure(Throwable caught) {
            logger.log(
                Level.SEVERE,
                "Loading file list and inline comments failed: " + caught.getMessage());
            loadConfigInfo(info, rev);
          }
        });
    group.done();
  }

  private void loadConfigInfo(ChangeInfo info, RevisionInfo rev) {
    if (loaded) {
      return;
    }

    RevisionInfoCache.add(changeId, rev);
    ConfigInfoCache.add(info);
    ConfigInfoCache.get(
        info.projectNameKey(),
        new ScreenLoadCallback<ConfigInfoCache.Entry>(this) {
          @Override
          protected void preDisplay(Entry result) {
            loaded = true;
            commentLinkProcessor = result.getCommentLinkProcessor();
            setTheme(result.getTheme());
            renderChangeInfo(info);
            loadRevisionInfo();
          }
        });
    ConfigInfoCache.get(
        info.projectNameKey(),
        new GerritCallback<Entry>() {
          @Override
          public void onSuccess(Entry entry) {
            addExtensionPoints(info, rev, entry);
          }
        });
  }

  private void updateToken(ChangeInfo info, DiffObject base, RevisionInfo rev) {
    StringBuilder token = new StringBuilder("/c/").append(info._number()).append("/");
    if (base.asString() != null) {
      token.append(base.asString()).append("..");
    }
    if (base.asString() != null || !rev.name().equals(info.currentRevision())) {
      token.append(rev._number());
    }
    setToken(token.toString());
  }

  static Timestamp myLastReply(ChangeInfo info) {
    if (Gerrit.isSignedIn() && info.messages() != null) {
      int self = Gerrit.getUserAccount()._accountId();
      for (int i = info.messages().length() - 1; i >= 0; i--) {
        MessageInfo m = info.messages().get(i);
        if (m.author() != null && m.author()._accountId() == self) {
          return m.date();
        }
      }
    }
    return null;
  }

  private void loadDiff(
      DiffObject base,
      RevisionInfo baseRev,
      RevisionInfo rev,
      Timestamp myLastReply,
      CallbackGroup group) {
    List<NativeMap<JsArray<CommentInfo>>> comments = loadComments(rev, group);
    List<NativeMap<JsArray<CommentInfo>>> drafts = loadDrafts(rev, group);
    loadFileList(base, baseRev, rev, myLastReply, group, comments, drafts);

    if (Gerrit.isSignedIn() && fileTableMode == FileTable.Mode.REVIEW) {
      ChangeApi.revision(changeId.get(), rev.name())
          .view("files")
          .addParameterTrue("reviewed")
          .get(
              group.add(
                  new AsyncCallback<JsArrayString>() {
                    @Override
                    public void onSuccess(JsArrayString result) {
                      files.markReviewed(result);
                    }

                    @Override
                    public void onFailure(Throwable caught) {}
                  }));
    }
  }

  private void loadFileList(
      final DiffObject base,
      final RevisionInfo baseRev,
      final RevisionInfo rev,
      final Timestamp myLastReply,
      CallbackGroup group,
      final List<NativeMap<JsArray<CommentInfo>>> comments,
      final List<NativeMap<JsArray<CommentInfo>>> drafts) {
    DiffApi.list(
        changeId.get(),
        rev.name(),
        baseRev,
        group.add(
            new AsyncCallback<NativeMap<FileInfo>>() {
              @Override
              public void onSuccess(NativeMap<FileInfo> m) {
                files.set(
                    base,
                    new PatchSet.Id(changeId, rev._number()),
                    style,
                    reply,
                    fileTableMode,
                    edit != null);
                files.setValue(
                    m,
                    myLastReply,
                    comments != null ? comments.get(0) : null,
                    drafts != null ? drafts.get(0) : null);
              }

              @Override
              public void onFailure(Throwable caught) {
                files.showError(caught);
              }
            }));
  }

  private List<NativeMap<JsArray<CommentInfo>>> loadComments(
      final RevisionInfo rev, CallbackGroup group) {
    final List<NativeMap<JsArray<CommentInfo>>> r = new ArrayList<>(1);
    // TODO(dborowitz): Could eliminate this call by adding an option to include
    // inline comments in the change detail.
    ChangeApi.comments(changeId.get())
        .get(
            group.add(
                new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
                  @Override
                  public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
                    // Return value is used for populating the file table, so only count
                    // comments for the current revision. Still include all comments in
                    // the history table.
                    r.add(filterForRevision(result, rev._number()));
                    history.addComments(result);
                  }

                  @Override
                  public void onFailure(Throwable caught) {}
                }));
    return r;
  }

  private static NativeMap<JsArray<CommentInfo>> filterForRevision(
      NativeMap<JsArray<CommentInfo>> comments, int id) {
    NativeMap<JsArray<CommentInfo>> filtered = NativeMap.create();
    for (String k : comments.keySet()) {
      JsArray<CommentInfo> allRevisions = comments.get(k);
      JsArray<CommentInfo> thisRevision = JsArray.createArray().cast();
      for (int i = 0; i < allRevisions.length(); i++) {
        CommentInfo c = allRevisions.get(i);
        if (c.patchSet() == id) {
          thisRevision.push(c);
        }
      }
      filtered.put(k, thisRevision);
    }
    return filtered;
  }

  private List<NativeMap<JsArray<CommentInfo>>> loadDrafts(RevisionInfo rev, CallbackGroup group) {
    final List<NativeMap<JsArray<CommentInfo>>> r = new ArrayList<>(1);
    if (Gerrit.isSignedIn()) {
      ChangeApi.revision(changeId.get(), rev.name())
          .view("drafts")
          .get(
              group.add(
                  new AsyncCallback<NativeMap<JsArray<CommentInfo>>>() {
                    @Override
                    public void onSuccess(NativeMap<JsArray<CommentInfo>> result) {
                      r.add(result);
                    }

                    @Override
                    public void onFailure(Throwable caught) {}
                  }));
    } else {
      r.add(NativeMap.<JsArray<CommentInfo>>create());
    }
    return r;
  }

  private void loadCommit(RevisionInfo rev, CallbackGroup group) {
    if (rev.isEdit() || rev.commit() != null) {
      return;
    }

    ChangeApi.commitWithLinks(
        changeId.get(),
        rev.name(),
        group.add(
            new AsyncCallback<CommitInfo>() {
              @Override
              public void onSuccess(CommitInfo info) {
                rev.setCommit(info);
              }

              @Override
              public void onFailure(Throwable caught) {}
            }));
  }

  private void renderSubmitType(Change.Status status, boolean canSubmit, SubmitType submitType) {
    if (canSubmit && status == Change.Status.NEW) {
      statusText.setInnerText(
          changeInfo.mergeable() ? Util.C.readyToSubmit() : Util.C.mergeConflict());
    }
    setVisible(notMergeable, !changeInfo.mergeable());
    submitActionText.setInnerText(com.google.gerrit.client.admin.Util.toLongString(submitType));
  }

  private RevisionInfo resolveRevisionToDisplay(ChangeInfo info) {
    RevisionInfo rev = resolveRevisionOrPatchSetId(info, revision, info.currentRevision());
    if (rev != null) {
      revision = rev.name();
      return rev;
    }

    // the revision is not visible to the calling user (maybe it is a draft?)
    // or the change is corrupt, take the last revision that was returned,
    // if no revision was returned display an error
    JsArray<RevisionInfo> revisions = info.revisions().values();
    if (revisions.length() > 0) {
      RevisionInfo.sortRevisionInfoByNumber(revisions);
      rev = revisions.get(revisions.length() - 1);
      revision = rev.name();
      return rev;
    }
    new ErrorDialog(Resources.M.changeWithNoRevisions(info.legacyId().get())).center();
    throw new IllegalStateException("no revision, cannot proceed");
  }

  /**
   * Resolve a revision or patch set id string to RevisionInfo. When this view is created from the
   * changes table, revision is passed as a real revision. When this view is created from side by
   * side (by closing it with 'u') patch set id is passed.
   *
   * @param info change info
   * @param revOrId revision or patch set id
   * @param defaultValue value returned when revOrId is null
   * @return resolved revision or default value
   */
  private RevisionInfo resolveRevisionOrPatchSetId(
      ChangeInfo info, String revOrId, String defaultValue) {
    int parentNum;
    if (revOrId == null) {
      revOrId = defaultValue;
    } else if ((parentNum = toParentNum(revOrId)) > 0) {
      CommitInfo commitInfo = info.revision(revision).commit();
      JsArray<CommitInfo> parents = commitInfo.parents();
      if (parents.length() >= parentNum) {
        return RevisionInfo.forParent(-parentNum, parents.get(parentNum - 1));
      }
    } else if (!info.revisions().containsKey(revOrId)) {
      JsArray<RevisionInfo> list = info.revisions().values();
      for (int i = 0; i < list.length(); i++) {
        RevisionInfo r = list.get(i);
        if (revOrId.equals(String.valueOf(r._number()))) {
          revOrId = r.name();
          break;
        }
      }
    }
    return revOrId != null ? info.revision(revOrId) : null;
  }

  private boolean isSubmittable(ChangeInfo info) {
    boolean canSubmit =
        info.status().isOpen()
            && revision.equals(info.currentRevision())
            && !info.revision(revision).draft();
    if (canSubmit && info.status() == Change.Status.NEW) {
      for (String name : info.labels()) {
        LabelInfo label = info.label(name);
        switch (label.status()) {
          case NEED:
          case RECOMMEND:
          case DISLIKE:
            statusText.setInnerText(Util.M.needs(name));
            canSubmit = false;
            break;
          case REJECT:
          case IMPOSSIBLE:
            if (label.blocking()) {
              statusText.setInnerText(Util.M.blockedOn(name));
              canSubmit = false;
            }
            break;
          case MAY:
          case OK:
          default:
            break;
        }
      }
    }
    return canSubmit;
  }

  private void renderChangeInfo(ChangeInfo info) {
    RevisionInfo revisionInfo = info.revision(revision);
    changeInfo = info;
    lastDisplayedUpdate = info.updated();

    labels.set(info);

    renderOwner(info);
    renderUploader(info, revisionInfo);
    renderActionTextDate(info);
    renderDiffBaseListBox(info);
    initReplyButton(info, revision);
    initIncludedInAction(info);
    initDownloadAction(info, revision);
    initProjectLinks(info);
    initBranchLink(info);
    initEditMode(info, revision);
    actions.display(info, revision);

    star.setValue(info.starred());
    permalink.setHref(ChangeLink.permalink(changeId));
    permalink.setText(String.valueOf(info.legacyId()));
    topic.set(info, revision);
    commit.set(commentLinkProcessor, info, revision);
    related.set(info, revision);
    reviewers.set(info);
    assignee.set(info);
    if (Gerrit.isNoteDbEnabled()) {
      hashtags.set(info, revision);
    } else {
      setVisible(hashtagTableRow, false);
    }

    StringBuilder sb = new StringBuilder();
    sb.append(Util.M.changeScreenTitleId(info.idAbbreviated()));
    if (info.subject() != null) {
      sb.append(": ");
      sb.append(info.subject());
    }
    setWindowTitle(sb.toString());

    // Although this is related to the revision, we can process it early to
    // render it faster.
    if (!info.status().isOpen()
        || !revision.equals(info.currentRevision())
        || revisionInfo.isEdit()) {
      setVisible(strategy, false);
    }

    // Properly render revision actions initially while waiting for
    // the callback to populate them correctly.
    NativeMap<ActionInfo> emptyMap = NativeMap.<ActionInfo>create();
    initRevisionsAction(info, revision, emptyMap);
    quickApprove.setVisible(false);
    actions.reloadRevisionActions(emptyMap);

    boolean current = revision.equals(info.currentRevision()) && !revisionInfo.isEdit();

    if (revisionInfo.isEdit()) {
      statusText.setInnerText(Util.C.changeEdit());
    } else if (!current) {
      statusText.setInnerText(Util.C.notCurrent());
      labels.setVisible(false);
    } else {
      Status s = info.revision(revision).draft() ? Status.DRAFT : info.status();
      statusText.setInnerText(Util.toLongString(s));
    }

    if (info.isPrivate()) {
      privateText.setInnerText(Util.C.isPrivate());
    }

    if (info.isWorkInProgress()) {
      wipText.setInnerText(Util.C.isWorkInProgress());
    }

    if (Gerrit.isSignedIn()) {
      replyAction =
          new ReplyAction(
              info, revision, hasDraftComments, style, commentLinkProcessor, reply, quickApprove);
    }
    history.set(commentLinkProcessor, replyAction, changeId, info);

    if (current && info.status().isOpen()) {
      quickApprove.set(info, revision, replyAction);
      renderSubmitType(info.status(), isSubmittable(info), info.submitType());
    } else {
      quickApprove.setVisible(false);
    }
  }

  private void renderRevisionInfo(ChangeInfo info, NativeMap<ActionInfo> actionMap) {
    initRevisionsAction(info, revision, actionMap);
    commit.setParentNotCurrent(
        actionMap.containsKey("rebase") && actionMap.get("rebase").enabled());
    actions.reloadRevisionActions(actionMap);
  }

  private void renderOwner(ChangeInfo info) {
    // TODO info card hover
    String name = name(info.owner());
    if (info.owner().avatar(AvatarInfo.DEFAULT_SIZE) != null) {
      ownerPanel.insert(new AvatarImage(info.owner()), 0);
    }
    ownerLink.setText(name);
    ownerLink.setTitle(email(info.owner(), name));
    ownerLink.setTargetHistoryToken(
        PageLinks.toAccountQuery(
            info.owner().name() != null
                ? info.owner().name()
                : info.owner().email() != null
                    ? info.owner().email()
                    : String.valueOf(info.owner()._accountId()),
            Change.Status.NEW));
  }

  private void renderUploader(ChangeInfo changeInfo, RevisionInfo revInfo) {
    AccountInfo uploader = revInfo.uploader();
    boolean isOwner = uploader == null || uploader._accountId() == changeInfo.owner()._accountId();
    renderPushCertificate(revInfo, isOwner ? ownerPanel : uploaderPanel);
    if (isOwner) {
      uploaderRow.getStyle().setDisplay(Display.NONE);
      return;
    }
    uploaderRow.getStyle().setDisplay(Display.TABLE_ROW);

    if (uploader.avatar(AvatarInfo.DEFAULT_SIZE) != null) {
      uploaderPanel.insert(new AvatarImage(uploader), 0);
    }
    String name = name(uploader);
    uploaderName.setText(name);
    uploaderName.setTitle(email(uploader, name));
  }

  private void renderPushCertificate(RevisionInfo revInfo, FlowPanel panel) {
    if (!enableSignedPush()) {
      return;
    }
    Image status = new Image();
    panel.add(status);
    status.setStyleName(style.pushCertStatus());
    if (!revInfo.hasPushCertificate() || revInfo.pushCertificate().key() == null) {
      status.setResource(Gerrit.RESOURCES.question());
      status.setTitle(Util.C.pushCertMissing());
      return;
    }
    PushCertificateInfo certInfo = revInfo.pushCertificate();
    GpgKeyInfo.Status s = certInfo.key().status();
    switch (s) {
      case BAD:
        status.setResource(Gerrit.RESOURCES.redNot());
        status.setTitle(problems(Util.C.pushCertBad(), certInfo));
        break;
      case OK:
        status.setResource(Gerrit.RESOURCES.warning());
        status.setTitle(problems(Util.C.pushCertOk(), certInfo));
        break;
      case TRUSTED:
        status.setResource(Gerrit.RESOURCES.greenCheck());
        status.setTitle(Util.C.pushCertTrusted());
        break;
    }
  }

  private static String name(AccountInfo info) {
    return info.name() != null ? info.name() : Gerrit.info().user().anonymousCowardName();
  }

  private static String email(AccountInfo info, String name) {
    return info.email() != null ? info.email() : name;
  }

  private static String problems(String msg, PushCertificateInfo info) {
    if (info.key() == null || !info.key().hasProblems() || info.key().problems().length() == 0) {
      return msg;
    }

    StringBuilder sb = new StringBuilder();
    sb.append(msg).append(':');
    for (String problem : Natives.asList(info.key().problems())) {
      sb.append('\n').append(problem);
    }
    return sb.toString();
  }

  private void renderActionTextDate(ChangeInfo info) {
    String action;
    if (info.created().equals(info.updated())) {
      action = Util.C.changeInfoBlockUploaded();
    } else {
      action = Util.C.changeInfoBlockUpdated();
    }
    actionText.setInnerText(action);
    actionDate.setInnerText(FormatUtil.relativeFormat(info.updated()));
  }

  private void renderDiffBaseListBox(ChangeInfo info) {
    JsArray<RevisionInfo> list = info.revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(list);
    int selectedIdx = list.length();
    for (int i = list.length() - 1; i >= 0; i--) {
      RevisionInfo r = list.get(i);
      diffBase.addItem(r.id() + ": " + r.name().substring(0, 6), r.id());
      if (r.name().equals(revision)) {
        SelectElement.as(diffBase.getElement())
            .getOptions()
            .getItem(diffBase.getItemCount() - 1)
            .setDisabled(true);
      }
      if (base.isPatchSet() && base.asPatchSetId().get() == r._number()) {
        selectedIdx = diffBase.getItemCount() - 1;
      }
    }

    RevisionInfo rev = info.revisions().get(revision);
    JsArray<CommitInfo> parents = rev.commit().parents();
    if (parents.length() > 1) {
      diffBase.addItem(Util.C.autoMerge(), DiffObject.AUTO_MERGE);
      for (int i = 0; i < parents.length(); i++) {
        int parentNum = i + 1;
        diffBase.addItem(Util.M.diffBaseParent(parentNum), String.valueOf(-parentNum));
      }

      if (base.isParent()) {
        selectedIdx = list.length() + base.getParentNum();
      }
    } else {
      diffBase.addItem(Util.C.baseDiffItem(), "");
    }

    diffBase.setSelectedIndex(selectedIdx);
  }

  void showUpdates(ChangeInfo newInfo) {
    if (!isAttached() || newInfo.updated().equals(lastDisplayedUpdate)) {
      return;
    }

    JsArray<MessageInfo> om = changeInfo.messages();
    JsArray<MessageInfo> nm = newInfo.messages();

    if (om == null) {
      om = JsArray.createArray().cast();
    }
    if (nm == null) {
      nm = JsArray.createArray().cast();
    }

    if (om.length() == nm.length()) {
      return;
    }

    if (updateAvailable == null) {
      updateAvailable =
          new UpdateAvailableBar() {
            @Override
            void onShow() {
              Gerrit.display(PageLinks.toChange(changeId));
            }

            @Override
            void onIgnore(Timestamp newTime) {
              lastDisplayedUpdate = newTime;
            }
          };
    }
    updateAvailable.set(Natives.asList(nm).subList(om.length(), nm.length()), newInfo.updated());
    if (!updateAvailable.isAttached()) {
      add(updateAvailable);
    }
  }

  private void startPoller() {
    if (Gerrit.isSignedIn() && 0 < Gerrit.info().change().updateDelay()) {
      updateCheck = new UpdateCheckTimer(this);
      updateCheck.schedule();
      handlers.add(UserActivityMonitor.addValueChangeHandler(updateCheck));
    }
  }

  private static String normalize(String r) {
    return r != null && !r.isEmpty() ? r : null;
  }

  /**
   * @param parentToken
   * @return 1-based parentNum if parentToken is a String which can be parsed as a negative integer
   *     i.e. "-1", "-2", etc. If parentToken cannot be parsed as a negative integer, return zero.
   */
  private static int toParentNum(String parentToken) {
    try {
      int n = Integer.parseInt(parentToken);
      if (n < 0) {
        return -n;
      }
      return 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
