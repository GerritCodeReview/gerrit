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
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator.js';
import '../../shared/gr-account-chip/gr-account-chip.js';
import '../../shared/gr-textarea/gr-textarea.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-formatted-text/gr-formatted-text.js';
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import '../../shared/gr-overlay/gr-overlay.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../shared/gr-storage/gr-storage.js';
import '../../shared/gr-account-list/gr-account-list.js';
import '../gr-label-scores/gr-label-scores.js';
import '../gr-thread-list/gr-thread-list.js';
import '../../../styles/shared-styles.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-reply-dialog_html.js';
import {GrReviewerSuggestionsProvider, SUGGESTIONS_PROVIDERS_USERS_TYPES} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider.js';
import {appContext} from '../../../services/app-context.js';
import {SpecialFilePath} from '../../../constants/constants.js';
import {ExperimentIds} from '../../../services/flags.js';
import {fetchChangeUpdates} from '../../../utils/patch-set-util.js';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import {getDisplayName} from '../../../utils/display-name-util.js';

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
  SAVE: 'Save but do not send notification or change review state',
  START_REVIEW: 'Mark as ready for review and send reply',
  SEND: 'Send reply',
};

const EMPTY_REPLY_MESSAGE = 'Cannot send an empty reply.';

const SEND_REPLY_TIMING_LABEL = 'SendReply';

/**
 * @extends PolymerElement
 */
