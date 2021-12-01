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
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {GrTextarea} from '../gr-textarea/gr-textarea';
import {GrOverlay} from '../gr-overlay/gr-overlay';
import {
  AccountDetailInfo,
  CommentLinks,
  NumericChangeId,
  RepoName,
  RobotCommentInfo,
} from '../../../types/common';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {
  Comment,
  isDraftOrUnsaved,
  isRobot,
  isUnsaved,
} from '../../../utils/comment-util';
import {
  OpenFixPreviewEventDetail,
  ValueChangedEvent,
} from '../../../types/events';
import {fire, fireEvent} from '../../../utils/event-util';
import {assertIsDefined} from '../../../utils/common-util';
import {Key, Modifier} from '../../../utils/dom-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {ShortcutController} from '../../lit/shortcut-controller';
import {classMap} from 'lit/directives/class-map';
import {LineNumber} from '../../../api/diff';
import {CommentSide} from '../../../constants/constants';
import {getRandomInt} from '../../../utils/math-util';
import {Subject} from 'rxjs';
import {debounceTime} from 'rxjs/operators';

const UNSAVED_MESSAGE = 'Unable to save draft';

const FILE = 'FILE';

// visible for testing
export const AUTO_SAVE_DEBOUNCE_DELAY_MS = 2000;

export const __testOnly_UNSAVED_MESSAGE = UNSAVED_MESSAGE;

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

declare global {
  interface HTMLElementEventMap {
    'comment-editing-changed': CustomEvent<boolean>;
    'comment-unresolved-changed': CustomEvent<boolean>;
    'comment-anchor-tap': CustomEvent<CommentAnchorTapEventDetail>;
  }
}

export interface CommentAnchorTapEventDetail {
  number: LineNumber;
  side?: CommentSide;
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

  @query('#confirmDeleteOverlay')
  confirmDeleteOverlay?: GrOverlay;

  @property({type: Object})
  comment?: Comment;

  // TODO: Move this out of gr-comment. gr-comment should not have a comments
  // property. This is only used for hasHumanReply at the moment.
  @property({type: Array})
  comments?: Comment[];

  /**
   * Initial collapsed state of the comment.
   */
  @property({type: Boolean, attribute: 'initially-collapsed'})
  initiallyCollapsed?: boolean;

  /**
   * This is the *current* (internal) collapsed state of the comment. Do not set
   * from the outside. Use `initiallyCollapsed` instead. This is just a
   * reflected property such that css rules can be based on it.
   */
  @property({type: Boolean, reflect: true})
  collapsed?: boolean;

  @property({type: Boolean, attribute: 'robot-button-disabled'})
  robotButtonDisabled = false;

  /* internal only, but used in css rules */
  @property({type: Boolean, reflect: true})
  saving = false;

  /**
   * `saving` and `autoSaving` are separate and cannot be set at the same time.
   * `saving` affects the UI state (disabled buttons, etc.) and eventually
   * leaves editing mode, but `autoSaving` just happens in the background
   * without the user noticing.
   */
  @state()
  autoSaving?: Promise<void>;

  @state()
  changeNum?: NumericChangeId;

  @state()
  editing = false;

  @state()
  commentLinks: CommentLinks = {};

  @state()
  repoName?: RepoName;

  /* The 'dirty' state of the comment.message, which will be saved on demand. */
  @state()
  messageText = '';

  /* The 'dirty' state of !comment.unresolved, which will be saved on demand. */
  @state()
  unresolved = true;

  @property({type: Boolean})
  showConfirmDeleteOverlay = false;

  @property({type: Boolean})
  showRespectfulTip = false;

  @property({type: String})
  respectfulReviewTip?: string;

  @property({type: Boolean})
  respectfulTipDismissed = false;

  @property({type: Boolean})
  unableToSave = false;

  @property({type: Boolean, attribute: 'show-patchset'})
  showPatchset = false;

  @property({type: Boolean, attribute: 'show-ported-comment'})
  showPortedComment = false;

  @state()
  account?: AccountDetailInfo;

  @state()
  isAdmin = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly storage = getAppContext().storageService;

  private readonly reporting = getAppContext().reportingService;

  private readonly changeModel = getAppContext().changeModel;

  private readonly commentsModel = getAppContext().commentsModel;

  private readonly userModel = getAppContext().userModel;

  private readonly configModel = getAppContext().configModel;

  private readonly shortcuts = new ShortcutController(this);

  /**
   * This is triggered when the user types into the editing textarea. We then
   * debounce it and call autoSave().
   */
  private autoSaveTrigger$ = new Subject();

  /**
   * Set to the content of DraftInfo when entering editing mode.
   * Only used for "Cancel".
   */
  private originalMessage = '';

  /**
   * Set to the content of DraftInfo when entering editing mode.
   * Only used for "Cancel".
   */
  private originalUnresolved = false;

