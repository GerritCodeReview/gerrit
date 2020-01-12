/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  const STORAGE_DEBOUNCE_INTERVAL = 400;
  const TOAST_DEBOUNCE_INTERVAL = 200;

  const SAVING_MESSAGE = 'Saving';
  const DRAFT_SINGULAR = 'draft...';
  const DRAFT_PLURAL = 'drafts...';
  const SAVED_MESSAGE = 'All changes saved';

  const REPORT_CREATE_DRAFT = 'CreateDraftComment';
  const REPORT_UPDATE_DRAFT = 'UpdateDraftComment';
  const REPORT_DISCARD_DRAFT = 'DiscardDraftComment';

  const FILE = 'FILE';

  /**
   * @appliesMixin Gerrit.FireMixin
   * @appliesMixin Gerrit.KeyboardShortcutMixin
   * @extends Polymer.Element
   */
  class GrComment extends Polymer.mixinBehaviors( [
    Gerrit.FireBehavior,
    Gerrit.KeyboardShortcutBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-comment'; }
    /**
     * Fired when the create fix comment action is triggered.
     *
     * @event create-fix-comment
     */

    /**
     * Fired when the show fix preview action is triggered.
     *
     * @event open-fix-preview
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
     * Fired when the comment's timestamp is tapped.
     *
     * @event comment-anchor-tap
     */

    static get properties() {
      return {
        changeNum: String,
        /** @type {!Gerrit.Comment} */
        comment: {
          type: Object,
          notify: true,
          observer: '_commentChanged',
        },
        comments: {
          type: Array,
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
        discarding: {
          type: Boolean,
          value: false,
          reflectToAttribute: true,
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
        /** @type {?} */
        projectConfig: Object,
        robotButtonDisabled: Boolean,
        _hasHumanReply: Boolean,
        _isAdmin: {
          type: Boolean,
          value: false,
        },

        _xhrPromise: Object, // Used for testing.
        _messageText: {
          type: String,
          value: '',
          observer: '_messageTextChanged',
        },
        commentSide: String,
        side: String,

        resolved: Boolean,

        _numPendingDraftRequests: {
          type: Object,
          value:
            {number: 0}, // Intentional to share the object across instances.
        },

        _enableOverlay: {
          type: Boolean,
          value: false,
        },

        /**
         * Property for storing references to overlay elements. When the overlays
         * are moved to Gerrit.getRootElement() to be shown they are no-longer
         * children, so they can't be queried along the tree, so they are stored
         * here.
         */
        _overlays: {
          type: Object,
          value: () => { return {}; },
        },
      };
    }

    static get observers() {
      return [
        '_commentMessageChanged(comment.message)',
        '_loadLocalDraft(changeNum, patchNum, comment)',
        '_isRobotComment(comment)',
        '_calculateActionstoShow(showActions, isRobotComment)',
        '_computeHasHumanReply(comment, comments.*)',
      ];
    }

    get keyBindings() {
      return {
        'ctrl+enter meta+enter ctrl+s meta+s': '_handleSaveKey',
        'esc': '_handleEsc',
      };
    }

    /** @override */
    attached() {
      super.attached();
      if (this.editing) {
        this.collapsed = false;
      } else if (this.comment) {
        this.collapsed = this.comment.collapsed;
      }
      this._getIsAdmin().then(isAdmin => {
        this._isAdmin = isAdmin;
      });
    }

    /** @override */
    detached() {
      super.detached();
      this.cancelDebouncer('fire-update');
      if (this.textarea) {
        this.textarea.closeDropdown();
      }
    }

    get textarea() {
      return this.shadowRoot.querySelector('#editTextarea');
    }

    get confirmDeleteOverlay() {
      if (!this._overlays.confirmDelete) {
        this._enableOverlay = true;
        Polymer.dom.flush();
        this._overlays.confirmDelete = this.shadowRoot
            .querySelector('#confirmDeleteOverlay');
      }
      return this._overlays.confirmDelete;
    }

    get confirmDiscardOverlay() {
      if (!this._overlays.confirmDiscard) {
        this._enableOverlay = true;
        Polymer.dom.flush();
        this._overlays.confirmDiscard = this.shadowRoot
            .querySelector('#confirmDiscardOverlay');
      }
      return this._overlays.confirmDiscard;
    }

    _computeShowHideIcon(collapsed) {
      return collapsed ? 'gr-icons:expand-more' : 'gr-icons:expand-less';
    }

    _calculateActionstoShow(showActions, isRobotComment) {
      // Polymer 2: check for undefined
      if ([showActions, isRobotComment].some(arg => arg === undefined)) {
        return;
      }

      this._showHumanActions = showActions && !isRobotComment;
      this._showRobotActions = showActions && isRobotComment;
    }

    _isRobotComment(comment) {
      this.isRobotComment = !!comment.robot_id;
    }

    isOnParent() {
      return this.side === 'PARENT';
    }

    _getIsAdmin() {
      return this.$.restAPI.getIsAdmin();
    }

    /**
     * @param {*=} opt_comment
     */
    save(opt_comment) {
      let comment = opt_comment;
      if (!comment) {
        comment = this.comment;
      }

      this.set('comment.message', this._messageText);
      this.editing = false;
      this.disabled = true;

      if (!this._messageText) {
        return this._discardDraft();
      }

      this._xhrPromise = this._saveDraft(comment).then(response => {
        this.disabled = false;
        if (!response.ok) { return response; }

        this._eraseDraftComment();
        return this.$.restAPI.getResponseObject(response).then(obj => {
          const resComment = obj;
          resComment.__draft = true;
          // Maintain the ephemeral draft ID for identification by other
          // elements.
          if (this.comment.__draftID) {
            resComment.__draftID = this.comment.__draftID;
          }
          resComment.__commentSide = this.commentSide;
          this.comment = resComment;
          this._fireSave();
          return obj;
        });
      })
          .catch(err => {
            this.disabled = false;
            throw err;
          });

      return this._xhrPromise;
    }

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
    }

    _commentChanged(comment) {
      this.editing = !!comment.__editing;
      this.resolved = !comment.unresolved;
      if (this.editing) { // It's a new draft/reply, notify.
        this._fireUpdate();
      }
    }

    _computeHasHumanReply() {
      if (!this.comment || !this.comments) return;
      // hide please fix button for robot comment that has human reply
      this._hasHumanReply = this.comments
          .some(c => c.in_reply_to && c.in_reply_to === this.comment.id &&
            !c.robot_id);
    }

    /**
     * @param {!Object=} opt_mixin
     *
     * @return {!Object}
     */
    _getEventPayload(opt_mixin) {
      return Object.assign({}, opt_mixin, {
        comment: this.comment,
        patchNum: this.patchNum,
      });
    }

    _fireSave() {
      this.fire('comment-save', this._getEventPayload());
    }

    _fireUpdate() {
      this.debounce('fire-update', () => {
        this.fire('comment-update', this._getEventPayload());
      });
    }

    _draftChanged(draft) {
      this.$.container.classList.toggle('draft', draft);
    }

    _editingChanged(editing, previousValue) {
      // Polymer 2: observer fires when at least one property is defined.
      // Do nothing to prevent comment.__editing being overwritten
      // if previousValue is undefined
      if (previousValue === undefined) return;

      this.$.container.classList.toggle('editing', editing);
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
      if (editing) {
        this.async(() => {
          Polymer.dom.flush();
          this.textarea && this.textarea.putCursorAtEnd();
        }, 1);
      }
    }

    _computeDeleteButtonClass(isAdmin, draft) {
      return isAdmin && !draft ? 'showDeleteButtons' : '';
    }

    _computeSaveDisabled(draft, comment, resolved) {
      // If resolved state has changed and a msg exists, save should be enabled.
      if (!comment || comment.unresolved === resolved && draft) {
        return false;
      }
      return !draft || draft.trim() === '';
    }

    _handleSaveKey(e) {
      if (!this._computeSaveDisabled(this._messageText, this.comment,
          this.resolved)) {
        e.preventDefault();
        this._handleSave(e);
      }
    }

    _handleEsc(e) {
      if (!this._messageText.length) {
        e.preventDefault();
        this._handleCancel(e);
      }
    }

    _handleToggleCollapsed() {
      this.collapsed = !this.collapsed;
    }

    _toggleCollapseClass(collapsed) {
      if (collapsed) {
        this.$.container.classList.add('collapsed');
      } else {
        this.$.container.classList.remove('collapsed');
      }
    }

    _commentMessageChanged(message) {
      this._messageText = message || '';
    }

    _messageTextChanged(newValue, oldValue) {
      if (!this.comment || (this.comment && this.comment.id)) {
        return;
      }

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
      }, STORAGE_DEBOUNCE_INTERVAL);
    }

    _handleAnchorClick(e) {
      e.preventDefault();
      if (!this.comment.line) {
        return;
      }
      this.dispatchEvent(new CustomEvent('comment-anchor-tap', {
        bubbles: true,
        composed: true,
        detail: {
          number: this.comment.line || FILE,
          side: this.side,
        },
      }));
    }

    _handleEdit(e) {
      e.preventDefault();
      this._messageText = this.comment.message;
      this.editing = true;
      this.$.reporting.recordDraftInteraction();
    }

    _handleSave(e) {
      e.preventDefault();

      // Ignore saves started while already saving.
      if (this.disabled) {
        return;
      }
      const timingLabel = this.comment.id ?
        REPORT_UPDATE_DRAFT : REPORT_CREATE_DRAFT;
      const timer = this.$.reporting.getTimer(timingLabel);
      this.set('comment.__editing', false);
      return this.save().then(() => { timer.end(); });
    }

    _handleCancel(e) {
      e.preventDefault();

      if (!this.comment.message ||
          this.comment.message.trim().length === 0 ||
          !this.comment.id) {
        this._fireDiscard();
        return;
      }
      this._messageText = this.comment.message;
      this.editing = false;
    }

    _fireDiscard() {
      this.cancelDebouncer('fire-update');
      this.fire('comment-discard', this._getEventPayload());
    }

    _handleFix() {
      this.dispatchEvent(new CustomEvent('create-fix-comment', {
        bubbles: true,
        composed: true,
        detail: this._getEventPayload(),
      }));
    }

    _handleShowFix() {
      this.dispatchEvent(new CustomEvent('open-fix-preview', {
        bubbles: true,
        composed: true,
        detail: this._getEventPayload(),
      }));
    }

    _hasNoFix(comment) {
      return !comment || !comment.fix_suggestions;
    }

    _handleDiscard(e) {
      e.preventDefault();
      this.$.reporting.recordDraftInteraction();

      if (!this._messageText) {
        this._discardDraft();
        return;
      }

      this._openOverlay(this.confirmDiscardOverlay).then(() => {
        this.confirmDiscardOverlay.querySelector('#confirmDiscardDialog')
            .resetFocus();
      });
    }

    _handleConfirmDiscard(e) {
      e.preventDefault();
      const timer = this.$.reporting.getTimer(REPORT_DISCARD_DRAFT);
      this._closeConfirmDiscardOverlay();
      return this._discardDraft().then(() => { timer.end(); });
    }

    _discardDraft() {
      if (!this.comment.__draft) {
        throw Error('Cannot discard a non-draft comment.');
      }
      this.discarding = true;
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
        if (!response.ok) {
          this.discarding = false;
          return response;
        }

        this._fireDiscard();
      })
          .catch(err => {
            this.disabled = false;
            throw err;
          });

      return this._xhrPromise;
    }

    _closeConfirmDiscardOverlay() {
      this._closeOverlay(this.confirmDiscardOverlay);
    }

    _getSavingMessage(numPending) {
      if (numPending === 0) {
        return SAVED_MESSAGE;
      }
      return [
        SAVING_MESSAGE,
        numPending,
        numPending === 1 ? DRAFT_SINGULAR : DRAFT_PLURAL,
      ].join(' ');
    }

    _showStartRequest() {
      const numPending = ++this._numPendingDraftRequests.number;
      this._updateRequestToast(numPending);
    }

    _showEndRequest() {
      const numPending = --this._numPendingDraftRequests.number;
      this._updateRequestToast(numPending);
    }

    _handleFailedDraftRequest() {
      this._numPendingDraftRequests.number--;

      // Cancel the debouncer so that error toasts from the error-manager will
      // not be overridden.
      this.cancelDebouncer('draft-toast');
    }

    _updateRequestToast(numPending) {
      const message = this._getSavingMessage(numPending);
      this.debounce('draft-toast', () => {
        // Note: the event is fired on the body rather than this element because
        // this element may not be attached by the time this executes, in which
        // case the event would not bubble.
        document.body.dispatchEvent(new CustomEvent(
            'show-alert', {detail: {message}, bubbles: true, composed: true}));
      }, TOAST_DEBOUNCE_INTERVAL);
    }

    _saveDraft(draft) {
      this._showStartRequest();
      return this.$.restAPI.saveDiffDraft(this.changeNum, this.patchNum, draft)
          .then(result => {
            if (result.ok) {
              this._showEndRequest();
            } else {
              this._handleFailedDraftRequest();
            }
            return result;
          });
    }

    _deleteDraft(draft) {
      this._showStartRequest();
      return this.$.restAPI.deleteDiffDraft(this.changeNum, this.patchNum,
          draft).then(result => {
        if (result.ok) {
          this._showEndRequest();
        } else {
          this._handleFailedDraftRequest();
        }
        return result;
      });
    }

    _getPatchNum() {
      return this.isOnParent() ? 'PARENT' : this.patchNum;
    }

    _loadLocalDraft(changeNum, patchNum, comment) {
      // Polymer 2: check for undefined
      if ([changeNum, patchNum, comment].some(arg => arg === undefined)) {
        return;
      }

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
    }

    _handleToggleResolved() {
      this.$.reporting.recordDraftInteraction();
      this.resolved = !this.resolved;
      // Modify payload instead of this.comment, as this.comment is passed from
      // the parent by ref.
      const payload = this._getEventPayload();
      payload.comment.unresolved = !this.$.resolvedCheckbox.checked;
      this.fire('comment-update', payload);
      if (!this.editing) {
        // Save the resolved state immediately.
        this.save(payload.comment);
      }
    }

    _handleCommentDelete() {
      this._openOverlay(this.confirmDeleteOverlay);
    }

    _handleCancelDeleteComment() {
      this._closeOverlay(this.confirmDeleteOverlay);
    }

    _openOverlay(overlay) {
      Polymer.dom(Gerrit.getRootElement()).appendChild(overlay);
      return overlay.open();
    }

    _computeAuthorName(comment) {
      if (!comment) return '';
      if (comment.robot_id) {
        return comment.robot_id;
      }
      return comment.author && comment.author.name;
    }

    _computeHideRunDetails(comment, collapsed) {
      if (!comment) return true;
      return !(comment.robot_id && comment.url && !collapsed);
    }

    _closeOverlay(overlay) {
      Polymer.dom(Gerrit.getRootElement()).removeChild(overlay);
      overlay.close();
    }

    _handleConfirmDeleteComment() {
      const dialog =
          this.confirmDeleteOverlay.querySelector('#confirmDeleteComment');
      this.$.restAPI.deleteComment(
          this.changeNum, this.patchNum, this.comment.id, dialog.message)
          .then(newComment => {
            this._handleCancelDeleteComment();
            this.comment = newComment;
          });
    }
  }

  customElements.define(GrComment.is, GrComment);
})();
