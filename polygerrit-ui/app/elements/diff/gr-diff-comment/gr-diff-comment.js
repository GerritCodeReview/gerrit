/**
@license
Copyright (C) 2015 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import '../../../../@polymer/polymer/polymer-legacy.js';

import '../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../../styles/shared-styles.js';
import '../../core/gr-reporting/gr-reporting.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../plugins/gr-endpoint-param/gr-endpoint-param.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-dialog/gr-dialog.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-formatted-text/gr-formatted-text.js';
import '../../shared/gr-icons/gr-icons.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-storage/gr-storage.js';
import '../../shared/gr-textarea/gr-textarea.js';
import '../../shared/gr-tooltip-content/gr-tooltip-content.js';
import '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog.js';
/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

(function(window) {
  window.Gerrit = window.Gerrit || {};
  if (window.Gerrit.hasOwnProperty('getRootElement')) { return; }

  window.Gerrit.getRootElement = () => document.body;
})(window);

const STORAGE_DEBOUNCE_INTERVAL = 400;
const TOAST_DEBOUNCE_INTERVAL = 200;

const SAVING_MESSAGE = 'Saving';
const DRAFT_SINGULAR = 'draft...';
const DRAFT_PLURAL = 'drafts...';
const SAVED_MESSAGE = 'All changes saved';

const REPORT_CREATE_DRAFT = 'CreateDraftComment';
const REPORT_UPDATE_DRAFT = 'UpdateDraftComment';
const REPORT_DISCARD_DRAFT = 'DiscardDraftComment';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        display: block;
        font-family: var(--font-family);
        padding: .7em .7em;
        --iron-autogrow-textarea: {
          box-sizing: border-box;
          padding: 2px;
        };
      }
      :host([disabled]) {
        pointer-events: none;
      }
      :host([disabled]) .actions,
      :host([disabled]) .robotActions,
      :host([disabled]) .date {
        opacity: .5;
      }
      :host([discarding]) {
        display: none;
      }
      .header {
        align-items: baseline;
        cursor: pointer;
        display: flex;
        font-family: 'Open Sans', sans-serif;
        margin: -.7em -.7em 0 -.7em;
        padding: .7em;
      }
      .container.collapsed .header {
        margin-bottom: -.7em;
      }
      .headerMiddle {
        color: var(--deemphasized-text-color);
        flex: 1;
        overflow: hidden;
      }
      .authorName,
      .draftLabel,
      .draftTooltip {
        font-family: var(--font-family-bold);
      }
      .draftLabel,
      .draftTooltip {
        color: var(--deemphasized-text-color);
        display: none;
      }
      .date {
        justify-content: flex-end;
        margin-left: 5px;
        min-width: 4.5em;
        text-align: right;
        white-space: nowrap;
      }
      a.date:link,
      a.date:visited {
        color: var(--deemphasized-text-color);
      }
      .actions {
        display: flex;
        justify-content: flex-end;
        padding-top: 0;
      }
      .action {
        margin-left: 1em;
      }
      .robotActions {
        display: flex;
        justify-content: flex-start;
        padding-top: 0;
      }
      .robotActions .action {
        /* Keep button text lined up with output text */
        margin-left: -.3rem;
        margin-right: 1em;
      }
      .rightActions {
        display: flex;
        justify-content: flex-end;
      }
      .editMessage {
        display: none;
        margin: .5em 0;
        width: 100%;
      }
      .container:not(.draft) .actions .hideOnPublished {
        display: none;
      }
      .draft .reply,
      .draft .quote,
      .draft .ack,
      .draft .done {
        display: none;
      }
      .draft .draftLabel,
      .draft .draftTooltip {
        display: inline;
      }
      .draft:not(.editing) .save,
      .draft:not(.editing) .cancel {
        display: none;
      }
      .editing .message,
      .editing .reply,
      .editing .quote,
      .editing .ack,
      .editing .done,
      .editing .edit,
      .editing .discard,
      .editing .unresolved {
        display: none;
      }
      .editing .editMessage {
        display: block;
      }
      .show-hide {
        margin-left: .4em;
      }
      .robotId {
        color: var(--deemphasized-text-color);
        margin-bottom: .8em;
        margin-top: -.4em;
      }
      .robotIcon {
        margin-right: .2em;
        /* because of the antenna of the robot, it looks off center even when it
         is centered. artificially adjust margin to account for this. */
        margin-top: -.3em;
      }
      .runIdInformation {
        margin: .7em 0;
      }
      .robotRun {
        margin-left: .5em;
      }
      .robotRunLink {
        margin-left: .5em;
      }
      input.show-hide {
        display: none;
      }
      label.show-hide {
        color: var(--comment-text-color);
        cursor: pointer;
        display: block;
        font-size: .8rem;
        height: 1.1em;
        margin-top: .1em;
      }
      #container .collapsedContent {
        display: none;
      }
      #container.collapsed {
        padding-bottom: 3px;
      }
      #container.collapsed .collapsedContent {
        display: block;
        overflow: hidden;
        padding-left: 5px;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      #container.collapsed .actions,
      #container.collapsed gr-formatted-text,
      #container.collapsed gr-textarea {
        display: none;
      }
      .resolve,
      .unresolved {
        align-items: center;
        display: flex;
        flex: 1;
        margin: 0;
      }
      .resolve label {
        color: var(--comment-text-color);
      }
      gr-dialog .main {
        display: flex;
        flex-direction: column;
        width: 100%;
      }
      #deleteBtn {
        display: none;
        --gr-button: {
          color: var(--deemphasized-text-color);
          padding: 0;
        }
      }
      #deleteBtn.showDeleteButtons {
        display: block;
      }
    </style>
    <div id="container" class="container" on-mouseenter="_handleMouseEnter" on-mouseleave="_handleMouseLeave">
      <div class="header" id="header" on-tap="_handleToggleCollapsed">
        <div class="headerLeft">
          <span class="authorName">[[comment.author.name]]</span>
          <span class="draftLabel">DRAFT</span>
          <gr-tooltip-content class="draftTooltip" has-tooltip="" title="This draft is only visible to you. To publish drafts, click the 'Reply' or 'Start review' button at the top of the change or press the 'A' key." max-width="20em" show-icon=""></gr-tooltip-content>
        </div>
        <div class="headerMiddle">
          <span class="collapsedContent">[[comment.message]]</span>
        </div>
        <gr-button id="deleteBtn" link="" secondary="" class\$="action delete [[_computeDeleteButtonClass(_isAdmin, draft)]]" on-tap="_handleCommentDelete">
          (Delete)
        </gr-button>
        <a class="date" href\$="[[_computeLinkToComment(comment)]]" on-tap="_handleLinkTap">
          <gr-date-formatter has-tooltip="" date-str="[[comment.updated]]"></gr-date-formatter>
        </a>
        <div class="show-hide">
          <label class="show-hide">
            <input type="checkbox" class="show-hide" checked\$="[[collapsed]]" on-change="_handleToggleCollapsed">
            [[_computeShowHideText(collapsed)]]
          </label>
        </div>
      </div>
      <div class="body">
        <template is="dom-if" if="[[comment.robot_id]]">
          <div class="robotId" hidden\$="[[collapsed]]">
            <iron-icon class="robotIcon" icon="gr-icons:robot"></iron-icon>
            [[comment.robot_id]]
          </div>
        </template>
        <template is="dom-if" if="[[editing]]">
          <gr-textarea id="editTextarea" class="editMessage" autocomplete="on" monospace="" disabled="{{disabled}}" rows="4" text="{{_messageText}}"></gr-textarea>
        </template>
        <!--The message class is needed to ensure selectability from
        gr-diff-selection.-->
        <gr-formatted-text class="message" content="[[comment.message]]" no-trailing-margin="[[!comment.__draft]]" collapsed="[[collapsed]]" config="[[projectConfig.commentlinks]]"></gr-formatted-text>
        <div hidden\$="[[!comment.robot_run_id]]" class="message">
          <div class="runIdInformation" hidden\$="[[collapsed]]">
            Run ID:
            <template is="dom-if" if="[[comment.url]]">
              <a class="robotRunLink" href\$="[[comment.url]]">
                <span class="robotRun link">[[comment.robot_run_id]]</span>
              </a>
            </template>
            <template is="dom-if" if="[[!comment.url]]">
              <span class="robotRun text">[[comment.robot_run_id]]</span>
            </template>
          </div>
        </div>
        <div class="actions humanActions" hidden\$="[[!_showHumanActions]]">
          <div class="action resolve hideOnPublished">
            <label>
              <input type="checkbox" id="resolvedCheckbox" checked="[[resolved]]" on-change="_handleToggleResolved">
              Resolved
            </label>
          </div>
          <div class="rightActions">
            <gr-button link="" secondary="" class="action cancel hideOnPublished" on-tap="_handleCancel">Cancel</gr-button>
            <gr-button link="" secondary="" class="action discard hideOnPublished" on-tap="_handleDiscard">Discard</gr-button>
            <gr-button link="" secondary="" class="action edit hideOnPublished" on-tap="_handleEdit">Edit</gr-button>
            <gr-button link="" secondary="" disabled\$="[[_computeSaveDisabled(_messageText, comment, resolved)]]" class="action save hideOnPublished" on-tap="_handleSave">Save</gr-button>
          </div>
        </div>
        <div class="robotActions" hidden\$="[[!_showRobotActions]]">
          <template is="dom-if" if="[[isRobotComment]]">
            <gr-button link="" secondary="" class="action fix" on-tap="_handleFix" disabled="[[robotButtonDisabled]]">
              Please Fix
            </gr-button>
            <gr-endpoint-decorator name="robot-comment-controls">
              <gr-endpoint-param name="comment" value="[[comment]]">
              </gr-endpoint-param>
            </gr-endpoint-decorator>
          </template>
        </div>
      </div>
    </div>
    <template is="dom-if" if="[[_enableOverlay]]">
      <gr-overlay id="confirmDeleteOverlay" with-backdrop="">
        <gr-confirm-delete-comment-dialog id="confirmDeleteComment" on-confirm="_handleConfirmDeleteComment" on-cancel="_handleCancelDeleteComment">
        </gr-confirm-delete-comment-dialog>
      </gr-overlay>
      <gr-overlay id="confirmDiscardOverlay" with-backdrop="">
        <gr-dialog id="confirmDiscardDialog" confirm-label="Discard" confirm-on-enter="" on-confirm="_handleConfirmDiscard" on-cancel="_closeConfirmDiscardOverlay">
          <div class="header" slot="header">
            Discard comment
          </div>
          <div class="main" slot="main">
            Are you sure you want to discard this draft comment?
          </div>
        </gr-dialog>
      </gr-overlay>
    </template>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-storage id="storage"></gr-storage>
    <gr-reporting id="reporting"></gr-reporting>