  constructor() {
    super();
    subscribe(this, this.userModel.account$, x => (this.account = x));
    subscribe(this, this.userModel.isAdmin$, x => (this.isAdmin = x));
    subscribe(
      this,
      this.configModel.repoCommentLinks$,
      x => (this.commentLinks = x)
    );
    subscribe(this, this.changeModel.repo$, x => (this.repoName = x));
    subscribe(this, this.changeModel.changeNum$, x => (this.changeNum = x));
    subscribe(
      this,
      this.autoSaveTrigger$.pipe(debounceTime(AUTO_SAVE_DEBOUNCE_DELAY_MS)),
      () => {
        this.autoSave();
      }
    );
    this.shortcuts.addLocal({key: Key.ESC}, () => this.handleEsc());
    for (const key of ['s', Key.ENTER]) {
      for (const modifier of [Modifier.CTRL_KEY, Modifier.META_KEY]) {
        this.shortcuts.addLocal({key, modifiers: [modifier]}, () => {
          this.save();
        });
      }
    }
  }

  override disconnectedCallback() {
    // Clean up emoji dropdown.
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
        :host([saving]) {
          pointer-events: none;
        }
        :host([saving]) .actions,
        :host([saving]) .robotActions,
        :host([saving]) .date {
          opacity: 0.5;
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
    if (isUnsaved(this.comment) && !this.editing) return;
    const classes = {container: true, draft: isDraftOrUnsaved(this.comment)};
    return html`
      <div id="container" class="${classMap(classes)}">
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
    const classes = {draft: isDraftOrUnsaved(this.comment)};
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
      <a href="${this.getUrlForComment()}">
        <span class="portedMessage" @click="${this.handlePortedMessageClick}">
          From patchset ${this.comment?.patch_set}
        </span>
      </a>
    `;
  }

  private renderDraftLabel() {
    if (!isDraftOrUnsaved(this.comment)) return;
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

  /**
   * Deleting a comment is an admin feature. It means more than just discarding
   * a draft. It is an action applied to published comments.
   */
  private renderDeleteButton() {
    if (
      !this.isAdmin ||
      isDraftOrUnsaved(this.comment) ||
      isRobot(this.comment)
    )
      return;
    if (this.collapsed) return;
    return html`
      <gr-button
        id="deleteBtn"
        title="Delete Comment"
        link
        class="action delete"
        @click="${this.openDeleteCommentOverlay}"
      >
        <iron-icon id="icon" icon="gr-icons:delete"></iron-icon>
      </gr-button>
    `;
  }

  private renderPatchset() {
    if (!this.showPatchset) return;
    assertIsDefined(this.comment?.patch_set, 'comment.patch_set');
    return html`
      <span class="patchset-text"> Patchset ${this.comment.patch_set}</span>
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
        ?disabled="${this.saving}"
        rows="4"
        text="${this.messageText}"
        @text-changed="${(e: ValueChangedEvent) => {
          // TODO: This is causing a re-render of <gr-comment> on every key
          // press. Try to avoid always setting `this.messageText` or at least
          // debounce it. Most of the code can just inspect the current value
          // of the textare instead of needing a dedicated property.
          this.messageText = e.detail.value;
          this.autoSaveTrigger$.next();
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
      <!--The "message" class is needed to ensure selectability from
          gr-diff-selection.-->
      <gr-formatted-text
        class="message"
        .content="${this.comment?.message}"
        .config="${this.commentLinks}"
        ?noTrailingMargin="${!isDraftOrUnsaved(this.comment)}"
      ></gr-formatted-text>
    `;
  }

  private renderCopyLinkIcon() {
    // Only show the icon when the thread contains a published comment.
    if (!this.comment?.in_reply_to && isDraftOrUnsaved(this.comment)) return;
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
    if (this.collapsed || !isDraftOrUnsaved(this.comment)) return;
    return html`
      <div class="actions">
        <div class="action resolve">
          <label>
            <input
              type="checkbox"
              id="resolvedCheckbox"
              ?checked="${!this.unresolved}"
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
    if (!isDraftOrUnsaved(this.comment)) return;
    return html`
      <div class="rightActions">
        ${this.autoSaving ? html`.&nbsp;&nbsp;` : ''}
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
      ?disabled="${this.saving}"
      class="action discard"
      @click="${this.discard}"
      >Discard</gr-button
    >`;
  }

  private renderEditButton() {
    if (this.editing) return;
    return html`<gr-button
      link
      ?disabled="${this.saving}"
      class="action edit"
      @click="${this.edit}"
      >Edit</gr-button
    >`;
  }

  private renderCancelButton() {
    if (!this.editing) return;
    return html`
      <gr-button
        link
        ?disabled="${this.saving}"
        class="action cancel"
        @click="${this.cancel}"
        >Cancel</gr-button
      >
    `;
  }

  private renderSaveButton() {
    if (!this.editing && !this.unableToSave) return;
    return html`
      <gr-button
        link
        ?disabled="${this.isSaveDisabled()}"
        class="action save"
        @click="${this.save}"
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
    if (!(this.comment as RobotCommentInfo)?.fix_suggestions) return;
    return html`
      <gr-button
        link
        secondary
        class="action show-fix"
        ?disabled="${this.saving}"
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
    if (!this.showConfirmDeleteOverlay) return;
    return html`
      <gr-overlay id="confirmDeleteOverlay" with-backdrop>
        <gr-confirm-delete-comment-dialog
          id="confirmDeleteComment"
          @confirm="${this.handleConfirmDeleteComment}"
          @cancel="${this.closeDeleteCommentOverlay}"
        >
        </gr-confirm-delete-comment-dialog>
      </gr-overlay>
    `;
  }

  private getUrlForComment() {
    const comment = this.comment;
    if (!comment || !this.changeNum || !this.repoName) return '';
    if (!comment.id) throw new Error('comment must have an id');
    return GerritNav.getUrlForComment(
      this.changeNum as NumericChangeId,
      this.repoName,
      comment.id
    );
  }

  private firstWillUpdateDone = false;

  firstWillUpdate() {
    if (this.firstWillUpdateDone) return;
    this.firstWillUpdateDone = true;

    assertIsDefined(this.comment, 'comment');
    this.unresolved = this.comment.unresolved ?? true;
    if (isUnsaved(this.comment)) this.editing = true;
    if (isDraftOrUnsaved(this.comment)) {
      this.collapsed = false;
    } else {
      this.collapsed = !!this.initiallyCollapsed;
    }
  }

  override willUpdate(changed: PropertyValues) {
    this.firstWillUpdate();
    if (changed.has('editing')) {
      this.onEditingChanged();
    }
    if (changed.has('unresolved')) {
      // The <gr-comment-thread> component wants to change its color based on
      // the (dirty) unresolved state, so let's notify it about changes.
      fire(this, 'comment-unresolved-changed', this.unresolved);
    }
  }

  private handlePortedMessageClick() {
    assertIsDefined(this.comment, 'comment');
    this.reporting.reportInteraction('navigate-to-original-comment', {
      line: this.comment.line,
      range: this.comment.range,
    });
  }

  // private, but visible for testing
  getRandomInt(from: number, to: number) {
    return getRandomInt(from, to);
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

  private handleCopyLink() {
    fireEvent(this, 'copy-comment-link');
  }

  /** Enter editing mode. */
  private edit() {
    if (!isDraftOrUnsaved(this.comment)) {
      throw new Error('Cannot edit published comment.');
    }
    if (this.editing) return;
    this.editing = true;
  }

  // TODO: Move this out of gr-comment. gr-comment should not have a comments
  // property.
  private hasHumanReply() {
    if (!this.comment || !this.comments) return false;
    return this.comments.some(
      c => c.in_reply_to && c.in_reply_to === this.comment?.id && !isRobot(c)
    );
  }

  // private, but visible for testing
  getEventPayload(): OpenFixPreviewEventDetail {
    assertIsDefined(this.comment?.patch_set, 'comment.patch_set');
    return {comment: this.comment, patchNum: this.comment.patch_set};
  }

  private onEditingChanged() {
    if (this.editing) {
      this.collapsed = false;
      this.messageText = this.comment?.message ?? '';
      this.unresolved = this.comment?.unresolved ?? true;
      this.originalMessage = this.messageText;
      this.originalUnresolved = this.unresolved;
      setTimeout(() => this.textarea?.putCursorAtEnd(), 1);
    }
    this.setRespectfulTip();

    // Parent components such as the reply dialog might be interested in whether
    // come of their child components are in editing mode.
    fire(this, 'comment-editing-changed', this.editing);
  }

  private setRespectfulTip() {
    // visibility based on cache this will make sure we only and always show
    // a tip once every Math.max(a day, period between creating comments)
    const cachedVisibilityOfRespectfulTip =
      this.storage.getRespectfulTipVisibility();
    if (this.editing && !cachedVisibilityOfRespectfulTip) {
      // we still want to show the tip with a probability of 33%
      if (this.getRandomInt(0, 2) >= 1) return;
      this.showRespectfulTip = true;
      const randomIdx = this.getRandomInt(0, RESPECTFUL_REVIEW_TIPS.length);
      this.respectfulReviewTip = RESPECTFUL_REVIEW_TIPS[randomIdx];
      this.reporting.reportInteraction('respectful-tip-appeared', {
        tip: this.respectfulReviewTip,
      });
      // update cache
      this.storage.setRespectfulTipVisibility();
    }
  }

  // private, but visible for testing
  isSaveDisabled() {
    assertIsDefined(this.comment, 'comment');
    if (this.saving) return true;
    if (this.comment.unresolved !== this.unresolved) return false;
    return !this.messageText?.trimEnd();
  }

  private handleEsc() {
    // vim users don't like ESC to cancel/discard, so only do this when the
    // comment text is empty.
    if (!this.messageText?.trimEnd()) this.cancel();
  }

  private handleAnchorClick() {
    assertIsDefined(this.comment, 'comment');
    fire(this, 'comment-anchor-tap', {
      number: this.comment.line || FILE,
      side: this.comment?.side,
    });
  }

  private handleFix() {
    // Handled by <gr-comment-thread>.
    fire(this, 'create-fix-comment', this.getEventPayload());
  }

  private handleShowFix() {
    // Handled top-level in the diff and change view components.
    fire(this, 'open-fix-preview', this.getEventPayload());
  }

  // private, but visible for testing
  cancel() {
    assertIsDefined(this.comment, 'comment');
    if (!isDraftOrUnsaved(this.comment)) {
      throw new Error('only unsaved and draft comments are editable');
    }
    this.messageText = this.originalMessage;
    this.unresolved = this.originalUnresolved;
    this.save();
  }

  async autoSave() {
    if (this.saving || this.autoSaving) return;
    if (!this.editing || !this.comment) return;
    if (!isDraftOrUnsaved(this.comment)) return;
    const messageToSave = this.messageText.trimEnd();
    if (messageToSave === '') return;
    if (messageToSave === this.comment.message) return;

    try {
      this.autoSaving = this.rawSave(messageToSave, {showToast: false});
      await this.autoSaving;
    } finally {
      this.autoSaving = undefined;
    }
  }

  async discard() {
    this.messageText = '';
    await this.save();
  }

  async save() {
    if (!isDraftOrUnsaved(this.comment)) throw new Error('not a draft');

    try {
      this.saving = true;
      this.unableToSave = false;
      if (this.autoSaving) await this.autoSaving;
      // Depending on whether `messageToSave` is empty we treat this either as
      // a discard or a save action.
      const messageToSave = this.messageText.trimEnd();
      if (messageToSave === '') {
        // Don't try to discard UnsavedInfo. Nothing to do then.
        if (this.comment.id) {
          await this.commentsModel.discardDraft(this.comment.id);
        }
      } else {
        // No need to make a backend call when nothing has changed.
        if (
          messageToSave !== this.comment?.message ||
          this.unresolved !== this.comment.unresolved
        ) {
          await this.rawSave(messageToSave, {showToast: true});
        }
      }
      this.editing = false;
    } catch (e) {
      this.unableToSave = true;
      throw e;
    } finally {
      this.saving = false;
    }
  }

  /** For sharing between save() and autoSave(). */
  private rawSave(message: string, options: {showToast: boolean}) {
    if (!isDraftOrUnsaved(this.comment)) throw new Error('not a draft');
    return this.commentsModel.saveDraft(
      {
        ...this.comment,
        message,
        unresolved: this.unresolved,
      },
      options.showToast
    );
  }

  private handleToggleResolved() {
    this.unresolved = !this.unresolved;
    if (!this.editing) this.save();
  }

  private async openDeleteCommentOverlay() {
    this.showConfirmDeleteOverlay = true;
    await this.updateComplete;
    await this.confirmDeleteOverlay?.open();
  }

  private closeDeleteCommentOverlay() {
    this.showConfirmDeleteOverlay = false;
    this.confirmDeleteOverlay?.remove();
    this.confirmDeleteOverlay?.close();
  }

  /**
   * Deleting a *published* comment is an admin feature. It means more than just
   * discarding a draft.
   *
   * TODO: Also move this into the comments-service.
   * TODO: Figure out a good reloading strategy when deleting was successful.
   *       `this.comment = newComment` does not seem sufficient.
   */
  // private, but visible for testing
  handleConfirmDeleteComment() {
    const dialog = this.confirmDeleteOverlay?.querySelector(
      '#confirmDeleteComment'
    ) as GrConfirmDeleteCommentDialog | null;
    if (!dialog || !dialog.message) {
      throw new Error('missing confirm delete dialog');
    }
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.comment, 'comment');
    assertIsDefined(this.comment.patch_set, 'comment.patch_set');
    if (isDraftOrUnsaved(this.comment)) {
      throw new Error('Admin deletion is only for published comments.');
    }
    this.restApiService
      .deleteComment(
        this.changeNum,
        this.comment.patch_set,
        this.comment.id,
        dialog.message
      )
      .then(newComment => {
        this.closeDeleteCommentOverlay();
        this.comment = newComment;
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment': GrComment;
  }
}
