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
import '@polymer/iron-autogrow-textarea/iron-autogrow-textarea';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-textarea/gr-textarea';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-formatted-text/gr-formatted-text';
import '../../shared/gr-js-api-interface/gr-js-api-interface';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../shared/gr-storage/gr-storage';
import '../../shared/gr-account-list/gr-account-list';
import '../gr-label-scores/gr-label-scores';
import '../gr-thread-list/gr-thread-list';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-reply-dialog_html';
import {
  GrReviewerSuggestionsProvider,
  SUGGESTIONS_PROVIDERS_USERS_TYPES,
} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {appContext} from '../../../services/app-context';
import {
  ChangeStatus,
  DraftsAction,
  ReviewerState,
  SpecialFilePath,
} from '../../../constants/constants';
import {KnownExperimentId} from '../../../services/flags/flags';
import {fetchChangeUpdates} from '../../../utils/patch-set-util';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {accountKey, removeServiceUsers} from '../../../utils/account-util';
import {getDisplayName} from '../../../utils/display-name-util';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {TargetElement} from '../../plugins/gr-plugin-types';
import {customElement, observe, property} from '@polymer/decorators';
import {
  ErrorCallback,
  RestApiService,
} from '../../../services/services/gr-rest-api/gr-rest-api';
import {FixIronA11yAnnouncer} from '../../../types/types';
import {
  AccountAddition,
  AccountInfoInput,
  GrAccountList,
  GroupInfoInput,
  GroupObjectInput,
  RawAccountInput,
} from '../../shared/gr-account-list/gr-account-list';
import {JsApiService} from '../../shared/gr-js-api-interface/gr-js-api-types';
import {
  AccountId,
  AccountInfo,
  AttentionSetInput,
  ChangeInfo,
  CommentInput,
  EmailAddress,
  GroupId,
  GroupInfo,
  isAccount,
  isGroup,
  isReviewerAccountSuggestion,
  isReviewerGroupSuggestion,
  LabelNameToValueMap,
  ParsedJSON,
  PatchSetNum,
  ProjectInfo,
  ReviewerInput,
  Reviewers,
  ReviewInput,
  ReviewResult,
  ServerInfo,
  Suggestion,
} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrLabelScores} from '../gr-label-scores/gr-label-scores';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {
  PolymerDeepPropertyChange,
  PolymerSplice,
  PolymerSpliceChange,
} from '@polymer/polymer/interfaces';
import {
  areSetsEqual,
  assertNever,
  containsAll,
} from '../../../utils/common-util';
import {CommentThread} from '../../diff/gr-comment-api/gr-comment-api';
import {GrTextarea} from '../../shared/gr-textarea/gr-textarea';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {GrStorage, StorageLocation} from '../../shared/gr-storage/gr-storage';
import {isAttentionSetEnabled} from '../../../utils/attention-set-util';
import {CODE_REVIEW, getMaxAccounts} from '../../../utils/label-util';
import {isUnresolved} from '../../../utils/comment-util';

const STORAGE_DEBOUNCE_INTERVAL_MS = 400;

enum FocusTarget {
  ANY = 'any',
  BODY = 'body',
  CCS = 'cc',
  REVIEWERS = 'reviewers',
}

enum ReviewerType {
  REVIEWER = 'REVIEWER',
  CC = 'CC',
}

enum LatestPatchState {
  LATEST = 'latest',
  CHECKING = 'checking',
  NOT_LATEST = 'not-latest',
}

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

interface PendingRemovals {
  CC: (AccountInfoInput | GroupInfoInput)[];
  REVIEWER: (AccountInfoInput | GroupInfoInput)[];
}
const PENDING_REMOVAL_KEYS: (keyof PendingRemovals)[] = [
  ReviewerType.CC,
  ReviewerType.REVIEWER,
];

export interface GrReplyDialog {
  $: {
    restAPI: RestApiService & Element;
    jsAPI: JsApiService & Element;
    reviewers: GrAccountList;
    ccs: GrAccountList;
    cancelButton: GrButton;
    sendButton: GrButton;
    labelScores: GrLabelScores;
    textarea: GrTextarea;
    reviewerConfirmationOverlay: GrOverlay;
    storage: GrStorage;
  };
}

