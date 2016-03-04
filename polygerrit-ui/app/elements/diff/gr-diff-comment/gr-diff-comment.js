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

  Polymer({
    is: 'gr-diff-comment',

    /**
     * Fired when the height of the comment changes.
     *
     * @event height-change
     */

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
     * @event discard
     */

    properties: {
      changeNum: String,
      comment: {
        type: Object,
        notify: true,
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
      _editDraft: String,
    },

    ready: function() {
      this._editDraft = (this.comment && this.comment.message) || '';
      this.editing = this._editDraft.length == 0;
    },

    attached: function() {
      this._heightChanged();
    },

    save: function() {
      this.comment.message = this._editDraft;
      this.disabled = true;
      var endpoint = this._restEndpoint(this.comment.id);
      this._send('PUT', endpoint).then(function(req) {
        this.disabled = false;
        var comment = req.response;
        comment.__draft = true;
        // Maintain the ephemeral draft ID for identification by other
        // elements.
        if (this.comment.__draftID) {
          comment.__draftID = this.comment.__draftID;
        }
        this.comment = comment;
        this.editing = false;
      }.bind(this)).catch(function(err) {
        alert('Your draft couldn’t be saved. Check the console and contact ' +
            'the PolyGerrit team for assistance.');
        this.disabled = false;
      }.bind(this));
    },

    _heightChanged: function() {
      this.async(function() {
        this.fire('height-change', {height: this.offsetHeight},
            {bubbles: false});
      }.bind(this));
    },

    _draftChanged: function(draft) {
      this.$.container.classList.toggle('draft', draft);
    },

    _editingChanged: function(editing) {
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
      this._heightChanged();
    },

    _computeLinkToComment: function(comment) {
      return '#' + comment.line;
    },

    _computeSaveDisabled: function(draft) {
      return draft == null || draft.trim() == '';
    },

    _handleTextareaKeyup: function(e) {
      // TODO(andybons): This isn't always true, but I can't currently think
      // of a better metric.
      this._heightChanged();
    },

    _handleTextareaKeydown: function(e) {
      if (e.keyCode == 27) {  // 'esc'
        this._handleCancel(e);
      }
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
      this.fire('reply', {comment: this.comment}, {bubbles: false});
    },

    _handleQuote: function(e) {
      this._preventDefaultAndBlur(e);
      this.fire('reply', {comment: this.comment, quote: true},
          {bubbles: false});
    },

    _handleDone: function(e) {
      this._preventDefaultAndBlur(e);
      this.fire('done', {comment: this.comment}, {bubbles: false});
    },

    _handleEdit: function(e) {
      this._preventDefaultAndBlur(e);
      this._editDraft = this.comment.message;
      this.editing = true;
    },

    _handleSave: function(e) {
      this._preventDefaultAndBlur(e);
      this.save();
    },

    _handleCancel: function(e) {
      this._preventDefaultAndBlur(e);
      if (this.comment.message == null || this.comment.message.length == 0) {
        this.fire('discard', null, {bubbles: false});
        return;
      }
      this._editDraft = this.comment.message;
      this.editing = false;
    },

    _handleDiscard: function(e) {
      this._preventDefaultAndBlur(e);
      if (!this.comment.__draft) {
        throw Error('Cannot discard a non-draft comment.');
      }
      this.disabled = true;
      var commentID = this.comment.id;
      if (!commentID) {
        this.fire('discard', null, {bubbles: false});
        return;
      }
      this._send('DELETE', this._restEndpoint(commentID)).then(function(req) {
        this.fire('discard', null, {bubbles: false});
      }.bind(this)).catch(function(err) {
        alert('Your draft couldn’t be deleted. Check the console and ' +
            'contact the PolyGerrit team for assistance.');
        this.disabled = false;
      }.bind(this));
    },

    _preventDefaultAndBlur: function(e) {
      e.preventDefault();
      Polymer.dom(e).rootTarget.blur();
    },

    _send: function(method, url) {
      var xhr = document.createElement('gr-request');
      var opts = {
        method: method,
        url: url,
      };
      if (method == 'PUT' || method == 'POST') {
        opts.body = this.comment;
      }
      this._xhrPromise = xhr.send(opts);
      return this._xhrPromise;
    },

    _restEndpoint: function(id) {
      var path = '/changes/' + this.changeNum + '/revisions/' +
          this.patchNum + '/drafts';
      if (id) {
        path += '/' + id;
      }
      return path;
    },
  });
})();
