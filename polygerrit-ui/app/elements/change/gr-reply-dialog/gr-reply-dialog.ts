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
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-account-list/gr-account-list';
import '../gr-label-scores/gr-label-scores';
import '../gr-thread-list/gr-thread-list';
import '../../../styles/shared-styles';
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
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {
  accountOrGroupKey,
  isReviewerOrCC,
  mapReviewer,
  removeServiceUsers,
} from '../../../utils/account-util';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {TargetElement} from '../../../api/plugin';
import {customElement, observe, property} from '@polymer/decorators';
import {FixIronA11yAnnouncer} from '../../../types/types';
import {
  AccountAddition,
  AccountInfoInput,
  GrAccountList,
  GroupInfoInput,
  GroupObjectInput,
  RawAccountInput,
} from '../../shared/gr-account-list/gr-account-list';
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
  isDetailedLabelInfo,
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
  PolymerSpliceChange,
} from '@polymer/polymer/interfaces';
import {
  areSetsEqual,
  assertIsDefined,
  containsAll,
  queryAndAssert,
} from '../../../utils/common-util';
import {CommentThread, isUnresolved} from '../../../utils/comment-util';
import {GrTextarea} from '../../shared/gr-textarea/gr-textarea';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {
  getApprovalInfo,
  getMaxAccounts,
  StandardLabels,
} from '../../../utils/label-util';
import {pluralize} from '../../../utils/string-util';
import {
  fireAlert,
  fireEvent,
  fireIronAnnounce,
  fireReload,
  fireServerError,
} from '../../../utils/event-util';
import {ErrorCallback} from '../../../api/rest';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {StorageLocation} from '../../../services/storage/gr-storage';
import {Interaction, Timing} from '../../../constants/reporting';
import {getReplyByReason} from '../../../utils/attention-set-util';

const STORAGE_DEBOUNCE_INTERVAL_MS = 400;

export enum FocusTarget {
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
  SAVE: 'Send changes and comments as work in progress but do not start review',
  START_REVIEW: 'Mark as ready for review and send reply',
  SEND: 'Send reply',
  DISABLED_COMMENT_EDITING: 'Save draft comments to enable send',
};

const EMPTY_REPLY_MESSAGE = 'Cannot send an empty reply.';

export interface GrReplyDialog {
  $: {
    reviewers: GrAccountList;
    ccs: GrAccountList;
    cancelButton: GrButton;
    sendButton: GrButton;
    labelScores: GrLabelScores;
    textarea: GrTextarea;
    reviewerConfirmationOverlay: GrOverlay;
  };
}

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = KeyboardShortcutMixin(PolymerElement);

@customElement('gr-reply-dialog')
export class GrReplyDialog extends base {
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

  private readonly reporting = appContext.reportingService;

