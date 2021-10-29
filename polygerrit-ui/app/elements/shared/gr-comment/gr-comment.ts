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
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-button/gr-button';
import '../gr-dialog/gr-dialog';
import '../gr-formatted-text/gr-formatted-text';
import '../gr-icons/gr-icons';
import '../gr-overlay/gr-overlay';
import '../gr-textarea/gr-textarea';
import '../gr-tooltip-content/gr-tooltip-content';
import '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import '../gr-account-label/gr-account-label';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {getRootElement} from '../../../scripts/rootElement';
import {appContext} from '../../../services/app-context';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {GrTextarea} from '../gr-textarea/gr-textarea';
import {GrOverlay} from '../gr-overlay/gr-overlay';
import {
  AccountDetailInfo,
  BasePatchSetNum,
  CommentLinks,
  NumericChangeId,
  PatchSetNum,
  RepoName,
} from '../../../types/common';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {
  isDraft,
  isRobot,
  UIComment,
  UIDraft,
  UIRobot,
} from '../../../utils/comment-util';
import {
  OpenFixPreviewEventDetail,
  ValueChangedEvent,
} from '../../../types/events';
import {fire, fireAlert, fireEvent} from '../../../utils/event-util';
import {pluralize} from '../../../utils/string-util';
import {assertIsDefined} from '../../../utils/common-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {StorageLocation} from '../../../services/storage/gr-storage';
import {Key, Modifier} from '../../../utils/dom-util';
import {Interaction} from '../../../constants/reporting';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {account$, isAdmin$} from '../../../services/user/user-model';
import {ShortcutController} from '../../lit/shortcut-controller';
import {repoCommentLinks$} from '../../../services/config/config-model';
import {classMap} from 'lit/directives/class-map';
import {PluginApi} from '../../../api/plugin';

const STORAGE_DEBOUNCE_INTERVAL = 400;
const TOAST_DEBOUNCE_INTERVAL = 200;

const SAVED_MESSAGE = 'All changes saved';
const UNSAVED_MESSAGE = 'Unable to save draft';

const REPORT_CREATE_DRAFT = 'CreateDraftComment';
const REPORT_UPDATE_DRAFT = 'UpdateDraftComment';
const REPORT_DISCARD_DRAFT = 'DiscardDraftComment';

const FILE = 'FILE';

export const __testOnly_UNSAVED_MESSAGE = UNSAVED_MESSAGE;

export type Asdf = PluginApi & {
  url(): string;
};

/**
 * All candidates tips to show, will pick randomly.
 */
const RESPECTFUL_REVIEW_TIPS = [
  'Assume competence.',
  'Provide rationale or context.',
  'Consider how comments may be interpreted.',
  'Avoid harsh language.',
  'Make your comments specific and actionable.',
  'When disagreeing, explain the advantage of your approach.',
];

interface CommentOverlays {
  confirmDelete?: GrOverlay | null;
  confirmDiscard?: GrOverlay | null;
}

declare global {
  interface HTMLElementEventMap {
    'comment-editing-changed': CustomEvent<boolean>;
    'comment-edit': CustomEvent<CommentEventDetail>;
    'comment-save': CustomEvent<CommentEventDetail>;
    'comment-update': CustomEvent<CommentEventDetail>;
  }
}

export interface CommentEventDetail {
  patchNum?: PatchSetNum;
  comment?: UIComment;
}

@customElement('gr-comment')
export class GrComment extends LitElement {
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
   * Fired when this comment is edited.
   *
   * @event comment-edit
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

  @query('#editTextarea')
  textarea?: GrTextarea;

  @query('#container')
  container?: HTMLElement;

