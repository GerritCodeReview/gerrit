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

  const UNRESOLVED_EXPAND_COUNT = 5;
  const NEWLINE_PATTERN = /\n/g;

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
        value() { return []; },
      },
      locationRange: String,
      keyEventTarget: {
        type: Object,
        value() { return document.body; },
      },
      commentSide: String,
      patchNum: String,
      path: String,
      projectName: {
        type: String,
        observer: '_projectNameChanged',
      },
      isOnParent: {
        type: Boolean,
        value: false,
      },

      _showActions: Boolean,
      _lastComment: Object,
      _orderedComments: Array,
      _unresolved: {
        type: Boolean,
        notify: true,
      },
      _projectConfig: Object,
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

    attached() {
      this._getLoggedIn().then(loggedIn => {
        this._showActions = loggedIn;
      });
      this._setInitialExpandedState();
    },

    addOrEditDraft(opt_lineNum, opt_range) {
      const lastComment = this.comments[this.comments.length - 1] || {};
      if (lastComment.__draft) {
        const commentEl = this._commentElWithDraftID(
            lastComment.id || lastComment.__draftID);
        commentEl.editing = true;

        // If the comment was collapsed, re-open it to make it clear which
        // actions are available.
        commentEl.collapsed = false;
      } else {
        const range = opt_range ? opt_range :
            lastComment ? lastComment.range : undefined;
        const unresolved = lastComment ? lastComment.unresolved : undefined;
        this.addDraft(opt_lineNum, range, unresolved);
      }
    },

    addDraft(opt_lineNum, opt_range, opt_unresolved) {
      const draft = this._newDraft(opt_lineNum, opt_range);
      draft.__editing = true;
      draft.unresolved = opt_unresolved === false ? opt_unresolved : true;
      this.push('comments', draft);
    },

    _getLoggedIn() {
      return this.$.restAPI.getLoggedIn();
    },

    _commentsChanged(changeRecord) {
      this._orderedComments = this._sortedComments(this.comments);
      if (this._orderedComments.length) {
        this._lastComment = this._getLastComment();
        this._unresolved = this._lastComment.unresolved;
      }
    },

    _hideActions(_showActions, _lastComment) {
      return !_showActions || !_lastComment || !!_lastComment.__draft;
    },

    _getLastComment() {
      return this._orderedComments[this._orderedComments.length - 1] || {};
    },

    _handleEKey(e) {
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

    _expandCollapseComments(actionIsCollapse) {
      const comments =
          Polymer.dom(this.root).querySelectorAll('gr-diff-comment');
      for (const comment of comments) {
        comment.collapsed = actionIsCollapse;
      }
    },

    /**
     * Sets the initial state of the comment thread to have the last
     * {UNRESOLVED_EXPAND_COUNT} comments expanded by default if the
     * thread is unresolved.
     */
    _setInitialExpandedState() {
      let comment;
      if (this._orderedComments) {
        for (let i = 0; i < this._orderedComments.length; i++) {
          comment = this._orderedComments[i];
          comment.collapsed =
              this._orderedComments.length - i - 1 >= UNRESOLVED_EXPAND_COUNT ||
              !this._unresolved;
        }
      }
    },

    _sortedComments(comments) {
      return comments.slice().sort((c1, c2) => {
        const c1Date = c1.__date || util.parseDate(c1.updated);
        const c2Date = c2.__date || util.parseDate(c2.updated);
        const dateCompare = c1Date - c2Date;
        if (!c1.id || !c1.id.localeCompare) { return 0; }
        // If same date, fall back to sorting by id.
        return dateCompare ? dateCompare : c1.id.localeCompare(c2.id);
      });
    },

    _createReplyComment(parent, content, opt_isEditing,
        opt_unresolved) {
      const reply = this._newReply(
          this._orderedComments[this._orderedComments.length - 1].id,
          parent.line,
          content,
          opt_unresolved,
          parent.range);

      // If there is currently a comment in an editing state, add an attribute
      // so that the gr-diff-comment knows not to populate the draft text.
      for (let i = 0; i < this.comments.length; i++) {
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
        this.async(() => {
          const commentEl = this._commentElWithDraftID(reply.__draftID);
          commentEl.save();
        }, 1);
      }
    },

    _isDraft(comment) {
      return !!comment.__draft;
    },

    /**
     * @param {boolean=} opt_quote
     */
    _processCommentReply(opt_quote) {
      const comment = this._lastComment;
      let quoteStr;
      if (opt_quote) {
        const msg = comment.message;
        quoteStr = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
      }
      this._createReplyComment(comment, quoteStr, true, comment.unresolved);
    },

    _handleCommentReply(e) {
      this._processCommentReply();
    },

    _handleCommentQuote(e) {
      this._processCommentReply(true);
    },

    _handleCommentAck(e) {
      const comment = this._lastComment;
      this._createReplyComment(comment, 'Ack', false, false);
    },

    _handleCommentDone(e) {
      const comment = this._lastComment;
      this._createReplyComment(comment, 'Done', false, false);
    },

    _handleCommentFix(e) {
      const comment = e.detail.comment;
      const msg = comment.message;
      const quoteStr = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
      const response = quoteStr + 'Please Fix';
      this._createReplyComment(comment, response, false, true);
    },

    _commentElWithDraftID(id) {
      const els = Polymer.dom(this.root).querySelectorAll('gr-diff-comment');
      for (const el of els) {
        if (el.comment.id === id || el.comment.__draftID === id) {
          return el;
        }
      }
      return null;
    },

    _newReply(inReplyTo, opt_lineNum, opt_message, opt_unresolved,
        opt_range) {
      const d = this._newDraft(opt_lineNum);
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

    /**
     * @param {number=} opt_lineNum
     * @param {Object=} opt_range
     */
    _newDraft(opt_lineNum, opt_range) {
      const d = {
        __draft: true,
        __draftID: Math.random().toString(36),
        __date: new Date(),
        path: this.path,
        patchNum: this.patchNum,
        side: this._getSide(this.isOnParent),
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

    _getSide(isOnParent) {
      if (isOnParent) { return 'PARENT'; }
      return 'REVISION';
    },

    _handleCommentDiscard(e) {
      const diffCommentEl = Polymer.dom(e).rootTarget;
      const comment = diffCommentEl.comment;
      const idx = this._indexOf(comment, this.comments);
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
      for (const changeComment of this.comments) {
        if (changeComment.__editing) {
          const commentLocation = {
            changeNum: this.changeNum,
            patchNum: this.patchNum,
            path: changeComment.path,
            line: changeComment.line,
          };
          return this.$.storage.setDraftComment(commentLocation,
              changeComment.message);
        }
      }
    },

    _handleCommentUpdate(e) {
      const comment = e.detail.comment;
      const index = this._indexOf(comment, this.comments);
      if (index === -1) {
        // This should never happen: comment belongs to another thread.
        console.warn('Comment update for another comment thread.');
        return;
      }
      this.set(['comments', index], comment);
    },

    _indexOf(comment, arr) {
      for (let i = 0; i < arr.length; i++) {
        const c = arr[i];
        if ((c.__draftID != null && c.__draftID == comment.__draftID) ||
            (c.id != null && c.id == comment.id)) {
          return i;
        }
      }
      return -1;
    },

    _computeHostClass(unresolved) {
      return unresolved ? 'unresolved' : '';
    },

    /**
     * Load the project config when a project name has been provided.
     * @param {string} name The project name.
     */
    _projectNameChanged(name) {
      if (!name) { return; }
      this.$.restAPI.getProjectConfig(name).then(config => {
        this._projectConfig = config;
      });
    },
  });
})();
