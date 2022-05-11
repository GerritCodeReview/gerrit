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
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../plugins/gr-endpoint-slot/gr-endpoint-slot';
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-textarea/gr-textarea';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-formatted-text/gr-formatted-text';
import '../../shared/gr-overlay/gr-overlay';
import '../../shared/gr-account-list/gr-account-list';
import '../gr-label-scores/gr-label-scores';
import '../gr-thread-list/gr-thread-list';
import '../../../styles/shared-styles';
import {
  GrReviewerSuggestionsProvider,
  SUGGESTIONS_PROVIDERS_USERS_TYPES,
} from '../../../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {getAppContext} from '../../../services/app-context';
import {
  ChangeStatus,
  DraftsAction,
  ReviewerState,
  SpecialFilePath,
} from '../../../constants/constants';
import {
  accountOrGroupKey,
  isReviewerOrCC,
  mapReviewer,
  removeServiceUsers,
} from '../../../utils/account-util';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {TargetElement} from '../../../api/plugin';
import {FixIronA11yAnnouncer} from '../../../types/types';
import {
  AccountAddition,
  AccountInfoInput,
  AccountInput,
  AccountInputDetail,
  GrAccountList,
  GroupInfoInput,
  RawAccountInput,
} from '../../shared/gr-account-list/gr-account-list';
import {
  AccountId,
  AccountInfo,
  AttentionSetInput,
  ChangeInfo,
  CommentInput,
  GroupInfo,
  isAccount,
  isDetailedLabelInfo,
  isReviewerAccountSuggestion,
  isReviewerGroupSuggestion,
  ParsedJSON,
  PatchSetNum,
  ReviewerInput,
  ReviewInput,
  ReviewResult,
  ServerInfo,
  SuggestedReviewerGroupInfo,
  Suggestion,
} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrLabelScores} from '../gr-label-scores/gr-label-scores';
import {GrLabelScoreRow} from '../gr-label-score-row/gr-label-score-row';
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
import {addShortcut, Key, Modifier} from '../../../utils/dom-util';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';
import {ConfigInfo, LabelNameToValuesMap} from '../../../api/rest-api';
import {css, html, PropertyValues, LitElement} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {when} from 'lit/directives/when';
import {classMap} from 'lit/directives/class-map';
import {BindValueChangeEvent, ValueChangedEvent} from '../../../types/events';
import {customElement, property, state, query} from 'lit/decorators';
import {subscribe} from '../../lit/subscription-controller';

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

@customElement('gr-reply-dialog')
export class GrReplyDialog extends LitElement {
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