  @query('#resolvedCheckbox')
  resolvedCheckbox?: HTMLInputElement;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: String})
  projectName?: RepoName;

  @property({type: Object})
  comment?: UIComment;

  // TODO: Move this out of gr-comment. gr-comment should not have a comments
  // property. This is only used for hasHumanReply at the moment.
  @property({type: Array})
  comments?: UIComment[];

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean, reflect: true})
  collapsed = true;

  /* internal only, but used in css rules */
  @property({type: Boolean, reflect: true})
  disabled = false;

  /* internal only, but used in css rules */
  @property({type: Boolean, reflect: true})
  discarding = false;

  @state()
  editing = false;

  @state()
  commentLinks: CommentLinks = {};

  @property({type: Boolean})
  robotButtonDisabled = false;

  /* This is just what the editing textarea contains. */
  @property({type: String})
  messageText = '';

  /* Can probably be derived. */
  @property({type: Boolean})
  resolved = false;

  // TODO! Move this logic into the service such that this is shared across
  // instances.
  @state()
  numPendingDraftRequests: {number: number} = {number: 0};

  @property({type: Boolean})
  enableOverlay = false;

  /**
   * Property for storing references to overlay elements. When the overlays
   * are moved to getRootElement() to be shown they are no-longer
   * children, so they can't be queried along the tree, so they are stored
   * here.
   */
  @property({type: Object})
  overlays: CommentOverlays = {};

  @property({type: Boolean})
  showRespectfulTip = false;

  @property({type: Boolean})
  showPatchset = true;

  @property({type: String})
  respectfulReviewTip?: string;

  @property({type: Boolean})
  respectfulTipDismissed = false;

  @property({type: Boolean})
  unableToSave = false;

  @state()
  account?: AccountDetailInfo;

  @state()
  isAdmin = false;

  @property({type: Boolean})
  showPortedComment = false;

  // for testing only
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  xhrPromise?: Promise<any>;

  private readonly restApiService = appContext.restApiService;

  private readonly storage = appContext.storageService;

  private readonly reporting = appContext.reportingService;

  private readonly commentsService = appContext.commentsService;

  private readonly shortcuts = new ShortcutController(this);

  private fireUpdateTask?: DelayedTask;

  private storeTask?: DelayedTask;

  private draftToastTask?: DelayedTask;

  override connectedCallback() {
    super.connectedCallback();
    subscribe(this, account$, x => (this.account = x));
    subscribe(this, isAdmin$, x => (this.isAdmin = x));
    subscribe(this, repoCommentLinks$, x => (this.commentLinks = x));

    if (this.editing) {
      this.collapsed = false;
    } else if (this.comment) {
      this.collapsed = !!this.comment.collapsed;
    }
    this.shortcuts.addLocal({key: Key.ESC}, e => this.handleEsc(e));
    for (const key of ['s', Key.ENTER]) {
      for (const modifier of [Modifier.CTRL_KEY, Modifier.META_KEY]) {
        this.shortcuts.addLocal({key, modifiers: [modifier]}, e =>
          this.handleSaveKey(e)
        );
      }
    }
  }

  override disconnectedCallback() {
    this.fireUpdateTask?.cancel();
    this.storeTask?.cancel();
    this.draftToastTask?.cancel();
    if (this.textarea) this.textarea.closeDropdown();
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          font-family: var(--font-family);
          padding: var(--spacing-m);
        }
        :host([collapsed]) {
          padding: var(--spacing-s) var(--spacing-m);
        }
        :host([disabled]) {
          pointer-events: none;
        }
        :host([disabled]) .actions,
        :host([disabled]) .robotActions,
        :host([disabled]) .date {
          opacity: 0.5;
        }
        :host([discarding]) {
          display: none;
        }
        .body {
          padding-top: var(--spacing-m);
        }
        .header {
          align-items: center;
          cursor: pointer;
          display: flex;
        }
        .headerLeft > span {
          font-weight: var(--font-weight-bold);
        }
        .headerMiddle {
          color: var(--deemphasized-text-color);
          flex: 1;
          overflow: hidden;
        }
        .draftLabel,
        .draftTooltip {
          color: var(--deemphasized-text-color);
          display: inline;
        }
        .date {
          justify-content: flex-end;
          text-align: right;
          white-space: nowrap;
        }
        span.date {
          color: var(--deemphasized-text-color);
        }
        span.date:hover {
          text-decoration: underline;
        }
        .actions,
        .robotActions {
          display: flex;
          justify-content: flex-end;
          padding-top: 0;
        }
        .robotActions {
          /* Better than the negative margin would be to remove the gr-button
       * padding, but then we would also need to fix the buttons that are
       * inserted by plugins. :-/ */
          margin: 4px 0 -4px;
        }
        .action {
          margin-left: var(--spacing-l);
        }
        .rightActions {
          display: flex;
          justify-content: flex-end;
        }
        .rightActions gr-button {
          --gr-button-padding: 0 var(--spacing-s);
        }
        .editMessage {
          display: block;
          margin: var(--spacing-m) 0;
          width: 100%;
        }
        .show-hide {
          margin-left: var(--spacing-s);
        }
        .robotId {
          color: var(--deemphasized-text-color);
          margin-bottom: var(--spacing-m);
        }
        .robotRun {
          margin-left: var(--spacing-m);
        }
        .robotRunLink {
          margin-left: var(--spacing-m);
        }
        /* just for a11y */
        input.show-hide {
          display: none;
        }
        label.show-hide {
          cursor: pointer;
          display: block;
        }
        label.show-hide iron-icon {
          vertical-align: top;
        }
        :host([collapsed]) #container .body {
          padding-top: 0;
        }
        #container .collapsedContent {
          display: block;
          overflow: hidden;
          padding-left: var(--spacing-m);
          text-overflow: ellipsis;
          white-space: nowrap;
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
          --gr-button-text-color: var(--deemphasized-text-color);
          --gr-button-padding: 0;
        }

        /** Disable select for the caret and actions */
        .actions,
        .show-hide {
          -webkit-user-select: none;
          -moz-user-select: none;
          -ms-user-select: none;
          user-select: none;
        }

        .respectfulReviewTip {
          justify-content: space-between;
          display: flex;
          padding: var(--spacing-m);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          margin-bottom: var(--spacing-m);
        }
        .respectfulReviewTip div {
          display: flex;
        }
        .respectfulReviewTip div iron-icon {
          margin-right: var(--spacing-s);
        }
        .respectfulReviewTip a {
          white-space: nowrap;
          margin-right: var(--spacing-s);
          padding-left: var(--spacing-m);
          text-decoration: none;
        }
        .pointer {
          cursor: pointer;
        }
        .patchset-text {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-s);
        }
        .headerLeft gr-account-label {
          --account-max-length: 130px;
          width: 150px;
        }
        .headerLeft gr-account-label::part(gr-account-label-text) {
          font-weight: var(--font-weight-bold);
        }
        .draft gr-account-label {
          width: unset;
        }
        .portedMessage {
          margin: 0 var(--spacing-m);
        }
        .link-icon {
          cursor: pointer;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div id="container" class="container">
        <div
          class="header"
          id="header"
          @click="${() => (this.collapsed = !this.collapsed)}"
        >
          <div class="headerLeft">
            ${this.renderAuthor()} ${this.renderPortedCommentMessage()}
            ${this.renderDraftLabel()}
          </div>
          <div class="headerMiddle">${this.renderCollapsedContent()}</div>
          ${this.renderRunDetails()} ${this.renderDeleteButton()}
          ${this.renderPatchset()} ${this.renderDate()} ${this.renderToggle()}
        </div>
        <div class="body">
          ${this.renderRobotAuthor()} ${this.renderEditingTextarea()}
          ${this.renderRespectfulTip()} ${this.renderCommentMessage()}
          ${this.renderHumanActions()} ${this.renderRobotActions()}
        </div>
      </div>
      ${this.renderConfirmDialog()}
    `;
  }

  private renderAuthor() {
    if (isRobot(this.comment)) {
      const id = this.comment.robot_id;
      return html`<span class="robotName">${id}</span>`;
    }
    const classes = {draft: isDraft(this.comment)};
    return html`
      <gr-account-label
        .account="${this.comment?.author ?? this.account}"
        class="${classMap(classes)}"
        hideStatus
      >
      </gr-account-label>
    `;
  }

  private renderPortedCommentMessage() {
    if (!this.showPortedComment) return;
    if (!this.comment?.patch_set) return;
    return html`
      <a href="${this.getUrlForComment()}"
        ><span class="portedMessage" @click="${this.handlePortedMessageClick}"
          >From patchset ${this.comment?.patch_set}]]</span
        ></a
      >
    `;
  }

  private renderDraftLabel() {
    if (!isDraft(this.comment)) return;
    let label = 'DRAFT';
    let tooltip =
      'This draft is only visible to you. ' +
      "To publish drafts, click the 'Reply' or 'Start review' button " +
      "at the top of the change or press the 'a' key.";
    if (this.unableToSave) {
      label += ' (Failed to save)';
      tooltip = 'Unable to save draft. Please try to save again.';
    }
    return html`
      <gr-tooltip-content
        class="draftTooltip"
        has-tooltip
        title="${tooltip}"
        max-width="20em"
        show-icon
      >
        <span class="draftLabel">${label}</span>
      </gr-tooltip-content>
    `;
  }

  private renderCollapsedContent() {
    if (!this.collapsed) return;
    return html`
      <span class="collapsedContent">${this.comment?.message}</span>
    `;
  }

  private renderRunDetails() {
    if (!isRobot(this.comment)) return;
    if (!this.comment?.url || this.collapsed) return;
    return html`
      <div class="runIdMessage message">
        <div class="runIdInformation">
          <a class="robotRunLink" href="${this.comment.url}">
            <span class="robotRun link">Run Details</span>
          </a>
        </div>
      </div>
    `;
  }

  private renderDeleteButton() {
    if (!this.isAdmin || isDraft(this.comment) || isRobot(this.comment)) return;
    if (this.collapsed) return;
    return html`
      <gr-button
        id="deleteBtn"
        title="Delete Comment"
        link
        class="action delete"
        @click="${this.handleCommentDelete}"
      >
        <iron-icon id="icon" icon="gr-icons:delete"></iron-icon>
      </gr-button>
    `;
  }

  private renderPatchset() {
    if (!this.showPatchset) return;
    return html`
      <span class="patchset-text"> Patchset ${this.patchNum}</span>
    `;
  }

  private renderDate() {
    if (!this.comment?.updated || this.collapsed) return;
    return html`
      <span class="separator"></span>
      <span class="date" tabindex="0" @click="${this.handleAnchorClick}">
        <gr-date-formatter
          withTooltip
          .dateStr="${this.comment.updated}"
        ></gr-date-formatter>
      </span>
    `;
  }

  private renderToggle() {
    const icon = this.collapsed
      ? 'gr-icons:expand-more'
      : 'gr-icons:expand-less';
    const ariaLabel = this.collapsed ? 'Expand' : 'Collapse';
    return html`
      <div class="show-hide" tabindex="0">
        <label class="show-hide" aria-label="${ariaLabel}">
          <input
            type="checkbox"
            class="show-hide"
            ?checked="${this.collapsed}"
            @change="${() => (this.collapsed = !this.collapsed)}"
          />
          <iron-icon id="icon" icon="${icon}"></iron-icon>
        </label>
      </div>
    `;
  }

  private renderRobotAuthor() {
    if (!isRobot(this.comment) || this.collapsed) return;
    return html`<div class="robotId">${this.comment.author?.name}</div>`;
  }

  private renderEditingTextarea() {
    if (!this.editing || this.collapsed) return;
    return html`
      <gr-textarea
        id="editTextarea"
        class="editMessage"
        autocomplete="on"
        code=""
        ?disabled="${this.disabled}"
        rows="4"
        text="${this.messageText}"
        @text-changed="${(e: ValueChangedEvent) => {
          const oldValue = this.messageText;
          this.messageText = e.detail.value;
          this.messageTextChanged(this.messageText, oldValue);
        }}"
      ></gr-textarea>
    `;
  }

  private renderRespectfulTip() {
    if (!this.showRespectfulTip || this.respectfulTipDismissed) return;
    if (this.collapsed) return;
    return html`
      <div class="respectfulReviewTip">
        <div>
          <gr-tooltip-content
            has-tooltip
            title="Tips for respectful code reviews."
          >
            <iron-icon
              class="pointer"
              icon="gr-icons:lightbulb-outline"
            ></iron-icon>
          </gr-tooltip-content>
          ${this.respectfulReviewTip}
        </div>
        <div>
          <a
            tabindex="-1"
            @click="${this.onRespectfulReadMoreClick}"
            href="https://testing.googleblog.com/2019/11/code-health-respectful-reviews-useful.html"
            target="_blank"
          >
            Read more
          </a>
          <a
            tabindex="-1"
            class="close pointer"
            @click="${this.dismissRespectfulTip}"
          >
            Not helpful
          </a>
        </div>
      </div>
    `;
  }

  private renderCommentMessage() {
    if (this.collapsed || this.editing) return;
    return html`
      <!--The message class is needed to ensure selectability from
          gr-diff-selection.-->
      <gr-formatted-text
        class="message"
        .content="${this.comment?.message}"
        .config="${this.commentLinks}"
        ?noTrailingMargin="${!isDraft(this.comment)}"
      ></gr-formatted-text>
    `;
  }

  private renderCopyLinkIcon() {
    if (!this.comment?.in_reply_to && !this.comment?.id) return;
    return html`
      <iron-icon
        class="copy link-icon"
        @click="${this.handleCopyLink}"
        title="Copy link to this comment"
        icon="gr-icons:link"
        role="button"
        tabindex="0"
      >
      </iron-icon>
    `;
  }

  private renderHumanActions() {
    if (!this.account || isRobot(this.comment)) return;
    if (this.collapsed || !isDraft(this.comment)) return;
    return html`
      <div class="actions">
        <div class="action resolve">
          <label>
            <input
              type="checkbox"
              id="resolvedCheckbox"
              ?checked="${this.resolved}"
              @change="${this.handleToggleResolved}"
            />
            Resolved
          </label>
        </div>
        ${this.renderDraftActions()}
      </div>
    `;
  }

  private renderDraftActions() {
    if (!isDraft(this.comment)) return;
    return html`
      <div class="rightActions">
        ${this.renderCopyLinkIcon()} ${this.renderDiscardButton()}
        ${this.renderEditButton()} ${this.renderCancelButton()}
        ${this.renderSaveButton()}
      </div>
    `;
  }

  private renderDiscardButton() {
    if (this.editing) return;
    return html`<gr-button
      link
      class="action discard"
      @click="${this.handleDiscard}"
      >Discard</gr-button
    >`;
  }

  private renderEditButton() {
    if (this.editing) return;
    return html`<gr-button link class="action edit" @click="${this.handleEdit}"
      >Edit</gr-button
    >`;
  }

  private renderCancelButton() {
    if (!this.editing) return;
    return html`
      <gr-button link class="action cancel" @click="${this.handleCancel}"
        >Cancel</gr-button
      >
    `;
  }

  private renderSaveButton() {
    if (!this.editing && !this.unableToSave) return;
    return html`
      <gr-button
        link
        ?disabled="${this.computeSaveDisabled(
          this.messageText,
          this.comment,
          this.resolved
        )}"
        class="action save"
        @click="${this.handleSave}"
        >Save</gr-button
      >
    `;
  }

  private renderRobotActions() {
    if (!this.account || !isRobot(this.comment)) return;
    const endpoint = html`
      <gr-endpoint-decorator name="robot-comment-controls">
        <gr-endpoint-param name="comment" .value="${this.comment}">
        </gr-endpoint-param>
      </gr-endpoint-decorator>
    `;
    return html`
      <div class="robotActions">
        ${this.renderCopyLinkIcon()} ${endpoint} ${this.renderShowFixButton()}
        ${this.renderPleaseFixButton()}
      </div>
    `;
  }

  private renderShowFixButton() {
    if (!(this.comment as UIRobot)?.fix_suggestions) return;
    return html`
      <gr-button
        link
        secondary
        class="action show-fix"
        @click="${this.handleShowFix}"
      >
        Show Fix
      </gr-button>
    `;
  }

  private renderPleaseFixButton() {
    if (this.hasHumanReply()) return;
    return html`
      <gr-button
        link
        ?disabled="${this.robotButtonDisabled}"
        class="action fix"
        @click="${this.handleFix}"
      >
        Please Fix
      </gr-button>
    `;
  }

  private renderConfirmDialog() {
    if (!this.enableOverlay) return;
    return html`
      <gr-overlay id="confirmDeleteOverlay" with-backdrop>
        <gr-confirm-delete-comment-dialog
          id="confirmDeleteComment"
          @confirm="${this.handleConfirmDeleteComment}"
          @cancel="${this.handleCancelDeleteComment}"
        >
        </gr-confirm-delete-comment-dialog>
      </gr-overlay>
    `;
  }

  private getUrlForComment() {
    const comment = this.comment;
    if (!comment || !this.changeNum || !this.projectName) return '';
    if (!comment.id) throw new Error('comment must have an id');
    return GerritNav.getUrlForComment(
      this.changeNum as NumericChangeId,
      this.projectName,
      comment.id
    );
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('editing')) {
      this.onEditingChanged();
    }
  }

  override firstUpdated() {
    assertIsDefined(this.comment, 'comment');
    assertIsDefined(this.container, 'container element');
    this.container.classList.toggle('draft', isDraft(this.comment));
    this.resolved = !this.comment.unresolved;
    this.discarding = false;
    if (isDraft(this.comment) && !this.comment.id) {
      this.editing = true;
    }
    if (this.editing) {
      // TODO! This is weird. The thread should not be notified at this point.
      // It's a new draft/reply, notify.
      this.fireUpdate();
    }

    this.loadLocalDraft();
  }

  private handlePortedMessageClick() {
    assertIsDefined(this.comment, 'comment');
    this.reporting.reportInteraction('navigate-to-original-comment', {
      line: this.comment.line,
      range: this.comment.range,
    });
  }

  /** Set as a separate method so easy to stub. */
  private getRandomNum(min: number, max: number) {
    return Math.floor(Math.random() * (max - min) + min);
  }

  private dismissRespectfulTip() {
    this.respectfulTipDismissed = true;
    this.reporting.reportInteraction('respectful-tip-dismissed', {
      tip: this.respectfulReviewTip,
    });
    // add a 14-day delay to the tip cache
    this.storage.setRespectfulTipVisibility(/* delayDays= */ 14);
  }

  private onRespectfulReadMoreClick() {
    this.reporting.reportInteraction('respectful-read-more-clicked');
  }

  get confirmDeleteOverlay() {
    if (!this.overlays.confirmDelete) {
      this.enableOverlay = true;
      flush();
      this.overlays.confirmDelete = this.shadowRoot?.querySelector(
        '#confirmDeleteOverlay'
      ) as GrOverlay | null;
    }
    return this.overlays.confirmDelete;
  }

  get confirmDiscardOverlay() {
    if (!this.overlays.confirmDiscard) {
      this.enableOverlay = true;
      flush();
      this.overlays.confirmDiscard = this.shadowRoot?.querySelector(
        '#confirmDiscardOverlay'
      ) as GrOverlay | null;
    }
    return this.overlays.confirmDiscard;
  }

  private handleCopyLink() {
    fireEvent(this, 'copy-comment-link');
  }

  private save(opt_comment?: UIComment) {
    assertIsDefined(this.comment, 'comment');
    let comment = opt_comment;
    if (!comment) {
      comment = this.comment;
    }

    this.comment.message = this.messageText;
    this.editing = false;
    this.disabled = true;

    if (!this.messageText) {
      return this.discardDraft();
    }

    const details = this.commentDetailsForReporting();
    this.reporting.reportInteraction(Interaction.SAVE_COMMENT, details);
    this.xhrPromise = this.saveDraft(comment)
      .then(response => {
        this.disabled = false;
        if (!response.ok) {
          return;
        }

        this.eraseDraftCommentFromStorage();
        return this.restApiService.getResponseObject(response).then(obj => {
          const resComment = obj as unknown as UIDraft;
          if (!isDraft(this.comment)) throw new Error('Can only save drafts.');
          resComment.__draft = true;
          // Maintain the ephemeral draft ID for identification by other
          // elements.
          if (this.comment?.__draftID) {
            resComment.__draftID = this.comment.__draftID;
          }
          if (!resComment.patch_set) resComment.patch_set = this.patchNum;
          this.comment = resComment;
          const details = this.commentDetailsForReporting();
          this.reporting.reportInteraction(Interaction.COMMENT_SAVED, details);
          this.fireSave();
          return obj;
        });
      })
      .catch(err => {
        this.disabled = false;
        throw err;
      });

    return this.xhrPromise;
  }

  private commentDetailsForReporting() {
    return {
      id: this.comment?.id,
      message_length: this.comment?.message?.length,
      in_reply_to: this.comment?.in_reply_to,
      unresolved: this.comment?.unresolved,
      path_length: this.comment?.path?.length,
      line: this.comment?.range?.start_line ?? this.comment?.line,
    };
  }

  eraseDraftCommentFromStorage() {
    // Prevents a race condition in which removing the draft comment occurs
    // prior to it being saved.
    this.storeTask?.cancel();

    assertIsDefined(this.comment?.path, 'comment.path');
    assertIsDefined(this.changeNum, 'changeNum');
    this.storage.eraseDraftComment({
      changeNum: this.changeNum,
      patchNum: this.getPatchNum(),
      path: this.comment.path,
      line: this.comment.line,
      range: this.comment.range,
    });
  }

  // TODO: Move this out of gr-comment. gr-comment should not have a comments
  // property.
  private hasHumanReply() {
    if (!this.comment || !this.comments) return false;
    return this.comments.some(
      c => c.in_reply_to && c.in_reply_to === this.comment?.id && !isRobot(c)
    );
  }

  getEventPayload(): OpenFixPreviewEventDetail {
    return {comment: this.comment, patchNum: this.patchNum};
  }

  fireEdit() {
    if (this.comment) this.commentsService.editDraft(this.comment);
    fire(this, 'comment-edit', this.getEventPayload());
  }

  fireSave() {
    if (this.comment) this.commentsService.addDraft(this.comment);
    fire(this, 'comment-save', this.getEventPayload());
  }

  fireUpdate() {
    this.fireUpdateTask = debounce(this.fireUpdateTask, () => {
      fire(this, 'comment-update', this.getEventPayload());
    });
  }

  private onEditingChanged() {
    // visibility based on cache this will make sure we only and always show
    // a tip once every Math.max(a day, period between creating comments)
    const cachedVisibilityOfRespectfulTip =
      this.storage.getRespectfulTipVisibility();
    if (this.editing && !cachedVisibilityOfRespectfulTip) {
      // we still want to show the tip with a probability of 30%
      if (this.getRandomNum(0, 3) >= 1) return;
      this.showRespectfulTip = true;
      const randomIdx = this.getRandomNum(0, RESPECTFUL_REVIEW_TIPS.length);
      this.respectfulReviewTip = RESPECTFUL_REVIEW_TIPS[randomIdx];
      this.reporting.reportInteraction('respectful-tip-appeared', {
        tip: this.respectfulReviewTip,
      });
      // update cache
      this.storage.setRespectfulTipVisibility();
    }

    fire(this, 'comment-editing-changed', this.editing);
    this.fireUpdate();

    if (this.editing) {
      setTimeout(() => this.textarea?.putCursorAtEnd(), 1);
    }
  }

  computeSaveDisabled(
    draft: string,
    comment: UIComment | undefined,
    resolved?: boolean
  ) {
    // If resolved state has changed and a msg exists, save should be enabled.
    if (!comment || (comment.unresolved === resolved && draft)) {
      return false;
    }
    return !draft || draft.trim() === '';
  }

  handleSaveKey(e: Event) {
    if (
      !this.computeSaveDisabled(this.messageText, this.comment, this.resolved)
    ) {
      e.preventDefault();
      this.handleSave(e);
    }
  }

  handleEsc(e: Event) {
    if (!this.messageText.length) {
      e.preventDefault();
      this.handleCancel(e);
    }
  }

  // TODO! Do we need to react to comment message changes??
  /*
  @observe('comment.message')
  commentMessageChanged(message: string) {
    /*
     * Only overwrite the message text user has typed if there is no existing
     * text typed by the user. This prevents the bug where creating another
     * comment triggered a recomputation of comments and the text written by
     * the user was lost.
     *
    if (!this.messageText || !this.editing) this.messageText = message || '';
  }
  */

  messageTextChanged(_: string, oldValue: string) {
    assertIsDefined(this.comment, 'comment');
    // Only store comments that are being edited in local storage.
    if (this.comment.id && (!isDraft(this.comment) || !this.editing)) return;

    const patchNum = this.comment.patch_set
      ? this.comment.patch_set
      : this.getPatchNum();
    const {path, line, range} = this.comment;
    if (!path) return;
    this.storeTask = debounce(
      this.storeTask,
      () => {
        const message = this.messageText;
        if (this.changeNum === undefined) {
          throw new Error('undefined changeNum');
        }
        const commentLocation: StorageLocation = {
          changeNum: this.changeNum,
          patchNum,
          path,
          line,
          range,
        };

        if ((!message || !message.length) && oldValue) {
          // If the draft has been modified to be empty, then erase the storage
          // entry.
          this.storage.eraseDraftComment(commentLocation);
        } else {
          this.storage.setDraftComment(commentLocation, message);
        }
      },
      STORAGE_DEBOUNCE_INTERVAL
    );
  }

  handleAnchorClick(e: Event) {
    e.preventDefault();
    if (!this.comment) return;
    this.dispatchEvent(
      new CustomEvent('comment-anchor-tap', {
        bubbles: true,
        composed: true,
        detail: {
          number: this.comment.line || FILE,
          side: this.comment?.side,
        },
      })
    );
  }

  handleEdit(e: Event) {
    e.preventDefault();
    if (this.comment?.message) this.messageText = this.comment.message;
    this.editing = true;
    this.fireEdit();
    this.reporting.recordDraftInteraction();
  }

  handleSave(e: Event) {
    e.preventDefault();

    // Ignore saves started while already saving.
    if (this.disabled) return;
    const timingLabel = this.comment?.id
      ? REPORT_UPDATE_DRAFT
      : REPORT_CREATE_DRAFT;
    const timer = this.reporting.getTimer(timingLabel);
    return this.save().then(() => {
      timer.end({id: this.comment?.id});
    });
  }

  handleCancel(e: Event) {
    e.preventDefault();
    assertIsDefined(this.comment, 'comment');
    if (!this.comment.id) {
      // Ensures we update the discarded draft message before deleting the draft
      // TODO! Do we need to set this?
      // this.set('comment.message', this.messageText);
      this.fireDiscard();
    } else {
      this.commentsService.cancelDraft(this.comment);
      this.editing = false;
    }
  }

  fireDiscard() {
    if (this.comment) this.commentsService.deleteDraft(this.comment);
    this.fireUpdateTask?.cancel();
    this.dispatchEvent(
      new CustomEvent('comment-discard', {
        detail: this.getEventPayload(),
        composed: true,
        bubbles: true,
      })
    );
  }

  handleFix() {
    this.dispatchEvent(
      new CustomEvent('create-fix-comment', {
        bubbles: true,
        composed: true,
        detail: this.getEventPayload(),
      })
    );
  }

  handleShowFix() {
    this.dispatchEvent(
      new CustomEvent('open-fix-preview', {
        bubbles: true,
        composed: true,
        detail: this.getEventPayload(),
      })
    );
  }

  handleDiscard(e: Event) {
    e.preventDefault();
    this.reporting.recordDraftInteraction();

    this.discardDraft();
  }

  discardDraft() {
    if (!this.comment) return Promise.reject(new Error('undefined comment'));
    if (!isDraft(this.comment)) {
      return Promise.reject(new Error('Cannot discard a non-draft comment.'));
    }
    this.discarding = true;
    const timer = this.reporting.getTimer(REPORT_DISCARD_DRAFT);
    this.editing = false;
    this.disabled = true;
    this.eraseDraftCommentFromStorage();

    if (!this.comment.id) {
      this.disabled = false;
      this.fireDiscard();
      return Promise.resolve();
    }

    this.xhrPromise = this.deleteDraft(this.comment)
      .then(response => {
        this.disabled = false;
        if (!response.ok) {
          this.discarding = false;
        }
        timer.end({id: this.comment?.id});
        this.fireDiscard();
        return response;
      })
      .catch(err => {
        this.disabled = false;
        throw err;
      });

    return this.xhrPromise;
  }

  getSavingMessage(numPending: number, requestFailed?: boolean) {
    if (requestFailed) {
      return UNSAVED_MESSAGE;
    }
    if (numPending === 0) {
      return SAVED_MESSAGE;
    }
    return `Saving ${pluralize(numPending, 'draft')}...`;
  }

  showStartRequest() {
    const numPending = ++this.numPendingDraftRequests.number;
    this.updateRequestToast(numPending);
  }

  showEndRequest() {
    const numPending = --this.numPendingDraftRequests.number;
    this.updateRequestToast(numPending);
  }

  handleFailedDraftRequest() {
    this.numPendingDraftRequests.number--;

    // Cancel the debouncer so that error toasts from the error-manager will
    // not be overridden.
    this.draftToastTask?.cancel();
    this.updateRequestToast(
      this.numPendingDraftRequests.number,
      /* requestFailed=*/ true
    );
  }

  updateRequestToast(numPending: number, requestFailed?: boolean) {
    const message = this.getSavingMessage(numPending, requestFailed);
    this.draftToastTask = debounce(
      this.draftToastTask,
      () => {
        // Note: the event is fired on the body rather than this element because
        // this element may not be attached by the time this executes, in which
        // case the event would not bubble.
        fireAlert(document.body, message);
      },
      TOAST_DEBOUNCE_INTERVAL
    );
  }

  handleDraftFailure() {
    this.unableToSave = true;
    this.handleFailedDraftRequest();
  }

  saveDraft(draft?: UIComment) {
    if (!draft || this.changeNum === undefined || this.patchNum === undefined) {
      throw new Error('undefined draft or changeNum or patchNum');
    }
    this.showStartRequest();
    return this.restApiService
      .saveDiffDraft(this.changeNum, this.patchNum, draft)
      .then(result => {
        if (result.ok) {
          this.unableToSave = false;
          this.showEndRequest();
        } else {
          this.handleDraftFailure();
        }
        return result;
      })
      .catch(err => {
        this.handleDraftFailure();
        throw err;
      });
  }

  deleteDraft(draft: UIComment) {
    const changeNum = this.changeNum;
    const patchNum = this.patchNum;
    if (changeNum === undefined || patchNum === undefined) {
      throw new Error('undefined changeNum or patchNum');
    }
    fireAlert(this, 'Discarding draft...');
    const draftID = draft.id;
    if (!draftID) throw new Error('Missing id in comment draft.');
    return this.restApiService
      .deleteDiffDraft(changeNum, patchNum, {id: draftID})
      .then(result => {
        if (result.ok) {
          fire(this, 'show-alert', {
            message: 'Draft Discarded',
            action: 'Undo',
            callback: () =>
              this.commentsService.restoreDraft(changeNum, patchNum, draftID),
          });
        }
        return result;
      });
  }

  getPatchNum(): PatchSetNum {
    const isOnParent = this.comment?.side === 'PARENT';
    const patchNum = isOnParent ? ('PARENT' as BasePatchSetNum) : this.patchNum;
    if (patchNum === undefined) throw new Error('patchNum undefined');
    return patchNum;
  }

  loadLocalDraft() {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchNum, 'patchNum');
    assertIsDefined(this.comment, 'comment');

    if (
      !this.comment.path ||
      this.comment.message ||
      !isDraft(this.comment) ||
      !this.editing
    ) {
      return;
    }

    const draft = this.storage.getDraftComment({
      changeNum: this.changeNum,
      patchNum: this.getPatchNum(),
      path: this.comment.path,
      line: this.comment.line,
      range: this.comment.range,
    });

    if (draft) {
      this.messageText = draft.message || '';
    }
  }

  handleToggleResolved() {
    this.reporting.recordDraftInteraction();
    this.resolved = !this.resolved;
    // Modify payload instead of this.comment, as this.comment is passed from
    // the parent by ref.
    const payload = this.getEventPayload();
    if (!payload.comment) {
      throw new Error('comment not defined in payload');
    }
    assertIsDefined(this.resolvedCheckbox, 'resolvedCheckbox element');
    payload.comment.unresolved = !this.resolvedCheckbox.checked;
    this.dispatchEvent(
      new CustomEvent('comment-update', {
        detail: payload,
        composed: true,
        bubbles: true,
      })
    );
    if (!this.editing) {
      // Save the resolved state immediately.
      this.save(payload.comment);
    }
  }

  handleCommentDelete() {
    this.openOverlay(this.confirmDeleteOverlay);
  }

  openOverlay(overlay?: GrOverlay | null) {
    if (!overlay) {
      return Promise.reject(new Error('undefined overlay'));
    }
    getRootElement().appendChild(overlay);
    return overlay.open();
  }

  closeOverlay(overlay?: GrOverlay | null) {
    if (overlay) {
      getRootElement().removeChild(overlay);
      overlay.close();
    }
  }

  handleConfirmDeleteComment() {
    const dialog = this.confirmDeleteOverlay?.querySelector(
      '#confirmDeleteComment'
    ) as GrConfirmDeleteCommentDialog | null;
    if (!dialog || !dialog.message) {
      throw new Error('missing confirm delete dialog');
    }
    if (
      !this.comment ||
      !this.comment.id ||
      this.changeNum === undefined ||
      this.patchNum === undefined
    ) {
      throw new Error('undefined comment or id or changeNum or patchNum');
    }
    this.restApiService
      .deleteComment(
        this.changeNum,
        this.patchNum,
        this.comment.id,
        dialog.message
      )
      .then(newComment => {
        this.closeOverlay(this.confirmDeleteOverlay);
        this.comment = newComment;
      });
  }

  handleCancelDeleteComment() {
    this.closeOverlay(this.confirmDeleteOverlay);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment': GrComment;
  }
}
