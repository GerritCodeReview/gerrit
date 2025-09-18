/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../plugins/gr-endpoint-slot/gr-endpoint-slot';
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-formatted-text/gr-formatted-text';
import '../../shared/gr-account-list/gr-account-list';
import '../gr-label-scores/gr-label-scores';
import '../gr-thread-list/gr-thread-list';
import '../../../styles/shared-styles';
import {GrReviewerSuggestionsProvider} from '../../../services/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {getAppContext} from '../../../services/app-context';
import {
  ChangeStatus,
  DraftsAction,
  ReviewerState,
  SpecialFilePath,
} from '../../../constants/constants';

import {
  AccountInfoInput,
  AccountInput,
  AccountInputDetail,
  getUserId,
  GroupInfoInput,
  isAccountNewlyAdded,
  RawAccountInput,
  removeServiceUsers,
  toReviewInput,
} from '../../../utils/account-util';
import {TargetElement} from '../../../api/plugin';
import {isDefined, ParsedChangeInfo} from '../../../types/types';
import {GrAccountList} from '../../shared/gr-account-list/gr-account-list';
import {
  AccountId,
  AccountInfo,
  AttentionSetInput,
  ChangeInfo,
  ChangeViewChangeInfo,
  CommentThread,
  DraftInfo,
  GroupInfo,
  isAccount,
  isDetailedLabelInfo,
  isDraft,
  isReviewerAccountSuggestion,
  isReviewerGroupSuggestion,
  ReviewerInput,
  ReviewInput,
  ReviewResult,
  ServerInfo,
  SuggestedReviewerGroupInfo,
  Suggestion,
  UserId,
} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrLabelScores} from '../gr-label-scores/gr-label-scores';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
import {
  areSetsEqual,
  assertIsDefined,
  containsAll,
  difference,
  queryAndAssert,
} from '../../../utils/common-util';
import {
  getFirstComment,
  isPatchsetLevel,
  isUnresolved,
} from '../../../utils/comment-util';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {
  getApprovalInfo,
  getMaxAccounts,
  StandardLabels,
} from '../../../utils/label-util';
import {pluralize} from '../../../utils/string-util';
import {
  fire,
  fireAlert,
  fireError,
  fireIronAnnounce,
  fireNoBubble,
  fireReload,
  fireServerError,
} from '../../../utils/event-util';
import {ErrorCallback} from '../../../api/rest';
import {DelayedTask} from '../../../utils/async-util';
import {Interaction, Timing} from '../../../constants/reporting';
import {
  getMentionedReason,
  getReplyByReason,
} from '../../../utils/attention-set-util';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {LabelNameToValuesMap, PatchSetNumber} from '../../../api/rest-api';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {when} from 'lit/directives/when.js';
import {classMap} from 'lit/directives/class-map.js';
import {
  AddReviewerEvent,
  RemoveReviewerEvent,
  ValueChangedEvent,
} from '../../../types/events';
import {customElement, property, query, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {configModelToken} from '../../../models/config/config-model';
import {hasHumanReviewer} from '../../../utils/change-util';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {
  CommentEditingChangedDetail,
  GrComment,
} from '../../shared/gr-comment/gr-comment';
import {ShortcutController} from '../../lit/shortcut-controller';
import {Key, Modifier, whenVisible} from '../../../utils/dom-util';
import {GrThreadList} from '../gr-thread-list/gr-thread-list';
import {userModelToken} from '../../../models/user/user-model';
import {accountsModelToken} from '../../../models/accounts/accounts-model';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {modalStyles} from '../../../styles/gr-modal-styles';
import '@material/web/checkbox/checkbox';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import {materialStyles} from '../../../styles/gr-material-styles';
import {grAnnouncerRequestAvailability} from '../../lit-util';
import {GrReviewerUpdatesParser} from '../../shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {formStyles} from '../../../styles/form-styles';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {getDocUrl} from '../../../utils/url-util';
import {
  readJSONResponsePayload,
  ResponsePayload,
} from '../../shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

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
  START_REVIEW: 'Start Review',
  SEND: 'Send',
};

const ButtonTooltips = {
  SAVE: 'Send changes and comments as work in progress but do not start review',
  START_REVIEW: 'Mark as ready for review and send reply',
  SEND: 'Send reply',
  DISABLED_COMMENT_EDITING: 'Save draft comments to enable send',
};

const EMPTY_REPLY_MESSAGE = 'Cannot send an empty reply.';

@customElement('gr-reply-dialog')
export class GrReplyDialog extends LitElement {
  FocusTarget = FocusTarget;

  private readonly reporting = getAppContext().reportingService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  // TODO: update type to only ParsedChangeInfo
  @property({type: Object})
  change?: ParsedChangeInfo | ChangeInfo;

  @property({type: Boolean})
  canBeStarted = false;

  @property({type: Boolean, reflect: true})
  disabled = false;

  @state()
  draftCommentThreads: CommentThread[] = [];

  @property({type: Object})
  permittedLabels?: LabelNameToValuesMap;

  @query('#patchsetLevelComment') patchsetLevelGrComment?: GrComment;

  @query('#reviewers') reviewersList?: GrAccountList;

  @query('#ccs') ccsList?: GrAccountList;

  @query('#cancelButton') cancelButton?: GrButton;

  @query('#sendButton') sendButton?: GrButton;

  @query('#labelScores') labelScores?: GrLabelScores;

  @query('#reviewerConfirmationModal')
  reviewerConfirmationModal?: HTMLDialogElement;

  @state() latestPatchNum?: PatchSetNumber;

  @state() serverConfig?: ServerInfo;

  @state() private docsBaseUrl = '';

  @state()
  patchsetLevelDraftMessage = '';

  @state()
  filterReviewerSuggestion: (input: Suggestion) => boolean;

  @state()
  filterCCSuggestion: (input: Suggestion) => boolean;

  @state()
  knownLatestState?: LatestPatchState;

  @state()
  isChangeMerged = false;

  @state()
  underReview = true;

  @state()
  account?: AccountInfo;

  get ccs() {
    return [
      ...this._ccs,
      ...this.mentionedUsers.filter(v => !this.isAlreadyReviewerOrCC(v)),
    ];
  }

  /**
   * We pass the ccs object to AccountInput for modifying where it needs to
   * add a value to CC. The returned value contains both mentionedUsers and
   * normal ccs hence separate the two when setting ccs.
   */
  set ccs(ccs: AccountInput[]) {
    this._ccs = ccs.filter(
      cc =>
        !this.mentionedUsers.some(
          mentionedCC => getUserId(mentionedCC) === getUserId(cc)
        )
    );
    this.requestUpdate('ccs', ccs);
  }

  @state()
  _ccs: AccountInput[] = [];

  /**
   * Maintain a separate list of users added to cc due to being mentioned in
   * unresolved drafts.
   * If the draft is discarded or edited to remove the mention then we want to
   * remove the user from being added to CC.
   * Instead of figuring out when we should remove the mentioned user ie when
   * they get removed from the last comment, we recompute this property when
   * any of the draft comments change.
   * If we add the user to the existing ccs object then we cannot differentiate
   * if the user was added manually to CC or added due to being mentioned hence
   * we cannot reset the mentioned ccs when drafts change.
   */
  @state()
  mentionedUsers: AccountInput[] = [];

  @state()
  mentionedUsersInUnresolvedDrafts: AccountInfo[] = [];

  @state()
  attentionCcsCount = 0;

  @state()
  ccPendingConfirmation: SuggestedReviewerGroupInfo | null = null;

  @state()
  messagePlaceholder?: string;

  @state()
  uploader?: AccountInfo;

  @state()
  pendingConfirmationDetails: SuggestedReviewerGroupInfo | null = null;

  @state()
  includeComments = true;

  @state() reviewers: AccountInput[] = [];

  @state()
  reviewerPendingConfirmation: SuggestedReviewerGroupInfo | null = null;

  @state()
  savingComments = false;

  @state()
  reviewersMutated = false;

  /**
   * Signifies that the user has changed their vote on a label or (if they have
   * not yet voted on a label) if a selected vote is different from the default
   * vote.
   */
  @state()
  labelsChanged = false;

  @state()
  readonly saveTooltip: string = ButtonTooltips.SAVE;

  @state()
  pluginMessage = '';

  @state()
  commentEditing = false;

  @state()
  attentionExpanded = false;

  @state()
  currentAttentionSet: Set<UserId> = new Set();

