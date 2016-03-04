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
    is: 'gr-diff-comment-thread',

    /**
     * Fired when the height of the thread changes.
     *
     * @event height-change
     */

    /**
     * Fired when the thread should be discarded.
     *
     * @event discard
     */

    properties: {
      changeNum: String,
      comments: {
        type: Array,
        value: function() { return []; },
      },
      patchNum: String,
      path: String,
      showActions: Boolean,
      projectConfig: Object,

      _boundWindowResizeHandler: {
        type: Function,
        value: function() { return this._handleWindowResize.bind(this); }
      },
      _lastHeight: Number,
      _orderedComments: Array,
    },

    get naturalHeight() {
      return this.$.container.offsetHeight;
    },

    observers: [
      '_commentsChanged(comments.splices)',
    ],

    attached: function() {
      window.addEventListener('resize', this._boundWindowResizeHandler);
    },

    detached: function() {
      window.removeEventListener('resize', this._boundWindowResizeHandler);
    },

    _handleWindowResize: function(e) {
      this._heightChanged();
    },

    _commentsChanged: function(changeRecord) {
      this._orderedComments = this._sortedComments(this.comments);
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
      return results;
    },

    _visitComment: function(parent, commentIDToReplies, results) {
      results.push(parent);

      var replies = commentIDToReplies[parent.id];
      if (!replies) { return; }
      for (var i = 0; i < replies.length; i++) {
        this._visitComment(replies[i], commentIDToReplies, results);
      }
    },

    _handleCommentHeightChange: function(e) {
      e.stopPropagation();
      this._heightChanged();
    },

    _handleCommentReply: function(e) {
      var comment = e.detail.comment;
      var quoteStr;
      if (e.detail.quote) {
        var msg = comment.message;
        var quoteStr = msg.split('\n').map(
            function(line) { return ' > ' + line; }).join('\n') + '\n\n';
      }
      var reply =
          this._newReply(comment.id, comment.line, this.path, quoteStr);
      this.push('comments', reply);

      // Allow the reply to render in the dom-repeat.
      this.async(function() {
        var commentEl = this._commentElWithDraftID(reply.__draftID);
        commentEl.editing = true;
        this.async(this._heightChanged.bind(this), 1);
      }.bind(this), 1);
    },

    _handleCommentDone: function(e) {
      var comment = e.detail.comment;
      var reply = this._newReply(comment.id, comment.line, this.path, 'Done');
      this.push('comments', reply);

      // Allow the reply to render in the dom-repeat.
      this.async(function() {
        var commentEl = this._commentElWithDraftID(reply.__draftID);
        commentEl.save();
        this.async(this._heightChanged.bind(this), 1);
      }.bind(this), 1);
    },

    _commentElWithDraftID: function(draftID) {
      var commentEls =
          Polymer.dom(this.root).querySelectorAll('gr-diff-comment');
      for (var i = 0; i < commentEls.length; i++) {
        if (commentEls[i].comment.__draftID == draftID) {
          return commentEls[i];
        }
      }
      return null;
    },

    _newReply: function(inReplyTo, line, path, opt_message) {
      var c = {
        __draft: true,
        __draftID: Math.random().toString(36),
        __date: new Date(),
        line: line,
        path: path,
        in_reply_to: inReplyTo,
      };
      if (opt_message != null) {
        c.message = opt_message;
      }
      return c;
    },

    _handleCommentDiscard: function(e) {
      // TODO(andybons): In Shadow DOM, the event bubbles up, while in Shady
      // DOM, it respects the bubbles property.
      // https://github.com/Polymer/polymer/issues/3226
      e.stopPropagation();
      var diffCommentEl = Polymer.dom(e).rootTarget;
      var idx = this._indexOf(diffCommentEl.comment, this.comments);
      if (idx == -1) {
        throw Error('Cannot find comment ' +
            JSON.stringify(diffCommentEl.comment));
      }
      this.splice('comments', idx, 1);
      if (this.comments.length == 0) {
        this.fire('discard', null, {bubbles: false});
        return;
      }
      this.async(this._heightChanged.bind(this), 1);
    },

    _heightChanged: function() {
      var height = this.$.container.offsetHeight;
      if (height == this._lastHeight) { return; }

      this.fire('height-change', {height: height}, {bubbles: false});
      this._lastHeight = height;
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