  private readonly changeService = appContext.changeService;

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean})
  canBeStarted = false;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({
    type: Boolean,
    computed: '_computeHasDrafts(draft, draftCommentThreads.*)',
  })
  hasDrafts = false;

  @property({type: String, observer: '_draftChanged'})
  draft = '';

  @property({type: String})
  quote = '';

  @property({type: Object})
  filterReviewerSuggestion: (input: Suggestion) => boolean;

  @property({type: Object})
  filterCCSuggestion: (input: Suggestion) => boolean;

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

  @property({type: Number})
  _attentionCcsCount = 0;

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

  @property({type: String, computed: '_computeSendButtonLabel(canBeStarted)'})
  _sendButtonLabel?: string;

  @property({type: Boolean})
  _savingComments = false;

  @property({type: Boolean})
  _reviewersMutated = false;

  /**
   * Signifies that the user has changed their vote on a label or (if they have
   * not yet voted on a label) if a selected vote is different from the default
   * vote.
   */
  @property({type: Boolean})
  _labelsChanged = false;

  @property({type: String})
  readonly _saveTooltip: string = ButtonTooltips.SAVE;

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
      '_includeComments, disabled, _commentEditing, change, _account)',
    observer: '_sendDisabledChanged',
  })
  _sendDisabled?: boolean;

  @property({type: Array, observer: '_handleHeightChanged'})
  draftCommentThreads: CommentThread[] | undefined;

  @property({type: Boolean})
  _isResolvedPatchsetLevelComment = true;

  @property({type: Array, computed: '_computeAllReviewers(_reviewers.*)'})
  _allReviewers: (AccountInfo | GroupInfo)[] = [];

  private readonly restApiService = appContext.restApiService;

  private readonly storage = appContext.storageService;

  private readonly jsAPI = appContext.jsApiService;

  private storeTask?: DelayedTask;

  get keyBindings() {
    return {
      esc: '_handleEscKey',
      'ctrl+enter meta+enter': '_handleEnterKey',
    };
  }

  constructor() {
    super();
    this.filterReviewerSuggestion =
      this._filterReviewerSuggestionGenerator(false);
    this.filterCCSuggestion = this._filterReviewerSuggestionGenerator(true);
  }

  override connectedCallback() {
    super.connectedCallback();
    (
      IronA11yAnnouncer as unknown as FixIronA11yAnnouncer
    ).requestAvailability();
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

  override ready() {
    super.ready();
    this.jsAPI.addElement(TargetElement.REPLY_DIALOG, this);
  }

  override disconnectedCallback() {
    this.storeTask?.cancel();
    super.disconnectedCallback();
  }

  open(focusTarget?: FocusTarget) {
    assertIsDefined(this.change, 'change');
    this.knownLatestState = LatestPatchState.CHECKING;
    this.changeService.fetchChangeUpdates(this.change).then(result => {
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
    if (this.restApiService.hasPendingDiffDrafts()) {
      this._savingComments = true;
      this.restApiService.awaitPendingDiffDrafts().then(() => {
        fireEvent(this, 'comment-refresh');
        this._savingComments = false;
      });
    }
  }

  _computeHasDrafts(
    draft: string,
    draftCommentThreads: PolymerDeepPropertyChange<
      CommentThread[] | undefined,
      CommentThread[] | undefined
    >
  ) {
    if (draftCommentThreads.base === undefined) return false;
    return draft.length > 0 || draftCommentThreads.base.length > 0;
  }

  override focus() {
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
    const selectorEl = this.getLabelScores().shadowRoot?.querySelector(
      `gr-label-score-row[name="${label}"]`
    );
    if (!selectorEl) {
      return;
    }
    (selectorEl as GrLabelScoreRow).setSelectedValue(value);
  }

  getLabelValue(label: string) {
    const selectorEl = this.getLabelScores().shadowRoot?.querySelector(
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
  _ccsChanged(splices: PolymerSpliceChange<AccountInfo[] | GroupInfo[]>) {
    this._reviewerTypeChanged(splices, ReviewerType.CC);
  }

  @observe('_reviewers.splices')
  _reviewersChanged(splices: PolymerSpliceChange<AccountInfo[] | GroupInfo[]>) {
    this._reviewerTypeChanged(splices, ReviewerType.REVIEWER);
  }

  _reviewerTypeChanged(
    splices: PolymerSpliceChange<AccountInfo[] | GroupInfo[]>,
    reviewerType: ReviewerType
  ) {
    if (splices && splices.indexSplices) {
      this._reviewersMutated = true;
      let key: AccountId | EmailAddress | GroupId | undefined;
      let index;
      let account;
      // Remove any accounts that already exist as a CC for reviewer
      // or vice versa.
      const isReviewer = ReviewerType.REVIEWER === reviewerType;
      for (const splice of splices.indexSplices) {
        for (let i = 0; i < splice.addedCount; i++) {
          account = splice.object[splice.index + i];
          key = accountOrGroupKey(account);
          const array = isReviewer ? this._ccs : this._reviewers;
          index = array.findIndex(
            account => accountOrGroupKey(account) === key
          );
          if (index >= 0) {
            this.splice(isReviewer ? '_ccs' : '_reviewers', index, 1);
            const moveFrom = isReviewer ? 'CC' : 'reviewer';
            const moveTo = isReviewer ? 'reviewer' : 'CC';
            const id = account.name || key;
            const message = `${id} moved from ${moveFrom} to ${moveTo}.`;
            fireAlert(this, message);
          }
        }
      }
    }
  }

  getUnresolvedPatchsetLevelClass(isResolvedPatchsetLevelComment: boolean) {
    return isResolvedPatchsetLevelComment ? 'resolved' : 'unresolved';
  }

  computeReviewers(change: ChangeInfo) {
    const reviewers: ReviewerInput[] = [];
    const addToReviewInput = (
      additions: AccountAddition[],
      state?: ReviewerState
    ) => {
      additions.forEach(addition => {
        const reviewer = mapReviewer(addition);
        if (state) reviewer.state = state;
        reviewers.push(reviewer);
      });
    };
    addToReviewInput(this.$.reviewers.additions(), ReviewerState.REVIEWER);
    addToReviewInput(this.$.ccs.additions(), ReviewerState.CC);
    addToReviewInput(
      this.$.reviewers.removals().filter(
        r =>
          isReviewerOrCC(change, r) &&
          // ignore removal from reviewer request if being added to CC
          !this.$.ccs
            .additions()
            .some(
              account =>
                mapReviewer(account).reviewer === mapReviewer(r).reviewer
            )
      ),
      ReviewerState.REMOVED
    );
    addToReviewInput(
      this.$.ccs.removals().filter(
        r =>
          isReviewerOrCC(change, r) &&
          // ignore removal from CC request if being added as reviewer
          !this.$.reviewers
            .additions()
            .some(
              account =>
                mapReviewer(account).reviewer === mapReviewer(r).reviewer
            )
      ),
      ReviewerState.REMOVED
    );
    return reviewers;
  }

  send(includeComments: boolean, startReview: boolean) {
    this.reporting.time(Timing.SEND_REPLY);
    const labels = this.getLabelScores().getLabelValues();

    const reviewInput: ReviewInput = {
      drafts: includeComments
        ? DraftsAction.PUBLISH_ALL_REVISIONS
        : DraftsAction.KEEP,
      labels,
    };

    if (startReview) {
      reviewInput.ready = true;
    }

    const reason = getReplyByReason(this._account, this.serverConfig);

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

    if (this.draft) {
      const comment: CommentInput = {
        message: this.draft,
        unresolved: !this._isResolvedPatchsetLevelComment,
      };
      reviewInput.comments = {
        [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [comment],
      };
    }

    assertIsDefined(this.change, 'change');
    reviewInput.reviewers = this.computeReviewers(this.change);
    this.disabled = true;

    const errFn = (r?: Response | null) => this._handle400Error(r);
    return this._saveReview(reviewInput, errFn)
      .then(response => {
        if (!response) {
          // Null or undefined response indicates that an error handler
          // took responsibility, so just return.
          return;
        }
        if (!response.ok) {
          fireServerError(response);
          return;
        }

        this.draft = '';
        this._includeComments = true;
        this.dispatchEvent(
          new CustomEvent('send', {
            composed: true,
            bubbles: false,
          })
        );
        fireIronAnnounce(this, 'Reply sent');
        return;
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
      const textarea = queryAndAssert<GrTextarea>(this, 'gr-textarea');
      setTimeout(() => textarea.getNativeTextarea().focus());
    } else if (section === FocusTarget.REVIEWERS) {
      const reviewerEntry = this.$.reviewers.focusStart;
      setTimeout(() => reviewerEntry.focus());
    } else if (section === FocusTarget.CCS) {
      const ccEntry = this.$.ccs.focusStart;
      setTimeout(() => ccEntry.focus());
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

  _handle400Error(r?: Response | null) {
    if (!r) throw new Error('Response is empty.');
    let response: Response = r;
    // A call to _saveReview could fail with a server error if erroneous
    // reviewers were requested. This is signalled with a 400 Bad Request
    // status. The default gr-rest-api error handling would result in a large
    // JSON response body being displayed to the user in the gr-error-manager
    // toast.
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
    const jsonPromise = this.restApiService.getResponseObject(response.clone());
    return jsonPromise.then((parsed: ParsedJSON) => {
      const result = parsed as ReviewResult;
      // Only perform custom error handling for 400s and a parseable
      // ReviewResult response.
      if (response.status === 400 && result && result.reviewers) {
        const errors: string[] = [];
        const addReviewers = Object.values(result.reviewers);
        addReviewers.forEach(r => errors.push(r.error ?? 'no explanation'));
        response = {
          ...response,
          ok: false,
          text: () => Promise.resolve(errors.join(', ')),
        };
      }
      fireServerError(response);
    });
  }

  _computeHideDraftList(draftCommentThreads?: CommentThread[]) {
    return !draftCommentThreads || draftCommentThreads.length === 0;
  }

  _computeDraftsTitle(draftCommentThreads?: CommentThread[]) {
    const total = draftCommentThreads ? draftCommentThreads.length : 0;
    return pluralize(total, 'Draft');
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
          this.reporting.error(new Error(`Unexpected reviewer state: ${key}`));
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
  }

  @observe('_attentionExpanded')
  _onAttentionExpandedChange() {
    // If the attention-detail section is expanded without dispatching this
    // event, then the dialog may expand beyond the screen's bottom border.
    fireEvent(this, 'iron-resize');
  }

  _showAttentionSummary(attentionExpanded?: boolean) {
    return !attentionExpanded;
  }

  _showAttentionDetails(attentionExpanded?: boolean) {
    return attentionExpanded;
  }

  _computeAttentionButtonTitle(sendDisabled?: boolean) {
    return sendDisabled
      ? 'Modify the attention set by adding a comment or use the account ' +
          'hovercard in the change page.'
      : 'Edit attention set changes';
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
      this.reporting.reportInteraction(Interaction.ATTENTION_SET_CHIP, {
        action: `REMOVE${self}${role}`,
      });
    } else {
      this._newAttentionSet.add(id);
      this.reporting.reportInteraction(Interaction.ATTENTION_SET_CHIP, {
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
    'draftCommentThreads',
    '_includeComments',
    '_labelsChanged',
    'hasDrafts'
  )
  _computeNewAttention(
    currentUser?: AccountInfo,
    reviewers?: PolymerDeepPropertyChange<
      AccountInfoInput[],
      AccountInfoInput[]
    >,
    ccs?: PolymerDeepPropertyChange<AccountInfoInput[], AccountInfoInput[]>,
    change?: ChangeInfo,
    draftCommentThreads?: CommentThread[],
    includeComments?: boolean,
    _labelsChanged?: boolean,
    hasDrafts?: boolean
  ) {
    if (
      currentUser === undefined ||
      currentUser._account_id === undefined ||
      reviewers === undefined ||
      ccs === undefined ||
      change === undefined ||
      draftCommentThreads === undefined ||
      includeComments === undefined
    ) {
      return;
    }
    // The draft comments are only relevant for the attention set as long as the
    // user actually plans to publish their drafts.
    draftCommentThreads = includeComments ? draftCommentThreads : [];
    const hasVote = !!_labelsChanged;
    const isOwner = this._isOwner(currentUser, change);
    const isUploader = this._uploader?._account_id === currentUser._account_id;
    this._attentionCcsCount = removeServiceUsers(ccs.base).length;
    this._currentAttentionSet = new Set(
      Object.keys(change.attention_set || {}).map(id => Number(id) as AccountId)
    );
    const newAttention = new Set(this._currentAttentionSet);
    if (change.status === ChangeStatus.NEW) {
      // Add everyone that the user is replying to in a comment thread.
      this._computeCommentAccounts(draftCommentThreads).forEach(id =>
        newAttention.add(id)
      );
      // Remove the current user.
      newAttention.delete(currentUser._account_id);
      // Add all new reviewers, but not the current reviewer, if they are also
      // sending a draft or a label vote.
      const notIsReviewerAndHasDraftOrLabel = (r: AccountInfo) =>
        !(r._account_id === currentUser._account_id && (hasDrafts || hasVote));
      reviewers.base
        .filter(r => r._account_id)
        .filter(r => r._pendingAdd || (this.canBeStarted && isOwner))
        .filter(notIsReviewerAndHasDraftOrLabel)
        .forEach(r => newAttention.add(r._account_id!));
      // Add owner and uploader, if someone else replies.
      if (hasDrafts || hasVote) {
        if (this._uploader?._account_id && !isUploader) {
          newAttention.add(this._uploader._account_id);
        }
        if (change.owner?._account_id && !isOwner) {
          newAttention.add(change.owner._account_id);
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
    this._attentionExpanded = this._computeShowAttentionTip(
      currentUser,
      change.owner,
      this._currentAttentionSet,
      this._newAttentionSet
    );
  }

  _computeShowAttentionTip(
    currentUser?: AccountInfo,
    owner?: AccountInfo,
    currentAttentionSet?: Set<AccountId>,
    newAttentionSet?: Set<AccountId>
  ) {
    if (!currentUser || !owner || !currentAttentionSet || !newAttentionSet)
      return false;
    const isOwner = currentUser._account_id === owner._account_id;
    const addedIds = [...newAttentionSet].filter(
      id => !currentAttentionSet.has(id)
    );
    return isOwner && addedIds.length > 2;
  }

  _computeCommentAccounts(threads: CommentThread[]) {
    const crLabel = this.change?.labels?.[StandardLabels.CODE_REVIEW];
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

  _computeShowNoAttentionUpdate(
    config?: ServerInfo,
    currentAttentionSet?: Set<AccountId>,
    newAttentionSet?: Set<AccountId>,
    sendDisabled?: boolean
  ) {
    return (
      sendDisabled ||
      this._computeNewAttentionAccounts(
        config,
        currentAttentionSet,
        newAttentionSet
      ).length === 0
    );
  }

  _computeDoNotUpdateMessage(
    currentAttentionSet?: Set<AccountId>,
    newAttentionSet?: Set<AccountId>,
    sendDisabled?: boolean
  ) {
    if (!currentAttentionSet || !newAttentionSet) return '';
    if (sendDisabled || areSetsEqual(currentAttentionSet, newAttentionSet)) {
      return 'No changes to the attention set.';
    }
    if (containsAll(currentAttentionSet, newAttentionSet)) {
      return 'No additions to the attention set.';
    }
    this.reporting.error(
      new Error(
        '_computeDoNotUpdateMessage()' +
          'should not be called when users were added to the attention set.'
      )
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
        this.reporting.error(
          new Error(`Suggestion is neither account nor group: ${suggestion}`)
        );
        return false;
      }

      const key = accountOrGroupKey(entry);
      const finder = (entry: AccountInfo | GroupInfo) =>
        accountOrGroupKey(entry) === key;
      if (isCCs) {
        return this._ccs.find(finder) === undefined;
      }
      return this._reviewers.find(finder) === undefined;
    };
  }

  _getAccount() {
    return this.restApiService.getAccount();
  }

  _cancelTapHandler(e: Event) {
    e.preventDefault();
    this.cancel();
  }

  cancel() {
    assertIsDefined(this.change, 'change');
    if (!this._owner) throw new Error('missing required _owner property');
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
    queryAndAssert<GrTextarea>(this, 'gr-textarea').closeDropdown();
    this.$.reviewers.clearPendingRemovals();
    this._rebuildReviewerArrays(this.change.reviewers, this._owner);
  }

  _saveClickHandler(e: Event) {
    e.preventDefault();
    if (!this.$.ccs.submitEntryText()) {
      // Do not proceed with the save if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    this.send(this._includeComments, false);
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
      fireAlert(this, EMPTY_REPLY_MESSAGE);
      return;
    }
    return this.send(this._includeComments, this.canBeStarted).catch(err => {
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
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    return this.restApiService.saveChangeReview(
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
    this.reporting.error(
      new Error('_confirmPendingReviewer called without pending confirm')
    );
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
    assertIsDefined(this.change, 'change');
    return {
      changeNum: this.change._number,
      patchNum: '@change',
      path: '@change',
    };
  }

  _loadStoredDraft() {
    const draft = this.storage.getDraftComment(this._getStorageLocation());
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
    this.storeTask = debounce(
      this.storeTask,
      () => {
        if (!newDraft.length && oldDraft) {
          // If the draft has been modified to be empty, then erase the storage
          // entry.
          this.storage.eraseDraftComment(this._getStorageLocation());
        } else if (newDraft.length) {
          this.storage.setDraftComment(this._getStorageLocation(), this.draft);
        }
      },
      STORAGE_DEBOUNCE_INTERVAL_MS
    );
  }

  _handleHeightChanged() {
    fireEvent(this, 'autogrow');
  }

  getLabelScores() {
    return this.$.labelScores || queryAndAssert(this, 'gr-label-scores');
  }

  _handleLabelsChanged() {
    this._labelsChanged =
      Object.keys(this.getLabelScores().getLabelValues(false)).length !== 0;
  }

  _isState(knownLatestState?: LatestPatchState, value?: LatestPatchState) {
    return knownLatestState === value;
  }

  _reload() {
    fireReload(this, true);
    this.cancel();
  }

  _computeSendButtonLabel(canBeStarted: boolean) {
    return canBeStarted
      ? ButtonLabels.SEND + ' and ' + ButtonLabels.START_REVIEW
      : ButtonLabels.SEND;
  }

  _computeSendButtonTooltip(canBeStarted?: boolean, commentEditing?: boolean) {
    if (commentEditing) {
      return ButtonTooltips.DISABLED_COMMENT_EDITING;
    }
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
    change?: ChangeInfo,
    account?: AccountInfo
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
      change?.labels === undefined ||
      account === undefined
    ) {
      return undefined;
    }
    if (commentEditing || disabled) {
      return true;
    }
    if (canBeStarted === true) {
      return false;
    }
    const existingVote = Object.values(change.labels).some(
      label => isDetailedLabelInfo(label) && getApprovalInfo(label, account)
    );
    const revotingOrNewVote = labelsChanged || existingVote;
    const hasDrafts = includeComments && draftCommentThreads.length;
    return (
      !hasDrafts && !text.length && !reviewersMutated && !revotingOrNewVote
    );
  }

  _computePatchSetWarning(patchNum?: PatchSetNum, labelsChanged?: boolean) {
    let str = `Patch ${patchNum} is not latest.`;
    if (labelsChanged) {
      str += ' Voting may have no effect.';
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
      this.restApiService,
      change._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER
    );
    provider.init();
    return provider;
  }

  _getCcSuggestionsProvider(change: ChangeInfo) {
    const provider = GrReviewerSuggestionsProvider.create(
      this.restApiService,
      change._number,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.CC
    );
    provider.init();
    return provider;
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
