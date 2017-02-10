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

  var UNRESOLVED_EXPAND_COUNT = 5;
  var NEWLINE_PATTERN = /\n/g;

  Polymer({
    is: 'gr-diff-comment-thread',

    /**
     * Fired when the thread should be discarded.
     *
     * @event thread-discard
     */

    properties: {
      changeNum: String,
      comments: {
        type: Array,
        value: function() { return []; },
      },
      locationRange: String,
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },
      commentSide: String,
      patchNum: String,
      path: String,
      projectConfig: Object,
      side: {
        type: String,
        value: 'REVISION',
      },

      _showActions: Boolean,
      _lastComment: Object,
      _orderedComments: Array,
      _unresolved: {
        type: Boolean,
        notify: true,
      },
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    listeners: {
      'comment-update': '_handleCommentUpdate',
    },

    observers: [
      '_commentsChanged(comments.*)',
    ],

    keyBindings: {
      'e shift+e': '_handleEKey',
    },

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._showActions = loggedIn;
      }.bind(this));
      this._setInitialExpandedState();
    },

    addOrEditDraft: function(opt_lineNum, opt_range) {
      var lastComment = this.comments[this.comments.length - 1] || {};
      if (lastComment.__draft) {
        var commentEl = this._commentElWithDraftID(
            lastComment.id || lastComment.__draftID);
        commentEl.editing = true;

        // If the comment was collapsed, re-open it to make it clear which
        // actions are available.
        commentEl.collapsed = false;
      } else {
        var range = opt_range ? opt_range :
            lastComment ? lastComment.range : undefined;
        var unresolved = lastComment ? lastComment.unresolved : undefined;
        this.addDraft(opt_lineNum, range, unresolved);
      }
    },

    addDraft: function(opt_lineNum, opt_range, opt_unresolved) {
      var draft = this._newDraft(opt_lineNum, opt_range);
      draft.__editing = true;
      draft.unresolved = opt_unresolved === false ? opt_unresolved : true;
      this.push('comments', draft);
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _commentsChanged: function(changeRecord) {
      this._orderedComments = this._sortedComments(this.comments);
      if (this._orderedComments.length) {
        this._lastComment = this._getLastComment();
        this._unresolved = this._lastComment.unresolved;
      }
    },

    _hideActions: function(_showActions, _lastComment) {
      return !_showActions || !_lastComment || !!_lastComment.__draft;
    },

    _getLastComment: function() {
      return this._orderedComments[this._orderedComments.length - 1] || {};
    },

    _handleEKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      // Don’t preventDefault in this case because it will render the event
      // useless for other handlers (other gr-diff-comment-thread elements).
      if (e.detail.keyboardEvent.shiftKey) {
        this._expandCollapseComments(true);
      } else {
        if (this.modifierPressed(e)) { return; }
        this._expandCollapseComments(false);
      }
    },

    _expandCollapseComments: function(actionIsCollapse) {
      var comments =
          Polymer.dom(this.root).querySelectorAll('gr-diff-comment');
      comments.forEach(function(comment) {
        comment.collapsed = actionIsCollapse;
      });
    },

    /**
     * Sets the initial state of the comment thread to have the last
     * {UNRESOLVED_EXPAND_COUNT} comments expanded by default if the
     * thread is unresolved.
     */
    _setInitialExpandedState: function() {
      var comment;
      if (this._orderedComments) {
        for (var i = 0; i < this._orderedComments.length; i++) {
          comment = this._orderedComments[i];
          comment.collapsed =
              this._orderedComments.length - i - 1 >= UNRESOLVED_EXPAND_COUNT ||
              !this._unresolved;
        }
      }

    },

    _sortedComments: function(comments) {
      return comments.slice().sort(function(c1, c2) {
        var c1Date = c1.__date || util.parseDate(c1.updated);
        var c2Date = c2.__date || util.parseDate(c2.updated);
        var dateCompare = c1Date - c2Date;
        // If same date, fall back to sorting by id.
        return dateCompare ? dateCompare : c1.id.localeCompare(c2.id);
      });
    },

    _createReplyComment: function(parent, content, opt_isEditing,
        opt_unresolved) {
      var reply = this._newReply(
          this._orderedComments[this._orderedComments.length - 1].id,
          parent.line,
          content,
          opt_unresolved,
          parent.range);

      // If there is currently a comment in an editing state, add an attribute
      // so that the gr-diff-comment knows not to populate the draft text.
      for (var i = 0; i < this.comments.length; i++) {
        if (this.comments[i].__editing) {
          reply.__otherEditing = true;
          break;
        }
      }

      if (opt_isEditing) {
        reply.__editing = true;
      }

      this.push('comments', reply);

      if (!opt_isEditing) {
        // Allow the reply to render in the dom-repeat.
        this.async(function() {
          var commentEl = this._commentElWithDraftID(reply.__draftID);
          commentEl.save();
        }, 1);
      }
    },

    _processCommentReply: function(opt_quote) {
      var comment = this._lastComment;
      var quoteStr;
      if (opt_quote) {
        var msg = comment.message;
        quoteStr = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
      }
      this._createReplyComment(comment, quoteStr, true, comment.unresolved);
    },

    _handleCommentReply: function(e) {
      this._processCommentReply();
    },

    _handleCommentQuote: function(e) {
      this._processCommentReply(true);
    },

    _handleCommentAck: function(e) {
      var comment = this._lastComment;
      this._createReplyComment(comment, 'Ack', false, comment.unresolved);
    },

    _handleCommentDone: function(e) {
      var comment = this._lastComment;
      this._createReplyComment(comment, 'Done', false, false);
    },

    _handleCommentFix: function(e) {
      var comment = e.detail.comment;
      var msg = comment.message;
      var quoteStr = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
      var response = quoteStr + 'Please Fix';
      this._createReplyComment(comment, response, false, true);
    },

    _commentElWithDraftID: function(id) {
      var els = Polymer.dom(this.root).querySelectorAll('gr-diff-comment');
      for (var i = 0; i < els.length; i++) {
        if (els[i].comment.id === id || els[i].comment.__draftID === id) {
          return els[i];
        }
      }
      return null;
    },

    _newReply: function(inReplyTo, opt_lineNum, opt_message, opt_unresolved,
          opt_range) {
      var d = this._newDraft(opt_lineNum);
      d.in_reply_to = inReplyTo;
      d.range = opt_range;
      if (opt_message != null) {
        d.message = opt_message;
      }
      if (opt_unresolved !== undefined) {
        d.unresolved = opt_unresolved;
      }
      return d;
    },

    _newDraft: function(opt_lineNum, opt_range) {
      var d = {
        __draft: true,
        __draftID: Math.random().toString(36),
        __date: new Date(),
        path: this.path,
        side: this.side,
        __commentSide: this.commentSide,
      };
      if (opt_lineNum) {
        d.line = opt_lineNum;
      }
      if (opt_range) {
        d.range = {
          start_line: opt_range.startLine,
          start_character: opt_range.startChar,
          end_line: opt_range.endLine,
          end_character: opt_range.endChar,
        };
      }
      return d;
    },

    _handleCommentDiscard: function(e) {
      var diffCommentEl = Polymer.dom(e).rootTarget;
      var comment = diffCommentEl.comment;
      var idx = this._indexOf(comment, this.comments);
      if (idx == -1) {
        throw Error('Cannot find comment ' +
            JSON.stringify(diffCommentEl.comment));
      }
      this.splice('comments', idx, 1);
      if (this.comments.length == 0) {
        this.fire('thread-discard', {lastComment: comment});
      }

      // Check to see if there are any other open comments getting edited and
      // set the local storage value to its message value.
      for (var i = 0; i < this.comments.length; i++) {
        if (this.comments[i].__editing) {
          var commentLocation = {
            changeNum: this.changeNum,
            patchNum: this.patchNum,
            path: this.comments[i].path,
            line: this.comments[i].line,
          };
          return this.$.storage.setDraftComment(commentLocation,
              this.comments[i].message);
        }
      }
    },

    _handleCommentUpdate: function(e) {
      var comment = e.detail.comment;
      var index = this._indexOf(comment, this.comments);
      if (index === -1) {
        // This should never happen: comment belongs to another thread.
        console.error('Comment update for another comment thread.');
        return;
      }
      this.set(['comments', index], comment);
    },

    _indexOf: function(comment, arr) {
      for (var i = 0; i < arr.length; i++) {
        var c = arr[i];
        if ((c.__draftID != null && c.__draftID == comment.__draftID) ||
            (c.id != null && c.id == comment.id)) {
          return i;
        }
      }
      return -1;
    },

    _computeHostClass: function(unresolved) {
      return unresolved ? 'unresolved' : '';
    },
  });
})();
