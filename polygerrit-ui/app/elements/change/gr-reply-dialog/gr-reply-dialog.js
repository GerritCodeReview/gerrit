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

import '../../../behaviors/base-url-behavior/base-url-behavior.js';
import '../../../behaviors/gr-patch-set-behavior/gr-patch-set-behavior.js';
import '../../../behaviors/keyboard-shortcut-behavior/keyboard-shortcut-behavior.js';
import '../../../behaviors/rest-client-behavior/rest-client-behavior.js';
import '../../../../@polymer/iron-autogrow-textarea/iron-autogrow-textarea.js';
import '../../core/gr-reporting/gr-reporting.js';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../shared/gr-account-chip/gr-account-chip.js';
import '../../shared/gr-textarea/gr-textarea.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-formatted-text/gr-formatted-text.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-storage/gr-storage.js';
import '../gr-account-list/gr-account-list.js';
import '../gr-label-scores/gr-label-scores.js';
import '../../../styles/shared-styles.js';

const STORAGE_DEBOUNCE_INTERVAL_MS = 400;

const FocusTarget = {
  ANY: 'any',
  BODY: 'body',
  CCS: 'cc',
  REVIEWERS: 'reviewers',
};

const ReviewerTypes = {
  REVIEWER: 'REVIEWER',
  CC: 'CC',
};

const LatestPatchState = {
  LATEST: 'latest',
  CHECKING: 'checking',
  NOT_LATEST: 'not-latest',
};

const ButtonLabels = {
  START_REVIEW: 'Start review',
  SEND: 'Send',
};

const ButtonTooltips = {
  SAVE: 'Save reply but do not send',
  START_REVIEW: 'Mark as ready for review and send reply',
  SEND: 'Send reply',
};

// TODO(logan): Remove once the fix for issue 6841 is stable on
// googlesource.com.
const START_REVIEW_MESSAGE = 'This change is ready for review.';

const EMPTY_REPLY_MESSAGE = 'Cannot send an empty reply.';

const SEND_REPLY_TIMING_LABEL = 'SendReply';

