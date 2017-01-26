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

  var STORAGE_DEBOUNCE_INTERVAL = 400;

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

    attached: function() {
      if (this.editing) {
        this.collapsed = false;
      } else if (this.comment) {
        this.collapsed = this.comment.collapsed;
      }
    },

    detached: function() {
      this.cancelDebouncer('fire-update');
    },

    _computeShowHideText: function(collapsed) {
      return collapsed ? '◀' : '▼';
    },

    _calculateActionstoShow: function(showActions, isRobotComment) {
      this._showHumanActions = showActions && !isRobotComment;
      this._showRobotActions = showActions && isRobotComment;
    },

    _isRobotComment: function(comment) {
      this.isRobotComment = !!comment.robot_id;
    },

    save: function() {
      this.comment.message = this._messageText;
      this.disabled = true;

      this._eraseDraftComment();

      this._xhrPromise = this._saveDraft(this.comment).then(function(response) {
        this.disabled = false;
        if (!response.ok) { return response; }

        return this.$.restAPI.getResponseObject(response).then(function(obj) {
          var comment = obj;
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
        }.bind(this));
      }.bind(this)).catch(function(err) {
        this.disabled = false;
        throw err;
      }.bind(this));
    },

    _eraseDraftComment: function() {
      this.$.storage.eraseDraftComment({
        changeNum: this.changeNum,
        patchNum: this.patchNum,
        path: this.comment.path,
        line: this.comment.line,
        range: this.comment.range,
      });
    },

    _commentChanged: function(comment) {
      this.editing = !!comment.__editing;
      this.resolved = !comment.unresolved;
      if (this.editing) { // It's a new draft/reply, notify.
        this._fireUpdate();
      }
    },

    _getCommentClass: function(comment) {
      var commentClass = 'message';
      if (!comment.__draft) {
        commentClass += ' short';
      }
      return commentClass;
    },

    _getEventPayload: function(opt_mixin) {
      var payload = {
        comment: this.comment,
        patchNum: this.patchNum,
      };
      for (var k in opt_mixin) {
        payload[k] = opt_mixin[k];
      }
      return payload;
    },

    _fireSave: function() {
      this.fire('comment-save', this._getEventPayload());
    },

    _fireUpdate: function() {
      this.debounce('fire-update', function() {
        this.fire('comment-update', this._getEventPayload());
      });
    },

    _draftChanged: function(draft) {
      this.$.container.classList.toggle('draft', draft);
    },

    _editingChanged: function(editing, previousValue) {
      this.$.container.classList.toggle('editing', editing);
      if (editing) {
        var textarea = this.$.editTextarea.textarea;
        // Put the cursor at the end always.
        textarea.selectionStart = textarea.value.length;
        textarea.selectionEnd = textarea.selectionStart;
        this.async(function() {
          textarea.focus();
        }.bind(this));
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

    _computeLinkToComment: function(comment) {
      return '#' + comment.line;
    },

    _computeSaveDisabled: function(draft) {
      return draft == null || draft.trim() == '';
    },

    _handleTextareaKeydown: function(e) {
      switch (e.keyCode) {
        case 27: // 'esc'
          if (this._messageText.length === 0) {
            this._handleCancel(e);
          }
          break;
        case 83: // 's'
          if (e.ctrlKey) {
            this._handleSave(e);
          }
          break;
      }
    },

    _handleToggleCollapsed: function() {
      this.collapsed = !this.collapsed;
    },

    _toggleCollapseClass: function(collapsed) {
      if (collapsed) {
        this.$.container.classList.add('collapsed');
      } else {
        this.$.container.classList.remove('collapsed');
      }
    },

    _commentMessageChanged: function(message) {
      this._messageText = message || '';
    },

    _messageTextChanged: function(newValue, oldValue) {
      if (!this.comment || (this.comment && this.comment.id)) { return; }

      // Keep comment.message in sync so that gr-diff-comment-thread is aware
      // of the current message in the case that another comment is deleted.
      this.comment.message = this._messageText || '';
      this.debounce('store', function() {
        var message = this._messageText;

        var commentLocation = {
          changeNum: this.changeNum,
          patchNum: this.patchNum,
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

    _handleLinkTap: function(e) {
      e.preventDefault();
      var hash = this._computeLinkToComment(this.comment);
      // Don't add the hash to the window history if it's already there.
      // Otherwise you mess up expected back button behavior.
      if (window.location.hash == hash) { return; }
      // Change the URL but don’t trigger a nav event. Otherwise it will
      // reload the page.
      page.show(window.location.pathname + hash, null, false);
    },

    _handleReply: function(e) {
      e.preventDefault();
      this.fire('create-reply-comment', this._getEventPayload(),
          {bubbles: false});
    },

    _handleQuote: function(e) {
      e.preventDefault();
      this.fire('create-reply-comment', this._getEventPayload({quote: true}),
          {bubbles: false});
    },

    _handleFix: function(e) {
      e.preventDefault();
      this.fire('create-fix-comment', this._getEventPayload({quote: true}),
          {bubbles: false});
    },

    _handleAck: function(e) {
      e.preventDefault();
      this.fire('create-ack-comment', this._getEventPayload(),
          {bubbles: false});
    },

    _handleDone: function(e) {
      e.preventDefault();
      this.fire('create-done-comment', this._getEventPayload(),
          {bubbles: false});
    },

    _handleEdit: function(e) {
      e.preventDefault();
      this._messageText = this.comment.message;
      this.editing = true;
    },

    _handleSave: function(e) {
      e.preventDefault();
      this.set('comment.__editing', false);
      this.save();
    },

    _handleCancel: function(e) {
      e.preventDefault();
      if (this.comment.message === null ||
          this.comment.message.trim().length === 0) {
        this._fireDiscard();
        return;
      }
      this._messageText = this.comment.message;
      this.editing = false;
    },

    _fireDiscard: function() {
      this.cancelDebouncer('fire-update');
      this.fire('comment-discard', this._getEventPayload());
    },

    _handleDiscard: function(e) {
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

      this._xhrPromise = this._deleteDraft(this.comment).then(
          function(response) {
            this.disabled = false;
            if (!response.ok) { return response; }

            this._fireDiscard();
          }.bind(this)).catch(function(err) {
            this.disabled = false;
            throw err;
          }.bind(this));
    },

    _saveDraft: function(draft) {
      return this.$.restAPI.saveDiffDraft(this.changeNum, this.patchNum, draft);
    },

    _deleteDraft: function(draft) {
      return this.$.restAPI.deleteDiffDraft(this.changeNum, this.patchNum,
          draft);
    },

    _loadLocalDraft: function(changeNum, patchNum, comment) {
      // Only apply local drafts to comments that haven't been saved
      // remotely, and haven't been given a default message already.
      //
      // Don't get local draft if there is another comment that is currently
      // in an editing state.
      if (!comment || comment.id || comment.message || comment.__otherEditing) {
        delete comment.__otherEditing;
        return;
      }

      var draft = this.$.storage.getDraftComment({
        changeNum: changeNum,
        patchNum: patchNum,
        path: comment.path,
        line: comment.line,
        range: comment.range,
      });

      if (draft) {
        this.set('comment.message', draft.message);
      }
    },

    _handleMouseEnter: function(e) {
      this.fire('comment-mouse-over', this._getEventPayload());
    },

    _handleMouseLeave: function(e) {
      this.fire('comment-mouse-out', this._getEventPayload());
    },

    _handleToggleResolved: function() {
      this.resolved = !this.resolved;
    },

    _toggleResolved: function(resolved) {
      this.comment.unresolved = !resolved;
      this.fire('comment-update', this._getEventPayload());
    },
  });
})();