  @state()
  newAttentionSet: Set<UserId> = new Set();

  @state()
  manuallyAddedAttentionSet: Set<UserId> = new Set();

  @state()
  manuallyDeletedAttentionSet: Set<UserId> = new Set();

  @state()
  patchsetLevelDraftIsResolved = true;

  @state()
  patchsetLevelComment?: DraftInfo;

  @state()
  isOwner = false;

  @state()
  private draggedAccount: AccountInfo | null = null;

  @state()
  private draggedFrom: GrAccountList | null = null;

  private readonly restApiService: RestApiService =
    getAppContext().restApiService;

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getAccountsModel = resolve(this, accountsModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getNavigation = resolve(this, navigationToken);

  storeTask?: DelayedTask;

  private isLoggedIn = false;

  private readonly shortcuts = new ShortcutController(this);

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      modalStyles,
      materialStyles,
      css`
        gr-account-list.drag-over {
          background-color: var(--table-header-background-color);
          outline: 2px dashed var(--border-color);
        }
        :host {
          background-color: var(--dialog-background-color);
          display: block;
          max-height: 90vh;
          --label-score-padding-left: var(--spacing-xl);
        }
        :host([disabled]) {
          pointer-events: none;
        }
        :host([disabled]) .container {
          opacity: 0.5;
        }
        section {
          border-top: 1px solid var(--border-color);
          flex-shrink: 0;
          padding: var(--spacing-m) var(--spacing-xl);
          width: 100%;
        }
        section.labelsContainer {
          /* We want the :hover highlight to extend to the border of the dialog. */
          padding: var(--spacing-m) 0;
        }
        .stickyBottom {
          background-color: var(--dialog-background-color);
          box-shadow: 0px 0px 8px 0px rgba(60, 64, 67, 0.15);
          margin-top: var(--spacing-s);
          bottom: 0;
          position: sticky;
          /* @see Issue 8602 */
          z-index: 1;
        }
        .stickyBottom.newReplyDialog {
          margin-top: unset;
        }
        .actions {
          display: flex;
          justify-content: space-between;
        }
        .actions .right gr-button {
          margin-left: var(--spacing-l);
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
        }
        .peopleListLabel {
          color: var(--deemphasized-text-color);
          margin-top: var(--spacing-xs);
          min-width: 6em;
          padding-right: var(--spacing-m);
        }
        gr-account-list {
          display: flex;
          flex-wrap: wrap;
          flex: 1;
        }
        #reviewerConfirmationModal {
          padding: var(--spacing-l);
          text-align: center;
        }
        .reviewerConfirmationButtons {
          margin-top: var(--spacing-l);
        }
        .groupName {
          font-weight: var(--font-weight-medium);
        }
        .groupSize {
          font-style: italic;
        }
        .textareaContainer {
          min-height: 12em;
          position: relative;
        }
        .newReplyDialog.textareaContainer {
          min-height: unset;
        }
        textareaContainer,
        gr-endpoint-decorator[name='reply-text'] {
          display: flex;
          width: 100%;
        }
        gr-endpoint-decorator[name='reply-text'] {
          flex-direction: column;
        }
        #changeIsMergedLabel #checkingStatusLabel,
        #notLatestLabel {
          margin-left: var(--spacing-l);
        }
        #checkingStatusLabel {
          color: var(--deemphasized-text-color);
          font-style: italic;
        }
        #changeIsMergedLabel,
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
          margin-left: var(--spacing-l);
          margin-bottom: var(--spacing-m);
        }
        #pluginMessage:empty {
          display: none;
        }
        .edit-attention-button {
          vertical-align: top;
          --gr-button-padding: 0px 4px;
        }
        .edit-attention-button gr-icon {
          color: inherit;
        }
        .attentionSummary .edit-attention-button gr-icon {
          /* The line-height:26px hack (see below) requires us to do this.
           Normally the gr-icon would account for a proper positioning
           within the standard line-height:20px context. */
          top: 5px;
        }
        .attention a,
        .attention-detail a {
          text-decoration: none;
        }
        .attentionSummary {
          display: flex;
          justify-content: space-between;
        }
        .attentionSummary {
          /* The account label for selection is misbehaving currently: It consumes
          26px height instead of 20px, which is the default line-height and thus
          the max that can be nicely fit into an inline layout flow. We
          acknowledge that using a fixed 26px value here is a hack and not a
          great solution. */
          line-height: 26px;
        }
        .attentionSummary gr-account-label,
        .attention-detail gr-account-label {
          --account-max-length: 120px;
          display: inline-block;
          padding: var(--spacing-xs) var(--spacing-m);
          user-select: none;
          --label-border-radius: 8px;
        }
        .attentionSummary gr-account-label {
          margin: 0 var(--spacing-xs);
          line-height: var(--line-height-normal);
          vertical-align: top;
        }
        .attention-detail .peopleListValues {
          line-height: calc(var(--line-height-normal) + 10px);
        }
        .attention-detail gr-account-label {
          line-height: var(--line-height-normal);
        }
        .attentionSummary gr-account-label:focus,
        .attention-detail gr-account-label:focus {
          outline: none;
        }
        .attentionSummary gr-account-label:hover,
        .attention-detail gr-account-label:hover {
          box-shadow: var(--elevation-level-1);
          cursor: pointer;
        }
        .attention-detail .attentionDetailsTitle {
          display: flex;
          justify-content: space-between;
        }
        .attention-detail .selectUsers {
          color: var(--deemphasized-text-color);
          margin-bottom: var(--spacing-m);
        }
        .attentionTip {
          padding: var(--spacing-m);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          margin-top: var(--spacing-m);
          background-color: var(--line-item-highlight-color);
        }
        .attentionTip div gr-icon {
          margin-right: var(--spacing-s);
        }
        .patchsetLevelContainer {
          width: calc(min(80ch, 100%));
          border-radius: var(--border-radius);
          box-shadow: var(--elevation-level-2);
        }
        .patchsetLevelContainer.resolved {
          background-color: var(--comment-background-color);
        }
        .patchsetLevelContainer.unresolved {
          background-color: var(--unresolved-comment-background-color);
        }
        .privateVisiblityInfo {
          display: flex;
          justify-content: center;
          background-color: var(--info-background);
          padding: var(--spacing-s) 0;
        }
        .privateVisiblityInfo gr-icon {
          margin-right: var(--spacing-m);
          color: var(--info-foreground);
        }
        .rightActions {
          display: flex;
          align-items: center;
          gap: var(--spacing-s);
        }
        .rightActions a {
          display: flex;
          align-items: center;
        }
        .rightActions gr-icon {
          margin: 0;
        }
        md-checkbox {
          --md-checkbox-container-size: 15px;
          --md-checkbox-icon-size: 15px;
        }
      `,
    ];
  }

  constructor() {
    super();
    this.filterReviewerSuggestion =
      this.filterReviewerSuggestionGenerator(false);
    this.filterCCSuggestion = this.filterReviewerSuggestionGenerator(true);

    this.shortcuts.addLocal({key: Key.ESC}, () => this.cancel());
    this.shortcuts.addLocal(
      {key: Key.ENTER, modifiers: [Modifier.CTRL_KEY]},
      () => this.submit()
    );
    this.shortcuts.addLocal(
      {key: Key.ENTER, modifiers: [Modifier.META_KEY]},
      () => this.submit()
    );

    subscribe(
      this,
      () => this.getUserModel().loggedIn$,
      isLoggedIn => (this.isLoggedIn = isLoggedIn)
    );
    subscribe(
      this,
      () => this.getUserModel().account$,
      account => {
        this.account = account;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.change = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().isOwner$,
      x => (this.isOwner = x)
    );
    subscribe(
      this,
      () => this.getCommentsModel().mentionedUsersInDrafts$,
      x => {
        this.mentionedUsers = x;
        this.reviewersMutated =
          this.reviewersMutated || this.mentionedUsers.length > 0;
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().mentionedUsersInUnresolvedDrafts$,
      x => {
        this.mentionedUsersInUnresolvedDrafts = x.filter(
          v => !this.isAlreadyReviewerOrCC(v)
        );
      }
    );
    subscribe(
      this,
      () => this.getCommentsModel().patchsetLevelDrafts$,
      x => (this.patchsetLevelComment = x[0])
    );
    subscribe(
      this,
      () => this.getCommentsModel().draftThreadsSaved$,
      threads =>
        (this.draftCommentThreads = threads.filter(
          t => !(isDraft(getFirstComment(t)) && isPatchsetLevel(t))
        ))
    );
  }

  override connectedCallback() {
    super.connectedCallback();
    grAnnouncerRequestAvailability();

    this.getPluginLoader().jsApiService.addElement(
      TargetElement.REPLY_DIALOG,
      this
    );

    this.addEventListener(
      'comment-editing-changed',
      (e: CustomEvent<CommentEditingChangedDetail>) => {
        // Patchset level comment is always in editing mode which means it would
        // set commentEditing = true and the send button would be permanently
        // disabled.
        if (e.detail.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) return;
        const commentList = queryAndAssert<GrThreadList>(this, '#commentList');
        // It can be one or more comments were in editing mode. Wwitching one
        // thread in editing, we need to check if there are still other threads
        // in editing.
        this.commentEditing = Array.from(commentList.threadElements ?? []).some(
          thread => thread.editing
        );
      }
    );

    // Plugins on reply-reviewers endpoint can take advantage of these
    // events to add / remove reviewers

    this.addEventListener('add-reviewer', (e: AddReviewerEvent) => {
      const reviewer = e.detail.reviewer;
      // Only support account type, see more from:
      // elements/shared/gr-account-list/gr-account-list.js#addAccountItem
      this.reviewersList?.addAccountItem({
        account: reviewer,
        count: 1,
      });
    });

    this.addEventListener('remove-reviewer', (e: RemoveReviewerEvent) => {
      const reviewer = e.detail.reviewer;
      this.reviewersList?.removeAccount(reviewer);
    });
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('ccPendingConfirmation')) {
      this.pendingConfirmationUpdated(this.ccPendingConfirmation);
    }
    if (changedProperties.has('reviewerPendingConfirmation')) {
      this.pendingConfirmationUpdated(this.reviewerPendingConfirmation);
    }
    if (changedProperties.has('change')) {
      this.computeUploader();
      this.rebuildReviewerArrays();
    }
    if (changedProperties.has('canBeStarted')) {
      this.computeMessagePlaceholder();
    }
    if (changedProperties.has('attentionExpanded')) {
      this.onAttentionExpandedChange();
    }
    if (
      changedProperties.has('account') ||
      changedProperties.has('reviewers') ||
      changedProperties.has('ccs') ||
      changedProperties.has('change') ||
      changedProperties.has('draftCommentThreads') ||
      changedProperties.has('mentionedUsersInUnresolvedDrafts') ||
      changedProperties.has('includeComments') ||
      changedProperties.has('labelsChanged') ||
      changedProperties.has('patchsetLevelDraftMessage') ||
      changedProperties.has('mentionedCCs')
    ) {
      this.computeNewAttention();
    }
  }

  override disconnectedCallback() {
    this.storeTask?.flush();
    super.disconnectedCallback();
  }

  override render() {
    if (!this.change) return;
    return html`
      <div tabindex="-1">
        <section class="peopleContainer">
          <gr-endpoint-decorator name="reply-reviewers">
            <gr-endpoint-param
              name="change"
              .value=${this.change}
            ></gr-endpoint-param>
            <gr-endpoint-param name="reviewers" .value=${[...this.reviewers]}>
            </gr-endpoint-param>
            ${this.renderReviewerList()}
            <gr-endpoint-slot name="below"></gr-endpoint-slot>
          </gr-endpoint-decorator>
          ${this.renderCCList()} ${this.renderReviewConfirmation()}
          ${this.renderPrivateVisiblityInfo()}
        </section>
        <section class="labelsContainer">${this.renderLabels()}</section>
        <section class="newReplyDialog textareaContainer">
          ${this.renderReplyText()}
        </section>
        ${this.renderDraftsSection()}
        <div class="stickyBottom newReplyDialog">
          <gr-endpoint-decorator name="reply-bottom">
            <gr-endpoint-param
              name="change"
              .value=${this.change}
            ></gr-endpoint-param>
            ${when(
              this.attentionExpanded,
              () => this.renderAttentionDetailsSection(),
              () => this.renderAttentionSummarySection()
            )}
            <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
            ${this.renderActionsSection()}
          </gr-endpoint-decorator>
        </div>
      </div>
    `;
  }

  private renderReviewerList() {
    return html`
      <div class="peopleList">
        <div class="peopleListLabel">Reviewers</div>
        <gr-account-list
          id="reviewers"
          .accounts=${[...this.reviewers]}
          .change=${this.change}
          .reviewerState=${ReviewerState.REVIEWER}
          .chipDraggable=${true}
          @account-added=${this.handleAccountAdded}
          @accounts-changed=${this.handleReviewersChanged}
          @account-drag-start=${this.handleAccountDragStart}
          @dragover=${this.handleDragOver}
          @dragleave=${this.handleDragLeave}
          @drop=${this.handleDrop}
          .removableValues=${this.change?.removable_reviewers}
          .filter=${this.filterReviewerSuggestion}
          .pendingConfirmation=${this.reviewerPendingConfirmation}
          @pending-confirmation-changed=${this
            .handleReviewersConfirmationChanged}
          .placeholder=${'Add reviewer...'}
          @account-text-changed=${this.handleAccountTextEntry}
          .suggestionsProvider=${this.getReviewerSuggestionsProvider(
            this.change
          )}
        >
        </gr-account-list>
        <gr-endpoint-slot name="right"></gr-endpoint-slot>
      </div>
    `;
  }

  private renderCCList() {
    return html`
      <div class="peopleList">
        <div class="peopleListLabel">CC</div>
        <gr-account-list
          id="ccs"
          .accounts=${[...this.ccs]}
          .change=${this.change}
          .reviewerState=${ReviewerState.CC}
          .chipDraggable=${true}
          @account-added=${this.handleAccountAdded}
          @accounts-changed=${this.handleCcsChanged}
          @account-drag-start=${this.handleAccountDragStart}
          @dragover=${this.handleDragOver}
          @dragleave=${this.handleDragLeave}
          @drop=${this.handleDrop}
          .filter=${this.filterCCSuggestion}
          .pendingConfirmation=${this.ccPendingConfirmation}
          @pending-confirmation-changed=${this.handleCcsConfirmationChanged}
          allow-any-input
          .placeholder=${'Add CC...'}
          @account-text-changed=${this.handleAccountTextEntry}
          .suggestionsProvider=${this.getCcSuggestionsProvider(this.change)}
        >
        </gr-account-list>
      </div>
    `;
  }

  private renderReviewConfirmation() {
    return html`
      <dialog
        tabindex="-1"
        id="reviewerConfirmationModal"
        @close=${this.cancelPendingReviewer}
      >
        <div class="reviewerConfirmation">
          Group
          <span class="groupName">
            ${this.pendingConfirmationDetails?.group.name}
          </span>
          has
          <span class="groupSize">
            ${this.pendingConfirmationDetails?.count}
          </span>
          members.
          <br />
          Are you sure you want to add them all?
        </div>
        <div class="reviewerConfirmationButtons">
          <gr-button @click=${this.confirmPendingReviewer}>Yes</gr-button>
          <gr-button @click=${this.cancelPendingReviewer}>No</gr-button>
        </div>
      </dialog>
    `;
  }

  private renderPrivateVisiblityInfo() {
    const addedAccounts = [
      ...(this.reviewersList?.additions() ?? []),
      ...(this.ccsList?.additions() ?? []),
    ];
    if (!this.change?.is_private || !addedAccounts.length) return nothing;
    return html`
      <div class="privateVisiblityInfo">
        <gr-icon icon="info"></gr-icon>
        <div>
          Adding a reviewer/CC will make this private change visible to them
        </div>
      </div>
    `;
  }

  private renderLabels() {
    if (!this.change || !this.account || !this.permittedLabels) return;
    return html`
      <gr-endpoint-decorator name="reply-label-scores">
        <gr-label-scores
          id="labelScores"
          .account=${this.account}
          .change=${this.change}
          @labels-changed=${this._handleLabelsChanged}
          .permittedLabels=${this.permittedLabels}
        ></gr-label-scores>
        <gr-endpoint-param
          name="change"
          .value=${this.change}
        ></gr-endpoint-param>
      </gr-endpoint-decorator>
      <div id="pluginMessage">${this.pluginMessage}</div>
    `;
  }

  private renderPatchsetLevelComment() {
    if (!this.patchsetLevelComment) return nothing;
    return html`
      <gr-comment
        id="patchsetLevelComment"
        .comment=${this.patchsetLevelComment}
        .comments=${[this.patchsetLevelComment]}
        @comment-unresolved-changed=${(e: ValueChangedEvent<boolean>) => {
          this.patchsetLevelDraftIsResolved = !e.detail.value;
        }}
        @comment-text-changed=${(e: ValueChangedEvent<string>) => {
          this.patchsetLevelDraftMessage = e.detail.value;
          // See `addReplyTextChangedCallback` in `ChangeReplyPluginApi`.
          fire(e.currentTarget as HTMLElement, 'value-changed', e.detail);
        }}
        .messagePlaceholder=${this.messagePlaceholder ?? ''}
        hide-header
        permanent-editing-mode
      ></gr-comment>
    `;
  }

  private renderReplyText() {
    if (!this.change) return;
    return html`
      <div
        class=${classMap({
          patchsetLevelContainer: true,
          [this.getUnresolvedPatchsetLevelClass(
            this.patchsetLevelDraftIsResolved
          )]: true,
        })}
      >
        <gr-endpoint-decorator name="reply-text">
          ${this.renderPatchsetLevelComment()}
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>
    `;
  }

  private renderDraftsSection() {
    const threads = this.draftCommentThreads;
    if (!threads || threads.length === 0) return;
    return html`
      <section class="draftsContainer">
        <div class="includeComments">
          <md-checkbox
            id="includeComments"
            @change=${this.handleIncludeCommentsChanged}
            ?checked=${this.includeComments}
          ></md-checkbox>
          Publish ${this.computeDraftsTitle(threads)}
        </div>
        ${when(
          this.includeComments,
          () => html`
            <gr-thread-list id="commentList" .threads=${threads} hide-dropdown>
            </gr-thread-list>
          `
        )}
        <span
          id="savingLabel"
          class=${this.computeSavingLabelClass(this.savingComments)}
        >
          Saving comments...
        </span>
      </section>
    `;
  }

  private renderAttentionSummarySection() {
    return html`
      <section class="attention">
        <div class="attentionSummary">
          <div>
            ${when(
              this.computeShowNoAttentionUpdate(),
              () => html` <span>${this.computeDoNotUpdateMessage()}</span> `
            )}
            ${when(
              !this.computeShowNoAttentionUpdate(),
              () => html`
                <span>Bring to attention of</span>
                ${this.computeNewAttentionAccounts().map(
                  account => html`
                    <gr-account-label
                      .account=${account}
                      .forceAttention=${this.computeHasNewAttention(account)}
                      .selected=${this.computeHasNewAttention(account)}
                      .hideHovercard=${true}
                      .selectionChipStyle=${true}
                      @click=${this.handleAttentionClick}
                    ></gr-account-label>
                  `
                )}
              `
            )}
          </div>
          <div class="rightActions">
            ${this.renderModifyAttentionSetButton()}
            <a
              href=${getDocUrl(this.docsBaseUrl, 'user-attention-set.html')}
              target="_blank"
              rel="noopener noreferrer"
            >
              <gr-icon icon="help" title="read documentation"></gr-icon>
            </a>
          </div>
        </div>
      </section>
    `;
  }

  private renderModifyAttentionSetButton() {
    return html` <gr-button
      class="edit-attention-button"
      @click=${this.toggleAttentionModify}
      link
      position-below
      data-label="Edit"
      data-action-type="change"
      data-action-key="edit"
      role="button"
      tabindex="0"
    >
      <div>
        <gr-icon
          icon=${this.attentionExpanded ? 'expand_circle_up' : 'edit'}
          filled
          small
        ></gr-icon>
        <span>${this.attentionExpanded ? 'Collapse' : 'Modify'}</span>
      </div>
    </gr-button>`;
  }

  private renderAttentionDetailsSection() {
    return html`
      <section class="attention-detail">
        <div class="attentionDetailsTitle">
          <div>
            <span>Modify attention to</span>
          </div>
          <div class="rightActions">
            ${this.renderModifyAttentionSetButton()}
            <a
              href=${getDocUrl(this.docsBaseUrl, 'user-attention-set.html')}
              target="_blank"
              rel="noopener noreferrer"
            >
              <gr-icon icon="help" title="read documentation"></gr-icon>
            </a>
          </div>
        </div>
        <div class="selectUsers">
          <span
            >Select chips to set who will be in the attention set after sending
            this reply</span
          >
        </div>
        <div class="peopleList">
          <div class="peopleListLabel">Owner</div>
          <div class="peopleListValues">
            <gr-account-label
              .account=${this.change?.owner}
              ?forceAttention=${this.computeHasNewAttention(this.change?.owner)}
              .selected=${this.computeHasNewAttention(this.change?.owner)}
              .hideHovercard=${true}
              .selectionChipStyle=${true}
              @click=${this.handleAttentionClick}
            >
            </gr-account-label>
          </div>
        </div>
        ${when(
          this.uploader,
          () => html`
            <div class="peopleList">
              <div class="peopleListLabel">Uploader</div>
              <div class="peopleListValues">
                <gr-account-label
                  .account=${this.uploader}
                  ?forceAttention=${this.computeHasNewAttention(this.uploader)}
                  .selected=${this.computeHasNewAttention(this.uploader)}
                  .hideHovercard=${true}
                  .selectionChipStyle=${true}
                  @click=${this.handleAttentionClick}
                >
                </gr-account-label>
              </div>
            </div>
          `
        )}
        <div class="peopleList">
          <div class="peopleListLabel">Reviewers</div>
          <div class="peopleListValues">
            ${removeServiceUsers(this.reviewers).map(
              account => html`
                <gr-account-label
                  .account=${account}
                  ?forceAttention=${this.computeHasNewAttention(account)}
                  .selected=${this.computeHasNewAttention(account)}
                  .hideHovercard=${true}
                  .selectionChipStyle=${true}
                  @click=${this.handleAttentionClick}
                >
                </gr-account-label>
              `
            )}
          </div>
        </div>

        ${when(
          this.attentionCcsCount,
          () => html`
            <div class="peopleList">
              <div class="peopleListLabel">CC</div>
              <div class="peopleListValues">
                ${removeServiceUsers(this.ccs).map(
                  account => html`
                    <gr-account-label
                      .account=${account}
                      ?forceAttention=${this.computeHasNewAttention(account)}
                      .selected=${this.computeHasNewAttention(account)}
                      .hideHovercard=${true}
                      .selectionChipStyle=${true}
                      @click=${this.handleAttentionClick}
                    >
                    </gr-account-label>
                  `
                )}
              </div>
            </div>
          `
        )}
        ${when(
          this.computeShowAttentionTip(3),
          () => html`
            <div class="attentionTip">
              <gr-icon icon="lightbulb"></gr-icon>
              Please be mindful of requiring attention from too many users.
            </div>
          `
        )}
      </section>
    `;
  }

  private renderActionsSection() {
    return html`
      <section class="actions">
        <div class="left">
          ${when(
            this.knownLatestState === LatestPatchState.CHECKING,
            () => html`
              <span id="checkingStatusLabel">
                Checking whether patch ${this.latestPatchNum} is latest...
              </span>
            `
          )}
          ${when(
            this.knownLatestState !== LatestPatchState.CHECKING &&
              this.isChangeMerged,
            () => html`
              <span id="changeIsMergedLabel">
                ${this.computeChangeMergedWarning()}
              </span>
            `
          )}
          ${when(
            !this.isChangeMerged &&
              this.knownLatestState === LatestPatchState.NOT_LATEST,
            () => html`
              <span id="notLatestLabel">
                ${this.computePatchSetWarning()}
                <gr-button link @click=${this._reload}>Reload</gr-button>
              </span>
            `
          )}
        </div>
        <div class="right">
          <gr-button
            link
            id="cancelButton"
            class="action cancel"
            @click=${this.cancelTapHandler}
            >Cancel</gr-button
          >
          ${when(
            this.canBeStarted,
            () => html`
              <!-- Use 'Send' here as the change may only about reviewers / ccs
            and when this button is visible, the next button will always
            be 'Start review' -->
              <gr-tooltip-content has-tooltip title=${this.saveTooltip}>
                <gr-button
                  link
                  ?disabled=${this.knownLatestState ===
                  LatestPatchState.NOT_LATEST}
                  class="action save"
                  @click=${this.saveClickHandler}
                  >Send As WIP</gr-button
                >
              </gr-tooltip-content>
            `
          )}
          <gr-tooltip-content
            has-tooltip
            title=${this.computeSendButtonTooltip(
              this.canBeStarted,
              this.commentEditing
            )}
          >
            <gr-button
              id="sendButton"
              primary
              ?disabled=${!!this.isSendDisabled()}
              class="action send"
              @click=${this.sendClickHandler}
              >${this.canBeStarted
                ? ButtonLabels.SEND + ' and ' + ButtonLabels.START_REVIEW
                : ButtonLabels.SEND}
            </gr-button>
          </gr-tooltip-content>
        </div>
      </section>
    `;
  }

  /**
   * Note that this method is not actually *opening* the dialog. Opening and
   * showing the dialog is dealt with by the overlay. This method is used by the
   * change view for initializing the dialog after opening the overlay. Maybe it
   * should be called `onOpened()` or `initialize()`?
   */
  open(focusTarget?: FocusTarget) {
    assertIsDefined(this.change, 'change');
    this.knownLatestState = LatestPatchState.CHECKING;
    this.getChangeModel()
      .fetchChangeUpdates(this.change)
      .then(result => {
        this.knownLatestState = result.isLatest
          ? LatestPatchState.LATEST
          : LatestPatchState.NOT_LATEST;
        this.isChangeMerged = result.newStatus === ChangeStatus.MERGED;
      });

    this.focusOn(focusTarget);
    if (this.restApiService.hasPendingDiffDrafts()) {
      this.savingComments = true;
      this.restApiService.awaitPendingDiffDrafts().then(() => {
        fire(this, 'comment-refresh', {});
        this.savingComments = false;
      });
    }
  }

  hasDrafts() {
    return (
      this.patchsetLevelDraftMessage.length > 0 ||
      this.draftCommentThreads.length > 0
    );
  }

  override focus() {
    this.focusOn(FocusTarget.ANY);
  }

  private handleIncludeCommentsChanged(e: Event) {
    if (!(e.target instanceof MdCheckbox)) return;
    this.includeComments = e.target.checked;
  }

  setLabelValue(label: string, value: string): void {
    const selectorEl =
      this.getLabelScores().shadowRoot?.querySelector<GrLabelScoreRow>(
        `gr-label-score-row[name="${label}"]`
      );
    selectorEl?.setSelectedValue(value);
  }

  getLabelValue(label: string) {
    const selectorEl =
      this.getLabelScores().shadowRoot?.querySelector<GrLabelScoreRow>(
        `gr-label-score-row[name="${label}"]`
      );
    return selectorEl?.selectedValue;
  }

  // TODO: Combine logic into handleReviewersChanged & handleCCsChanged and
  // remove account-added event from GrAccountList.
  handleAccountAdded(e: CustomEvent<AccountInputDetail>) {
    const account = e.detail.account;
    const key = getUserId(account);
    const reviewerType =
      (e.target as GrAccountList).getAttribute('id') === 'ccs'
        ? ReviewerType.CC
        : ReviewerType.REVIEWER;
    const isReviewer = ReviewerType.REVIEWER === reviewerType;
    const reviewerList = isReviewer ? this.ccsList : this.reviewersList;
    // Remove any accounts that already exist as a CC for reviewer
    // or vice versa.
    if (reviewerList?.removeAccount(account)) {
      const moveFrom = isReviewer ? 'CC' : 'reviewer';
      const moveTo = isReviewer ? 'reviewer' : 'CC';
      const id = account.name || key;
      const message = `${id} moved from ${moveFrom} to ${moveTo}.`;
      fireAlert(this, message);
    }
  }

  private handleAccountDragStart(e: CustomEvent<{account: AccountInfo}>) {
    const account = e.detail.account;
    if (!account) return;

    this.draggedAccount = account;
    this.draggedFrom = e.currentTarget as GrAccountList;
  }

  private handleDragOver(e: DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'move';
    }
    const list = e.currentTarget as GrAccountList;
    if (this.draggedFrom && this.draggedFrom !== list) {
      list.classList.add('drag-over');
    }
  }

  private handleDragLeave(e: DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    const list = e.currentTarget as GrAccountList;
    list.classList.remove('drag-over');
  }

  private handleDrop(e: DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    const dropTarget = e.currentTarget as GrAccountList;
    dropTarget.classList.remove('drag-over');

    if (!this.draggedAccount || !this.draggedFrom) {
      return;
    }
    if (this.draggedFrom === dropTarget) {
      this.draggedAccount = null;
      this.draggedFrom = null;
      return;
    }

    const accountToMove = this.draggedAccount;
    const targetIsCcs = dropTarget.id === 'ccs';

    if (targetIsCcs) {
      this.reviewers = this.reviewers.filter(
        r => getUserId(r) !== getUserId(accountToMove)
      );
      if (!this.ccs.find(cc => getUserId(cc) === getUserId(accountToMove))) {
        this.ccs = [...this.ccs, accountToMove];
      }
    } else {
      this.ccs = this.ccs.filter(
        cc => getUserId(cc) !== getUserId(accountToMove)
      );
      if (
        !this.reviewers.find(r => getUserId(r) === getUserId(accountToMove))
      ) {
        this.reviewers = [...this.reviewers, accountToMove];
      }
    }

    const moveFrom = targetIsCcs ? 'reviewers' : 'CCs';
    const moveTo = targetIsCcs ? 'CCs' : 'reviewers';
    const id = accountToMove.name || getUserId(accountToMove);
    const message = `${id} moved from ${moveFrom} to ${moveTo}.`;
    fireAlert(this, message);

    this.draggedAccount = null;
    this.draggedFrom = null;
  }

  getUnresolvedPatchsetLevelClass(patchsetLevelDraftIsResolved: boolean) {
    return patchsetLevelDraftIsResolved ? 'resolved' : 'unresolved';
  }

  computeReviewers() {
    const reviewers: ReviewerInput[] = [];
    const reviewerAdditions = this.reviewersList?.additions() ?? [];
    reviewers.push(
      ...reviewerAdditions.map(v => toReviewInput(v, ReviewerState.REVIEWER))
    );

    const ccAdditions = this.ccsList?.additions() ?? [];
    reviewers.push(...ccAdditions.map(v => toReviewInput(v, ReviewerState.CC)));

    // ignore removal from reviewer request if being added as CC
    let removals = difference(
      this.reviewersList?.removals() ?? [],
      ccAdditions,
      (a, b) => getUserId(a) === getUserId(b)
    ).map(v => toReviewInput(v, ReviewerState.REMOVED));
    reviewers.push(...removals);

    // ignore removal from CC request if being added as reviewer
    removals = difference(
      this.ccsList?.removals() ?? [],
      reviewerAdditions,
      (a, b) => getUserId(a) === getUserId(b)
    ).map(v => toReviewInput(v, ReviewerState.REMOVED));
    reviewers.push(...removals);

    // The owner is returned as a reviewer in the ChangeInfo object in some
    // cases, and trying to remove the owner as a reviewer returns in a
    // 500 server error.
    return reviewers.filter(
      reviewerInput =>
        !(
          this.change?.owner._account_id === reviewerInput.reviewer &&
          reviewerInput.state === ReviewerState.REMOVED
        )
    );
  }

  // visible for testing
  async send(includeComments: boolean, startReview: boolean) {
    // ChangeModel will be updated once the reply returns at which point the
    // timer will be ended.
    this.reporting.time(Timing.SEND_REPLY);
    const labels = this.getLabelScores().getLabelValues();
    if (labels[StandardLabels.CODE_REVIEW] === 2) {
      this.reporting.reportInteraction(Interaction.CODE_REVIEW_APPROVAL);
    }

    const reviewInput: ReviewInput = {
      drafts: includeComments
        ? DraftsAction.PUBLISH_ALL_REVISIONS
        : DraftsAction.KEEP,
      labels,
    };

    if (startReview) {
      reviewInput.ready = true;
    } else if (this.change?.work_in_progress) {
      const addedAccounts = [
        ...(this.reviewersList?.additions() ?? []),
        ...(this.ccsList?.additions() ?? []),
      ];
      if (addedAccounts.length > 0) {
        fireAlert(this, 'Reviewers are not notified for WIP changes');
      }
    }

    this.disabled = true;

    const reason = getReplyByReason(this.account, this.serverConfig);

    reviewInput.ignore_automatic_attention_set_rules = true;
    reviewInput.add_to_attention_set = [];
    const allAccounts = this.allAccounts();

    const newAttentionSetAdditions: AccountInfo[] = Array.from(
      this.newAttentionSet
    )
      .filter(user => !this.currentAttentionSet.has(user))
      .map(user => allAccounts.find(a => getUserId(a) === user))
      .filter(isDefined);

    const newAttentionSetUsers = (
      await Promise.all(
        newAttentionSetAdditions.map(a =>
          this.getAccountsModel().fillDetails(a)
        )
      )
    ).filter(isDefined);

    for (const user of newAttentionSetUsers) {
      const reason =
        getMentionedReason(
          this.draftCommentThreads,
          this.account,
          user,
          this.serverConfig
        ) ?? '';
      reviewInput.add_to_attention_set.push({user: getUserId(user), reason});
    }
    reviewInput.remove_from_attention_set = [];
    for (const user of this.currentAttentionSet) {
      if (!this.newAttentionSet.has(user)) {
        reviewInput.remove_from_attention_set.push({user, reason});
      }
    }
    this.reportAttentionSetChanges(
      this.attentionExpanded,
      reviewInput.add_to_attention_set,
      reviewInput.remove_from_attention_set
    );

    if (this.patchsetLevelGrComment) {
      this.patchsetLevelGrComment.disableAutoSaving = true;
      await this.restApiService.awaitPendingDiffDrafts();
      const comment =
        await this.patchsetLevelGrComment.convertToCommentInputAndOrDiscard();
      if (comment && comment.path && comment.message) {
        reviewInput.comments ??= {};
        reviewInput.comments[comment.path] ??= [];
        reviewInput.comments[comment.path].push(comment);
      }
    }

    assertIsDefined(this.change, 'change');
    reviewInput.reviewers = this.computeReviewers();
    this.reportStartReview(reviewInput);

    const errFn = (r?: Response | null) => this.handle400Error(r);
    if (
      !(await this.getPluginLoader().jsApiService.handleBeforeReplySent(
        this.change,
        reviewInput
      ))
    )
      return;
    this.getNavigation().blockNavigation('sending review');
    return this.saveReview(reviewInput, errFn)
      .then(result => {
        // change-info is not set only if request resulted in error.
        if (!result?.change_info) {
          return;
        }

        // saveReview response don't contain revision information, if the
        // newer patchset was uploaded in the meantime, we should reload.
        const reloadRequired =
          result.change_info.current_revision_number !==
          this.change?.current_revision_number;
        // Update the state right away to update comments, even if the full
        // reload is scheduled right after.
        const updatedChange = {
          ...result.change_info,
          revisions: this.change?.revisions,
          current_revision: this.change?.current_revision,
          current_revision_number: this.change?.current_revision_number,
        };
        this.getChangeModel().updateStateChange(
          GrReviewerUpdatesParser.parse(updatedChange as ChangeViewChangeInfo)
        );
        if (reloadRequired) {
          fireReload(this);
        }

        this.patchsetLevelDraftMessage = '';
        this.includeComments = true;
        fireNoBubble(this, 'send', {});
        fireIronAnnounce(this, 'Reply sent');
        this.getPluginLoader().jsApiService.handleReplySent();
      })
      .finally(() => {
        this.getNavigation().releaseNavigation('sending review');
        this.disabled = false;
        if (this.patchsetLevelGrComment) {
          this.patchsetLevelGrComment.disableAutoSaving = false;
        }
        // The request finished and reloads if necessary are asynchronously
        // scheduled.
        this.reporting.timeEnd(Timing.SEND_REPLY);
      });
  }

  private reportStartReview(reviewInput: ReviewInput) {
    const changeHasReviewers =
      (this.change?.reviewers.REVIEWER ?? []).length > 0;
    const newReviewersAdded =
      (this.reviewersList?.additions() ?? []).length > 0;

    // A review starts if either a WIP change is set to active with reviewers ...
    const setActiveWithReviewers =
      this.change?.work_in_progress &&
      reviewInput.ready &&
      // Setting a change active and *removing* all reviewers at the same time
      // is an obscure corner case that we don't care about. :-)
      (changeHasReviewers || newReviewersAdded);
    // ... or if reviewers are added to an already active change that has no reviewers yet.
    const isActiveAddReviewers =
      !this.change?.work_in_progress &&
      !reviewInput.work_in_progress &&
      !changeHasReviewers &&
      newReviewersAdded;

    if (setActiveWithReviewers || isActiveAddReviewers) {
      this.reporting.reportInteraction(Interaction.START_REVIEW);
    }
  }

  focusOn(section?: FocusTarget) {
    // Safeguard- always want to focus on something.
    if (!section || section === FocusTarget.ANY) {
      section = this.chooseFocusTarget();
    }
    whenVisible(this, () => {
      if (section === FocusTarget.REVIEWERS) {
        const reviewerEntry = this.reviewersList?.focusStart;
        reviewerEntry?.focus();
      } else if (section === FocusTarget.CCS) {
        const ccEntry = this.ccsList?.focusStart;
        ccEntry?.focus();
      } else {
        this.patchsetLevelGrComment?.focus();
      }
    });
  }

  chooseFocusTarget() {
    if (!this.isOwner) return FocusTarget.BODY;
    if (hasHumanReviewer(this.change)) return FocusTarget.BODY;
    return FocusTarget.REVIEWERS;
  }

  handle400Error(r?: Response | null) {
    if (!r) throw new Error('Response is empty.');
    let response: Response = r;
    // A call to saveReview could fail with a server error if erroneous
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

    // Using response.clone() here, because readJSONResponsePayload() and
    // potentially the generic error handler will want to call text() on the
    // response object, which can only be done once per object.
    const jsonPromise = readJSONResponsePayload(response.clone());
    return jsonPromise
      .then((payload: ResponsePayload) => {
        const result = payload.parsed as ReviewResult;
        // Only perform custom error handling for 400s and a parsable
        // ReviewResult response.
        if (response.status === 400 && result.reviewers) {
          const errors: string[] = [];
          const addReviewers = Object.values(result.reviewers);
          addReviewers.forEach(r => errors.push(r.error ?? 'no explanation'));
          response = {
            ...response,
            ok: false,
            text: () => Promise.resolve(errors.join(', ')),
          };
        }
      })
      .finally(() => {
        fireServerError(response);
      });
  }

  computeDraftsTitle(draftCommentThreads?: CommentThread[]) {
    const total = draftCommentThreads ? draftCommentThreads.length : 0;
    return pluralize(total, 'Draft');
  }

  computeMessagePlaceholder() {
    this.messagePlaceholder = this.canBeStarted
      ? 'Add a note for your reviewers...'
      : 'Say something nice...';
  }

  rebuildReviewerArrays() {
    if (!this.change?.owner || !this.change?.reviewers) return;
    const getAccounts = (state: ReviewerState) =>
      Object.values(this.change?.reviewers[state] ?? []).filter(
        account => account._account_id !== this.change!.owner._account_id
      );

    this.ccs = getAccounts(ReviewerState.CC);
    this.reviewers = getAccounts(ReviewerState.REVIEWER);
  }

  toggleAttentionModify() {
    this.attentionExpanded = !this.attentionExpanded;
  }

  onAttentionExpandedChange() {
    // If the attention-detail section is expanded without dispatching this
    // event, then the dialog may expand beyond the screen's bottom border.
    fire(this, 'iron-resize', {});
  }

  handleAttentionClick(e: Event) {
    const targetAccount = (e.target as GrAccountChip)?.account;
    if (!targetAccount) return;
    const id = getUserId(targetAccount);
    if (!id || !this.account || !this.change?.owner) return;

    const self = id === getUserId(this.account) ? '_SELF' : '';
    const role = id === getUserId(this.change.owner) ? 'OWNER' : '_REVIEWER';

    if (this.newAttentionSet.has(id)) {
      this.newAttentionSet.delete(id);
      this.manuallyAddedAttentionSet.delete(id);
      this.manuallyDeletedAttentionSet.add(id);
      this.reporting.reportInteraction(Interaction.ATTENTION_SET_CHIP, {
        action: `REMOVE${self}${role}`,
      });
    } else {
      this.newAttentionSet.add(id);
      this.manuallyAddedAttentionSet.add(id);
      this.manuallyDeletedAttentionSet.delete(id);
      this.reporting.reportInteraction(Interaction.ATTENTION_SET_CHIP, {
        action: `ADD${self}${role}`,
      });
    }

    this.requestUpdate();
  }

  computeHasNewAttention(account?: AccountInfo) {
    return !!(account && this.newAttentionSet?.has(getUserId(account)));
  }

  computeNewAttention() {
    if (
      this.account?._account_id === undefined ||
      this.change === undefined ||
      this.includeComments === undefined
    ) {
      return;
    }
    // The draft comments are only relevant for the attention set as long as the
    // user actually plans to publish their drafts.
    const draftCommentThreads = this.includeComments
      ? this.draftCommentThreads
      : [];
    const hasVote = !!this.labelsChanged;
    const isUploader = this.uploader?._account_id === this.account._account_id;

    this.attentionCcsCount = removeServiceUsers(this.ccs).length;
    this.currentAttentionSet = new Set(
      Object.keys(this.change.attention_set || {}).map(
        id => Number(id) as AccountId
      )
    );
    const newAttention = new Set(this.currentAttentionSet);

    for (const user of this.mentionedUsersInUnresolvedDrafts) {
      newAttention.add(getUserId(user));
    }

    if (this.change.status === ChangeStatus.NEW) {
      // Add everyone that the user is replying to in a comment thread.
      this.computeCommentAccountsForAttention(
        draftCommentThreads,
        isUploader
      ).forEach(id => newAttention.add(id));
      // Remove the current user.
      newAttention.delete(this.account._account_id);
      // Add all new reviewers, but not the current reviewer, if they are also
      // sending a draft or a label vote.
      const notIsReviewerAndHasDraftOrLabel = (r: AccountInfo) =>
        !(
          r._account_id === this.account!._account_id &&
          (this.hasDrafts() || hasVote)
        );
      this.reviewers
        .filter(r => isAccount(r))
        .filter(
          r =>
            isAccountNewlyAdded(r, ReviewerState.REVIEWER, this.change) ||
            (this.canBeStarted && this.isOwner)
        )
        .filter(notIsReviewerAndHasDraftOrLabel)
        .forEach(r => newAttention.add((r as AccountInfo)._account_id!));
      // Add owner and uploader, if someone else replies.
      if (this.hasDrafts() || hasVote) {
        if (this.uploader?._account_id && !isUploader) {
          newAttention.add(this.uploader._account_id);
        }
        if (this.change.owner?._account_id && !this.isOwner) {
          newAttention.add(this.change.owner._account_id);
        }
      }
    } else {
      // The only reason for adding someone to the attention set for merged or
      // abandoned changes is that someone makes a comment thread unresolved.
      const hasUnresolvedDraft = draftCommentThreads.some(isUnresolved);
      if (this.change.owner && hasUnresolvedDraft) {
        // A change owner must have an account_id.
        newAttention.add(this.change.owner._account_id!);
      }
      // Remove the current user.
      newAttention.delete(this.account._account_id);
    }
    // Finally make sure that everyone in the attention set is still active as
    // owner, reviewer or cc.
    const allAccountIds = this.allAccounts()
      .map(a => getUserId(a))
      .filter(id => !!id);
    this.newAttentionSet = new Set([
      ...this.manuallyAddedAttentionSet,
      ...[...newAttention].filter(
        id =>
          allAccountIds.includes(id) &&
          !this.manuallyDeletedAttentionSet.has(id)
      ),
    ]);
    // Possibly expand if need be, never collapse as this is jarring to the user.
    // For long account lists (10 or more), avoid automatic expansion.
    this.attentionExpanded =
      this.attentionExpanded ||
      (allAccountIds.length < 10 && this.computeShowAttentionTip(1));
  }

  computeShowAttentionTip(minimum: number) {
    if (!this.currentAttentionSet || !this.newAttentionSet) return false;
    const addedIds = [...this.newAttentionSet].filter(
      id => !this.currentAttentionSet.has(id)
    );
    return this.isOwner && addedIds.length >= minimum;
  }

  /**
   * Pick previous commenters for addition to attention set.
   *
   * For every thread:
   *   - If owner replied and thread is unresolved: add all commenters.
   *   - If owner replied and thread is resolved: add commenters who need to vote.
   *   - If reviewer replied and thread is resolved: add commenters who need to vote.
   *   - If reviewer replied and thread is unresolved: only add owner
   *     (owner added outside this function).
   */
  computeCommentAccountsForAttention(
    threads: CommentThread[],
    isUploader: boolean
  ) {
    const crLabel = this.change?.labels?.[StandardLabels.CODE_REVIEW];
    const maxCrVoteAccountIds = getMaxAccounts(crLabel).map(a => a._account_id);
    const accountIds = new Set<AccountId>();
    threads.forEach(thread => {
      const unresolved = isUnresolved(thread);
      let ignoreVoteCheck = false;
      if (unresolved) {
        if (this.isOwner || isUploader) {
          // Owner replied but didn't resolve, we assume clarification was asked
          // add everyone on the thread to attention set.
          ignoreVoteCheck = true;
        } else {
          // Reviewer replied owner is still the one to act. No need to add
          // commenters.
          return;
        }
      }
      // If thread is resolved, we only bring back the commenters who have not
      // yet left max Code-Review vote.
      thread.comments.forEach(comment => {
        if (comment.author) {
          // A comment author must have an account_id.
          const authorId = comment.author._account_id!;
          const needsToVote =
            !maxCrVoteAccountIds.includes(authorId) && // Didn't give max-vote
            this.uploader?._account_id !== authorId && // Not uploader
            this.change?.owner._account_id !== authorId; // Not owner
          if (ignoreVoteCheck || needsToVote) accountIds.add(authorId);
        }
      });
    });
    return accountIds;
  }

  computeShowNoAttentionUpdate() {
    return (
      this.isSendDisabled() || this.computeNewAttentionAccounts().length === 0
    );
  }

  computeDoNotUpdateMessage() {
    if (!this.currentAttentionSet || !this.newAttentionSet) return '';
    if (
      this.isSendDisabled() ||
      areSetsEqual(this.currentAttentionSet, this.newAttentionSet)
    ) {
      return 'No changes to the attention set.';
    }
    if (containsAll(this.currentAttentionSet, this.newAttentionSet)) {
      return 'No additions to the attention set.';
    }
    this.reporting.error(
      'computeDoNotUpdateMessage',
      new Error(
        'computeDoNotUpdateMessage()' +
          'should not be called when users were added to the attention set.'
      )
    );
    return '';
  }

  computeNewAttentionAccounts(): AccountInfo[] {
    if (
      this.currentAttentionSet === undefined ||
      this.newAttentionSet === undefined
    ) {
      return [];
    }
    return [...this.newAttentionSet]
      .filter(id => !this.currentAttentionSet.has(id))
      .map(id => this.findAccountById(id))
      .filter(account => !!account);
  }

  findAccountById(userId: UserId) {
    return this.allAccounts().find(r => getUserId(r) === userId);
  }

  allAccounts() {
    let allAccounts: (AccountInfoInput | GroupInfoInput)[] = [];
    if (this.change && this.change.owner) allAccounts.push(this.change.owner);
    if (this.uploader) allAccounts.push(this.uploader);
    if (this.reviewers) allAccounts = [...allAccounts, ...this.reviewers];
    if (this.ccs) allAccounts = [...allAccounts, ...this.ccs];
    return removeServiceUsers(allAccounts.filter(isAccount));
  }

  computeUploader() {
    if (
      !this.change?.current_revision ||
      !this.change?.revisions?.[this.change.current_revision]
    ) {
      this.uploader = undefined;
      return;
    }
    const rev = this.change.revisions[this.change.current_revision];

    if (
      !rev.uploader ||
      this.change?.owner._account_id === rev.uploader._account_id
    ) {
      this.uploader = undefined;
      return;
    }
    this.uploader = rev.uploader;
  }

  /**
   * Generates a function to filter out reviewer/CC entries. When isCCs is
   * truthy, the function filters out entries that already exist in this.ccs.
   * When falsy, the function filters entries that exist in this.reviewers.
   */
  filterReviewerSuggestionGenerator(
    isCCs: boolean
  ): (input: Suggestion) => boolean {
    return suggestion => {
      let entry: AccountInfo | GroupInfo;
      if (isReviewerAccountSuggestion(suggestion)) {
        entry = suggestion.account;
        if (entry._account_id === this.change?.owner?._account_id) {
          return false;
        }
      } else if (isReviewerGroupSuggestion(suggestion)) {
        entry = suggestion.group;
      } else {
        this.reporting.error(
          'Reviewer Suggestion',
          new Error(`Suggestion is neither account nor group: ${suggestion}`)
        );
        return false;
      }

      const key = getUserId(entry);
      const finder = (entry: AccountInfo | GroupInfo) =>
        getUserId(entry) === key;
      if (isCCs) {
        return this.ccs.find(finder) === undefined;
      }
      return this.reviewers.find(finder) === undefined;
    };
  }

  cancelTapHandler(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.cancel();
  }

  async cancel() {
    assertIsDefined(this.change, 'change');
    if (!this.change?.owner) throw new Error('missing required owner property');
    fireNoBubble(this, 'cancel', {});
    await this.patchsetLevelGrComment?.save();
    this.rebuildReviewerArrays();
  }

  private saveClickHandler(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.submit(false);
  }

  private sendClickHandler(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.submit(this.canBeStarted);
  }

  private submit(startReview?: boolean) {
    if (startReview === undefined) {
      startReview = this.isOwner && this.canBeStarted;
    }
    if (!this.ccsList?.submitEntryText()) {
      // Do not proceed with the send if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    if (this.isSendDisabled()) {
      fireAlert(this, EMPTY_REPLY_MESSAGE);
      return;
    }
    return this.send(this.includeComments, startReview).catch(err => {
      fireError(this, `Error submitting review ${err}`);
    });
  }

  saveReview(review: ReviewInput, errFn?: ErrorCallback) {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.latestPatchNum, 'latestPatchNum');
    return this.restApiService.saveChangeReview(
      this.change._number,
      this.latestPatchNum,
      review,
      errFn,
      /* fetchDetail=*/ true
    );
  }

  pendingConfirmationUpdated(reviewer: RawAccountInput | null) {
    if (reviewer === null) {
      this.reviewerConfirmationModal?.close();
    } else {
      this.pendingConfirmationDetails =
        this.ccPendingConfirmation || this.reviewerPendingConfirmation;
      this.reviewerConfirmationModal?.showModal();
    }
  }

  confirmPendingReviewer() {
    this.reviewerConfirmationModal?.close();
    if (this.ccPendingConfirmation) {
      this.ccsList?.confirmGroup(this.ccPendingConfirmation.group);
      this.focusOn(FocusTarget.CCS);
      return;
    }
    if (this.reviewerPendingConfirmation) {
      this.reviewersList?.confirmGroup(this.reviewerPendingConfirmation.group);
      this.focusOn(FocusTarget.REVIEWERS);
      return;
    }
    this.reporting.error(
      'confirmPendingReviewer',
      new Error('confirmPendingReviewer called without pending confirm')
    );
  }

  cancelPendingReviewer() {
    this.reviewerConfirmationModal?.close();
    this.ccPendingConfirmation = null;
    this.reviewerPendingConfirmation = null;

    const target = this.ccPendingConfirmation
      ? FocusTarget.CCS
      : FocusTarget.REVIEWERS;
    this.focusOn(target);
  }

  handleAccountTextEntry() {
    // When either of the account entries has input added to the autocomplete,
    // it should trigger the save button to enable/
    //
    // Note: if the text is removed, the save button will not get disabled.
    this.reviewersMutated = true;
  }

  private alreadyExists(ccs: AccountInput[], user: AccountInfoInput) {
    return ccs
      .filter(cc => isAccount(cc))
      .some(cc => getUserId(cc) === getUserId(user));
  }

  private isAlreadyReviewerOrCC(user: AccountInfo) {
    return (
      this.alreadyExists(this.reviewers, user) ||
      this.alreadyExists(this._ccs, user)
    );
  }

  getLabelScores(): GrLabelScores {
    return this.labelScores || queryAndAssert(this, 'gr-label-scores');
  }

  _handleLabelsChanged() {
    this.labelsChanged =
      Object.keys(this.getLabelScores().getLabelValues(false)).length !== 0;
  }

  handleReviewersChanged(e: ValueChangedEvent<(AccountInfo | GroupInfo)[]>) {
    this.reviewers = [...e.detail.value];
    this.reviewersMutated = true;
  }

  handleCcsChanged(e: ValueChangedEvent<(AccountInfo | GroupInfo)[]>) {
    this.ccs = [...e.detail.value];
    this.reviewersMutated = true;
  }

  handleReviewersConfirmationChanged(
    e: ValueChangedEvent<SuggestedReviewerGroupInfo | null>
  ) {
    this.reviewerPendingConfirmation = e.detail.value;
  }

  handleCcsConfirmationChanged(
    e: ValueChangedEvent<SuggestedReviewerGroupInfo | null>
  ) {
    this.ccPendingConfirmation = e.detail.value;
  }

  _reload() {
    this.getChangeModel().navigateToChangeResetReload();
    this.cancel();
  }

  computeSendButtonTooltip(canBeStarted?: boolean, commentEditing?: boolean) {
    if (commentEditing) {
      return ButtonTooltips.DISABLED_COMMENT_EDITING;
    }
    return canBeStarted ? ButtonTooltips.START_REVIEW : ButtonTooltips.SEND;
  }

  computeSavingLabelClass(savingComments: boolean) {
    return savingComments ? 'saving' : '';
  }

  // visible for testing
  isSendDisabled() {
    if (
      this.canBeStarted === undefined ||
      this.patchsetLevelDraftMessage === undefined ||
      this.reviewersMutated === undefined ||
      this.labelsChanged === undefined ||
      this.includeComments === undefined ||
      this.disabled === undefined ||
      this.commentEditing === undefined ||
      this.change?.labels === undefined ||
      this.account === undefined
    ) {
      return undefined;
    }
    if (this.commentEditing || this.disabled) {
      return true;
    }
    if (this.canBeStarted === true) {
      return false;
    }
    const existingVote = Object.values(this.change.labels).some(
      label =>
        isDetailedLabelInfo(label) && getApprovalInfo(label, this.account!)
    );
    const revotingOrNewVote = this.labelsChanged || existingVote;
    const hasDrafts =
      (this.includeComments && this.draftCommentThreads.length > 0) ||
      this.patchsetLevelDraftMessage.length > 0;
    return (
      !hasDrafts &&
      !this.patchsetLevelDraftMessage.length &&
      !this.reviewersMutated &&
      !revotingOrNewVote
    );
  }

  computeChangeMergedWarning() {
    return 'Change has already been merged';
  }

  computePatchSetWarning() {
    let str = `Patch ${this.latestPatchNum} is not latest.`;
    if (this.labelsChanged) {
      str += ' Voting may have no effect.';
    }
    return str;
  }

  setPluginMessage(message: string) {
    this.pluginMessage = message;
  }

  getReviewerSuggestionsProvider(change?: ChangeInfo | ParsedChangeInfo) {
    if (!change) return;
    const provider = new GrReviewerSuggestionsProvider(
      this.restApiService,
      ReviewerState.REVIEWER,
      this.serverConfig,
      this.isLoggedIn,
      change
    );
    return provider;
  }

  getCcSuggestionsProvider(change?: ChangeInfo | ParsedChangeInfo) {
    if (!change) return;
    const provider = new GrReviewerSuggestionsProvider(
      this.restApiService,
      ReviewerState.CC,
      this.serverConfig,
      this.isLoggedIn,
      change
    );
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
    const selfId = (this.account && this.account._account_id) || -1;
    for (const added of addedSet || []) {
      const addedId = added.user;
      const self = addedId === selfId ? '_SELF' : '';
      const role = addedId === ownerId ? 'OWNER' : '_REVIEWER';
      actions.push('ADD' + self + role);
    }
    for (const removed of removedSet || []) {
      const removedId = removed.user;
      const self = removedId === selfId ? '_SELF' : '';
      const role = removedId === ownerId ? 'OWNER' : '_REVIEWER';
      actions.push('REMOVE' + self + role);
    }
    this.reporting.reportInteraction('attention-set-actions', {actions});
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-reply-dialog': GrReplyDialog;
  }
  interface HTMLElementEventMap {
    /** Fired when the user presses the cancel button. */
    // prettier-ignore
    'cancel': CustomEvent<{}>;
    /**
     * Fires when the reply dialog believes that the server side diff drafts
     * have been updated and need to be refreshed.
     */
    'comment-refresh': CustomEvent<{}>;
    /** Fired when a reply is successfully sent. */
    // prettier-ignore
    'send': CustomEvent<{}>;
    /** Fires when the state of the send button (enabled/disabled) changes. */
    'send-disabled-changed': CustomEvent<{}>;
  }
}