  private readonly reporting = getAppContext().reportingService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean})
  canBeStarted = false;

  @property({type: Boolean, reflect: true})
  disabled = false;

  @property({type: Array})
  draftCommentThreads: CommentThread[] | undefined;

  @property({type: Object})
  permittedLabels?: LabelNameToValuesMap;

  @property({type: Object})
  projectConfig?: ConfigInfo;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @query('#reviewers') reviewersList?: GrAccountList;

  @query('#ccs') ccsList?: GrAccountList;

  @query('#cancelButton') cancelButton?: GrButton;

  @query('#sendButton') sendButton?: GrButton;

  @query('#labelScores') labelScores?: GrLabelScores;

  @query('#textarea') textarea?: GrTextarea;

  @query('#reviewerConfirmationOverlay')
  reviewerConfirmationOverlay?: GrOverlay;

  @state()
  draft = '';

  @state()
  filterReviewerSuggestion: (input: Suggestion) => boolean;

  @state()
  filterCCSuggestion: (input: Suggestion) => boolean;

  @state()
  knownLatestState?: LatestPatchState;

  @state()
  underReview = true;

  @state()
  account?: AccountInfo;

  @state()
  ccs: (AccountInfoInput | GroupInfoInput)[] = [];

  @state()
  attentionCcsCount = 0;

  @state()
  ccPendingConfirmation: SuggestedReviewerGroupInfo | null = null;

  @state()
  messagePlaceholder?: string;

  @state()
  owner?: AccountInfo;

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
  previewFormatting = false;

  @state()
  sendButtonLabel?: string;

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
  currentAttentionSet: Set<AccountId> = new Set();

  @state()
  newAttentionSet: Set<AccountId> = new Set();

  @state()
  sendDisabled?: boolean;

  @state()
  isResolvedPatchsetLevelComment = true;

  @state()
  allReviewers: (AccountInfo | GroupInfo)[] = [];

  private readonly restApiService: RestApiService =
    getAppContext().restApiService;

  private readonly storage = getAppContext().storageService;

  private readonly jsAPI = getAppContext().jsApiService;

  storeTask?: DelayedTask;

  private isLoggedIn = false;

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  static override styles = [
    sharedStyles,
    css`
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
      #reviewerConfirmationOverlay {
        padding: var(--spacing-l);
        text-align: center;
      }
      .reviewerConfirmationButtons {
        margin-top: var(--spacing-l);
      }
      .groupName {
        font-weight: var(--font-weight-bold);
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
      #textarea,
      gr-endpoint-decorator[name='reply-text'] {
        display: flex;
        width: 100%;
      }
      .newReplyDialog .textareaContainer,
      #textarea,
      gr-endpoint-decorator[name='reply-text'] {
        display: block;
        width: unset;
        font-family: var(--monospace-font-family);
        font-size: var(--font-size-code);
        line-height: calc(var(--font-size-code) + var(--spacing-s));
        font-weight: var(--font-weight-normal);
      }
      .newReplyDialog#textarea {
        padding: var(--spacing-m);
      }
      gr-endpoint-decorator[name='reply-text'] {
        flex-direction: column;
      }
      #textarea {
        flex: 1;
      }
      .previewContainer {
        border-top: none;
      }
      .previewContainer gr-formatted-text {
        background: var(--table-header-background-color);
        padding: var(--spacing-l);
      }
      #checkingStatusLabel,
      #notLatestLabel {
        margin-left: var(--spacing-l);
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
        margin-left: var(--spacing-l);
        margin-bottom: var(--spacing-m);
      }
      #pluginMessage:empty {
        display: none;
      }
      .preview-formatting {
        margin-left: var(--spacing-m);
      }
      .attention-icon {
        width: 14px;
        height: 14px;
        vertical-align: top;
        position: relative;
        top: 3px;
        --iron-icon-height: 24px;
        --iron-icon-width: 24px;
      }
      .attention .edit-attention-button {
        vertical-align: top;
        --gr-button-padding: 0px 4px;
      }
      .attention .edit-attention-button iron-icon {
        color: inherit;
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
        background-color: var(--assignee-highlight-color);
      }
      .attentionTip div iron-icon {
        margin-right: var(--spacing-s);
      }
      .patchsetLevelContainer {
        width: 80ch;
        border-radius: var(--border-radius);
        box-shadow: var(--elevation-level-2);
      }
      .patchsetLevelContainer.resolved {
        background-color: var(--comment-background-color);
      }
      .patchsetLevelContainer.unresolved {
        background-color: var(--unresolved-comment-background-color);
      }
      .labelContainer {
        padding-left: var(--spacing-m);
        padding-bottom: var(--spacing-m);
      }
    `,
  ];

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('draft')) {
      this.draftChanged(changedProperties.get('draft') as string);
    }
    if (changedProperties.has('ccPendingConfirmation')) {
      this.pendingConfirmationUpdated(this.ccPendingConfirmation);
    }
    if (changedProperties.has('reviewerPendingConfirmation')) {
      this.pendingConfirmationUpdated(this.reviewerPendingConfirmation);
    }
    if (changedProperties.has('change')) {
      this.computeUploader();
      this.changeUpdated();
    }
    if (changedProperties.has('canBeStarted')) {
      this.computeMessagePlaceholder();
      this.computeSendButtonLabel();
    }
    if (changedProperties.has('reviewFormatting')) {
      this.handleHeightChanged();
    }
    if (changedProperties.has('draftCommentThreads')) {
      this.handleHeightChanged();
    }
    if (changedProperties.has('reviewers')) {
      this.computeAllReviewers();
    }
    if (changedProperties.has('sendDisabled')) {
      this.sendDisabledChanged();
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
      changedProperties.has('includeComments') ||
      changedProperties.has('labelsChanged') ||
      changedProperties.has('draft')
    ) {
      this.computeNewAttention();
    }
  }

  constructor() {
    super();
    this.filterReviewerSuggestion =
      this.filterReviewerSuggestionGenerator(false);
    this.filterCCSuggestion = this.filterReviewerSuggestionGenerator(true);
    this.jsAPI.addElement(TargetElement.REPLY_DIALOG, this);
  }

  override connectedCallback() {
    super.connectedCallback();
    (
      IronA11yAnnouncer as unknown as FixIronA11yAnnouncer
    ).requestAvailability();
    this.restApiService.getAccount().then(account => {
      if (account) this.account = account;
    });

    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.CTRL_KEY]}, _ =>
        this.submit()
      )
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER, modifiers: [Modifier.META_KEY]}, _ =>
        this.submit()
      )
    );
    this.cleanups.push(addShortcut(this, {key: Key.ESC}, _ => this.cancel()));
    this.addEventListener('comment-editing-changed', e => {
      this.commentEditing = (e as CustomEvent).detail;
    });

    // Plugins on reply-reviewers endpoint can take advantage of these
    // events to add / remove reviewers

    this.addEventListener('add-reviewer', e => {
      // Only support account type, see more from:
      // elements/shared/gr-account-list/gr-account-list.js#addAccountItem
      this.reviewersList?.addAccountItem({
        account: (e as CustomEvent).detail.reviewer,
        count: 1,
      });
    });

    this.addEventListener('remove-reviewer', e => {
      this.reviewersList?.removeAccount((e as CustomEvent).detail.reviewer);
    });

    subscribe(
      this,
      getAppContext().userModel.loggedIn$,
      isLoggedIn => (this.isLoggedIn = isLoggedIn)
    );
  }

  override disconnectedCallback() {
    this.storeTask?.cancel();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
    super.disconnectedCallback();
  }

  override render() {
    if (!this.change) return;
    this.sendDisabled = this.computeSendButtonDisabled();
    return html`
      <div tabindex="-1">
        <section class="peopleContainer">
          <gr-endpoint-decorator name="reply-reviewers">
            <gr-endpoint-param
              name="change"
              .value=${this.change}
            ></gr-endpoint-param>
            <gr-endpoint-param name="reviewers" .value=${this.allReviewers}>
            </gr-endpoint-param>
            ${this.renderReviewerList()}
            <gr-endpoint-slot name="below"></gr-endpoint-slot>
          </gr-endpoint-decorator>
          ${this.renderCCList()} ${this.renderReviewConfirmation()}
        </section>
        <section class="labelsContainer">${this.renderLabels()}</section>
        <section class="newReplyDialog textareaContainer">
          ${this.renderReplyText()}
        </section>
        ${when(
          this.previewFormatting,
          () => html`
            <section class="previewContainer">
              <gr-formatted-text
                .content=${this.draft}
                .config=${this.projectConfig?.commentlinks}
              ></gr-formatted-text>
            </section>
          `
        )}
        ${this.renderDraftsSection()}
        <div class="stickyBottom newReplyDialog">
          <gr-endpoint-decorator name="reply-bottom">
            <gr-endpoint-param
              name="change"
              .value=${this.change}
            ></gr-endpoint-param>
            ${this.renderAttentionSummarySection()}
            ${this.renderAttentionDetailsSection()}
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
          .accounts=${this.getAccountListCopy(this.reviewers)}
          @account-added=${this.accountAdded}
          @accounts-changed=${this.handleReviewersChanged}
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
          .accounts=${this.getAccountListCopy(this.ccs)}
          @account-added=${this.accountAdded}
          @accounts-changed=${this.handleCcsChanged}
          .removableValues=${this.change?.removable_reviewers}
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
      <gr-overlay
        id="reviewerConfirmationOverlay"
        @iron-overlay-canceled=${this.cancelPendingReviewer}
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
      </gr-overlay>
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

  private renderReplyText() {
    if (!this.change) return;
    return html`
      <div
        class=${classMap({
          patchsetLevelContainer: true,
          [this.getUnresolvedPatchsetLevelClass(
            this.isResolvedPatchsetLevelComment
          )]: true,
        })}
      >
        <gr-endpoint-decorator name="reply-text">
          <gr-textarea
            id="textarea"
            class="message newReplyDialog"
            .autocomplete=${'on'}
            .placeholder=${this.messagePlaceholder}
            monospace
            ?disabled=${this.disabled}
            .rows=${4}
            .text=${this.draft}
            @bind-value-changed=${(e: BindValueChangeEvent) => {
              this.draft = e.detail.value;
              this.handleHeightChanged();
            }}
          >
          </gr-textarea>
          <gr-endpoint-param name="change" .value=${this.change}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
        <div class="labelContainer">
          <label>
            <input
              id="resolvedPatchsetLevelCommentCheckbox"
              type="checkbox"
              ?checked=${this.isResolvedPatchsetLevelComment}
              @change=${this.handleResolvedPatchsetLevelCommentCheckboxChanged}
            />
            Resolved
          </label>
          <label class="preview-formatting">
            <input
              type="checkbox"
              ?checked=${this.previewFormatting}
              @change=${this.handlePreviewFormattingChanged}
            />
            Preview formatting
          </label>
        </div>
      </div>
    `;
  }

  private renderDraftsSection() {
    if (this.computeHideDraftList(this.draftCommentThreads)) return;
    return html`
      <section class="draftsContainer">
        <div class="includeComments">
          <input
            type="checkbox"
            id="includeComments"
            @change=${this.handleIncludeCommentsChanged}
            ?checked=${this.includeComments}
          />
          <label for="includeComments"
            >Publish ${this.computeDraftsTitle(this.draftCommentThreads)}</label
          >
        </div>
        ${when(
          this.includeComments,
          () => html`
            <gr-thread-list
              id="commentList"
              .threads=${this.draftCommentThreads!}
              hide-dropdown
            >
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
    if (this.attentionExpanded) return;
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
            <gr-tooltip-content
              has-tooltip
              title=${this.computeAttentionButtonTitle()}
            >
              <gr-button
                class="edit-attention-button"
                @click=${this.handleAttentionModify}
                ?disabled=${this.sendDisabled}
                link
                position-below
                data-label="Edit"
                data-action-type="change"
                data-action-key="edit"
                role="button"
                tabindex="0"
              >
                <iron-icon icon="gr-icons:edit"></iron-icon>
                Modify
              </gr-button>
            </gr-tooltip-content>
          </div>
          <div>
            <a
              href="https://gerrit-review.googlesource.com/Documentation/user-attention-set.html"
              target="_blank"
            >
              <iron-icon
                icon="gr-icons:help-outline"
                title="read documentation"
              ></iron-icon>
            </a>
          </div>
        </div>
      </section>
    `;
  }

  private renderAttentionDetailsSection() {
    if (!this.attentionExpanded) return;
    return html`
      <section class="attention-detail">
        <div class="attentionDetailsTitle">
          <div>
            <span>Modify attention to</span>
          </div>
          <div></div>
          <div>
            <a
              href="https://gerrit-review.googlesource.com/Documentation/user-attention-set.html"
              target="_blank"
            >
              <iron-icon
                icon="gr-icons:help-outline"
                title="read documentation"
              ></iron-icon>
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
              .account=${this.owner}
              ?forceAttention=${this.computeHasNewAttention(this.owner)}
              .selected=${this.computeHasNewAttention(this.owner)}
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
            ${this.removeServiceUsers(this.reviewers).map(
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
                ${this.removeServiceUsers(this.ccs).map(
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
          this.computeShowAttentionTip(
            this.account,
            this.owner,
            this.currentAttentionSet,
            this.newAttentionSet
          ),
          () => html`
            <div class="attentionTip">
              <iron-icon
                class="pointer"
                icon="gr-icons:lightbulb-outline"
              ></iron-icon>
              Be mindful of requiring attention from too many users.
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
                Checking whether patch ${this.patchNum} is latest...
              </span>
            `
          )}
          ${when(
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
              ?disabled=${this.sendDisabled}
              class="action send"
              @click=${this.sendTapHandler}
              >${this.sendButtonLabel}
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
  open(focusTarget?: FocusTarget, quote?: string) {
    assertIsDefined(this.change, 'change');
    this.knownLatestState = LatestPatchState.CHECKING;
    this.getChangeModel()
      .fetchChangeUpdates(this.change)
      .then(result => {
        this.knownLatestState = result.isLatest
          ? LatestPatchState.LATEST
          : LatestPatchState.NOT_LATEST;
      });

    this.focusOn(focusTarget);
    if (quote?.length) {
      // If a reply quote has been provided, use it.
      this.draft = quote;
    } else {
      // Otherwise, check for an unsaved draft in localstorage.
      this.draft = this.loadStoredDraft();
    }
    if (this.restApiService.hasPendingDiffDrafts()) {
      this.savingComments = true;
      this.restApiService.awaitPendingDiffDrafts().then(() => {
        fireEvent(this, 'comment-refresh');
        this.savingComments = false;
      });
    }
  }

  hasDrafts() {
    if (this.draftCommentThreads === undefined) return false;
    return this.draft.length > 0 || this.draftCommentThreads.length > 0;
  }

  override focus() {
    this.focusOn(FocusTarget.ANY);
  }

  getFocusStops() {
    const end = this.sendDisabled ? this.cancelButton : this.sendButton;
    if (!this.reviewersList?.focusStart || !end) return undefined;
    return {
      start: this.reviewersList.focusStart,
      end,
    };
  }

  private handleResolvedPatchsetLevelCommentCheckboxChanged(e: Event) {
    if (!(e.target instanceof HTMLInputElement)) return;
    this.isResolvedPatchsetLevelComment = e.target.checked;
  }

  private handlePreviewFormattingChanged(e: Event) {
    if (!(e.target instanceof HTMLInputElement)) return;
    this.previewFormatting = e.target.checked;
  }

  private handleIncludeCommentsChanged(e: Event) {
    if (!(e.target instanceof HTMLInputElement)) return;
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

  accountAdded(e: CustomEvent<AccountInputDetail>) {
    const account = e.detail.account;
    const key = accountOrGroupKey(account);
    const reviewerType =
      (e.target as GrAccountList).getAttribute('id') === 'ccs'
        ? ReviewerType.CC
        : ReviewerType.REVIEWER;
    const isReviewer = ReviewerType.REVIEWER === reviewerType;
    const array = isReviewer ? this.ccs : this.reviewers;
    const index = array.findIndex(
      reviewer => accountOrGroupKey(reviewer) === key
    );
    if (index >= 0) {
      // Remove any accounts that already exist as a CC for reviewer
      // or vice versa.
      array.splice(index, 1);
      const moveFrom = isReviewer ? 'CC' : 'reviewer';
      const moveTo = isReviewer ? 'reviewer' : 'CC';
      const id = account.name || key;
      const message = `${id} moved from ${moveFrom} to ${moveTo}.`;
      fireAlert(this, message);
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
    addToReviewInput(this.reviewersList!.additions(), ReviewerState.REVIEWER);
    addToReviewInput(this.ccsList!.additions(), ReviewerState.CC);
    addToReviewInput(
      this.reviewersList!.removals().filter(
        r =>
          isReviewerOrCC(change, r) &&
          // ignore removal from reviewer request if being added to CC
          !this.ccsList!.additions().some(
            account => mapReviewer(account).reviewer === mapReviewer(r).reviewer
          )
      ),
      ReviewerState.REMOVED
    );
    addToReviewInput(
      this.ccsList!.removals().filter(
        r =>
          isReviewerOrCC(change, r) &&
          // ignore removal from CC request if being added as reviewer
          !this.reviewersList!.additions().some(
            account => mapReviewer(account).reviewer === mapReviewer(r).reviewer
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

    const reason = getReplyByReason(this.account, this.serverConfig);

    reviewInput.ignore_automatic_attention_set_rules = true;
    reviewInput.add_to_attention_set = [];
    for (const user of this.newAttentionSet) {
      if (!this.currentAttentionSet.has(user)) {
        reviewInput.add_to_attention_set.push({user, reason});
      }
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

    if (this.draft) {
      const comment: CommentInput = {
        message: this.draft,
        unresolved: !this.isResolvedPatchsetLevelComment,
      };
      reviewInput.comments = {
        [SpecialFilePath.PATCHSET_LEVEL_COMMENTS]: [comment],
      };
    }

    assertIsDefined(this.change, 'change');
    reviewInput.reviewers = this.computeReviewers(this.change);
    this.disabled = true;

    const errFn = (r?: Response | null) => this.handle400Error(r);
    return this.saveReview(reviewInput, errFn)
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
        this.includeComments = true;
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

  focusOn(section?: FocusTarget) {
    // Safeguard- always want to focus on something.
    if (!section || section === FocusTarget.ANY) {
      section = this.chooseFocusTarget();
    }
    if (section === FocusTarget.BODY) {
      const textarea = queryAndAssert<GrTextarea>(this, 'gr-textarea');
      setTimeout(() => textarea.getNativeTextarea().focus());
    } else if (section === FocusTarget.REVIEWERS) {
      const reviewerEntry = this.reviewersList?.focusStart;
      setTimeout(() => reviewerEntry?.focus());
    } else if (section === FocusTarget.CCS) {
      const ccEntry = this.ccsList?.focusStart;
      setTimeout(() => ccEntry?.focus());
    }
  }

  chooseFocusTarget() {
    // If we are the owner and the reviewers field is empty, focus on that.
    if (
      this.account &&
      this.change &&
      this.change.owner &&
      this.account._account_id === this.change.owner._account_id &&
      (!this.reviewers || this.reviewers?.length === 0)
    ) {
      return FocusTarget.REVIEWERS;
    }

    // Default to BODY.
    return FocusTarget.BODY;
  }

  isOwner(account?: AccountInfo, change?: ChangeInfo) {
    if (!account || !change || !change.owner) return false;
    return account._account_id === change.owner._account_id;
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

  computeHideDraftList(draftCommentThreads?: CommentThread[]) {
    return !draftCommentThreads || draftCommentThreads.length === 0;
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

  changeUpdated() {
    if (this.change === undefined) return;
    this.rebuildReviewerArrays();
  }

  rebuildReviewerArrays() {
    if (!this.change?.owner || !this.change?.reviewers) return;
    this.owner = this.change.owner;

    const reviewers = [];
    const ccs = [];

    if (this.change.reviewers) {
      for (const key of Object.keys(this.change.reviewers)) {
        if (key !== 'REVIEWER' && key !== 'CC') {
          this.reporting.error(new Error(`Unexpected reviewer state: ${key}`));
          continue;
        }
        if (!this.change.reviewers[key]) continue;
        for (const entry of this.change.reviewers[key]!) {
          if (entry._account_id === this.owner._account_id) {
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

    this.ccs = ccs;
    this.reviewers = reviewers;
  }

  handleAttentionModify() {
    this.attentionExpanded = true;
  }

  onAttentionExpandedChange() {
    // If the attention-detail section is expanded without dispatching this
    // event, then the dialog may expand beyond the screen's bottom border.
    fireEvent(this, 'iron-resize');
  }

  computeAttentionButtonTitle(sendDisabled?: boolean) {
    return sendDisabled
      ? 'Modify the attention set by adding a comment or use the account ' +
          'hovercard in the change page.'
      : 'Edit attention set changes';
  }

  handleAttentionClick(e: Event) {
    const id = (e.target as GrAccountChip)?.account?._account_id;
    if (!id) return;

    const selfId = (this.account && this.account._account_id) || -1;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;
    const self = id === selfId ? '_SELF' : '';
    const role = id === ownerId ? 'OWNER' : '_REVIEWER';

    if (this.newAttentionSet.has(id)) {
      this.newAttentionSet.delete(id);
      this.reporting.reportInteraction(Interaction.ATTENTION_SET_CHIP, {
        action: `REMOVE${self}${role}`,
      });
    } else {
      this.newAttentionSet.add(id);
      this.reporting.reportInteraction(Interaction.ATTENTION_SET_CHIP, {
        action: `ADD${self}${role}`,
      });
    }

    // Ensure that Polymer picks up the change.
    this.newAttentionSet = new Set(this.newAttentionSet);
  }

  computeHasNewAttention(account?: AccountInfo) {
    return !!(
      account &&
      account._account_id &&
      this.newAttentionSet?.has(account._account_id)
    );
  }

  computeNewAttention() {
    if (
      this.account?._account_id === undefined ||
      this.change === undefined ||
      this.includeComments === undefined ||
      this.draftCommentThreads === undefined
    ) {
      return;
    }
    // The draft comments are only relevant for the attention set as long as the
    // user actually plans to publish their drafts.
    const draftCommentThreads = this.includeComments
      ? this.draftCommentThreads
      : [];
    const hasVote = !!this.labelsChanged;
    const isOwner = this.isOwner(this.account, this.change);
    const isUploader = this.uploader?._account_id === this.account._account_id;
    this.attentionCcsCount = removeServiceUsers(this.ccs).length;
    this.currentAttentionSet = new Set(
      Object.keys(this.change.attention_set || {}).map(
        id => Number(id) as AccountId
      )
    );
    const newAttention = new Set(this.currentAttentionSet);
    if (this.change.status === ChangeStatus.NEW) {
      // Add everyone that the user is replying to in a comment thread.
      this.computeCommentAccounts(draftCommentThreads).forEach(id =>
        newAttention.add(id)
      );
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
        .filter(r => r._pendingAdd || (this.canBeStarted && isOwner))
        .filter(notIsReviewerAndHasDraftOrLabel)
        .forEach(r => newAttention.add((r as AccountInfo)._account_id!));
      // Add owner and uploader, if someone else replies.
      if (this.hasDrafts() || hasVote) {
        if (this.uploader?._account_id && !isUploader) {
          newAttention.add(this.uploader._account_id);
        }
        if (this.change.owner?._account_id && !isOwner) {
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
      .map(a => a._account_id)
      .filter(id => !!id);
    this.newAttentionSet = new Set(
      [...newAttention].filter(id => allAccountIds.includes(id))
    );
    this.attentionExpanded = this.computeShowAttentionTip(
      this.account,
      this.change.owner,
      this.currentAttentionSet,
      this.newAttentionSet
    );
  }

  computeShowAttentionTip(
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

  computeCommentAccounts(threads: CommentThread[]) {
    const crLabel = this.change?.labels?.[StandardLabels.CODE_REVIEW];
    const maxCrVoteAccountIds = getMaxAccounts(crLabel).map(a => a._account_id);
    const accountIds = new Set<AccountId>();
    threads.forEach(thread => {
      const unresolved = isUnresolved(thread);
      thread.comments.forEach(comment => {
        if (comment.author) {
          // A comment author must have an account_id.
          const authorId = comment.author._account_id!;
          const hasGivenMaxReviewVote = maxCrVoteAccountIds.includes(authorId);
          if (unresolved || !hasGivenMaxReviewVote) accountIds.add(authorId);
        }
      });
    });
    return accountIds;
  }

  computeShowNoAttentionUpdate() {
    return this.sendDisabled || this.computeNewAttentionAccounts().length === 0;
  }

  computeDoNotUpdateMessage() {
    if (!this.currentAttentionSet || !this.newAttentionSet) return '';
    if (
      this.sendDisabled ||
      areSetsEqual(this.currentAttentionSet, this.newAttentionSet)
    ) {
      return 'No changes to the attention set.';
    }
    if (containsAll(this.currentAttentionSet, this.newAttentionSet)) {
      return 'No additions to the attention set.';
    }
    this.reporting.error(
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
      .filter(account => !!account) as AccountInfo[];
  }

  findAccountById(accountId: AccountId) {
    return this.allAccounts().find(r => r._account_id === accountId);
  }

  allAccounts() {
    let allAccounts: (AccountInfoInput | GroupInfoInput)[] = [];
    if (this.change && this.change.owner) allAccounts.push(this.change.owner);
    if (this.uploader) allAccounts.push(this.uploader);
    if (this.reviewers) allAccounts = [...allAccounts, ...this.reviewers];
    if (this.ccs) allAccounts = [...allAccounts, ...this.ccs];
    return removeServiceUsers(allAccounts.filter(isAccount));
  }

  removeServiceUsers(accounts: AccountInfo[]) {
    return removeServiceUsers(accounts);
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
        if (entry._account_id === this.owner?._account_id) {
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
        return this.ccs.find(finder) === undefined;
      }
      return this.reviewers.find(finder) === undefined;
    };
  }

  cancelTapHandler(e: Event) {
    e.preventDefault();
    this.cancel();
  }

  cancel() {
    assertIsDefined(this.change, 'change');
    if (!this.owner) throw new Error('missing required owner property');
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
    queryAndAssert<GrTextarea>(this, 'gr-textarea').closeDropdown();
    this.reviewersList?.clearPendingRemovals();
    this.rebuildReviewerArrays();
  }

  saveClickHandler(e: Event) {
    e.preventDefault();
    if (!this.ccsList?.submitEntryText()) {
      // Do not proceed with the save if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    this.send(this.includeComments, false);
  }

  sendTapHandler(e: Event) {
    e.preventDefault();
    this.submit();
  }

  submit() {
    if (!this.ccsList?.submitEntryText()) {
      // Do not proceed with the send if there is an invalid email entry in
      // the text field of the CC entry.
      return;
    }
    if (this.sendDisabled) {
      fireAlert(this, EMPTY_REPLY_MESSAGE);
      return;
    }
    return this.send(this.includeComments, this.canBeStarted).catch(err => {
      this.dispatchEvent(
        new CustomEvent('show-error', {
          bubbles: true,
          composed: true,
          detail: {message: `Error submitting review ${err}`},
        })
      );
    });
  }

  saveReview(review: ReviewInput, errFn?: ErrorCallback) {
    assertIsDefined(this.change, 'change');
    assertIsDefined(this.patchNum, 'patchNum');
    return this.restApiService.saveChangeReview(
      this.change._number,
      this.patchNum,
      review,
      errFn
    );
  }

  pendingConfirmationUpdated(reviewer: RawAccountInput | null) {
    if (reviewer === null) {
      this.reviewerConfirmationOverlay?.close();
    } else {
      this.pendingConfirmationDetails =
        this.ccPendingConfirmation || this.reviewerPendingConfirmation;
      this.reviewerConfirmationOverlay?.open();
    }
  }

  confirmPendingReviewer() {
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
      new Error('confirmPendingReviewer called without pending confirm')
    );
  }

  cancelPendingReviewer() {
    this.ccPendingConfirmation = null;
    this.reviewerPendingConfirmation = null;

    const target = this.ccPendingConfirmation
      ? FocusTarget.CCS
      : FocusTarget.REVIEWERS;
    this.focusOn(target);
  }

  getStorageLocation(): StorageLocation {
    assertIsDefined(this.change, 'change');
    return {
      changeNum: this.change._number,
      patchNum: '@change',
      path: '@change',
    };
  }

  loadStoredDraft() {
    const draft = this.storage.getDraftComment(this.getStorageLocation());
    return draft?.message ?? '';
  }

  handleAccountTextEntry() {
    // When either of the account entries has input added to the autocomplete,
    // it should trigger the save button to enable/
    //
    // Note: if the text is removed, the save button will not get disabled.
    this.reviewersMutated = true;
  }

  draftChanged(oldDraft: string) {
    this.storeTask = debounce(
      this.storeTask,
      () => {
        if (!this.draft.length && oldDraft) {
          // If the draft has been modified to be empty, then erase the storage
          // entry.
          this.storage.eraseDraftComment(this.getStorageLocation());
        } else if (this.draft.length) {
          this.storage.setDraftComment(this.getStorageLocation(), this.draft);
        }
      },
      STORAGE_DEBOUNCE_INTERVAL_MS
    );
  }

  handleHeightChanged() {
    fireEvent(this, 'autogrow');
  }

  getLabelScores(): GrLabelScores {
    return this.labelScores || queryAndAssert(this, 'gr-label-scores');
  }

  _handleLabelsChanged() {
    this.labelsChanged =
      Object.keys(this.getLabelScores().getLabelValues(false)).length !== 0;
  }

  // To decouple account-list and reply dialog
  getAccountListCopy(list: (AccountInfo | GroupInfo)[]) {
    return list.slice();
  }

  handleReviewersChanged(e: ValueChangedEvent<(AccountInfo | GroupInfo)[]>) {
    this.reviewers = e.detail.value.slice();
    this.reviewersMutated = true;
  }

  handleCcsChanged(e: ValueChangedEvent<(AccountInfo | GroupInfo)[]>) {
    this.ccs = e.detail.value.slice();
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
    fireReload(this, true);
    this.cancel();
  }

  computeSendButtonLabel() {
    this.sendButtonLabel = this.canBeStarted
      ? ButtonLabels.SEND + ' and ' + ButtonLabels.START_REVIEW
      : ButtonLabels.SEND;
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

  computeSendButtonDisabled() {
    if (
      this.canBeStarted === undefined ||
      this.draftCommentThreads === undefined ||
      this.draft === undefined ||
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
      this.includeComments && this.draftCommentThreads.length > 0;
    return (
      !hasDrafts &&
      !this.draft.length &&
      !this.reviewersMutated &&
      !revotingOrNewVote
    );
  }

  computePatchSetWarning() {
    let str = `Patch ${this.patchNum} is not latest.`;
    if (this.labelsChanged) {
      str += ' Voting may have no effect.';
    }
    return str;
  }

  setPluginMessage(message: string) {
    this.pluginMessage = message;
  }

  sendDisabledChanged() {
    this.dispatchEvent(new CustomEvent('send-disabled-changed'));
  }

  getReviewerSuggestionsProvider(change?: ChangeInfo) {
    if (!change) return;
    const provider = new GrReviewerSuggestionsProvider(
      this.restApiService,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.REVIEWER,
      this.serverConfig,
      this.isLoggedIn,
      change._number
    );
    return provider;
  }

  getCcSuggestionsProvider(change?: ChangeInfo) {
    if (!change) return;
    const provider = new GrReviewerSuggestionsProvider(
      this.restApiService,
      SUGGESTIONS_PROVIDERS_USERS_TYPES.CC,
      this.serverConfig,
      this.isLoggedIn,
      change._number
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

  computeAllReviewers() {
    this.allReviewers = [...this.reviewers];
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-reply-dialog': GrReplyDialog;
  }
}
