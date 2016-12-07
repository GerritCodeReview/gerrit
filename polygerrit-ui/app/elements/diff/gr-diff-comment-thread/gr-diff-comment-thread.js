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

  var NEWLINE_PATTERN = '/\n/g';

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
      keyEventTarget: {
        type: Object,
        value: function() { return document.body; },
      },
      patchNum: String,
      path: String,
      projectConfig: Object,
      side: {
        type: String,
        value: 'REVISION',
      },

      _showActions: Boolean,
      _orderedComments: Array,
    },

    behaviors: [
      Gerrit.KeyboardShortcutBehavior,
    ],

    listeners: {
      'comment-update': '_handleCommentUpdate',
    },

    observers: [
      '_commentsChanged(comments.splices)',
    ],

    keyBindings: {
      'e shift+e': '_handleEKey',
    },

    attached: function() {
      this._getLoggedIn().then(function(loggedIn) {
        this._showActions = loggedIn;
      }.bind(this));
    },

    addOrEditDraft: function(opt_lineNum) {
      var lastComment = this.comments[this.comments.length - 1];
      if (lastComment && lastComment.__draft) {
        var commentEl = this._commentElWithDraftID(
            lastComment.id || lastComment.__draftID);
        commentEl.editing = true;
      } else {
        this.addDraft(opt_lineNum);
      }
    },

    addDraft: function(opt_lineNum, opt_range) {
      var draft = this._newDraft(opt_lineNum, opt_range);
      draft.__editing = true;
      this.push('comments', draft);
    },

    _getLoggedIn: function() {
      return this.$.restAPI.getLoggedIn();
    },

    _commentsChanged: function(changeRecord) {
      this._orderedComments = this._sortedComments(this.comments);
    },

    _handleEKey: function(e) {
      if (this.shouldSuppressKeyboardShortcut(e)) { return; }

      // Don’t preventDefault in this case because it will render the event
      // useless for other handlers (other gr-diff-comment-thread elements).
      this._expandCollapseComments(e.detail.keyboardEvent.shiftKey);
    },

    _expandCollapseComments: function(actionIsCollapse) {
      var comments =
          Polymer.dom(this.root).querySelectorAll('gr-diff-comment');
      comments.forEach(function(comment) {
        comment.collapsed = actionIsCollapse;
      });
    },

    _sortedComments: function(comments) {
      comments.sort(function(c1, c2) {
        var c1Date = c1.__date || util.parseDate(c1.updated);
        var c2Date = c2.__date || util.parseDate(c2.updated);
        return c1Date - c2Date;
      });

      var commentIDToReplies = {};
      var topLevelComments = [];
      for (var i = 0; i < comments.length; i++) {
        var c = comments[i];
        if (c.in_reply_to) {
          if (commentIDToReplies[c.in_reply_to] == null) {
            commentIDToReplies[c.in_reply_to] = [];
          }
          commentIDToReplies[c.in_reply_to].push(c);
        } else {
          topLevelComments.push(c);
        }
      }
      var results = [];
      for (var i = 0; i < topLevelComments.length; i++) {
        this._visitComment(topLevelComments[i], commentIDToReplies, results);
      }
      for (var missingCommentId in commentIDToReplies) {
        results = results.concat(commentIDToReplies[missingCommentId]);
      }
      return results;
    },

    _visitComment: function(parent, commentIDToReplies, results) {
      results.push(parent);

      var replies = commentIDToReplies[parent.id];
      delete commentIDToReplies[parent.id];
      if (!replies) { return; }
      for (var i = 0; i < replies.length; i++) {
        this._visitComment(replies[i], commentIDToReplies, results);
      }
    },

    _handleCommentReply: function(e) {
      var comment = e.detail.comment;
      var quoteStr;
      if (e.detail.quote) {
        var msg = comment.message;
        quoteStr = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
      }
      var reply = this._newReply(comment.id, comment.line, quoteStr);
      reply.__editing = true;
      this.push('comments', reply);
    },

    _createReplyComment: function(parent, content) {
      var reply = this._newReply(parent.id, parent.line, content);
      this.push('comments', reply);

      // Allow the reply to render in the dom-repeat.
      this.async(function() {
        var commentEl = this._commentElWithDraftID(reply.__draftID);
        commentEl.save();
      }.bind(this), 1);
    },

    _handleCommentAck: function(e) {
      var comment = e.detail.comment;
      this._createReplyComment(comment, 'Ack');
    },

    _handleCommentDone: function(e) {
      var comment = e.detail.comment;
      this._createReplyComment(comment, 'Done');
    },

    _handleCommentFix: function(e) {
      var comment = e.detail.comment;
      var msg = comment.message;
      var quoteStr = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
      var response = quoteStr + 'Please Fix';
      this._createReplyComment(comment, response);
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

    _newReply: function(inReplyTo, opt_lineNum, opt_message) {
      var d = this._newDraft(opt_lineNum);
      d.in_reply_to = inReplyTo;
      if (opt_message != null) {
        d.message = opt_message;
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
    },

    _handleCommentUpdate: function(e) {
      var comment = e.detail.comment;
      var index = this._indexOf(comment, this.comments);
      if (index === -1) {
        // This should never happen: comment belongs to another thread.
        console.error('Comment update for another comment thread.');
        return;
      }
      this.comments[index] = comment;
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
  });
})();