@customElement('gr-reply-dialog')
export class GrReplyDialog extends KeyboardShortcutMixin(
  GestureEventListeners(LegacyElementMixin(PolymerElement))
) {
  static get template() {
    return htmlTemplate;
  }

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

  FocusTarget = FocusTarget;

  reporting = appContext.reportingService;

  flagsService = appContext.flagsService;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean})
  canBeStarted = false;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: String, observer: '_draftChanged'})
  draft = '';

  @property({type: String})
  quote = '';

  @property({type: Object})
  filterReviewerSuggestion: () => (input: Suggestion) => boolean;

  @property({type: Object})
  filterCCSuggestion: () => (input: Suggestion) => boolean;

  @property({type: Object})
  permittedLabels?: LabelNameToValueMap;

  @property({type: Object})
  projectConfig?: ProjectInfo;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({type: String})
  knownLatestState?: LatestPatchState;

  @property({type: Boolean})
  underReview = true;

  @property({type: Object})
  _account?: AccountInfo;

  @property({type: Array})
  _ccs: (AccountInfo | GroupInfo)[] = [];

  @property({type: Object, observer: '_reviewerPendingConfirmationUpdated'})
  _ccPendingConfirmation: GroupObjectInput | null = null;

  @property({
    type: String,
    computed: '_computeMessagePlaceholder(canBeStarted)',
  })
  _messagePlaceholder?: string;

  @property({type: Object})
  _owner?: AccountInfo;

  @property({type: Object, computed: '_computeUploader(change)'})
  _uploader?: AccountInfo;

  @property({type: Object})
  _pendingConfirmationDetails: GroupObjectInput | null = null;

  @property({type: Boolean})
  _includeComments = true;

  @property({type: Array})
  _reviewers: (AccountInfo | GroupInfo)[] = [];

  @property({type: Object, observer: '_reviewerPendingConfirmationUpdated'})
  _reviewerPendingConfirmation: GroupObjectInput | null = null;

  @property({type: Boolean, observer: '_handleHeightChanged'})
  _previewFormatting = false;

  @property({type: Object})
  _reviewersPendingRemove: PendingRemovals = {
    CC: [],
    REVIEWER: [],
  };

  @property({type: String, computed: '_computeSendButtonLabel(canBeStarted)'})
  _sendButtonLabel?: string;

  @property({type: Boolean})
  _savingComments = false;

  @property({type: Boolean})
  _reviewersMutated = false;

  @property({type: Boolean})
  _labelsChanged = false;

  @property({type: String, readOnly: true})
  _saveTooltip: string = ButtonTooltips.SAVE;

  @property({type: String})
  _pluginMessage = '';

  @property({type: Boolean})
  _commentEditing = false;

  @property({type: Boolean})
  _attentionExpanded = false;

  @property({type: Object})
  _currentAttentionSet: Set<AccountId> = new Set();

  @property({type: Object})
  _newAttentionSet: Set<AccountId> = new Set();

  @property({
    type: Boolean,
    computed:
      '_computeSendButtonDisabled(canBeStarted, ' +
      'draftCommentThreads, draft, _reviewersMutated, _labelsChanged, ' +
      '_includeComments, disabled, _commentEditing, _attentionExpanded)',
    observer: '_sendDisabledChanged',
  })
  _sendDisabled?: boolean;

  @property({type: Array, observer: '_handleHeightChanged'})
  draftCommentThreads?: CommentThread[];

  @property({type: Boolean})
  _isResolvedPatchsetLevelComment = true;

  @property({type: Array, computed: '_computeAllReviewers(_reviewers.*)'})
  _allReviewers: (AccountInfo | GroupInfo)[] = [];

  get keyBindings() {
    return {
      esc: '_handleEscKey',
      'ctrl+enter meta+enter': '_handleEnterKey',
    };
  }

  _isPatchsetCommentsExperimentEnabled = false;

  constructor() {
    super();
    this.filterReviewerSuggestion = () =>
      this._filterReviewerSuggestionGenerator(false);
    this.filterCCSuggestion = () =>
      this._filterReviewerSuggestionGenerator(true);
  }

  /** @override */
  attached() {
    super.attached();
    ((IronA11yAnnouncer as unknown) as FixIronA11yAnnouncer).requestAvailability();
    this._getAccount().then(account => {
      if (account) this._account = account;
    });

    this.addEventListener('comment-editing-changed', e => {
      this._commentEditing = (e as CustomEvent).detail;
    });

    // Plugins on reply-reviewers endpoint can take advantage of these
    // events to add / remove reviewers

    this.addEventListener('add-reviewer', e => {
      // Only support account type, see more from:
      // elements/shared/gr-account-list/gr-account-list.js#addAccountItem
      this.$.reviewers.addAccountItem({
        account: (e as CustomEvent).detail.reviewer,
      });
    });

    this.addEventListener('remove-reviewer', e => {
      this.$.reviewers.removeAccount((e as CustomEvent).detail.reviewer);
    });
  }

  /** @override */
  ready() {
    super.ready();
    this._isPatchsetCommentsExperimentEnabled = this.flagsService.isEnabled(
      KnownExperimentId.PATCHSET_COMMENTS
    );
    this.$.jsAPI.addElement(TargetElement.REPLY_DIALOG, this);
  }

  open(focusTarget?: FocusTarget) {
    if (!this.change) throw new Error('missing required change property');
    this.knownLatestState = LatestPatchState.CHECKING;
    fetchChangeUpdates(this.change, this.$.restAPI).then(result => {
      this.knownLatestState = result.isLatest
        ? LatestPatchState.LATEST
        : LatestPatchState.NOT_LATEST;
    });

    this._focusOn(focusTarget);
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
        this.dispatchEvent(
          new CustomEvent('comment-refresh', {
            composed: true,
            bubbles: true,
          })
        );
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

  setLabelValue(label: string, value: string) {
    const selectorEl = this.$.labelScores.shadowRoot?.querySelector(
      `gr-label-score-row[name="${label}"]`
    );
    if (!selectorEl) {
      return;
    }
    (selectorEl as GrLabelScoreRow).setSelectedValue(value);
  }

  getLabelValue(label: string) {
    const selectorEl = this.$.labelScores.shadowRoot?.querySelector(
      `gr-label-score-row[name="${label}"]`
    );
    if (!selectorEl) {
      return null;
    }

    return (selectorEl as GrLabelScoreRow).selectedValue;
  }

  _handleEscKey() {
    this.cancel();
  }

  _handleEnterKey() {
    this._submit();
  }

  @observe('_ccs.splices')
  _ccsChanged(splices: PolymerSpliceChange<AccountInfo[]>) {
    this._reviewerTypeChanged(splices, ReviewerType.CC);
  }

  @observe('_reviewers.splices')
  _reviewersChanged(splices: PolymerSpliceChange<AccountInfo[]>) {
    this._reviewerTypeChanged(splices, ReviewerType.REVIEWER);
  }

  _reviewerTypeChanged(
    splices: PolymerSpliceChange<AccountInfo[]>,
    reviewerType: ReviewerType
  ) {
    if (splices && splices.indexSplices) {
      this._reviewersMutated = true;
      this._processReviewerChange(splices.indexSplices, reviewerType);
      let key: AccountId | EmailAddress | GroupId | undefined;
      let index;
      let account;
      // Remove any accounts that already exist as a CC for reviewer
      // or vice versa.
      const isReviewer = ReviewerType.REVIEWER === reviewerType;
      for (const splice of splices.indexSplices) {
        for (let i = 0; i < splice.addedCount; i++) {
          account = splice.object[splice.index + i];
          key = this._accountOrGroupKey(account);
          const array = isReviewer ? this._ccs : this._reviewers;
          index = array.findIndex(
            account => this._accountOrGroupKey(account) === key
          );
          if (index >= 0) {
            this.splice(isReviewer ? '_ccs' : '_reviewers', index, 1);
            const moveFrom = isReviewer ? 'CC' : 'reviewer';
            const moveTo = isReviewer ? 'reviewer' : 'CC';
            const id = account.name || account.email || key;
            const message = `${id} moved from ${moveFrom} to ${moveTo}.`;
            this.dispatchEvent(
              new CustomEvent('show-alert', {
                detail: {message},
                composed: true,
                bubbles: true,
              })
            );
          }
        }
      }
    }
  }

  _processReviewerChange(
    indexSplices: Array<PolymerSplice<AccountInfo[]>>,
    type: ReviewerType
  ) {
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
   * @param isCancel true if the action is a cancel.
   * @param keep map of account IDs that must
   * not be removed, because they have been readded in another state.
   */
  _purgeReviewersPendingRemove(
    isCancel: boolean,
    keep = new Map<AccountId | EmailAddress, boolean>()
  ) {
    let reviewerArr: (AccountInfoInput | GroupInfoInput)[];
    for (const type of PENDING_REMOVAL_KEYS) {
      if (!isCancel) {
        reviewerArr = this._reviewersPendingRemove[type];
        for (let i = 0; i < reviewerArr.length; i++) {
          const reviewer = reviewerArr[i];
          if (!isAccount(reviewer) || !keep.get(accountKey(reviewer))) {
            this._removeAccount(reviewer, type as ReviewerType);
          }
        }
      }
      this._reviewersPendingRemove[type] = [];
    }
  }

  /**
   * Removes an account from the change, both on the backend and the client.
   * Does nothing if the account is a pending addition.
   */
  _removeAccount(
    account: AccountInfoInput | GroupInfoInput,
    type: ReviewerType
  ) {
    if (!this.change) throw new Error('missing required change property');
    if (account._pendingAdd || !isAccount(account)) {
      return;
    }

    return this.$.restAPI
      .removeChangeReviewer(this.change._number, accountKey(account))
      .then((response?: Response) => {
        if (!response?.ok || !this.change) return;

        const reviewers = this.change.reviewers[type] || [];
        for (let i = 0; i < reviewers.length; i++) {
          if (reviewers[i]._account_id === account._account_id) {
            this.splice(`change.reviewers.${type}`, i, 1);
            break;
          }
        }
      });
  }

  _mapReviewer(addition: AccountAddition): ReviewerInput {
    if (addition.account) {
      return {reviewer: accountKey(addition.account)};
    }
    if (addition.group) {
      const reviewer = decodeURIComponent(addition.group.id) as GroupId;
      const confirmed = addition.group.confirmed;
      return {reviewer, confirmed};
    }
    throw new Error('Reviewer must be either an account or a group.');
  }

  send(
    includeComments: boolean,
    startReview: boolean
  ): Promise<Map<AccountId | EmailAddress, boolean>> {
    this.reporting.time(SEND_REPLY_TIMING_LABEL);
    const labels = this.$.labelScores.getLabelValues();

    const reviewInput: ReviewInput = {
      drafts: includeComments
        ? DraftsAction.PUBLISH_ALL_REVISIONS
        : DraftsAction.KEEP,
      labels,
    };

    if (startReview) {
      reviewInput.ready = true;
    }

    if (isAttentionSetEnabled(this.serverConfig)) {
      const selfName = getDisplayName(this.serverConfig, this._account);
      const reason = `${selfName} replied on the change`;

      reviewInput.ignore_automatic_attention_set_rules = true;
      reviewInput.add_to_attention_set = [];
      for (const user of this._newAttentionSet) {
        if (!this._currentAttentionSet.has(user)) {
          reviewInput.add_to_attention_set.push({user, reason});
        }
      }
      reviewInput.remove_from_attention_set = [];
      for (const user of this._currentAttentionSet) {
        if (!this._newAttentionSet.has(user)) {
          reviewInput.remove_from_attention_set.push({user, reason});
        }
      }
      this.reportAttentionSetChanges(
        this._attentionExpanded,
        reviewInput.add_to_attention_set,
        reviewInput.remove_from_attention_set
      );
    }

    if (this.draft) {
      if (this._isPatchsetCommentsExperimentEnabled) {
        const comment: CommentInput = {
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

    const accountAdditions = new Map<AccountId | EmailAddress, boolean>();
    reviewInput.reviewers = this.$.reviewers.additions().map(reviewer => {
      if (reviewer.account) {
        accountAdditions.set(accountKey(reviewer.account), true);
      }
      return this._mapReviewer(reviewer);
    });
    const ccsEl = this.$.ccs;
    if (ccsEl) {
      for (const addition of ccsEl.additions()) {
        if (addition.account) {
          accountAdditions.set(accountKey(addition.account), true);
        }
        const reviewer = this._mapReviewer(addition);
        reviewer.state = ReviewerState.CC;
        reviewInput.reviewers.push(reviewer);
      }
    }

    this.disabled = true;

    const errFn = (r?: Response | null) => this._handle400Error(r);
    return this._saveReview(reviewInput, errFn)
      .then(response => {
        if (!response) {
          // Null or undefined response indicates that an error handler
          // took responsibility, so just return.
          return new Map<AccountId | EmailAddress, boolean>();
        }
        if (!response.ok) {
          this.dispatchEvent(
            new CustomEvent('server-error', {
              detail: {response},
              composed: true,
              bubbles: true,
            })
          );
          return new Map<AccountId | EmailAddress, boolean>();
        }

        this.draft = '';
        this._includeComments = true;
        this.dispatchEvent(
          new CustomEvent('send', {
            composed: true,
            bubbles: false,
          })
        );
        this.fire('iron-announce', {text: 'Reply sent'}, {bubbles: true});
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

  _focusOn(section?: FocusTarget) {
    // Safeguard- always want to focus on something.
    if (!section || section === FocusTarget.ANY) {
      section = this._chooseFocusTarget();
    }
    if (section === FocusTarget.BODY) {
      const textarea = this.$.textarea;
      textarea.async(() => textarea.getNativeTextarea().focus());
    } else if (section === FocusTarget.REVIEWERS) {
      const reviewerEntry = this.$.reviewers.focusStart;
      reviewerEntry.async(() => reviewerEntry.focus());
    } else if (section === FocusTarget.CCS) {
      const ccEntry = this.$.ccs.focusStart;
      ccEntry.async(() => ccEntry.focus());
    }
  }

  _chooseFocusTarget() {
    // If we are the owner and the reviewers field is empty, focus on that.
    if (
      this._account &&
      this.change &&
      this.change.owner &&
      this._account._account_id === this.change.owner._account_id &&
      (!this._reviewers || this._reviewers.length === 0)
    ) {
      return FocusTarget.REVIEWERS;
    }

    // Default to BODY.
    return FocusTarget.BODY;
  }

  _isOwner(account?: AccountInfo, change?: ChangeInfo) {
    if (!account || !change || !change.owner) return false;
    return account._account_id === change.owner._account_id;
  }

  _handle400Error(response?: Response | null) {
    if (!response) throw new Error('Reponse is empty.');
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
    return jsonPromise.then((parsed: ParsedJSON) => {
      const result = parsed as ReviewResult;
      // Only perform custom error handling for 400s and a parseable
      // ReviewResult response.
      if (response && response.status === 400 && result && result.reviewers) {
        const errors: string[] = [];
        const addReviewers = Object.values(result.reviewers);
        addReviewers.forEach(r => errors.push(r.error ?? 'no explanation'));
        response = {
          ...response,
          ok: false,
          text: () => Promise.resolve(errors.join(', ')),
        };
      }
      this.dispatchEvent(
        new CustomEvent('server-error', {
          detail: {response},
          composed: true,
          bubbles: true,
        })
      );
    });
  }

  _computeHideDraftList(draftCommentThreads?: CommentThread[]) {
    return !draftCommentThreads || draftCommentThreads.length === 0;
  }

  _computeDraftsTitle(draftCommentThreads?: CommentThread[]) {
    const total = draftCommentThreads ? draftCommentThreads.length : 0;
    if (total === 0) {
      return '';
    }
    if (total === 1) {
      return '1 Draft';
    }
    return `${total} Drafts`;
  }

  _computeMessagePlaceholder(canBeStarted: boolean) {
    return canBeStarted
      ? 'Add a note for your reviewers...'
      : 'Say something nice...';
  }

  @observe('change.reviewers.*', 'change.owner')
  _changeUpdated(
    changeRecord: PolymerDeepPropertyChange<Reviewers, Reviewers>,
    owner: AccountInfo
  ) {
    if (changeRecord === undefined || owner === undefined) return;
    this._rebuildReviewerArrays(changeRecord.base, owner);
  }

  _rebuildReviewerArrays(changeReviewers: Reviewers, owner: AccountInfo) {
    this._owner = owner;

    const reviewers = [];
    const ccs = [];

    if (changeReviewers) {
      for (const key of Object.keys(changeReviewers)) {
        if (key !== 'REVIEWER' && key !== 'CC') {
          console.warn('unexpected reviewer state:', key);
          continue;
        }
        if (!changeReviewers[key]) continue;
        for (const entry of changeReviewers[key]!) {
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
    this._attentionExpanded = true;
    // If the attention-detail section is expanded without dispatching this
    // event, then the dialog may expand beyond the screen's bottom border.
    this.dispatchEvent(
      new CustomEvent('iron-resize', {composed: true, bubbles: true})
    );
  }

  _showAttentionSummary(config?: ServerInfo, attentionExpanded?: boolean) {
    return isAttentionSetEnabled(config) && !attentionExpanded;
  }

  _showAttentionDetails(config?: ServerInfo, attentionExpanded?: boolean) {
    return isAttentionSetEnabled(config) && attentionExpanded;
  }

  _handleAttentionClick(e: Event) {
    const id = (e.target as GrAccountChip)?.account?._account_id;
    if (!id) return;

    const selfId = (this._account && this._account._account_id) || -1;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;
    const self = id === selfId ? '_SELF' : '';
    const role = id === ownerId ? '_OWNER' : '_REVIEWER';

    if (this._newAttentionSet.has(id)) {
      this._newAttentionSet.delete(id);
      this.reporting.reportInteraction('attention-set-chip', {
        action: `REMOVE${self}${role}`,
      });
    } else {
      this._newAttentionSet.add(id);
      this.reporting.reportInteraction('attention-set-chip', {
        action: `ADD${self}${role}`,
      });
    }

    // Ensure that Polymer picks up the change.
    this._newAttentionSet = new Set(this._newAttentionSet);
  }

  _computeHasNewAttention(
    account?: AccountInfo,
    newAttention?: Set<AccountId>
  ) {
    return (
      newAttention &&
      account &&
      account._account_id &&
      newAttention.has(account._account_id)
    );
  }

  @observe(
    '_account',
    '_reviewers.*',
    '_ccs.*',
    'change',
    'draftCommentThreads'
  )
  _computeNewAttention(
    currentUser?: AccountInfo,
    reviewers?: PolymerDeepPropertyChange<
      AccountInfoInput[],
      AccountInfoInput[]
    >,
    _?: PolymerDeepPropertyChange<AccountInfoInput[], AccountInfoInput[]>,
    change?: ChangeInfo,
    draftCommentThreads?: CommentThread[]
  ) {
    if (
      currentUser === undefined ||
      currentUser._account_id === undefined ||
      reviewers === undefined ||
      change === undefined ||
      draftCommentThreads === undefined
    ) {
      return;
    }
    this._currentAttentionSet = new Set(
      Object.keys(change.attention_set || {}).map(
        id => parseInt(id) as AccountId
      )
    );
    const newAttention = new Set(this._currentAttentionSet);
    if (change.status === ChangeStatus.NEW) {
      // Add everyone that the user is replying to in a comment thread.
      this._computeCommentAccounts(draftCommentThreads).forEach(id =>
        newAttention.add(id)
      );
      // Remove the current user.
      newAttention.delete(currentUser._account_id);
      // Add all new reviewers.
      reviewers.base
        .filter(r => r._pendingAdd && r._account_id)
        .forEach(r => newAttention.add(r._account_id!));
      // Add the uploader, if someone else replies.
      if (
        this._uploader &&
        this._uploader._account_id !== currentUser._account_id
      ) {
        // An uploader must have an _account_id.
        newAttention.add(this._uploader._account_id!);
      }
      // Add the owner, if someone else replies. Also add the owner, if the
      // attention set would otherwise be empty.
      if (change.owner) {
        if (!this._isOwner(currentUser, change)) {
          // A change owner must have an _account_id.
          newAttention.add(change.owner._account_id!);
        }
      }
    } else {
      // The only reason for adding someone to the attention set for merged or
      // abandoned changes is that someone makes a comment thread unresolved.
      const hasUnresolvedDraft = draftCommentThreads.some(isUnresolved);
      if (change.owner && hasUnresolvedDraft) {
        // A change owner must have an _account_id.
        newAttention.add(change.owner._account_id!);
      }
      // Remove the current user.
      newAttention.delete(currentUser._account_id);
    }
    // Finally make sure that everyone in the attention set is still active as
    // owner, reviewer or cc.
    const allAccountIds = this._allAccounts()
      .map(a => a._account_id)
      .filter(id => !!id);
    this._newAttentionSet = new Set(
      [...newAttention].filter(id => allAccountIds.includes(id))
    );
    this._attentionExpanded = this._newAttentionSet.size > 2;
  }

  _computeShowAttentionTip(newAttentionSet: Set<AccountId>) {
    return newAttentionSet.size > 2;
  }

  _computeCommentAccounts(threads: CommentThread[]) {
    const crLabel = this.change?.labels?.[CODE_REVIEW];
    const maxCrVoteAccountIds = getMaxAccounts(crLabel).map(a => a._account_id);
    const accountIds = new Set<AccountId>();
    threads.forEach(thread => {
      const unresolved = isUnresolved(thread);
      thread.comments.forEach(comment => {
        if (comment.author) {
          // A comment author must have an _account_id.
          const authorId = comment.author._account_id!;
          const hasGivenMaxReviewVote = maxCrVoteAccountIds.includes(authorId);
          if (unresolved || !hasGivenMaxReviewVote) accountIds.add(authorId);
        }
      });
    });
    return accountIds;
  }

  _isNewAttentionEmpty(
    config?: ServerInfo,
    currentAttentionSet?: Set<AccountId>,
    newAttentionSet?: Set<AccountId>
  ) {
    return (
      this._computeNewAttentionAccounts(
        config,
        currentAttentionSet,
        newAttentionSet
      ).length === 0
    );
  }

  _computeDoNotUpdateMessage(
    currentAttentionSet?: Set<AccountId>,
    newAttentionSet?: Set<AccountId>
  ) {
    if (!currentAttentionSet || !newAttentionSet) return '';
    if (areSetsEqual(currentAttentionSet, newAttentionSet)) {
      return 'Do not update the attention set.';
    }
    if (containsAll(currentAttentionSet, newAttentionSet)) {
      return 'Do not add anyone to the attention set.';
    }
    console.error(
      '_computeDoNotUpdateMessage() should not be called when users were added to the attention set.'
    );
    return '';
  }

  _computeNewAttentionAccounts(
    _?: ServerInfo,
    currentAttentionSet?: Set<AccountId>,
    newAttentionSet?: Set<AccountId>
  ) {
    if (currentAttentionSet === undefined || newAttentionSet === undefined) {
      return [];
    }
    return [...newAttentionSet]
      .filter(id => !currentAttentionSet.has(id))
      .map(id => this._findAccountById(id))
      .filter(account => !!account);
  }

  _findAccountById(accountId: AccountId) {
    return this._allAccounts().find(r => r._account_id === accountId);
  }

  _allAccounts() {
    let allAccounts: (AccountInfoInput | GroupInfoInput)[] = [];
    if (this.change && this.change.owner) allAccounts.push(this.change.owner);
    if (this._uploader) allAccounts.push(this._uploader);
    if (this._reviewers) allAccounts = [...allAccounts, ...this._reviewers];
    if (this._ccs) allAccounts = [...allAccounts, ...this._ccs];
    return removeServiceUsers(allAccounts.filter(isAccount));
  }

  /**
   * The newAttentionSet param is only used to force re-computation.
   */
  _removeServiceUsers(accounts: AccountInfo[], _: Set<AccountId>) {
    return removeServiceUsers(accounts);
  }

  _computeShowAttentionCcs(ccs: AccountInfo[]) {
    return removeServiceUsers(ccs).length > 0;
  }

  _computeUploader(change: ChangeInfo) {
    if (
      !change ||
      !change.current_revision ||
      !change.revisions ||
      !change.revisions[change.current_revision]
    ) {
      return undefined;
    }
    const rev = change.revisions[change.current_revision];

    if (
      !rev.uploader ||
      change.owner._account_id === rev.uploader._account_id
    ) {
      return undefined;
    }
    return rev.uploader;
  }

  _accountOrGroupKey(entry: AccountInfo | GroupInfo) {
    if (isAccount(entry)) return accountKey(entry);
    if (isGroup(entry)) return entry.id;
    assertNever(entry, 'entry must be account or group');
  }

  /**
   * Generates a function to filter out reviewer/CC entries. When isCCs is
   * truthy, the function filters out entries that already exist in this._ccs.
   * When falsy, the function filters entries that exist in this._reviewers.
   */
  _filterReviewerSuggestionGenerator(
    isCCs: boolean
  ): (input: Suggestion) => boolean {
    return suggestion => {
      let entry: AccountInfo | GroupInfo;
      if (isReviewerAccountSuggestion(suggestion)) {
        entry = suggestion.account;
        if (entry._account_id === this._owner?._account_id) {
          return false;
        }
      } else if (isReviewerGroupSuggestion(suggestion)) {
        entry = suggestion.group;
      } else {
        console.warn(
          'received suggestion that was neither account nor group:',
          suggestion
        );
        return false;
      }

      const key = this._accountOrGroupKey(entry);
      const finder = (entry: AccountInfo | GroupInfo) =>
        this._accountOrGroupKey(entry) === key;
      if (isCCs) {
        return this._ccs.find(finder) === undefined;
      }
      return this._reviewers.find(finder) === undefined;
    };
  }

  _getAccount() {
    return this.$.restAPI.getAccount();
  }

  _cancelTapHandler(e: Event) {
    e.preventDefault();
    this.cancel();
  }

  cancel() {
    if (!this.change) throw new Error('missing required change property');
    if (!this._owner) throw new Error('missing required _owner property');
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
    this.$.textarea.closeDropdown();
    this._purgeReviewersPendingRemove(true);
    this._rebuildReviewerArrays(this.change.reviewers, this._owner);
  }

  _saveClickHandler(e: Event) {
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

  _sendTapHandler(e: Event) {
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
      this.dispatchEvent(
        new CustomEvent('show-alert', {
          bubbles: true,
          composed: true,
          detail: {message: EMPTY_REPLY_MESSAGE},
        })
      );
      return;
    }
    return this.send(this._includeComments, this.canBeStarted)
      .then(keepReviewers => {
        this._purgeReviewersPendingRemove(false, keepReviewers);
      })
      .catch(err => {
        this.dispatchEvent(
          new CustomEvent('show-error', {
            bubbles: true,
            composed: true,
            detail: {message: `Error submitting review ${err}`},
          })
        );
      });
  }

  _saveReview(review: ReviewInput, errFn?: ErrorCallback) {
    if (!this.change) throw new Error('missing required change property');
    if (!this.patchNum) throw new Error('missing required patchNum property');
    return this.$.restAPI.saveChangeReview(
      this.change._number,
      this.patchNum,
      review,
      errFn
    );
  }

  _reviewerPendingConfirmationUpdated(reviewer: RawAccountInput | null) {
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
      return;
    }
    if (this._reviewerPendingConfirmation) {
      this.$.reviewers.confirmGroup(this._reviewerPendingConfirmation.group);
      this._focusOn(FocusTarget.REVIEWERS);
      return;
    }
    console.error('_confirmPendingReviewer called without pending confirm');
  }

  _cancelPendingReviewer() {
    this._ccPendingConfirmation = null;
    this._reviewerPendingConfirmation = null;

    const target = this._ccPendingConfirmation
      ? FocusTarget.CCS
      : FocusTarget.REVIEWERS;
    this._focusOn(target);
  }

  _getStorageLocation(): StorageLocation {
    if (!this.change) throw new Error('missing required change property');
    return {
      changeNum: this.change._number,
      patchNum: '@change',
      path: '@change',
    };
  }

  _loadStoredDraft() {
    const draft = this.$.storage.getDraftComment(this._getStorageLocation());
    return draft?.message ?? '';
  }

  _handleAccountTextEntry() {
    // When either of the account entries has input added to the autocomplete,
    // it should trigger the save button to enable/
    //
    // Note: if the text is removed, the save button will not get disabled.
    this._reviewersMutated = true;
  }

  _draftChanged(newDraft: string, oldDraft?: string) {
    this.debounce(
      'store',
      () => {
        if (!newDraft.length && oldDraft) {
          // If the draft has been modified to be empty, then erase the storage
          // entry.
          this.$.storage.eraseDraftComment(this._getStorageLocation());
        } else if (newDraft.length) {
          this.$.storage.setDraftComment(
            this._getStorageLocation(),
            this.draft
          );
        }
      },
      STORAGE_DEBOUNCE_INTERVAL_MS
    );
  }

  _handleHeightChanged() {
    this.dispatchEvent(
      new CustomEvent('autogrow', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _handleLabelsChanged() {
    this._labelsChanged =
      Object.keys(this.$.labelScores.getLabelValues()).length !== 0;
  }

  _isState(knownLatestState?: LatestPatchState, value?: LatestPatchState) {
    return knownLatestState === value;
  }

  _reload() {
    this.dispatchEvent(
      new CustomEvent('reload', {
        detail: {clearPatchset: true},
        bubbles: false,
        composed: true,
      })
    );
    this.cancel();
  }

  _computeSendButtonLabel(canBeStarted: boolean) {
    return canBeStarted
      ? ButtonLabels.SEND + ' and ' + ButtonLabels.START_REVIEW
      : ButtonLabels.SEND;
  }

  _computeSendButtonTooltip(canBeStarted: boolean) {
    return canBeStarted ? ButtonTooltips.START_REVIEW : ButtonTooltips.SEND;
  }

  _computeSavingLabelClass(savingComments: boolean) {
    return savingComments ? 'saving' : '';
  }

  _computeSendButtonDisabled(
    canBeStarted?: boolean,
    draftCommentThreads?: CommentThread[],
    text?: string,
    reviewersMutated?: boolean,
    labelsChanged?: boolean,
    includeComments?: boolean,
    disabled?: boolean,
    commentEditing?: boolean,
    attentionExpanded?: boolean
  ) {
    if (
      canBeStarted === undefined ||
      draftCommentThreads === undefined ||
      text === undefined ||
      reviewersMutated === undefined ||
      labelsChanged === undefined ||
      includeComments === undefined ||
      disabled === undefined ||
      commentEditing === undefined ||
      attentionExpanded === undefined
    ) {
      return undefined;
    }
    if (commentEditing || disabled) {
      return true;
    }
    if (canBeStarted === true) {
      return false;
    }
    const hasDrafts = includeComments && draftCommentThreads.length;
    return (
      !hasDrafts &&
      !text.length &&
      !reviewersMutated &&
      !labelsChanged &&
      !attentionExpanded
    );
  }

  _computePatchSetWarning(patchNum?: PatchSetNum, labelsChanged?: boolean) {
    let str = `Patch ${patchNum} is not latest.`;
    if (labelsChanged) {
      str += ' Voting will have no effect.';
    }
    return str;
  }

  setPluginMessage(message: string) {
    this._pluginMessage = message;
  }

  _sendDisabledChanged() {
    this.dispatchEvent(new CustomEvent('send-disabled-changed'));
  }

  _getReviewerSuggestionsProvider(change: ChangeInfo) {
    const provider = GrReviewerSuggestionsProvider.create(
      this.$.restAPI,
      change._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER
    );
    provider.init();
    return provider;
  }

  _getCcSuggestionsProvider(change: ChangeInfo) {
    const provider = GrReviewerSuggestionsProvider.create(
      this.$.restAPI,
      change._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.CC
    );
    provider.init();
    return provider;
  }

  _onThreadListModified() {
    // TODO(taoalpha): this won't propogate the changes to the files
    // should consider replacing this with either top level events
    // or gerrit level events

    // emit the event so change-view can also get updated with latest changes
    this.dispatchEvent(
      new CustomEvent('comment-refresh', {
        composed: true,
        bubbles: true,
      })
    );
  }

  reportAttentionSetChanges(
    modified: boolean,
    addedSet?: AttentionSetInput[],
    removedSet?: AttentionSetInput[]
  ) {
    const actions = modified ? ['MODIFIED'] : ['NOT_MODIFIED'];
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;
    const selfId = (this._account && this._account._account_id) || -1;
    for (const added of addedSet || []) {
      const addedId = added.user;
      const self = addedId === selfId ? '_SELF' : '';
      const role = addedId === ownerId ? '_OWNER' : '_REVIEWER';
      actions.push('ADD' + self + role);
    }
    for (const removed of removedSet || []) {
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

declare global {
  interface HTMLElementTagNameMap {
    'gr-reply-dialog': GrReplyDialog;
  }
}