class GrReplyDialog extends KeyboardShortcutMixin(GestureEventListeners(
    LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-reply-dialog'; }
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

  /**
   * Fired to reload the change page.
   *
   * @event reload
   */

  constructor() {
    super();
    this.FocusTarget = FocusTarget;
    this.reporting = appContext.reportingService;
    this.flagsService = appContext.flagsService;
  }

  static get properties() {
    return {
    /**
     * @type {{ _number: number, removable_reviewers: Array }}
     */
      change: Object,
      /**
       * This is only set, if an uploader exists for the latest patchset, and
       * it is NOT the owner.
       */
      uploader: Object,
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
       * @type {{ commentlinks: Array }}
       */
      projectConfig: Object,
      serverConfig: Object,
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
      _commentEditing: {
        type: Boolean,
        value: false,
      },
      /**
       * Is the UI in the state where the user individually modifies attention
       * set entries?
       */
      _attentionModified: {
        type: Boolean,
        value: false,
      },
      /**
       * Set of account IDs that currently constitutes the attention set, read
       * from change.attention_set. Will be updated by the
       * _computeNewAttention() observer.
       */
      _currentAttentionSet: {
        type: Object,
        value: () => new Set(),
      },
      /**
       * Set of account IDs that should constitute the attention set after
       * publishing the votes/comments. Will be initialized with a default (that
       * matches the default rules that the backend would also apply) by the
       * _computeNewAttention(_account, _reviewers, change) observer.
       */
      _newAttentionSet: {
        type: Object,
        value: () => new Set(),
      },
      _sendDisabled: {
        type: Boolean,
        computed: '_computeSendButtonDisabled(canBeStarted, ' +
          'draftCommentThreads, draft, _reviewersMutated, _labelsChanged, ' +
          '_includeComments, disabled, _commentEditing, _attentionModified)',
        observer: '_sendDisabledChanged',
      },
      draftCommentThreads: {
        type: Array,
        observer: '_handleHeightChanged',
      },
      // Track if the message typed in the reply dialog will be created as a
      // resolved/unresolved patchset level comment
      _isResolvedPatchsetLevelComment: {
        type: Boolean,
        value: true,
      },

      /**
       * A copy of added reviewers, a new copy is created when any change
       * made to the reviewers.
       */
      _allReviewers: {
        type: Array,
        computed: '_computeAllReviewers(_reviewers.*)',
      },
    };
  }

  get keyBindings() {
    return {
      'esc': '_handleEscKey',
      'ctrl+enter meta+enter': '_handleEnterKey',
    };
  }

  static get observers() {
    return [
      '_changeUpdated(change.reviewers.*, change.owner)',
      '_ccsChanged(_ccs.splices)',
      '_reviewersChanged(_reviewers.splices)',
      '_computeNewAttention(' +
        '_account, _reviewers.*, change, draftCommentThreads)',
    ];
  }

  /** @override */
  attached() {
    super.attached();
    this._getAccount().then(account => {
      this._account = account || {};
    });

    this.addEventListener('comment-editing-changed', e => {
      this._commentEditing = e.detail;
    });

    // Plugins on reply-reviewers endpoint can take advantage of these
    // events to add / remove reviewers

    this.addEventListener('add-reviewer', e => {
      // Only support account type, see more from:
      // elements/shared/gr-account-list/gr-account-list.js#addAccountItem
      this.$.reviewers.addAccountItem({account: e.detail.reviewer});
    });

    this.addEventListener('remove-reviewer', e => {
      this.$.reviewers.removeAccount(e.detail.reviewer);
    });
  }

  /** @override */
  ready() {
    super.ready();
    this._isPatchsetCommentsExperimentEnabled = this.flagsService
        .isEnabled(ExperimentIds.PATCHSET_COMMENTS);
    this.$.jsAPI.addElement(this.$.jsAPI.Element.REPLY_DIALOG, this);
  }

  open(opt_focusTarget) {
    this.knownLatestState = LatestPatchState.CHECKING;
    fetchChangeUpdates(this.change, this.$.restAPI)
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
        this.dispatchEvent(new CustomEvent('comment-refresh', {
          composed: true, bubbles: true,
        }));
        this._savingComments = false;
      });
    }
  }

  focus() {
    this._focusOn(FocusTarget.ANY);
  }

  getFocusStops() {
    const end = this._sendDisabled ? this.$.cancelButton : this.$.sendButton;
    return {
      start: this.$.reviewers.focusStart,
      end,
    };
  }

  setLabelValue(label, value) {
    const selectorEl =
        this.$.labelScores.shadowRoot
            .querySelector(`gr-label-score-row[name="${label}"]`);
    if (!selectorEl) { return; }
    selectorEl.setSelectedValue(value);
  }

  getLabelValue(label) {
    const selectorEl =
        this.$.labelScores.shadowRoot
            .querySelector(`gr-label-score-row[name="${label}"]`);
    if (!selectorEl) { return null; }

    return selectorEl.selectedValue;
  }

  _handleEscKey(e) {
    this.cancel();
  }

  _handleEnterKey(e) {
    this._submit();
  }

  _ccsChanged(splices) {
    this._reviewerTypeChanged(splices, ReviewerTypes.CC);
  }

  _reviewersChanged(splices) {
    this._reviewerTypeChanged(splices, ReviewerTypes.REVIEWER);
  }

  _reviewerTypeChanged(splices, reviewerType) {
    if (splices && splices.indexSplices) {
      this._reviewersMutated = true;
      this._processReviewerChange(splices.indexSplices,
          reviewerType);
      let key;
      let index;
      let account;
      // Remove any accounts that already exist as a CC for reviewer
      // or vice versa.
      const isReviewer = ReviewerTypes.REVIEWER === reviewerType;
      for (const splice of splices.indexSplices) {
        for (let i = 0; i < splice.addedCount; i++) {
          account = splice.object[splice.index + i];
          key = this._accountOrGroupKey(account);
          const array = isReviewer ? this._ccs : this._reviewers;
          index = array.findIndex(
              account => this._accountOrGroupKey(account) === key);
          if (index >= 0) {
            this.splice(isReviewer ? '_ccs' : '_reviewers', index, 1);
            const moveFrom = isReviewer ? 'CC' : 'reviewer';
            const moveTo = isReviewer ? 'reviewer' : 'CC';
            const message = (account.name || account.email || key) +
                ` moved from ${moveFrom} to ${moveTo}.`;
            this.dispatchEvent(new CustomEvent('show-alert', {
              detail: {message},
              composed: true, bubbles: true,
            }));
          }
        }
      }
    }
  }

  _processReviewerChange(indexSplices, type) {
    for (const splice of indexSplices) {
      for (const account of splice.removed) {
        if (!this._reviewersPendingRemove[type]) {
          console.error('Invalid type ' + type + ' for reviewer.');
          return;
        }
        this._reviewersPendingRemove[type].push(account);
      }
    }
  }

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
  }

  /**
   * Removes an account from the change, both on the backend and the client.
   * Does nothing if the account is a pending addition.
   *
   * @param {!Object} account
   * @param {string} type
   */
  _removeAccount(account, type) {
    if (account._pendingAdd) { return; }

    return this.$.restAPI.removeChangeReviewer(this.change._number,
        account._account_id).then(response => {
      if (!response.ok) { return response; }

      const reviewers = this.change.reviewers[type] || [];
      for (let i = 0; i < reviewers.length; i++) {
        if (reviewers[i]._account_id == account._account_id) {
          this.splice(`change.reviewers.${type}`, i, 1);
          break;
        }
      }
    });
  }

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
  }

  send(includeComments, startReview) {
    this.reporting.time(SEND_REPLY_TIMING_LABEL);
    const labels = this.$.labelScores.getLabelValues();

    const reviewInput = {
      drafts: includeComments ? 'PUBLISH_ALL_REVISIONS' : 'KEEP',
      labels,
    };

    if (startReview) {
      reviewInput.ready = true;
    }

    reviewInput.ignore_default_attention_set_rules = true;
    reviewInput.add_to_attention_set = [];
    for (const user of this._newAttentionSet) {
      if (!this._currentAttentionSet.has(user)) {
        reviewInput.add_to_attention_set.push({
          user,
          reason: 'manually added in reply dialog',
        });
      }
    }
    reviewInput.remove_from_attention_set = [];
    for (const user of this._currentAttentionSet) {
      if (!this._newAttentionSet.has(user)) {
        reviewInput.remove_from_attention_set.push({
          user,
          reason: 'manually removed in reply dialog',
        });
      }
    }
    this.reportAttentionSetChanges(this._attentionModified,
        reviewInput.add_to_attention_set,
        reviewInput.remove_from_attention_set);

    if (this.draft != null) {
      if (this._isPatchsetCommentsExperimentEnabled) {
        const comment = {
          message: this.draft,
          unresolved: !this._isResolvedPatchsetLevelComment,
        };
        reviewInput.comments = {
          [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [comment],
        };
      } else {
        reviewInput.message = this.draft;
      }
    }

    const accountAdditions = {};
    reviewInput.reviewers = this.$.reviewers.additions().map(reviewer => {
      if (reviewer.account) {
        accountAdditions[reviewer.account._account_id] = true;
      }
      return this._mapReviewer(reviewer);
    });
    const ccsEl = this.$.ccs;
    if (ccsEl) {
      for (let reviewer of ccsEl.additions()) {
        if (reviewer.account) {
          accountAdditions[reviewer.account._account_id] = true;
        }
        reviewer = this._mapReviewer(reviewer);
        reviewer.state = 'CC';
        reviewInput.reviewers.push(reviewer);
      }
    }

    this.disabled = true;

    const errFn = this._handle400Error.bind(this);
    return this._saveReview(reviewInput, errFn)
        .then(response => {
          if (!response) {
            // Null or undefined response indicates that an error handler
            // took responsibility, so just return.
            return {};
          }
          if (!response.ok) {
            this.dispatchEvent(new CustomEvent('server-error', {
              detail: {response},
              composed: true, bubbles: true,
            }));
            return {};
          }

          this.draft = '';
          this._includeComments = true;
          this.dispatchEvent(new CustomEvent('send', {
            composed: true, bubbles: false,
          }));
          return accountAdditions;
        })
        .then(result => {
          this.disabled = false;
          return result;
        })
        .catch(err => {
          this.disabled = false;
          throw err;
        });
  }

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
      const ccEntry = this.$.ccs.focusStart;
      ccEntry.async(ccEntry.focus);
    }
  }

  _chooseFocusTarget() {
    // If we are the owner and the reviewers field is empty, focus on that.
    if (this._account && this.change && this.change.owner &&
        this._account._account_id === this.change.owner._account_id &&
        (!this._reviewers || this._reviewers.length === 0)) {
      return FocusTarget.REVIEWERS;
    }

    // Default to BODY.
    return FocusTarget.BODY;
  }

  _isOwner(account, change) {
    if (!account || !change || !change.owner) return false;
    return account._account_id === change.owner._account_id;
  }

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

    // Using response.clone() here, because getResponseObject() and
    // potentially the generic error handler will want to call text() on the
    // response object, which can only be done once per object.
    const jsonPromise = this.$.restAPI.getResponseObject(response.clone());
    return jsonPromise.then(result => {
      // Only perform custom error handling for 400s and a parseable
      // ReviewResult response.
      if (response.status === 400 && result) {
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
      }
      this.dispatchEvent(new CustomEvent('server-error', {
        detail: {response},
        composed: true, bubbles: true,
      }));
      return null; // Means that the error has been handled.
    });
  }

  _computeHideDraftList(draftCommentThreads) {
    return !draftCommentThreads || draftCommentThreads.length === 0;
  }

  _computeDraftsTitle(draftCommentThreads) {
    const total = draftCommentThreads ? draftCommentThreads.length : 0;
    if (total == 0) { return ''; }
    if (total == 1) { return '1 Draft'; }
    if (total > 1) { return total + ' Drafts'; }
  }

  _computeMessagePlaceholder(canBeStarted) {
    return canBeStarted ?
      'Add a note for your reviewers...' :
      'Say something nice...';
  }

  _changeUpdated(changeRecord, owner) {
    // Polymer 2: check for undefined
    if ([changeRecord, owner].includes(undefined)) {
      return;
    }

    this._rebuildReviewerArrays(changeRecord.base, owner);
  }

  _rebuildReviewerArrays(change, owner) {
    this._owner = owner;

    const reviewers = [];
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

    this._ccs = ccs;
    this._reviewers = reviewers;
  }

  _handleAttentionModify() {
    this._attentionModified = true;
    // If the attention-detail section is expanded without dispatching this
    // event, then the dialog may expand beyond the screen's bottom border.
    this.dispatchEvent(new CustomEvent(
        'iron-resize', {composed: true, bubbles: true}));
  }

  _showAttentionSummary(config, attentionModified) {
    return this._isAttentionSetEnabled(config) && !attentionModified;
  }

  _showAttentionDetails(config, attentionModified) {
    return this._isAttentionSetEnabled(config) && attentionModified;
  }

  _isAttentionSetEnabled(config) {
    return !!config && !!config.change && config.change.enable_attention_set;
  }

  _handleAttentionClick(e) {
    const id = e.target.account._account_id;
    if (!id) return;
    if (this._newAttentionSet.has(id)) {
      this._newAttentionSet.delete(id);
    } else {
      this._newAttentionSet.add(id);
    }
    // Ensure that Polymer picks up the change.
    this._newAttentionSet = new Set(this._newAttentionSet);
  }

  _computeHasNewAttention(account, newAttention) {
    return newAttention && account && newAttention.has(account._account_id);
  }

  _computeNewAttention(user, reviewers, change, draftCommentThreads) {
    if ([user, reviewers, change, draftCommentThreads].includes(undefined)) {
      return;
    }
    this._attentionModified = false;
    this._currentAttentionSet =
        new Set(Object.keys(change.attention_set || {})
            .map(id => parseInt(id)));
    const newAttention = new Set(this._currentAttentionSet);
    // Add everyone that the user is replying to in a comment thread.
    this._computeCommentAccounts(draftCommentThreads).forEach(
        id => newAttention.add(id)
    );
    // Add all new reviewers.
    reviewers.base.filter(r => r._pendingAdd)
        .forEach(r => newAttention.add(r._account_id));
    // Remove the current user.
    if (user) newAttention.delete(user._account_id);
    // Add the uploader, if someone else replies.
    if (this.uploader && user &&
        this.uploader._account_id === user._account_id) {
      newAttention.add(this.uploader._account_id);
    }
    // Add the owner, if someone else replies or the attention set would
    // otherwise be empty.
    if (change.owner) {
      if (!this._isOwner(user, change) || newAttention.size === 0) {
        newAttention.add(change.owner._account_id);
      }
    }
    // Finally make sure that everyone in the attention set is still active as
    // owner, reviewer or cc.
    const allAccountIds = this._allAccounts().map(a => a._account_id);
    this._newAttentionSet = new Set(
        [...newAttention].filter(id => allAccountIds.includes(id)));
  }

  _computeCommentAccounts(threads) {
    const accountIds = new Set();
    threads.forEach(thread => {
      thread.comments.forEach(comment => {
        if (comment.author) {
          accountIds.add(comment.author._account_id);
        }
      });
    });
    return accountIds;
  }

  _isNewAttentionEmpty(config, currentAttentionSet, newAttentionSet) {
    return this._computeNewAttentionNames(
        config, currentAttentionSet, newAttentionSet).length === 0;
  }

  _computeNewAttentionNames(config, currentAttentionSet, newAttentionSet) {
    if ([currentAttentionSet, newAttentionSet].includes(undefined)) return '';
    const addedNames = [...newAttentionSet]
        .filter(id => !currentAttentionSet.has(id))
        .map(id => this._findAccountById(id))
        .filter(account => !!account)
        .map(account => getDisplayName(config, account))
        .sort();
    return addedNames.join(', ');
  }

  _findAccountById(accountId) {
    return this._allAccounts().find(r => r._account_id === accountId);
  }

  _allAccounts() {
    let allAccounts = [];
    if (this.change && this.change.owner) allAccounts.push(this.change.owner);
    if (this.uploader) allAccounts.push(this.uploader);
    if (this._reviewers) allAccounts = [...allAccounts, ...this._reviewers];
    if (this._ccs) allAccounts = [...allAccounts, ...this._ccs];
    return allAccounts;
  }

  _computeShowAttentionCcs(ccs) {
    return !!ccs && ccs.length > 0;
  }

  _accountOrGroupKey(entry) {
    return entry.id || entry._account_id;
  }

  /**
   * Generates a function to filter out reviewer/CC entries. When isCCs is
   * truthy, the function filters out entries that already exist in this._ccs.
   * When falsy, the function filters entries that exist in this._reviewers.
   *
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
  }

  _getAccount() {
    return this.$.restAPI.getAccount();
  }

  _cancelTapHandler(e) {
    e.preventDefault();
    this.cancel();
  }

  cancel() {
    this.dispatchEvent(new CustomEvent('cancel', {
      composed: true, bubbles: false,
    }));
    this.$.textarea.closeDropdown();
    this._purgeReviewersPendingRemove(true);
    this._rebuildReviewerArrays(this.change.reviewers, this._owner);
  }

  _saveClickHandler(e) {
    e.preventDefault();
    if (!this.$.ccs.submitEntryText()) {
      // Do not proceed with the save if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    this.send(this._includeComments, false).then(keepReviewers => {
      this._purgeReviewersPendingRemove(false, keepReviewers);
    });
  }

  _sendTapHandler(e) {
    e.preventDefault();
    this._submit();
  }

  _submit() {
    if (!this.$.ccs.submitEntryText()) {
      // Do not proceed with the send if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    if (this._sendDisabled) {
      this.dispatchEvent(new CustomEvent('show-alert', {
        bubbles: true,
        composed: true,
        detail: {message: EMPTY_REPLY_MESSAGE},
      }));
      return;
    }
    return this.send(this._includeComments, this.canBeStarted)
        .then(keepReviewers => {
          this._purgeReviewersPendingRemove(false, keepReviewers);
        })
        .catch(err => {
          this.dispatchEvent(new CustomEvent('show-error', {
            bubbles: true,
            composed: true,
            detail: {message: `Error submitting review ${err}`},
          }));
        });
  }

  _saveReview(review, opt_errFn) {
    return this.$.restAPI.saveChangeReview(this.change._number, this.patchNum,
        review, opt_errFn);
  }

  _reviewerPendingConfirmationUpdated(reviewer) {
    if (reviewer === null) {
      this.$.reviewerConfirmationOverlay.close();
    } else {
      this._pendingConfirmationDetails =
          this._ccPendingConfirmation || this._reviewerPendingConfirmation;
      this.$.reviewerConfirmationOverlay.open();
    }
  }

  _confirmPendingReviewer() {
    if (this._ccPendingConfirmation) {
      this.$.ccs.confirmGroup(this._ccPendingConfirmation.group);
      this._focusOn(FocusTarget.CCS);
    } else {
      this.$.reviewers.confirmGroup(this._reviewerPendingConfirmation.group);
      this._focusOn(FocusTarget.REVIEWERS);
    }
  }

  _cancelPendingReviewer() {
    this._ccPendingConfirmation = null;
    this._reviewerPendingConfirmation = null;

    const target =
        this._ccPendingConfirmation ? FocusTarget.CCS : FocusTarget.REVIEWERS;
    this._focusOn(target);
  }

  _getStorageLocation() {
    // Tests trigger this method without setting change.
    if (!this.change) { return {}; }
    return {
      changeNum: this.change._number,
      patchNum: '@change',
      path: '@change',
    };
  }

  _loadStoredDraft() {
    const draft = this.$.storage.getDraftComment(this._getStorageLocation());
    return draft ? draft.message : '';
  }

  _handleAccountTextEntry() {
    // When either of the account entries has input added to the autocomplete,
    // it should trigger the save button to enable/
    //
    // Note: if the text is removed, the save button will not get disabled.
    this._reviewersMutated = true;
  }

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
  }

  _handleHeightChanged(e) {
    this.dispatchEvent(new CustomEvent('autogrow', {
      composed: true, bubbles: true,
    }));
  }

  _handleLabelsChanged() {
    this._labelsChanged = Object.keys(
        this.$.labelScores.getLabelValues()).length !== 0;
  }

  _isState(knownLatestState, value) {
    return knownLatestState === value;
  }

  _reload() {
    this.dispatchEvent(new CustomEvent('reload',
        {detail: {clearPatchset: true}, bubbles: false, composed: true}));
    this.cancel();
  }

  _computeSendButtonLabel(canBeStarted) {
    return canBeStarted ? ButtonLabels.SEND + ' and ' +
        ButtonLabels.START_REVIEW : ButtonLabels.SEND;
  }

  _computeSendButtonTooltip(canBeStarted) {
    return canBeStarted ? ButtonTooltips.START_REVIEW : ButtonTooltips.SEND;
  }

  _computeSavingLabelClass(savingComments) {
    return savingComments ? 'saving' : '';
  }

  _computeSendButtonDisabled(
      canBeStarted, draftCommentThreads, text, reviewersMutated,
      labelsChanged, includeComments, disabled, commentEditing,
      attentionModified) {
    // Polymer 2: check for undefined
    if ([
      canBeStarted,
      draftCommentThreads,
      text,
      reviewersMutated,
      labelsChanged,
      includeComments,
      disabled,
      commentEditing,
      attentionModified,
    ].includes(undefined)) {
      return undefined;
    }
    if (commentEditing || disabled) { return true; }
    if (canBeStarted === true) { return false; }
    const hasDrafts = includeComments && draftCommentThreads.length;
    return !hasDrafts && !text.length && !reviewersMutated && !labelsChanged &&
      !attentionModified;
  }

  _computePatchSetWarning(patchNum, labelsChanged) {
    let str = `Patch ${patchNum} is not latest.`;
    if (labelsChanged) {
      str += ' Voting will have no effect.';
    }
    return str;
  }

  setPluginMessage(message) {
    this._pluginMessage = message;
  }

  _sendDisabledChanged(sendDisabled) {
    this.dispatchEvent(new CustomEvent('send-disabled-changed'));
  }

  _getReviewerSuggestionsProvider(change) {
    const provider = GrReviewerSuggestionsProvider.create(this.$.restAPI,
        change._number, SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER);
    provider.init();
    return provider;
  }

  _getCcSuggestionsProvider(change) {
    const provider = GrReviewerSuggestionsProvider.create(this.$.restAPI,
        change._number, SUGGESTIONS_PROVIDERS_USERS_TYPES.CC);
    provider.init();
    return provider;
  }

  _onThreadListModified() {
    // TODO(taoalpha): this won't propogate the changes to the files
    // should consider replacing this with either top level events
    // or gerrit level events

    // emit the event so change-view can also get updated with latest changes
    this.dispatchEvent(new CustomEvent('comment-refresh', {
      composed: true, bubbles: true,
    }));
  }

  reportAttentionSetChanges(modified, addedSet, removedSet) {
    const actions = modified ? ['MODIFIED'] : ['NOT_MODIFIED'];
    const ownerId = (this.change && this.change.owner
        && this.change.owner._account_id) || -1;
    const selfId = (this._account && this._account._account_id) || -1;
    for (const added of (addedSet || [])) {
      const addedId = added.user;
      const self = addedId === selfId ? '_SELF' : '';
      const role = addedId === ownerId ? '_OWNER' : '_REVIEWER';
      actions.push('ADD' + self + role);
    }
    for (const removed of (removedSet || [])) {
      const removedId = removed.user;
      const self = removedId === selfId ? '_SELF' : '';
      const role = removedId === ownerId ? '_OWNER' : '_REVIEWER';
      actions.push('REMOVE' + self + role);
    }
    this.reporting.reportInteraction('attention-set-actions', {actions});
  }

  _computeAllReviewers() {
    return [...this._reviewers];
  }
}

customElements.define(GrReplyDialog.is, GrReplyDialog);
