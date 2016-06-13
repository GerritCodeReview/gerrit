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
  var UPDATE_DEBOUNCE_INTERVAL = 500;

  Polymer({
    is: 'gr-diff-comment',

    /**
     * Fired when the Reply action is triggered.
     *
     * @event reply
     */

    /**
     * Fired when the Done action is triggered.
     *
     * @event done
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
      patchNum: String,
      showActions: Boolean,
      projectConfig: Object,

      _xhrPromise: Object,  // Used for testing.
      _messageText: {
        type: String,
        value: '',
        observer: '_messageTextChanged',
      },
    },

    observers: [
      '_commentMessageChanged(comment.message)',
      '_loadLocalDraft(changeNum, patchNum, comment)',
    ],

    detached: function() {
      this.cancelDebouncer('fire-update');
    },

    save: function() {
      this.comment.message = this._messageText;
      this.disabled = true;

      this.$.storage.eraseDraftComment({
        changeNum: this.changeNum,
        patchNum: this.patchNum,
        path: this.comment.path,
        line: this.comment.line,
      });

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

    _commentChanged: function(comment) {
      this.editing = !!comment.__editing;
      if (this.editing) { // It's a new draft/reply, notify.
        this._fireUpdate();
      }
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
      }, UPDATE_DEBOUNCE_INTERVAL);
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
      if (e.keyCode == 27) {  // 'esc'
        this._handleCancel(e);
      }
    },

    _commentMessageChanged: function(message) {
      this._messageText = message || '';
    },

    _messageTextChanged: function(newValue, oldValue) {
      if (!this.comment || (this.comment && this.comment.id)) { return; }

      this.debounce('store', function() {
        var message = this._messageText;

        var commentLocation = {
          changeNum: this.changeNum,
          patchNum: this.patchNum,
          path: this.comment.path,
          line: this.comment.line,
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
      this._preventDefaultAndBlur(e);
      this.fire('reply', this._getEventPayload(), {bubbles: false});
    },

    _handleQuote: function(e) {
      this._preventDefaultAndBlur(e);
      this.fire(
          'reply', this._getEventPayload({quote: true}), {bubbles: false});
    },

    _handleDone: function(e) {
      this._preventDefaultAndBlur(e);
      this.fire('done', this._getEventPayload(), {bubbles: false});
    },

    _handleEdit: function(e) {
      this._preventDefaultAndBlur(e);
      this._messageText = this.comment.message;
      this.editing = true;
    },

    _handleSave: function(e) {
      this._preventDefaultAndBlur(e);
      this.save();
    },

    _handleCancel: function(e) {
      this._preventDefaultAndBlur(e);
      if (this.comment.message == null || this.comment.message.length == 0) {
        this._fireDiscard();
        return;
      }
      this._messageText = this.comment.message;
      this.editing = false;
    },

    _fireDiscard: function() {
      this.fire('comment-discard', this._getEventPayload());
    },

    _handleDiscard: function(e) {
      this._preventDefaultAndBlur(e);
      if (!this.comment.__draft) {
        throw Error('Cannot discard a non-draft comment.');
      }
      this.editing = false;
      this.disabled = true;
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

    _preventDefaultAndBlur: function(e) {
      e.preventDefault();
      Polymer.dom(e).rootTarget.blur();
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
      if (!comment || comment.id || comment.message) {
        return;
      }

      var draft = this.$.storage.getDraftComment({
        changeNum: changeNum,
        patchNum: patchNum,
        path: comment.path,
        line: comment.line,
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
  });
})();
