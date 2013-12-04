// Copyright (C) 2014 Digia Plc and/or its subsidiary(-ies).
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.patches.PatchUtil;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.globalkey.client.KeyCommand;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

public abstract class ContentTableKeyNavigation extends AbstractKeyNavigation {
  private class InsertCommentCommand extends NeedsSignInKeyCommand {
    public InsertCommentCommand(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onInsertComment();
    }
  }

  private class NextChunkKeyCmd extends KeyCommand {
    public NextChunkKeyCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onChunkNext();
    }
  }

  private class NextCommentCmd extends KeyCommand {
    public NextCommentCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onCommentNext();
    }
  }

  private class PrevChunkKeyCmd extends KeyCommand {
    public PrevChunkKeyCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onChunkPrev();
    }
  }

  private class PrevCommentCmd extends KeyCommand {
    public PrevCommentCmd(int mask, int key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onCommentPrev();
    }
  }

  private class PublishCommentsKeyCommand extends NeedsSignInKeyCommand {
    public PublishCommentsKeyCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(final KeyPressEvent event) {
      onPublishComments();
    }
  }

  private final KeyCommandSet keysComment;
  private HandlerRegistration regComment;
  public ContentTableKeyNavigation(final Widget parent) {
    super(parent);

    if (Gerrit.isSignedIn()) {
      keysComment = new KeyCommandSet(PatchUtil.C.commentEditorSet());
    } else {
      keysComment = null;
    }

    setKeyHelp(Action.NEXT, PatchUtil.C.lineNext());
    setKeyHelp(Action.PREV, PatchUtil.C.linePrev());
    setKeyHelp(Action.OPEN, PatchUtil.C.expandComment());
  }

  public void initializeKeys() {
    super.initializeKeys();

    keysNavigation.add(new PrevChunkKeyCmd(0, 'p', PatchUtil.C.chunkPrev()));
    keysNavigation.add(new NextChunkKeyCmd(0, 'n', PatchUtil.C.chunkNext()));
    keysNavigation.add(new PrevCommentCmd(0, 'P', PatchUtil.C.commentPrev()));
    keysNavigation.add(new NextCommentCmd(0, 'N', PatchUtil.C.commentNext()));

    if (Gerrit.isSignedIn()) {
      keysAction.add(new InsertCommentCommand(0, 'c', PatchUtil.C
          .commentInsert()));
      keysAction.add(new PublishCommentsKeyCommand(0, 'r', Util.C
          .keyPublishComments()));

      // See CommentEditorPanel
      //
      keysComment.add(new NoOpKeyCommand(KeyCommand.M_CTRL, 's', PatchUtil.C
          .commentSaveDraft()));
    }
  }

  public void setRegisterKeys(final boolean on) {
    super.setRegisterKeys(on);
    if (on) {
      if (Gerrit.isSignedIn()) {
        regComment = GlobalKey.add(parent, keysComment);
      }
    } else {
      if (regComment != null) {
        regComment.removeHandler();
        regComment = null;
      }
    }
  }

  protected void onCancelEdit() {}

  protected void onChunkNext() {}

  protected void onChunkPrev() {}

  protected void onCommentNext() {}

  protected void onCommentPrev() {}

  protected void onInsertComment() {}

  protected void onPublishComments() {}

  protected void onSaveDraft() {}
}