`,

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
    /** @type {?} */
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

    resolved: Boolean,

    _numPendingDraftRequests: {
      type: Object,
      value: {number: 0}, // Intentional to share the object across instances.
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
      value: () => ({}),
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
    if (this.textarea) {
      this.textarea.closeDropdown();
    }
  },

  get textarea() {
    return this.$$('#editTextarea');
  },

  get confirmDeleteOverlay() {
    if (!this._overlays.confirmDelete) {
      this._enableOverlay = true;
      Polymer.dom.flush();
      this._overlays.confirmDelete = this.$$('#confirmDeleteOverlay');
    }
    return this._overlays.confirmDelete;
  },

  get confirmDiscardOverlay() {
    if (!this._overlays.confirmDiscard) {
      this._enableOverlay = true;
      Polymer.dom.flush();
      this._overlays.confirmDiscard = this.$$('#confirmDiscardOverlay');
    }
    return this._overlays.confirmDiscard;
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

  /**
   * @param {*=} opt_comment
   */
  save(opt_comment) {
    let comment = opt_comment;
    if (!comment) { comment = this.comment; }

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
    }).catch(err => {
      this.disabled = false;
      throw err;
    });

    return this._xhrPromise;
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
        this.textarea.putCursorAtEnd();
      }, 1);
    }
  },

  _computeLinkToComment(comment) {
    return '#' + comment.line;
  },

  _computeDeleteButtonClass(isAdmin, draft) {
    return isAdmin && !draft ? 'showDeleteButtons' : '';
  },

  _computeSaveDisabled(draft, comment, resolved) {
    // If resolved state has changed and a msg exists, save should be enabled.
    if (comment.unresolved === resolved && draft) { return false; }
    return !draft || draft.trim() === '';
  },

  _handleSaveKey(e) {
    if (!this._computeSaveDisabled(this._messageText, this.comment,
        this.resolved)) {
      e.preventDefault();
      this._handleSave(e);
    }
  },

  _handleEsc(e) {
    if (!this._messageText.length) {
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

  _handleEdit(e) {
    e.preventDefault();
    this._messageText = this.comment.message;
    this.editing = true;
    this.$.reporting.recordDraftInteraction();
  },

  _handleSave(e) {
    e.preventDefault();

    // Ignore saves started while already saving.
    if (this.disabled) { return; }
    const timingLabel = this.comment.id ?
        REPORT_UPDATE_DRAFT : REPORT_CREATE_DRAFT;
    const timer = this.$.reporting.getTimer(timingLabel);
    this.set('comment.__editing', false);
    return this.save().then(() => { timer.end(); });
  },

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
  },

  _fireDiscard() {
    this.cancelDebouncer('fire-update');
    this.fire('comment-discard', this._getEventPayload());
  },

  _handleFix() {
    this.dispatchEvent(new CustomEvent('create-fix-comment', {
      bubbles: true,
      detail: this._getEventPayload(),
    }));
  },

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
  },

  _handleConfirmDiscard(e) {
    e.preventDefault();
    const timer = this.$.reporting.getTimer(REPORT_DISCARD_DRAFT);
    this._closeConfirmDiscardOverlay();
    return this._discardDraft().then(() => { timer.end(); });
  },

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
    }).catch(err => {
      this.disabled = false;
      throw err;
    });

    return this._xhrPromise;
  },

  _closeConfirmDiscardOverlay() {
    this._closeOverlay(this.confirmDiscardOverlay);
  },

  _getSavingMessage(numPending) {
    if (numPending === 0) { return SAVED_MESSAGE; }
    return [
      SAVING_MESSAGE,
      numPending,
      numPending === 1 ? DRAFT_SINGULAR : DRAFT_PLURAL,
    ].join(' ');
  },

  _showStartRequest() {
    const numPending = ++this._numPendingDraftRequests.number;
    this._updateRequestToast(numPending);
  },

  _showEndRequest() {
    const numPending = --this._numPendingDraftRequests.number;
    this._updateRequestToast(numPending);
  },

  _handleFailedDraftRequest() {
    this._numPendingDraftRequests.number--;

    // Cancel the debouncer so that error toasts from the error-manager will
    // not be overridden.
    this.cancelDebouncer('draft-toast');
  },

  _updateRequestToast(numPending) {
    const message = this._getSavingMessage(numPending);
    this.debounce('draft-toast', () => {
      // Note: the event is fired on the body rather than this element because
      // this element may not be attached by the time this executes, in which
      // case the event would not bubble.
      document.body.dispatchEvent(new CustomEvent('show-alert',
          {detail: {message}, bubbles: true}));
    }, TOAST_DEBOUNCE_INTERVAL);
  },

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
  },

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
  },

  _handleCommentDelete() {
    this._openOverlay(this.confirmDeleteOverlay);
  },

  _handleCancelDeleteComment() {
    this._closeOverlay(this.confirmDeleteOverlay);
  },

  _openOverlay(overlay) {
    Polymer.dom(Gerrit.getRootElement()).appendChild(overlay);
    return overlay.open();
  },

  _closeOverlay(overlay) {
    Polymer.dom(Gerrit.getRootElement()).removeChild(overlay);
    overlay.close();
  },

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
});
