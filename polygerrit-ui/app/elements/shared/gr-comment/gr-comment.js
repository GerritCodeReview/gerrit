/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../styles/shared-styles.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../gr-button/gr-button.js';
import '../gr-dialog/gr-dialog.js';
import '../gr-date-formatter/gr-date-formatter.js';
import '../gr-formatted-text/gr-formatted-text.js';
import '../gr-icons/gr-icons.js';
import '../gr-overlay/gr-overlay.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-storage/gr-storage.js';
import '../gr-textarea/gr-textarea.js';
import '../gr-tooltip-content/gr-tooltip-content.js';
import '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog.js';
import '../gr-account-label/gr-account-label.js';
import {flush, dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-comment_html.js';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {getRootElement} from '../../../scripts/rootElement.js';
import {getDisplayName} from '../../../utils/display-name-util.js';
import {appContext} from '../../../services/app-context.js';

const STORAGE_DEBOUNCE_INTERVAL = 400;
const TOAST_DEBOUNCE_INTERVAL = 200;

const SAVING_MESSAGE = 'Saving';
const DRAFT_SINGULAR = 'draft...';
const DRAFT_PLURAL = 'drafts...';
const SAVED_MESSAGE = 'All changes saved';
const UNSAVED_MESSAGE = 'Unable to save draft';

const REPORT_CREATE_DRAFT = 'CreateDraftComment';
const REPORT_UPDATE_DRAFT = 'UpdateDraftComment';
const REPORT_DISCARD_DRAFT = 'DiscardDraftComment';

const FILE = 'FILE';

export const __testOnly_UNSAVED_MESSAGE = UNSAVED_MESSAGE;

/**
 * All candidates tips to show, will pick randomly.
 */
const RESPECTFUL_REVIEW_TIPS= [
  'Assume competence.',
  'Provide rationale or context.',
  'Consider how comments may be interpreted.',
  'Avoid harsh language.',
  'Make your comments specific and actionable.',
  'When disagreeing, explain the advantage of your approach.',
];

/**
 * @extends PolymerElement
 */
class GrComment extends KeyboardShortcutMixin(GestureEventListeners(
    LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

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
   * Fired when editing status changed.
   *
   * @event comment-editing-changed
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
        reflectToAttribute: true,
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
       * are moved to getRootElement() to be shown they are no-longer
       * children, so they can't be queried along the tree, so they are stored
       * here.
       */
      _overlays: {
        type: Object,
        value: () => { return {}; },
      },

      _showRespectfulTip: {
        type: Boolean,
        value: false,
      },
      showPatchset: {
        type: Boolean,
        value: true,
      },
      _respectfulReviewTip: String,
      _respectfulTipDismissed: {
        type: Boolean,
        value: false,
      },
      _serverConfig: Object,
      _unableToSave: {
        type: Boolean,
        value: false,
      },
      _selfAccount: Object,
    };
  }

  static get observers() {
    return [
      '_commentMessageChanged(comment.message)',
      '_loadLocalDraft(changeNum, patchNum, comment)',
      '_isRobotComment(comment)',
      '_calculateActionstoShow(showActions, isRobotComment)',
      '_computeHasHumanReply(comment, comments.*)',
      '_onEditingChange(editing)',
    ];
  }

  get keyBindings() {
    return {
      'ctrl+enter meta+enter ctrl+s meta+s': '_handleSaveKey',
      'esc': '_handleEsc',
    };
  }

  constructor() {
    super();
    this.reporting = appContext.reportingService;
  }

  /** @override */
  attached() {
    super.attached();
    this.$.restAPI.getAccount().then(account => {
      this._selfAccount = account;
    });
    if (this.editing) {
      this.collapsed = false;
    } else if (this.comment) {
      this.collapsed = this.comment.collapsed;
    }
    this._getIsAdmin().then(isAdmin => {
      this._isAdmin = isAdmin;
    });
    this.$.restAPI.getConfig().then(cfg => {
      this._serverConfig = cfg;
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

  _getAuthor(comment) {
    return comment.author || this._selfAccount;
  }

  _onEditingChange(editing) {
    this.dispatchEvent(new CustomEvent('comment-editing-changed', {
      detail: !!editing,
      bubbles: true,
      composed: true,
    }));
    if (!editing) return;
    // visibility based on cache this will make sure we only and always show
    // a tip once every Math.max(a day, period between creating comments)
    const cachedVisibilityOfRespectfulTip =
      this.$.storage.getRespectfulTipVisibility();
    if (!cachedVisibilityOfRespectfulTip) {
      // we still want to show the tip with a probability of 30%
      if (this.getRandomNum(0, 3) >= 1) return;
      this._showRespectfulTip = true;
      const randomIdx = this.getRandomNum(0, RESPECTFUL_REVIEW_TIPS.length);
      this._respectfulReviewTip = RESPECTFUL_REVIEW_TIPS[randomIdx];
      this.reporting.reportInteraction(
          'respectful-tip-appeared',
          {tip: this._respectfulReviewTip}
      );
      // update cache
      this.$.storage.setRespectfulTipVisibility();
    }
  }

  /** Set as a separate method so easy to stub. */
  getRandomNum(min, max) {
    return Math.floor(Math.random() * (max - min) + min);
  }

  _computeVisibilityOfTip(showTip, tipDismissed) {
    return showTip && !tipDismissed;
  }

  _dismissRespectfulTip() {
    this._respectfulTipDismissed = true;
    this.reporting.reportInteraction(
        'respectful-tip-dismissed',
        {tip: this._respectfulReviewTip}
    );
    // add a 14-day delay to the tip cache
    this.$.storage.setRespectfulTipVisibility(/* delayDays= */ 14);
  }

  _onRespectfulReadMoreClick() {
    this.reporting.reportInteraction('respectful-read-more-clicked');
  }

  get textarea() {
    return this.shadowRoot.querySelector('#editTextarea');
  }

  get confirmDeleteOverlay() {
    if (!this._overlays.confirmDelete) {
      this._enableOverlay = true;
      flush();
      this._overlays.confirmDelete = this.shadowRoot
          .querySelector('#confirmDeleteOverlay');
    }
    return this._overlays.confirmDelete;
  }

  get confirmDiscardOverlay() {
    if (!this._overlays.confirmDiscard) {
      this._enableOverlay = true;
      flush();
      this._overlays.confirmDiscard = this.shadowRoot
          .querySelector('#confirmDiscardOverlay');
    }
    return this._overlays.confirmDiscard;
  }

  _computeShowHideIcon(collapsed) {
    return collapsed ? 'gr-icons:expand-more' : 'gr-icons:expand-less';
  }

  _computeShowHideAriaLabel(collapsed) {
    return collapsed ? 'Expand' : 'Collapse';
  }

  _calculateActionstoShow(showActions, isRobotComment) {
    // Polymer 2: check for undefined
    if ([showActions, isRobotComment].includes(undefined)) {
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

  _computeDraftTooltip(unableToSave) {
    return unableToSave ? `Unable to save draft. Please try to save again.` :
      `This draft is only visible to you. To publish drafts, click the 'Reply'`
    + `or 'Start review' button at the top of the change or press the 'A' key.`;
  }

  _computeDraftText(unableToSave) {
    return 'DRAFT' + (unableToSave ? '(Failed to save)' : '');
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
    return {...opt_mixin, comment: this.comment,
      patchNum: this.patchNum};
  }

  _fireSave() {
    this.dispatchEvent(new CustomEvent('comment-save', {
      detail: this._getEventPayload(),
      composed: true, bubbles: true,
    }));
  }

  _fireUpdate() {
    this.debounce('fire-update', () => {
      this.dispatchEvent(new CustomEvent('comment-update', {
        detail: this._getEventPayload(),
        composed: true, bubbles: true,
      }));
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
      const cancelButton = this.shadowRoot.querySelector('.cancel');
      if (cancelButton) {
        cancelButton.hidden = !editing;
      }
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
        flush();
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
    this.reporting.recordDraftInteraction();
  }

  _handleSave(e) {
    e.preventDefault();

    // Ignore saves started while already saving.
    if (this.disabled) {
      return;
    }
    const timingLabel = this.comment.id ?
      REPORT_UPDATE_DRAFT : REPORT_CREATE_DRAFT;
    const timer = this.reporting.getTimer(timingLabel);
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
    this.dispatchEvent(new CustomEvent('comment-discard', {
      detail: this._getEventPayload(),
      composed: true, bubbles: true,
    }));
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
    this.reporting.recordDraftInteraction();

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
    const timer = this.reporting.getTimer(REPORT_DISCARD_DRAFT);
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

  _getSavingMessage(numPending, requestFailed) {
    if (requestFailed) {
      return UNSAVED_MESSAGE;
    }
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
    this._updateRequestToast(this._numPendingDraftRequests.number,
        /* requestFailed=*/true);
  }

  _updateRequestToast(numPending, requestFailed) {
    const message = this._getSavingMessage(numPending, requestFailed);
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
          if (result.ok) { // remove
            this._unableToSave = false;
            this.$.container.classList.remove('unableToSave');
            this._showEndRequest();
          } else {
            this.$.container.classList.add('unableToSave');
            this._unableToSave = true;
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
    if ([changeNum, patchNum, comment].includes(undefined)) {
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
    this.reporting.recordDraftInteraction();
    this.resolved = !this.resolved;
    // Modify payload instead of this.comment, as this.comment is passed from
    // the parent by ref.
    const payload = this._getEventPayload();
    payload.comment.unresolved = !this.$.resolvedCheckbox.checked;
    this.dispatchEvent(new CustomEvent('comment-update', {
      detail: payload,
      composed: true, bubbles: true,
    }));
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
    dom(getRootElement()).appendChild(overlay);
    return overlay.open();
  }

  _computeAuthorName(comment, serverConfig) {
    if ([comment, serverConfig].includes(undefined)) return '';
    if (comment.robot_id) {
      return comment.robot_id;
    }
    if (comment.author) {
      return getDisplayName(serverConfig, comment.author);
    }
    return '';
  }

  _computeHideRunDetails(comment, collapsed) {
    if (!comment) return true;
    return !(comment.robot_id && comment.url && !collapsed);
  }

  _closeOverlay(overlay) {
    dom(getRootElement()).removeChild(overlay);
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
