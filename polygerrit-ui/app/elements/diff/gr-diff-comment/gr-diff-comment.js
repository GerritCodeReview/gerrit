// Copyright (C) 2016 The Android Open Source Project
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
(function() {
  'use strict';

  const STORAGE_DEBOUNCE_INTERVAL = 400;

  Polymer({
    is: 'gr-diff-comment',

    /**
     * Fired when the create fix comment action is triggered.
     *
     * @event create-fix-comment
     */

    /**
     * Fired when this comment is discarded.
     *
     * @event comment-discard
     */

    /**
     * Fired when this comment is saved.
     *
     * @event comment-save
     */

    /**
     * Fired when this comment is updated.
     *
     * @event comment-update
     */

    /**
     * @event comment-mouse-over
     */

    /**
     * @event comment-mouse-out
     */

    properties: {
      changeNum: String,
      comment: {
        type: Object,
        notify: true,
        observer: '_commentChanged',
      },
      isRobotComment: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      disabled: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      draft: {
        type: Boolean,
        value: false,
        observer: '_draftChanged',
      },
      editing: {
        type: Boolean,
        value: false,
        observer: '_editingChanged',
      },
      hasChildren: Boolean,
      patchNum: String,
      showActions: Boolean,
      _showHumanActions: Boolean,
      _showRobotActions: Boolean,
      collapsed: {
        type: Boolean,
        value: true,
        observer: '_toggleCollapseClass',
      },
      projectConfig: Object,
      robotButtonDisabled: Boolean,
      _isAdmin: {
        type: Boolean,
        value: false,
      },

      _xhrPromise: Object,  // Used for testing.
      _messageText: {
        type: String,
        value: '',
        observer: '_messageTextChanged',
      },
      commentSide: String,

      resolved: {
        type: Boolean,
        observer: '_toggleResolved',
      },
    },

    observers: [
      '_commentMessageChanged(comment.message)',
      '_loadLocalDraft(changeNum, patchNum, comment)',
      '_isRobotComment(comment)',
      '_calculateActionstoShow(showActions, isRobotComment)',
    ],

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    keyBindings: {
      'ctrl+enter meta+enter ctrl+s meta+s': '_handleSaveKey',
      'esc': '_handleEsc',
    },

    attached() {
      if (this.editing) {
        this.collapsed = false;
      } else if (this.comment) {
        this.collapsed = this.comment.collapsed;
      }
      this._getIsAdmin().then(isAdmin => {
        this._isAdmin = isAdmin;
      });
    },

    detached() {
      this.cancelDebouncer('fire-update');
      this.$.editTextarea.closeDropdown();
    },

    _computeShowHideText(collapsed) {
      return collapsed ? '◀' : '▼';
    },

    _calculateActionstoShow(showActions, isRobotComment) {
      this._showHumanActions = showActions && !isRobotComment;
      this._showRobotActions = showActions && isRobotComment;
    },

    _isRobotComment(comment) {
      this.isRobotComment = !!comment.robot_id;
    },

    isOnParent() {
      return this.side === 'PARENT';
    },

    _getIsAdmin() {
      return this.$.restAPI.getIsAdmin();
    },

    save() {
      this.comment.message = this._messageText;

      this.disabled = true;

      this._eraseDraftComment();

      this._xhrPromise = this._saveDraft(this.comment).then(response => {
        this.disabled = false;
        if (!response.ok) { return response; }

        return this.$.restAPI.getResponseObject(response).then(obj => {
          const comment = obj;
          comment.__draft = true;
          // Maintain the ephemeral draft ID for identification by other
          // elements.
          if (this.comment.__draftID) {
            comment.__draftID = this.comment.__draftID;
          }
          comment.__commentSide = this.commentSide;
          this.comment = comment;
          this.editing = false;
          this._fireSave();
          return obj;
        });
      }).catch(err => {
        this.disabled = false;
        throw err;
      });
    },

    _eraseDraftComment() {
      // Prevents a race condition in which removing the draft comment occurs
      // prior to it being saved.
      this.cancelDebouncer('store');

      this.$.storage.eraseDraftComment({
        changeNum: this.changeNum,
        patchNum: this._getPatchNum(),
        path: this.comment.path,
        line: this.comment.line,
        range: this.comment.range,
      });
    },

    _commentChanged(comment) {
      this.editing = !!comment.__editing;
      this.resolved = !comment.unresolved;
      if (this.editing) { // It's a new draft/reply, notify.
        this._fireUpdate();
      }
    },

    _getEventPayload(opt_mixin) {
      return Object.assign({}, opt_mixin, {
        comment: this.comment,
        patchNum: this.patchNum,
      });
    },

    _fireSave() {
      this.fire('comment-save', this._getEventPayload());
    },

    _fireUpdate() {
      this.debounce('fire-update', () => {
        this.fire('comment-update', this._getEventPayload());
      });
    },

    _draftChanged(draft) {
      this.$.container.classList.toggle('draft', draft);
    },

    _editingChanged(editing, previousValue) {
      this.$.container.classList.toggle('editing', editing);
      if (editing) {
        this.$.editTextarea.putCursorAtEnd();
      }
      if (this.comment && this.comment.id) {
        this.$$('.cancel').hidden = !editing;
      }
      if (this.comment) {
        this.comment.__editing = this.editing;
      }
      if (editing != !!previousValue) {
        // To prevent event firing on comment creation.
        this._fireUpdate();
      }
    },

    _computeLinkToComment(comment) {
      return '#' + comment.line;
    },

    _computeDeleteButtonClass(isAdmin, draft) {
      return isAdmin && !draft ? 'showDeleteButtons' : '';
    },

    _computeSaveDisabled(draft) {
      return draft == null || draft.trim() == '';
    },

    _handleSaveKey(e) {
      const target = this.getRootTarget(e);
      // Target is usually textarea, but could also be gr-textarea.
      if (target.localName.includes('textarea') && this._messageText.length) {
        e.preventDefault();
        this._handleSave(e);
      }
    },

    _handleEsc(e) {
      const target = this.getRootTarget(e);
      // Target is usually textarea, but could also be gr-textarea.
      if (target.localName.includes('textarea') && !this._messageText.length) {
        e.preventDefault();
        this._handleCancel(e);
      }
    },

    _handleToggleCollapsed() {
      this.collapsed = !this.collapsed;
    },

    _toggleCollapseClass(collapsed) {
      if (collapsed) {
        this.$.container.classList.add('collapsed');
      } else {
        this.$.container.classList.remove('collapsed');
      }
    },

    _commentMessageChanged(message) {
      this._messageText = message || '';
    },

    _messageTextChanged(newValue, oldValue) {
      if (!this.comment || (this.comment && this.comment.id)) { return; }

      // Keep comment.message in sync so that gr-diff-comment-thread is aware
      // of the current message in the case that another comment is deleted.
      this.comment.message = this._messageText || '';
      this.debounce('store', () => {
        const message = this._messageText;

        const commentLocation = {
          changeNum: this.changeNum,
          patchNum: this._getPatchNum(),
          path: this.comment.path,
          line: this.comment.line,
          range: this.comment.range,
        };

        if ((!this._messageText || !this._messageText.length) && oldValue) {
          // If the draft has been modified to be empty, then erase the storage
          // entry.
          this.$.storage.eraseDraftComment(commentLocation);
        } else {
          this.$.storage.setDraftComment(commentLocation, message);
        }
        this._fireUpdate();
      }, STORAGE_DEBOUNCE_INTERVAL);
    },

    _handleLinkTap(e) {
      e.preventDefault();
      const hash = this._computeLinkToComment(this.comment);
      // Don't add the hash to the window history if it's already there.
      // Otherwise you mess up expected back button behavior.
      if (window.location.hash == hash) { return; }
      // Change the URL but don’t trigger a nav event. Otherwise it will
      // reload the page.
      page.show(window.location.pathname + hash, null, false);
    },

    _handleReply(e) {
      e.preventDefault();
      this.fire('create-reply-comment', this._getEventPayload(),
          {bubbles: false});
    },

    _handleQuote(e) {
      e.preventDefault();
      this.fire('create-reply-comment', this._getEventPayload({quote: true}),
          {bubbles: false});
    },

    _handleFix(e) {
      e.preventDefault();
      this.fire('create-fix-comment', this._getEventPayload({quote: true}),
          {bubbles: false});
    },

    _handleAck(e) {
      e.preventDefault();
      this.fire('create-ack-comment', this._getEventPayload(),
          {bubbles: false});
    },

    _handleDone(e) {
      e.preventDefault();
      this.fire('create-done-comment', this._getEventPayload(),
          {bubbles: false});
    },

    _handleEdit(e) {
      e.preventDefault();
      this._messageText = this.comment.message;
      this.editing = true;
    },

    _handleSave(e) {
      e.preventDefault();
      this.set('comment.__editing', false);
      this.save();
    },

    _handleCancel(e) {
      e.preventDefault();
      if (!this.comment.message || this.comment.message.trim().length === 0) {
        this._fireDiscard();
        return;
      }
      this._messageText = this.comment.message;
      this.editing = false;
    },

    _fireDiscard() {
      this.cancelDebouncer('fire-update');
      this.fire('comment-discard', this._getEventPayload());
    },

    _handleDiscard(e) {
      e.preventDefault();
      if (!this.comment.__draft) {
        throw Error('Cannot discard a non-draft comment.');
      }
      this.editing = false;
      this.disabled = true;
      this._eraseDraftComment();

      if (!this.comment.id) {
        this.disabled = false;
        this._fireDiscard();
        return;
      }

      this._xhrPromise = this._deleteDraft(this.comment).then(response => {
        this.disabled = false;
        if (!response.ok) { return response; }

        this._fireDiscard();
      }).catch(err => {
        this.disabled = false;
        throw err;
      });
    },

    _saveDraft(draft) {
      return this.$.restAPI.saveDiffDraft(this.changeNum, this.patchNum, draft);
    },

    _deleteDraft(draft) {
      return this.$.restAPI.deleteDiffDraft(this.changeNum, this.patchNum,
          draft);
    },

    _getPatchNum() {
      return this.isOnParent() ? 'PARENT' : this.patchNum;
    },

    _loadLocalDraft(changeNum, patchNum, comment) {
      // Only apply local drafts to comments that haven't been saved
      // remotely, and haven't been given a default message already.
      //
      // Don't get local draft if there is another comment that is currently
      // in an editing state.
      if (!comment || comment.id || comment.message || comment.__otherEditing) {
        delete comment.__otherEditing;
        return;
      }

      const draft = this.$.storage.getDraftComment({
        changeNum,
        patchNum: this._getPatchNum(),
        path: comment.path,
        line: comment.line,
        range: comment.range,
      });

      if (draft) {
        this.set('comment.message', draft.message);
      }
    },

    _handleMouseEnter(e) {
      this.fire('comment-mouse-over', this._getEventPayload());
    },

    _handleMouseLeave(e) {
      this.fire('comment-mouse-out', this._getEventPayload());
    },

    _handleToggleResolved() {
      this.resolved = !this.resolved;
    },

    _toggleResolved(resolved) {
      this.comment.unresolved = !resolved;
      this.fire('comment-update', this._getEventPayload());
    },

    _handleCommentDelete() {
      Polymer.dom(Gerrit.getRootElement()).appendChild(this.$.overlay);
      this.async(() => {
        this.$.overlay.open();
      }, 1);
    },

    _handleCancelDeleteComment() {
      Polymer.dom(Gerrit.getRootElement()).removeChild(this.$.overlay);
      this.$.overlay.close();
    },

    _handleConfirmDeleteComment() {
      this.$.restAPI.deleteComment(
          this.changeNum, this.patchNum, this.comment.id,
          this.$.confirmDeleteComment.message).then(newComment => {
            this._handleCancelDeleteComment();
            this.comment = newComment;
          });
    },
  });
})();