Polymer({
  _template: Polymer.html`
    <style include="shared-styles">
      :host {
        background-color: var(--dialog-background-color);
        display: block;
        max-height: 100%;
      }
      :host([disabled]) {
        pointer-events: none;
      }
      :host([disabled]) .container {
        opacity: .5;
      }
      .container {
        display: flex;
        flex-direction: column;
        max-height: 100%;
      }
      section {
        border-top: 1px solid var(--border-color);
        flex-shrink: 0;
        padding: .5em 1.5em;
        width: 100%;
      }
      .actions {
        background-color: var(--dialog-background-color);
        bottom: 0;
        display: flex;
        justify-content: space-between;
        position: sticky;
        /* @see Issue 8602 */
        z-index: 1;
      }
      .actions .right gr-button {
        margin-left: 1em;
      }
      .peopleContainer,
      .labelsContainer {
        flex-shrink: 0;
      }
      .peopleContainer {
        border-top: none;
        display: table;
      }
      .peopleList {
        display: flex;
        padding-top: .1em;
      }
      .peopleListLabel {
        color: var(--deemphasized-text-color);
        margin-top: .2em;
        min-width: 7em;
        padding-right: .5em;
      }
      gr-account-list {
        display: flex;
        flex-wrap: wrap;
        flex: 1;
        min-height: 1.8em;
      }
      #reviewerConfirmationOverlay {
        padding: 1em;
        text-align: center;
      }
      .reviewerConfirmationButtons {
        margin-top: 1em;
      }
      .groupName {
        font-family: var(--font-family-bold);
      }
      .groupSize {
        font-style: italic;
      }
      .textareaContainer {
        min-height: 12em;
        position: relative;
      }
      .textareaContainer,
      #textarea,
      gr-endpoint-decorator {
        display: flex;
        width: 100%;
      }
      gr-endpoint-decorator[name="reply-label-scores"] {
        display: block;
      }
      .previewContainer gr-formatted-text {
        background: var(--table-header-background-color);
        padding: 1em;
      }
      .draftsContainer h3 {
        margin-top: .25em;
      }
      #checkingStatusLabel,
      #notLatestLabel {
        margin-left: 1em;
      }
      #checkingStatusLabel {
        color: var(--deemphasized-text-color);
        font-style: italic;
      }
      #notLatestLabel,
      #savingLabel {
        color: var(--error-text-color);
      }
      #savingLabel {
        display: none;
      }
      #savingLabel.saving {
        display: inline;
      }
      #pluginMessage {
        color: var(--deemphasized-text-color);
        margin-left: 1em;
        margin-bottom: .5em;
      }
      #pluginMessage:empty {
        display: none;
      }
    </style>
    <div class="container" tabindex="-1">
      <section class="peopleContainer">
        <div class="peopleList">
          <div class="peopleListLabel">Reviewers</div>
          <gr-account-list id="reviewers" accounts="{{_reviewers}}" removable-values="[[change.removable_reviewers]]" change="[[change]]" filter="[[filterReviewerSuggestion]]" pending-confirmation="{{_reviewerPendingConfirmation}}" placeholder="Add reviewer..." on-account-text-changed="_handleAccountTextEntry">
          </gr-account-list>
        </div>
        <template is="dom-if" if="[[serverConfig.note_db_enabled]]">
          <div class="peopleList">
            <div class="peopleListLabel">CC</div>
            <gr-account-list id="ccs" accounts="{{_ccs}}" change="[[change]]" filter="[[filterCCSuggestion]]" pending-confirmation="{{_ccPendingConfirmation}}" allow-any-input="" placeholder="Add CC..." on-account-text-changed="_handleAccountTextEntry">
            </gr-account-list>
          </div>
        </template>
        <gr-overlay id="reviewerConfirmationOverlay" on-iron-overlay-canceled="_cancelPendingReviewer">
          <div class="reviewerConfirmation">
            Group
            <span class="groupName">
              [[_pendingConfirmationDetails.group.name]]
            </span>
            has
            <span class="groupSize">
              [[_pendingConfirmationDetails.count]]
            </span>
            members.
            <br>
            Are you sure you want to add them all?
          </div>
          <div class="reviewerConfirmationButtons">
            <gr-button on-tap="_confirmPendingReviewer">Yes</gr-button>
            <gr-button on-tap="_cancelPendingReviewer">No</gr-button>
          </div>
        </gr-overlay>
      </section>
      <section class="textareaContainer">
        <gr-endpoint-decorator name="reply-text">
          <gr-textarea id="textarea" class="message" autocomplete="on" placeholder="[[_messagePlaceholder]]" fixed-position-dropdown="" hide-border="true" monospace="true" disabled="{{disabled}}" rows="4" text="{{draft}}" on-bind-value-changed="_handleHeightChanged">
          </gr-textarea>
        </gr-endpoint-decorator>
      </section>
      <section class="previewContainer">
        <label>
          <input type="checkbox" checked="{{_previewFormatting::change}}">
          Preview formatting
        </label>
        <gr-formatted-text content="[[draft]]" hidden\$="[[!_previewFormatting]]" config="[[projectConfig.commentlinks]]"></gr-formatted-text>
      </section>
      <section class="labelsContainer">
        <gr-endpoint-decorator name="reply-label-scores">
          <gr-label-scores id="labelScores" account="[[_account]]" change="[[change]]" on-labels-changed="_handleLabelsChanged" permitted-labels="[[permittedLabels]]"></gr-label-scores>
        </gr-endpoint-decorator>
        <div id="pluginMessage">[[_pluginMessage]]</div>
      </section>
      <section class="draftsContainer" hidden\$="[[_computeHideDraftList(diffDrafts)]]">
        <div class="includeComments">
          <input type="checkbox" id="includeComments" checked="{{_includeComments::change}}">
          <label for="includeComments">Publish [[_computeDraftsTitle(diffDrafts)]]</label>
        </div>
        <gr-comment-list id="commentList" comments="[[diffDrafts]]" change-num="[[change._number]]" project-config="[[projectConfig]]" patch-num="[[patchNum]]" hidden\$="[[!_includeComments]]"></gr-comment-list>
        <span id="savingLabel" class\$="[[_computeSavingLabelClass(_savingComments)]]">
          Saving comments...
        </span>
      </section>
      <section class="actions">
        <div class="left">
          <template is="dom-if" if="[[canBeStarted]]">
            <gr-button link="" secondary="" disabled="[[_isState(knownLatestState, 'not-latest')]]" class="action save" has-tooltip="" title="[[_saveTooltip]]" on-tap="_saveTapHandler">Save</gr-button>
          </template>
          <span id="checkingStatusLabel" hidden\$="[[!_isState(knownLatestState, 'checking')]]">
            Checking whether patch [[patchNum]] is latest...
          </span>
          <span id="notLatestLabel" hidden\$="[[!_isState(knownLatestState, 'not-latest')]]">
            [[_computePatchSetWarning(patchNum, _labelsChanged)]]
            <gr-button link="" on-tap="_reload">Reload</gr-button>
          </span>
        </div>
        <div class="right">
          <gr-button link="" id="cancelButton" class="action cancel" on-tap="_cancelTapHandler">Cancel</gr-button>
          <gr-button id="sendButton" link="" primary="" disabled="[[_sendDisabled]]" class="action send" has-tooltip="" title\$="[[_computeSendButtonTooltip(canBeStarted)]]" on-tap="_sendTapHandler">[[_sendButtonLabel]]</gr-button>
        </div>
      </section>
    </div>
    <gr-js-api-interface id="jsAPI"></gr-js-api-interface>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
    <gr-storage id="storage"></gr-storage>
    <gr-reporting id="reporting"></gr-reporting>
`,

  is: 'gr-reply-dialog',

  /**
   * Fired when a reply is successfully sent.
   *
   * @event send
   */

  /**
   * Fired when the user presses the cancel button.
   *
   * @event cancel
   */

  /**
   * Fired when the main textarea's value changes, which may have triggered
   * a change in size for the dialog.
   *
   * @event autogrow
   */

  /**
   * Fires to show an alert when a send is attempted on the non-latest patch.
   *
   * @event show-alert
   */

  /**
   * Fires when the reply dialog believes that the server side diff drafts
   * have been updated and need to be refreshed.
   *
   * @event comment-refresh
   */

  /**
   * Fires when the state of the send button (enabled/disabled) changes.
   *
   * @event send-disabled-changed
   */

  properties: {
    /**
     * @type {{ _number: number, removable_reviewers: Array }}
     */
    change: Object,
    patchNum: String,
    canBeStarted: {
      type: Boolean,
      value: false,
    },
    disabled: {
      type: Boolean,
      value: false,
      reflectToAttribute: true,
    },
    draft: {
      type: String,
      value: '',
      observer: '_draftChanged',
    },
    quote: {
      type: String,
      value: '',
    },
    diffDrafts: {
      type: Object,
      observer: '_handleHeightChanged',
    },
    /** @type {!Function} */
    filterReviewerSuggestion: {
      type: Function,
      value() {
        return this._filterReviewerSuggestionGenerator(false);
      },
    },
    /** @type {!Function} */
    filterCCSuggestion: {
      type: Function,
      value() {
        return this._filterReviewerSuggestionGenerator(true);
      },
    },
    permittedLabels: Object,
    /**
     * @type {{ note_db_enabled: boolean }}
     */
    serverConfig: Object,
    /**
     * @type {{ commentlinks: Array }}
     */
    projectConfig: Object,
    knownLatestState: String,
    underReview: {
      type: Boolean,
      value: true,
    },

    _account: Object,
    _ccs: Array,
    /** @type {?Object} */
    _ccPendingConfirmation: {
      type: Object,
      observer: '_reviewerPendingConfirmationUpdated',
    },
    _messagePlaceholder: {
      type: String,
      computed: '_computeMessagePlaceholder(canBeStarted)',
    },
    _owner: Object,
    /** @type {?} */
    _pendingConfirmationDetails: Object,
    _includeComments: {
      type: Boolean,
      value: true,
    },
    _reviewers: Array,
    /** @type {?Object} */
    _reviewerPendingConfirmation: {
      type: Object,
      observer: '_reviewerPendingConfirmationUpdated',
    },
    _previewFormatting: {
      type: Boolean,
      value: false,
      observer: '_handleHeightChanged',
    },
    _reviewersPendingRemove: {
      type: Object,
      value: {
        CC: [],
        REVIEWER: [],
      },
    },
    _sendButtonLabel: {
      type: String,
      computed: '_computeSendButtonLabel(canBeStarted)',
    },
    _ccsEnabled: {
      type: Boolean,
      computed: '_computeCCsEnabled(serverConfig)',
    },
    _savingComments: Boolean,
    _reviewersMutated: {
      type: Boolean,
      value: false,
    },
    _labelsChanged: {
      type: Boolean,
      value: false,
    },
    _saveTooltip: {
      type: String,
      value: ButtonTooltips.SAVE,
      readOnly: true,
    },
    _pluginMessage: {
      type: String,
      value: '',
    },
    _sendDisabled: {
      type: Boolean,
      computed: '_computeSendButtonDisabled(_sendButtonLabel, diffDrafts, ' +
          'draft, _reviewersMutated, _labelsChanged, _includeComments, ' +
          'disabled)',
      observer: '_sendDisabledChanged',
    },
  },

  FocusTarget,

  // TODO(logan): Remove once the fix for issue 6841 is stable on
  // googlesource.com.
  START_REVIEW_MESSAGE,

  behaviors: [
    Gerrit.BaseUrlBehavior,
    Gerrit.KeyboardShortcutBehavior,
    Gerrit.PatchSetBehavior,
    Gerrit.RESTClientBehavior,
  ],

  keyBindings: {
    'esc': '_handleEscKey',
    'ctrl+enter meta+enter': '_handleEnterKey',
  },

  observers: [
    '_changeUpdated(change.reviewers.*, change.owner, serverConfig)',
    '_ccsChanged(_ccs.splices)',
    '_reviewersChanged(_reviewers.splices)',
  ],

  attached() {
    this._getAccount().then(account => {
      this._account = account || {};
    });
  },

  ready() {
    this.$.jsAPI.addElement(this.$.jsAPI.Element.REPLY_DIALOG, this);
  },

  open(opt_focusTarget) {
    this.knownLatestState = LatestPatchState.CHECKING;
    this.fetchChangeUpdates(this.change, this.$.restAPI)
        .then(result => {
          this.knownLatestState = result.isLatest ?
              LatestPatchState.LATEST : LatestPatchState.NOT_LATEST;
        });

    this._focusOn(opt_focusTarget);
    if (this.quote && this.quote.length) {
      // If a reply quote has been provided, use it and clear the property.
      this.draft = this.quote;
      this.quote = '';
    } else {
      // Otherwise, check for an unsaved draft in localstorage.
      this.draft = this._loadStoredDraft();
    }
    if (this.$.restAPI.hasPendingDiffDrafts()) {
      this._savingComments = true;
      this.$.restAPI.awaitPendingDiffDrafts().then(() => {
        this.fire('comment-refresh');
        this._savingComments = false;
      });
    }
  },

  focus() {
    this._focusOn(FocusTarget.ANY);
  },

  getFocusStops() {
    const end = this._sendDisabled ? this.$.cancelButton : this.$.sendButton;
    return {
      start: this.$.reviewers.focusStart,
      end,
    };
  },

  setLabelValue(label, value) {
    const selectorEl =
        this.$.labelScores.$$(`gr-label-score-row[name="${label}"]`);
    if (!selectorEl) { return; }
    selectorEl.setSelectedValue(value);
  },

  getLabelValue(label) {
    const selectorEl =
        this.$.labelScores.$$(`gr-label-score-row[name="${label}"]`);
    if (!selectorEl) { return null; }

    return selectorEl.selectedValue;
  },

  _handleEscKey(e) {
    this.cancel();
  },

  _handleEnterKey(e) {
    this._submit();
  },

  _ccsChanged(splices) {
    if (splices && splices.indexSplices) {
      this._reviewersMutated = true;
      this._processReviewerChange(splices.indexSplices, ReviewerTypes.CC);
    }
  },

  _reviewersChanged(splices) {
    if (splices && splices.indexSplices) {
      this._reviewersMutated = true;
      this._processReviewerChange(splices.indexSplices,
          ReviewerTypes.REVIEWER);
      let key;
      let index;
      let account;
      // Remove any accounts that already exist as a CC.
      for (const splice of splices.indexSplices) {
        for (const addedKey of splice.addedKeys) {
          account = this.get(`_reviewers.${addedKey}`);
          key = this._accountOrGroupKey(account);
          index = this._ccs.findIndex(
              account => this._accountOrGroupKey(account) === key);
          if (index >= 0) {
            this.splice('_ccs', index, 1);
            const message = (account.name || account.email || key) +
                ' moved from CC to reviewer.';
            this.fire('show-alert', {message});
          }
        }
      }
    }
  },

  _processReviewerChange(indexSplices, type) {
    for (const splice of indexSplices) {
      for (const account of splice.removed) {
        if (!this._reviewersPendingRemove[type]) {
          console.err('Invalid type ' + type + ' for reviewer.');
          return;
        }
        this._reviewersPendingRemove[type].push(account);
      }
    }
  },

  /**
   * Resets the state of the _reviewersPendingRemove object, and removes
   * accounts if necessary.
   *
   * @param {boolean} isCancel true if the action is a cancel.
   * @param {Object=} opt_accountIdsTransferred map of account IDs that must
   *     not be removed, because they have been readded in another state.
   */
  _purgeReviewersPendingRemove(isCancel, opt_accountIdsTransferred) {
    let reviewerArr;
    const keep = opt_accountIdsTransferred || {};
    for (const type in this._reviewersPendingRemove) {
      if (this._reviewersPendingRemove.hasOwnProperty(type)) {
        if (!isCancel) {
          reviewerArr = this._reviewersPendingRemove[type];
          for (let i = 0; i < reviewerArr.length; i++) {
            if (!keep[reviewerArr[i]._account_id]) {
              this._removeAccount(reviewerArr[i], type);
            }
          }
        }
        this._reviewersPendingRemove[type] = [];
      }
    }
  },

  /**
   * Removes an account from the change, both on the backend and the client.
   * Does nothing if the account is a pending addition.
   *
   * @param {!Object} account
   * @param {string} type
   *
   * * TODO(beckysiegel) submit Polymer PR
   * @suppress {checkTypes}
   */
  _removeAccount(account, type) {
    if (account._pendingAdd) { return; }

    return this.$.restAPI.removeChangeReviewer(this.change._number,
        account._account_id).then(response => {
          if (!response.ok) { return response; }

          const reviewers = this.change.reviewers[type] || [];
          for (let i = 0; i < reviewers.length; i++) {
            if (reviewers[i]._account_id == account._account_id) {
              this.splice(['change', 'reviewers', type], i, 1);
              break;
            }
          }
        });
  },

  _mapReviewer(reviewer) {
    let reviewerId;
    let confirmed;
    if (reviewer.account) {
      reviewerId = reviewer.account._account_id || reviewer.account.email;
    } else if (reviewer.group) {
      reviewerId = reviewer.group.id;
      confirmed = reviewer.group.confirmed;
    }
    return {reviewer: reviewerId, confirmed};
  },

  send(includeComments, startReview) {
    this.$.reporting.time(SEND_REPLY_TIMING_LABEL);
    const labels = this.$.labelScores.getLabelValues();

    const obj = {
      drafts: includeComments ? 'PUBLISH_ALL_REVISIONS' : 'KEEP',
      labels,
    };

    if (startReview) {
      obj.ready = true;
    }

    if (this.draft != null) {
      obj.message = this.draft;
    }

    const accountAdditions = {};
    obj.reviewers = this.$.reviewers.additions().map(reviewer => {
      if (reviewer.account) {
        accountAdditions[reviewer.account._account_id] = true;
      }
      return this._mapReviewer(reviewer);
    });
    const ccsEl = this.$$('#ccs');
    if (ccsEl) {
      for (let reviewer of ccsEl.additions()) {
        if (reviewer.account) {
          accountAdditions[reviewer.account._account_id] = true;
        }
        reviewer = this._mapReviewer(reviewer);
        reviewer.state = 'CC';
        obj.reviewers.push(reviewer);
      }
    }

    this.disabled = true;

    if (obj.ready && !obj.message) {
      // TODO(logan): The server currently doesn't send email in this case.
      // Insert a dummy message to force an email to be sent. Remove this
      // once the fix for issue 6841 is stable on googlesource.com.
      obj.message = START_REVIEW_MESSAGE;
    }

    const errFn = this._handle400Error.bind(this);
    return this._saveReview(obj, errFn).then(response => {
      if (!response) {
        // Null or undefined response indicates that an error handler
        // took responsibility, so just return.
        return {};
      }
      if (!response.ok) {
        this.fire('server-error', {response});
        return {};
      }

      // TODO(logan): Remove once the required API changes are live and stable
      // on googlesource.com.
      return this._maybeSetReady(startReview, response).catch(err => {
        // We catch error here because we still want to treat this as a
        // successful review.
        console.error('error setting ready:', err);
      }).then(() => {
        this.draft = '';
        this._includeComments = true;
        this.fire('send', null, {bubbles: false});
        return accountAdditions;
      });
    }).then(result => {
      this.disabled = false;
      return result;
    }).catch(err => {
      this.disabled = false;
      throw err;
    });
  },

  /**
   * Returns a promise resolving to true if review was successfully posted,
   * false otherwise.
   *
   * TODO(logan): Remove this once the required API changes are live and
   * stable on googlesource.com.
   */
  _maybeSetReady(startReview, response) {
    return this.$.restAPI.getResponseObject(response).then(result => {
      if (!startReview || result.ready) {
        return Promise.resolve();
      }
      // We don't have confirmation that review was started, so attempt to
      // start review explicitly.
      return this.$.restAPI.startReview(
          this.change._number, null, response => {
            // If we see a 409 response code, then that means the server
            // *does* support moving from WIP->ready when posting a
            // review. Only alert user for non-409 failures.
            if (response.status !== 409) {
              this.fire('server-error', {response});
            }
          });
    });
  },

  _focusOn(section) {
    // Safeguard- always want to focus on something.
    if (!section || section === FocusTarget.ANY) {
      section = this._chooseFocusTarget();
    }
    if (section === FocusTarget.BODY) {
      const textarea = this.$.textarea;
      textarea.async(textarea.getNativeTextarea()
          .focus.bind(textarea.getNativeTextarea()));
    } else if (section === FocusTarget.REVIEWERS) {
      const reviewerEntry = this.$.reviewers.focusStart;
      reviewerEntry.async(reviewerEntry.focus);
    } else if (section === FocusTarget.CCS) {
      const ccEntry = this.$$('#ccs').focusStart;
      ccEntry.async(ccEntry.focus);
    }
  },

  _chooseFocusTarget() {
    // If we are the owner and the reviewers field is empty, focus on that.
    if (this._account && this.change && this.change.owner &&
        this._account._account_id === this.change.owner._account_id &&
        (!this._reviewers || this._reviewers.length === 0)) {
      return FocusTarget.REVIEWERS;
    }

    // Default to BODY.
    return FocusTarget.BODY;
  },

  _handle400Error(response) {
    // A call to _saveReview could fail with a server error if erroneous
    // reviewers were requested. This is signalled with a 400 Bad Request
    // status. The default gr-rest-api-interface error handling would
    // result in a large JSON response body being displayed to the user in
    // the gr-error-manager toast.
    //
    // We can modify the error handling behavior by passing this function
    // through to restAPI as a custom error handling function. Since we're
    // short-circuiting restAPI we can do our own response parsing and fire
    // the server-error ourselves.
    //
    this.disabled = false;

    if (response.status !== 400) {
      // This is all restAPI does when there is no custom error handling.
      this.fire('server-error', {response});
      return response;
    }

    // Process the response body, format a better error message, and fire
    // an event for gr-event-manager to display.
    const jsonPromise = this.$.restAPI.getResponseObject(response);
    return jsonPromise.then(result => {
      const errors = [];
      for (const state of ['reviewers', 'ccs']) {
        if (!result.hasOwnProperty(state)) { continue; }
        for (const reviewer of Object.values(result[state])) {
          if (reviewer.error) {
            errors.push(reviewer.error);
          }
        }
      }
      response = {
        ok: false,
        status: response.status,
        text() { return Promise.resolve(errors.join(', ')); },
      };
      this.fire('server-error', {response});
    });
  },

  _computeHideDraftList(drafts) {
    return Object.keys(drafts || {}).length == 0;
  },

  _computeDraftsTitle(drafts) {
    let total = 0;
    for (const file in drafts) {
      if (drafts.hasOwnProperty(file)) {
        total += drafts[file].length;
      }
    }
    if (total == 0) { return ''; }
    if (total == 1) { return '1 Draft'; }
    if (total > 1) { return total + ' Drafts'; }
  },

  _computeMessagePlaceholder(canBeStarted) {
    return canBeStarted ?
      'Add a note for your reviewers...' :
      'Say something nice...';
  },

  _changeUpdated(changeRecord, owner, serverConfig) {
    this._rebuildReviewerArrays(changeRecord.base, owner, serverConfig);
  },

  _rebuildReviewerArrays(change, owner, serverConfig) {
    this._owner = owner;

    let reviewers = [];
    const ccs = [];

    for (const key in change) {
      if (change.hasOwnProperty(key)) {
        if (key !== 'REVIEWER' && key !== 'CC') {
          console.warn('unexpected reviewer state:', key);
          continue;
        }
        for (const entry of change[key]) {
          if (entry._account_id === owner._account_id) {
            continue;
          }
          switch (key) {
            case 'REVIEWER':
              reviewers.push(entry);
              break;
            case 'CC':
              ccs.push(entry);
              break;
          }
        }
      }
    }

    if (this._ccsEnabled) {
      this._ccs = ccs;
    } else {
      this._ccs = [];
      reviewers = reviewers.concat(ccs);
    }
    this._reviewers = reviewers;
  },

  _accountOrGroupKey(entry) {
    return entry.id || entry._account_id;
  },

  /**
   * Generates a function to filter out reviewer/CC entries. When isCCs is
   * truthy, the function filters out entries that already exist in this._ccs.
   * When falsy, the function filters entries that exist in this._reviewers.
   * @param {boolean} isCCs
   * @return {!Function}
   */
  _filterReviewerSuggestionGenerator(isCCs) {
    return suggestion => {
      let entry;
      if (suggestion.account) {
        entry = suggestion.account;
      } else if (suggestion.group) {
        entry = suggestion.group;
      } else {
        console.warn(
            'received suggestion that was neither account nor group:',
            suggestion);
      }
      if (entry._account_id === this._owner._account_id) {
        return false;
      }

      const key = this._accountOrGroupKey(entry);
      const finder = entry => this._accountOrGroupKey(entry) === key;
      if (isCCs) {
        return this._ccs.find(finder) === undefined;
      }
      return this._reviewers.find(finder) === undefined;
    };
  },

  _getAccount() {
    return this.$.restAPI.getAccount();
  },

  _cancelTapHandler(e) {
    e.preventDefault();
    this.cancel();
  },

  cancel() {
    this.fire('cancel', null, {bubbles: false});
    this.$.textarea.closeDropdown();
    this._purgeReviewersPendingRemove(true);
    this._rebuildReviewerArrays(this.change.reviewers, this._owner,
        this.serverConfig);
  },

  _saveTapHandler(e) {
    e.preventDefault();
    if (this._ccsEnabled && !this.$$('#ccs').submitEntryText()) {
      // Do not proceed with the save if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    this.send(this._includeComments, false).then(keepReviewers => {
      this._purgeReviewersPendingRemove(false, keepReviewers);
    });
  },

  _sendTapHandler(e) {
    e.preventDefault();
    this._submit();
  },

  _submit() {
    if (this._ccsEnabled && !this.$$('#ccs').submitEntryText()) {
      // Do not proceed with the send if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    if (this._sendDisabled) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        bubbles: true,
        detail: {message: EMPTY_REPLY_MESSAGE},
      }));
      return;
    }
    return this.send(this._includeComments, this.canBeStarted)
        .then(keepReviewers => {
          this._purgeReviewersPendingRemove(false, keepReviewers);
        });
  },

  _saveReview(review, opt_errFn) {
    return this.$.restAPI.saveChangeReview(this.change._number, this.patchNum,
        review, opt_errFn);
  },

  _reviewerPendingConfirmationUpdated(reviewer) {
    if (reviewer === null) {
      this.$.reviewerConfirmationOverlay.close();
    } else {
      this._pendingConfirmationDetails =
          this._ccPendingConfirmation || this._reviewerPendingConfirmation;
      this.$.reviewerConfirmationOverlay.open();
    }
  },

  _confirmPendingReviewer() {
    if (this._ccPendingConfirmation) {
      this.$$('#ccs').confirmGroup(this._ccPendingConfirmation.group);
      this._focusOn(FocusTarget.CCS);
    } else {
      this.$.reviewers.confirmGroup(this._reviewerPendingConfirmation.group);
      this._focusOn(FocusTarget.REVIEWERS);
    }
  },

  _cancelPendingReviewer() {
    this._ccPendingConfirmation = null;
    this._reviewerPendingConfirmation = null;

    const target =
        this._ccPendingConfirmation ? FocusTarget.CCS : FocusTarget.REVIEWERS;
    this._focusOn(target);
  },

  _getStorageLocation() {
    // Tests trigger this method without setting change.
    if (!this.change) { return {}; }
    return {
      changeNum: this.change._number,
      patchNum: '@change',
      path: '@change',
    };
  },

  _loadStoredDraft() {
    const draft = this.$.storage.getDraftComment(this._getStorageLocation());
    return draft ? draft.message : '';
  },

  _handleAccountTextEntry() {
    // When either of the account entries has input added to the autocomplete,
    // it should trigger the save button to enable/
    //
    // Note: if the text is removed, the save button will not get disabled.
    this._reviewersMutated = true;
  },

  _draftChanged(newDraft, oldDraft) {
    this.debounce('store', () => {
      if (!newDraft.length && oldDraft) {
        // If the draft has been modified to be empty, then erase the storage
        // entry.
        this.$.storage.eraseDraftComment(this._getStorageLocation());
      } else if (newDraft.length) {
        this.$.storage.setDraftComment(this._getStorageLocation(),
            this.draft);
      }
    }, STORAGE_DEBOUNCE_INTERVAL_MS);
  },

  _handleHeightChanged(e) {
    this.fire('autogrow');
  },

  _handleLabelsChanged() {
    this._labelsChanged = Object.keys(
        this.$.labelScores.getLabelValues()).length !== 0;
  },

  _isState(knownLatestState, value) {
    return knownLatestState === value;
  },

  _reload() {
    // Load the current change without any patch range.
    location.href = this.getBaseUrl() + '/c/' + this.change._number;
  },

  _computeSendButtonLabel(canBeStarted) {
    return canBeStarted ? ButtonLabels.START_REVIEW : ButtonLabels.SEND;
  },

  _computeSendButtonTooltip(canBeStarted) {
    return canBeStarted ? ButtonTooltips.START_REVIEW : ButtonTooltips.SEND;
  },

  _computeCCsEnabled(serverConfig) {
    return serverConfig && serverConfig.note_db_enabled;
  },

  _computeSavingLabelClass(savingComments) {
    return savingComments ? 'saving' : '';
  },

  _computeSendButtonDisabled(buttonLabel, drafts, text, reviewersMutated,
      labelsChanged, includeComments, disabled) {
    if (disabled) { return true; }
    if (buttonLabel === ButtonLabels.START_REVIEW) { return false; }
    const hasDrafts = includeComments && Object.keys(drafts).length;
    return !hasDrafts && !text.length && !reviewersMutated && !labelsChanged;
  },

  _computePatchSetWarning(patchNum, labelsChanged) {
    let str = `Patch ${patchNum} is not latest.`;
    if (labelsChanged) {
      str += ' Voting on a non-latest patch will have no effect.';
    }
    return str;
  },

  setPluginMessage(message) {
    this._pluginMessage = message;
  },

  _sendDisabledChanged(sendDisabled) {
    this.dispatchEvent(new CustomEvent('send-disabled-changed'));
  }
});
