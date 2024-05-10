/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-button/gr-button';
import '../gr-dialog/gr-dialog';
import '../gr-formatted-text/gr-formatted-text';
import '../gr-icon/gr-icon';
import '../gr-suggestion-textarea/gr-suggestion-textarea';
import '../gr-tooltip-content/gr-tooltip-content';
import '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import '../gr-account-label/gr-account-label';
import '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import '../gr-fix-suggestions/gr-fix-suggestions';
import {getAppContext} from '../../../services/app-context';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {provide, resolve} from '../../../models/dependency';
import {GrSuggestionTextarea} from '../gr-suggestion-textarea/gr-suggestion-textarea';
import {
  AccountDetailInfo,
  DraftInfo,
  NumericChangeId,
  RepoName,
  RobotCommentInfo,
  Comment,
  isRobot,
  isSaving,
  isError,
  isDraft,
  isNew,
  CommentInput,
} from '../../../types/common';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {
  convertToCommentInput,
  createUserFixSuggestion,
  getContentInCommentRange,
  getUserSuggestion,
  hasUserSuggestion,
  id,
  NEWLINE_PATTERN,
  USER_SUGGESTION_START_PATTERN,
} from '../../../utils/comment-util';
import {
  OpenFixPreviewEventDetail,
  ReplyToCommentEventDetail,
  ValueChangedEvent,
} from '../../../types/events';
import {fire} from '../../../utils/event-util';
import {assertIsDefined, assert, uuid} from '../../../utils/common-util';
import {Key, Modifier, whenVisible} from '../../../utils/dom-util';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {ShortcutController} from '../../lit/shortcut-controller';
import {classMap} from 'lit/directives/class-map.js';
import {FILE, LineNumber} from '../../../api/diff';
import {CommentSide, SpecialFilePath} from '../../../constants/constants';
import {Subject} from 'rxjs';
import {debounceTime} from 'rxjs/operators';
import {changeModelToken} from '../../../models/change/change-model';
import {
  ChangeInfo,
  FixSuggestionInfo,
  isBase64FileContent,
} from '../../../api/rest-api';
import {createDiffUrl} from '../../../models/views/change';
import {userModelToken} from '../../../models/user/user-model';
import {modalStyles} from '../../../styles/gr-modal-styles';
import {KnownExperimentId} from '../../../services/flags/flags';
import {pluginLoaderToken} from '../gr-js-api-interface/gr-plugin-loader';
import {
  CommentModel,
  commentModelToken,
} from '../gr-comment-model/gr-comment-model';
import {formStyles} from '../../../styles/form-styles';
import {Interaction} from '../../../constants/reporting';
import {Suggestion, SuggestionsProvider} from '../../../api/suggestions';
import {when} from 'lit/directives/when.js';
import {getDocUrl} from '../../../utils/url-util';
import {configModelToken} from '../../../models/config/config-model';
import {getFileExtension} from '../../../utils/file-util';
import {storageServiceToken} from '../../../services/storage/gr-storage_impl';
import {deepEqual} from '../../../utils/deep-util';
import {GrSuggestionDiffPreview} from '../gr-suggestion-diff-preview/gr-suggestion-diff-preview';
import {waitUntil} from '../../../utils/async-util';
import {AutocompleteCache} from '../../../utils/autocomplete-cache';

// visible for testing
export const AUTO_SAVE_DEBOUNCE_DELAY_MS = 2000;
export const GENERATE_SUGGESTION_DEBOUNCE_DELAY_MS = 500;
export const AUTOCOMPLETE_DEBOUNCE_DELAY_MS = 200;
export const ENABLE_GENERATE_SUGGESTION_STORAGE_KEY =
  'enableGenerateSuggestionStorageKeyForCommentWithId-';

declare global {
  interface HTMLElementEventMap {
    'comment-editing-changed': CustomEvent<CommentEditingChangedDetail>;
    'comment-unresolved-changed': ValueChangedEvent<boolean>;
    'comment-text-changed': ValueChangedEvent<string>;
    'comment-anchor-tap': CustomEvent<CommentAnchorTapEventDetail>;
    'apply-user-suggestion': CustomEvent;
  }
}

export interface CommentAnchorTapEventDetail {
  number: LineNumber;
  side?: CommentSide;
}

export interface CommentEditingChangedDetail {
  editing: boolean;
  path: string;
}

@customElement('gr-comment')
export class GrComment extends LitElement {
  /**
   * Fired when the parent thread component should create a reply.
   *
   * @event reply-to-comment
   */

  /**
   * Fired when the open fix preview action is triggered.
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
  textarea?: GrSuggestionTextarea;

  @query('#container')
  container?: HTMLElement;

  @query('#resolvedCheckbox')
  resolvedCheckbox?: HTMLInputElement;

  @query('#confirmDeleteModal')
  confirmDeleteModal?: HTMLDialogElement;

  @query('#confirmDeleteCommentDialog')
  confirmDeleteDialog?: GrConfirmDeleteCommentDialog;

  @query('#suggestionDiffPreview')
  suggestionDiffPreview?: GrSuggestionDiffPreview;

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
   * Hide the header for patchset level comments used in GrReplyDialog.
   */
  @property({type: Boolean, attribute: 'hide-header'})
  hideHeader = false;

  /**
   * This is the *current* (internal) collapsed state of the comment. Do not set
   * from the outside. Use `initiallyCollapsed` instead. This is just a
   * reflected property such that css rules can be based on it.
   */
  @property({type: Boolean, reflect: true})
  collapsed?: boolean;

  @property({type: Boolean, attribute: 'robot-button-disabled'})
  robotButtonDisabled = false;

  @property({type: String})
  messagePlaceholder?: string;

  // GrReplyDialog requires the patchset level comment to always remain
  // editable.
  @property({type: Boolean, attribute: 'permanent-editing-mode'})
  permanentEditingMode = false;

  // Whether to disable autosaving
  @property({type: Boolean})
  disableAutoSaving = false;

  @state()
  autoSaving?: Promise<DraftInfo>;

  @state()
  changeNum?: NumericChangeId;

  @state()
  editing = false;

  @state()
  repoName?: RepoName;

  /* The 'dirty' state of the comment.message, which will be saved on demand. */
  @state()
  messageText = '';

  /**
   * An hint for autocompleting the comment message from plugin suggestion
   * providers.
   */
  @state() autocompleteHint = '';

  readonly autocompleteCache = new AutocompleteCache();

  /* The 'dirty' state of !comment.unresolved, which will be saved on demand. */
  @state()
  unresolved = true;

  @state()
  generateSuggestion = true;

  @state()
  generatedSuggestion?: Suggestion;

  @state()
  generatedFixSuggestion: FixSuggestionInfo | undefined =
    this.comment?.fix_suggestions?.[0];

  @state()
  generatedSuggestionId?: string;

  @state()
  addedGeneratedSuggestion?: string;

  @state()
  suggestionsProvider?: SuggestionsProvider;

  @state()
  suggestionLoading = false;

  @property({type: Boolean, attribute: 'show-patchset'})
  showPatchset = false;

  @property({type: Boolean, attribute: 'show-ported-comment'})
  showPortedComment = false;

  @state()
  account?: AccountDetailInfo;

  @state()
  isAdmin = false;

  @state()
  isOwner = false;

  @state()
  commentedText?: string;

  @state() private docsBaseUrl = '';

  private readonly restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getPluginLoader = resolve(this, pluginLoaderToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  private readonly getStorage = resolve(this, storageServiceToken);

  private readonly flagsService = getAppContext().flagsService;

  private readonly shortcuts = new ShortcutController(this);

  private commentModel = new CommentModel(this.restApiService);

  /**
   * This is triggered when the user types into the editing textarea. We then
   * debounce it and call autoSave().
   */
  private autoSaveTrigger$ = new Subject();

  /**
   * This is triggered when the user types into the editing textarea. We then
   * debounce it and call generateSuggestEdit().
   */
  private generateSuggestionTrigger$ = new Subject();

  /**
   * This is triggered when the user types into the editing textarea. We then
   * debounce it and call autocompleteComment().
   */
  private autocompleteTrigger$ = new Subject();

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
    provide(this, commentModelToken, () => this.commentModel);
    // Allow the shortcuts to bubble up so that GrReplyDialog can respond to
    // them as well.
    this.shortcuts.addLocal({key: Key.ESC}, () => this.handleEsc(), {
      preventDefault: false,
    });
    for (const modifier of [Modifier.CTRL_KEY, Modifier.META_KEY]) {
      this.shortcuts.addLocal(
        {key: Key.ENTER, modifiers: [modifier]},
        e => {
          this.save();
          // We don't stop propagation for patchset comment
          // (this.permanentEditingMode = true), but we stop it for normal
          // comments. This prevents accidentally sending a reply when
          // editing/saving them in the reply dialog.
          if (!this.permanentEditingMode) {
            e.preventDefault();
            e.stopPropagation();
          }
        },
        {preventDefault: false}
      );
    }
    // For Ctrl+s add shorctut with preventDefault so that it does
    // not bubble up to the browser
    for (const modifier of [Modifier.CTRL_KEY, Modifier.META_KEY]) {
      this.shortcuts.addLocal({key: 's', modifiers: [modifier]}, () => {
        this.save();
      });
    }
    this.addEventListener('apply-user-suggestion', () => {
      this.handleAppliedFix();
    });
    this.addEventListener('open-user-suggest-preview', e => {
      this.handleShowFix(e.detail.code);
    });
    this.addEventListener('add-generated-suggestion', e => {
      this.handleAddGeneratedSuggestion(e.detail.code);
    });
    this.messagePlaceholder = 'Mention others with @';
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.account = x)
    );
    subscribe(
      this,
      () => this.getUserModel().isAdmin$,
      x => (this.isAdmin = x)
    );

    subscribe(
      this,
      () => this.getChangeModel().repo$,
      x => (this.repoName = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      x => (this.changeNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().isOwner$,
      x => (this.isOwner = x)
    );
    subscribe(
      this,
      () =>
        this.autoSaveTrigger$.pipe(debounceTime(AUTO_SAVE_DEBOUNCE_DELAY_MS)),
      () => {
        this.autoSave();
      }
    );
    subscribe(
      this,
      () => this.getConfigModel().docsBaseUrl$,
      docsBaseUrl => (this.docsBaseUrl = docsBaseUrl)
    );
    subscribe(
      this,
      () => this.getPluginLoader().pluginsModel.suggestionsPlugins$,
      // We currently support results from only 1 provider.
      suggestionsPlugins =>
        (this.suggestionsProvider = suggestionsPlugins?.[0]?.provider)
    );
    subscribe(
      this,
      () =>
        this.autocompleteTrigger$.pipe(
          debounceTime(AUTOCOMPLETE_DEBOUNCE_DELAY_MS)
        ),
      () => {
        this.autocompleteComment();
      }
    );
    if (
      this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT) ||
      this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT_V2)
    ) {
      subscribe(
        this,
        () =>
          this.generateSuggestionTrigger$.pipe(
            debounceTime(GENERATE_SUGGESTION_DEBOUNCE_DELAY_MS)
          ),
        () => {
          this.generateSuggestEdit();
        }
      );
      subscribe(
        this,
        () => this.getUserModel().preferences$,
        prefs => {
          if (
            this.generateSuggestion !==
            !!prefs.allow_suggest_code_while_commenting
          ) {
            this.generateSuggestion =
              !!prefs.allow_suggest_code_while_commenting;
          }
        }
      );
    }
  }

  override connectedCallback() {
    super.connectedCallback();
    if (this.comment?.id) {
      const generateSuggestionStoredContent =
        this.getStorage().getEditableContentItem(
          ENABLE_GENERATE_SUGGESTION_STORAGE_KEY + this.comment.id
        );
      if (generateSuggestionStoredContent?.message === 'false') {
        this.generateSuggestion = false;
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
      formStyles,
      sharedStyles,
      modalStyles,
      css`
        :host {
          display: block;
          font-family: var(--font-family);
          padding: var(--spacing-m);
        }
        :host([collapsed]) {
          padding: var(--spacing-s) var(--spacing-m);
        }
        :host([error]) {
          background-color: var(--error-background);
          border-radius: var(--border-radius);
        }
        .header {
          align-items: center;
          cursor: pointer;
          display: flex;
          padding-bottom: var(--spacing-m);
        }
        :host([collapsed]) .header {
          padding-bottom: 0px;
        }
        .headerLeft > span {
          font-weight: var(--font-weight-bold);
        }
        .headerMiddle {
          color: var(--deemphasized-text-color);
          flex: 1;
          overflow: hidden;
        }
        .draftTooltip {
          font-weight: var(--font-weight-bold);
          display: inline;
        }
        .draftTooltip gr-icon {
          color: var(--info-foreground);
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
          justify-content: space-between;
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
        .leftActions,
        .rightActions {
          display: flex;
          justify-content: flex-end;
        }
        .leftActions gr-button,
        .rightActions gr-button {
          --gr-button-padding: 0 var(--spacing-s);
        }
        .editMessage {
          display: block;
          margin-bottom: var(--spacing-m);
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
        label.show-hide gr-icon {
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
        .draft gr-formatted-text.message {
          display: block;
          margin-bottom: var(--spacing-m);
        }
        .portedMessage {
          margin: 0 var(--spacing-m);
        }
        .link-icon {
          margin-left: var(--spacing-m);
          cursor: pointer;
        }
        .suggestEdit {
          /** same height as header */
          --margin: calc(0px - var(--spacing-s));
          margin-right: var(--spacing-s);
        }
        .suggestEdit gr-icon {
          color: inherit;
          margin-right: var(--spacing-s);
        }
        .info {
          background-color: var(--info-background);
          padding: var(--spacing-l) var(--spacing-xl);
        }
        .info gr-icon {
          color: var(--selected-foreground);
          margin-right: var(--spacing-xl);
        }
        /* The basics of .loadingSpin are defined in shared styles. */
        .loadingSpin {
          width: calc(var(--line-height-normal) - 2px);
          height: calc(var(--line-height-normal) - 2px);
          display: inline-block;
          vertical-align: top;
          position: relative;
          /* Making up for the 2px reduced height above. */
          top: 1px;
        }
      `,
    ];
  }

  override render() {
    if (!this.comment) return;
    this.toggleAttribute('saving', isSaving(this.comment));
    this.toggleAttribute('error', isError(this.comment));
    const classes = {
      container: true,
      draft: isDraft(this.comment),
    };
    return html`
      <gr-endpoint-decorator name="comment">
        <gr-endpoint-param name="comment" .value=${this.comment}>
        </gr-endpoint-param>
        <gr-endpoint-param name="editing" .value=${this.editing}>
        </gr-endpoint-param>
        <gr-endpoint-param name="message" .value=${this.messageText}>
        </gr-endpoint-param>
        <gr-endpoint-param name="isDraft" .value=${isDraft(this.comment)}>
        </gr-endpoint-param>
        <div id="container" class=${classMap(classes)}>
          ${this.renderHeader()}
          <div class="body">
            ${this.renderRobotAuthor()} ${this.renderEditingTextarea()}
            ${this.renderCommentMessage()}
            <gr-endpoint-slot name="above-actions"></gr-endpoint-slot>
            ${this.renderHumanActions()} ${this.renderRobotActions()}
          </div>
          ${/* if this.editing */ this.renderGeneratedSuggestionPreview()}
          ${/* if !this.editing */ this.renderFixSuggestionPreview()}
        </div>
      </gr-endpoint-decorator>
      ${this.renderConfirmDialog()}
    `;
  }

  private renderHeader() {
    if (this.hideHeader) return nothing;
    return html`
      <div
        class="header"
        id="header"
        @click=${() => (this.collapsed = !this.collapsed)}
      >
        <div class="headerLeft">
          ${this.renderAuthor()} ${this.renderPortedCommentMessage()}
          ${this.renderDraftLabel()}
        </div>
        <div class="headerMiddle">${this.renderCollapsedContent()}</div>
        ${this.renderSuggestEditButton()} ${this.renderRunDetails()}
        ${this.renderDeleteButton()} ${this.renderPatchset()}
        ${this.renderSeparator()} ${this.renderDate()} ${this.renderToggle()}
      </div>
    `;
  }

  private renderAuthor() {
    if (isDraft(this.comment)) return;
    if (isRobot(this.comment)) {
      const id = this.comment.robot_id;
      return html`<span class="robotName">${id}</span>`;
    }
    return html`
      <gr-account-label .account=${this.comment?.author ?? this.account}>
      </gr-account-label>
    `;
  }

  private renderPortedCommentMessage() {
    if (!this.showPortedComment) return;
    if (!this.comment?.patch_set) return;
    return html`
      <a href=${this.getUrlForComment()}>
        <span class="portedMessage" @click=${this.handlePortedMessageClick}>
          From patchset ${this.comment?.patch_set}
        </span>
      </a>
    `;
  }

  private renderDraftLabel() {
    if (!isDraft(this.comment)) return;
    let label = 'Draft';
    let tooltip =
      'This draft is only visible to you. ' +
      "To publish drafts, click the 'Reply' or 'Start review' button " +
      "at the top of the change or press the 'a' key.";
    if (isError(this.comment)) {
      label += ' (Failed to save)';
      tooltip = 'Unable to save draft. Please try to save again.';
    }
    return html`
      <gr-tooltip-content
        class="draftTooltip"
        has-tooltip
        title=${tooltip}
        max-width="20em"
      >
        <gr-icon filled icon="rate_review"></gr-icon>
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
          <a class="robotRunLink" href=${this.comment.url}>
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
    if (!this.isAdmin || isDraft(this.comment) || isRobot(this.comment)) return;
    if (this.collapsed) return;
    return html`
      <gr-button
        id="deleteBtn"
        title="Delete Comment"
        link
        class="action delete"
        @click=${(e: MouseEvent) => {
          e.stopPropagation();
          this.openDeleteCommentModal();
        }}
      >
        <gr-icon id="icon" icon="delete" filled></gr-icon>
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

  private renderSeparator() {
    // This should match the condition of `renderPatchset()`.
    if (!this.showPatchset) return;
    // This should match the condition of `renderDate()`.
    if (this.collapsed) return;
    // Render separator, if both are present: patchset AND date.
    return html`<span class="separator"></span>`;
  }

  private renderDate() {
    if (this.collapsed) return;
    return html`
      <span class="date" tabindex="0" @click=${this.handleAnchorClick}>
        ${this.renderDateInner()}
      </span>
    `;
  }

  private renderDateInner() {
    if (isError(this.comment)) return 'Error';
    if (isSaving(this.comment) && !this.autoSaving) return 'Saving';
    if (isNew(this.comment)) return 'New';
    return html`
      <gr-date-formatter
        withTooltip
        .dateStr=${this.comment!.updated}
      ></gr-date-formatter>
    `;
  }

  private renderToggle() {
    const icon = this.collapsed ? 'expand_more' : 'expand_less';
    const ariaLabel = this.collapsed ? 'Expand' : 'Collapse';
    return html`
      <div class="show-hide" tabindex="0">
        <label class="show-hide" aria-label=${ariaLabel}>
          <input
            type="checkbox"
            class="show-hide"
            ?checked=${this.collapsed}
            @change=${() => (this.collapsed = !this.collapsed)}
          />
          <gr-icon icon=${icon} id="icon"></gr-icon>
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
      <gr-suggestion-textarea
        id="editTextarea"
        class="editMessage"
        autocomplete="on"
        code=""
        rows="4"
        .placeholder=${this.messagePlaceholder}
        text=${this.messageText}
        autocompleteHint=${this.autocompleteHint}
        @text-changed=${this.handleTextChanged}
      ></gr-suggestion-textarea>
    `;
  }

  private handleTextChanged(e: ValueChangedEvent) {
    const oldValue = this.messageText;
    const newValue = e.detail.value;
    if (oldValue === newValue) return;
    // TODO: This is causing a re-render of <gr-comment> on every key
    // press. Try to avoid always setting `this.messageText` or at least
    // debounce it. Most of the code can just inspect the current value
    // of the textare instead of needing a dedicated property.
    this.messageText = newValue;

    this.handleTextChangedForAutocomplete();
    this.autoSaveTrigger$.next();
    this.generateSuggestionTrigger$.next();
  }

  // visible for testing
  handleTextChangedForAutocomplete() {
    const cachedHint = this.autocompleteCache.get(this.messageText);
    if (cachedHint) {
      this.autocompleteHint = cachedHint;
    } else {
      this.autocompleteHint = '';
      this.autocompleteTrigger$.next();
    }
  }

  private renderCommentMessage() {
    if (this.collapsed || this.editing) return;

    return html`
      <!--The "message" class is needed to ensure selectability from
          gr-diff-selection.-->
      <gr-formatted-text
        class="message"
        .markdown=${true}
        .content=${this.comment?.message ?? ''}
      ></gr-formatted-text>
    `;
  }

  private renderCopyLinkIcon() {
    // Only show the icon when the thread contains a published comment.
    if (!this.comment?.in_reply_to && isDraft(this.comment)) return;
    if (this.editing) return;
    return html`
      <gr-icon
        icon="link"
        class="copy link-icon"
        @click=${this.handleCopyLink}
        title="Copy link to this comment"
        role="button"
        tabindex="0"
      ></gr-icon>
    `;
  }

  private renderHumanActions() {
    if (!this.account || isRobot(this.comment)) return;
    if (this.collapsed || !isDraft(this.comment)) return;
    return html`
      <div class="actions">
        <div class="leftActions">
          <div class="action resolve">
            <label>
              <input
                type="checkbox"
                id="resolvedCheckbox"
                .checked=${!this.unresolved}
                @change=${this.handleToggleResolved}
              />
              Resolved
            </label>
          </div>
          ${this.renderGenerateSuggestEditButton()}
        </div>
        ${this.renderDraftActions()}
      </div>
    `;
  }

  private renderDraftActions() {
    if (!isDraft(this.comment)) return;
    return html`
      <div class="rightActions">
        ${this.renderDiscardButton()} ${this.renderEditButton()}
        ${this.renderCancelButton()} ${this.renderSaveButton()}
        ${this.renderCopyLinkIcon()}
        <gr-endpoint-slot name="draft-actions-end"></gr-endpoint-slot>
      </div>
    `;
  }

  private renderSuggestEditButton() {
    if (
      !this.editing ||
      this.permanentEditingMode ||
      this.comment?.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS
    ) {
      return nothing;
    }
    assertIsDefined(this.comment, 'comment');
    if (hasUserSuggestion(this.comment)) return nothing;
    // TODO(milutin): remove this check once suggesting on commit message is
    // fixed. Currently diff line doesn't match commit message line, because
    // of metadata in diff, which aren't in content api request.
    if (this.comment.path === SpecialFilePath.COMMIT_MESSAGE) return nothing;
    if (this.isOwner) return nothing;
    return html`<gr-button
      link
      class="action suggestEdit"
      title="This button copies the text to make a suggestion"
      @click=${this.createSuggestEdit}
      ><gr-icon icon="edit" id="icon" filled></gr-icon> Suggest edit</gr-button
    >`;
  }

  private renderDiscardButton() {
    if (this.editing || this.permanentEditingMode) return;
    return html`<gr-button
      link
      ?disabled=${isSaving(this.comment) && !this.autoSaving}
      class="action discard"
      @click=${this.discard}
      >Discard</gr-button
    >`;
  }

  private renderEditButton() {
    if (this.editing) return;
    return html`<gr-button link class="action edit" @click=${this.edit}
      >Edit</gr-button
    >`;
  }

  private renderCancelButton() {
    if (!this.editing || this.permanentEditingMode) return;
    return html`
      <gr-button
        link
        ?disabled=${isSaving(this.comment) && !this.autoSaving}
        class="action cancel"
        @click=${this.cancel}
        >Cancel</gr-button
      >
    `;
  }

  private renderSaveButton() {
    if (!this.editing) return;
    return html`
      <gr-button
        link
        ?disabled=${this.isSaveDisabled()}
        class="action save"
        @click=${this.handleSaveButtonClicked}
        >${this.permanentEditingMode ? 'Preview' : 'Save'}</gr-button
      >
    `;
  }

  private renderFixSuggestionPreview() {
    if (
      !this.comment?.fix_suggestions ||
      this.editing ||
      isRobot(this.comment) ||
      this.collapsed
    )
      return nothing;
    return html`<gr-fix-suggestions
      .comment=${this.comment}
    ></gr-fix-suggestions>`;
  }

  // private but used in test
  showGeneratedSuggestion() {
    return (
      (this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT) ||
        this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT_V2)) &&
      this.suggestionsProvider &&
      this.editing &&
      !this.permanentEditingMode &&
      this.comment &&
      this.comment.path &&
      this.comment.path !== SpecialFilePath.PATCHSET_LEVEL_COMMENTS &&
      this.comment.path !== SpecialFilePath.COMMIT_MESSAGE &&
      (!this.suggestionsProvider.supportedFileExtensions ||
        this.suggestionsProvider.supportedFileExtensions.includes(
          getFileExtension(this.comment.path)
        )) &&
      this.comment === this.comments?.[0] && // Is first comment
      (this.comment.range || this.comment.line) && // Disabled for File comments
      !hasUserSuggestion(this.comment) &&
      this.getChangeModel().getChange()?.is_private !== true
    );
  }

  private renderGeneratedSuggestionPreview() {
    if (
      !this.editing ||
      !this.showGeneratedSuggestion() ||
      !this.generateSuggestion
    )
      return nothing;
    if (!isDraft(this.comment)) return nothing;

    if (this.generatedFixSuggestion) {
      return html`<gr-suggestion-diff-preview
        id="suggestionDiffPreview"
        .fixSuggestionInfo=${this.generatedFixSuggestion}
      ></gr-suggestion-diff-preview>`;
    } else if (this.generatedSuggestion) {
      return html`<gr-suggestion-diff-preview
        .showAddSuggestionButton=${true}
        .suggestion=${this.generatedSuggestion?.replacement}
        .uuid=${this.generatedSuggestionId}
      ></gr-suggestion-diff-preview>`;
    } else {
      return nothing;
    }
  }

  private renderGenerateSuggestEditButton() {
    if (!this.showGeneratedSuggestion()) {
      return nothing;
    }
    const tooltip =
      'Select to show a generated suggestion based on your comment for commented text. This suggestion can be inserted as a code block in your comment.';
    return html`
      <div class="action">
        <label title=${tooltip}>
          <input
            type="checkbox"
            id="generateSuggestCheckbox"
            ?checked=${this.generateSuggestion}
            @change=${() => {
              this.generateSuggestion = !this.generateSuggestion;
              if (this.comment?.id) {
                this.getStorage().setEditableContentItem(
                  ENABLE_GENERATE_SUGGESTION_STORAGE_KEY + this.comment.id,
                  this.generateSuggestion.toString()
                );
              }
              if (this.generateSuggestion) {
                this.generateSuggestionTrigger$.next();
              } else {
                if (
                  this.flagsService.isEnabled(
                    KnownExperimentId.ML_SUGGESTED_EDIT_V2
                  )
                ) {
                  this.generatedFixSuggestion = undefined;
                  this.autoSaveTrigger$.next();
                }
              }
              this.reporting.reportInteraction(
                this.generateSuggestion
                  ? Interaction.GENERATE_SUGGESTION_ENABLED
                  : Interaction.GENERATE_SUGGESTION_DISABLED
              );
            }}
          />
          ${this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT_V2)
            ? 'Attach AI-suggested fix'
            : 'Generate Suggestion'}
          ${when(
            this.suggestionLoading,
            () => html`<span class="loadingSpin"></span>`,
            () => html`${this.getNumberOfSuggestions()}`
          )}
        </label>
        <a
          href=${this.suggestionsProvider?.getDocumentationLink?.() ||
          getDocUrl(
            this.docsBaseUrl,
            'user-suggest-edits.html$_generate_suggestion'
          )}
          target="_blank"
          rel="noopener noreferrer"
        >
          <gr-icon
            icon="help"
            title="About Generated Suggested Edits"
          ></gr-icon>
        </a>
      </div>
    `;
  }

  private getNumberOfSuggestions() {
    if (!this.generateSuggestion) {
      return '';
    }
    if (this.generatedSuggestion || this.generatedFixSuggestion) {
      return '(1)';
    } else {
      return '(0)';
    }
  }

  private handleAddGeneratedSuggestion(code: string) {
    const addNewLine = this.messageText.length !== 0;
    this.addedGeneratedSuggestion = `${
      addNewLine ? '\n' : ''
    }${USER_SUGGESTION_START_PATTERN}${code}${'\n```'}`;
    this.messageText += this.addedGeneratedSuggestion;
  }

  private generateSuggestEdit() {
    if (this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT_V2)) {
      this.generateSuggestEdit_v2();
    } else if (
      this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT)
    ) {
      this.generateSuggestEdit_v1();
    }
  }

  private async generateSuggestEdit_v1() {
    const suggestionsProvider = this.suggestionsProvider;
    const changeInfo = this.getChangeModel().getChange();
    if (
      !suggestionsProvider?.suggestCode ||
      !this.showGeneratedSuggestion() ||
      !this.generateSuggestion ||
      !changeInfo ||
      !this.comment ||
      !this.comment.patch_set ||
      !this.comment.path ||
      this.messageText.length === 0
    )
      return;
    this.generatedSuggestionId = uuid();
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_REQUEST, {
      uuid: this.generatedSuggestionId,
      type: 'suggest-code',
      commentId: this.comment.id,
    });
    this.suggestionLoading = true;
    let suggestionResponse;
    try {
      suggestionResponse = await suggestionsProvider.suggestCode({
        prompt: this.messageText,
        changeInfo: changeInfo as ChangeInfo,
        patchsetNumber: this.comment?.patch_set,
        filePath: this.comment.path,
        range: this.comment.range,
        lineNumber: this.comment.line,
      });
    } finally {
      this.suggestionLoading = false;
    }

    if (!suggestionResponse) return;
    // TODO(milutin): The suggestionResponse can contain multiple suggestion
    // options. We pick the first one for now. In future we shouldn't ignore
    // other suggestions.
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_RESPONSE, {
      uuid: this.generatedSuggestionId,
      type: 'suggest-code',
      commentId: this.comment.id,
      response: suggestionResponse.responseCode,
      numSuggestions: suggestionResponse.suggestions.length,
      hasNewRange: suggestionResponse.suggestions?.[0]?.newRange !== undefined,
    });
    const suggestion = suggestionResponse.suggestions?.[0];
    if (!suggestion?.replacement) return;
    this.generatedSuggestion = suggestion;
  }

  private async generateSuggestEdit_v2() {
    const suggestionsProvider = this.suggestionsProvider;
    const changeInfo = this.getChangeModel().getChange();
    if (
      !suggestionsProvider?.suggestFix ||
      !this.showGeneratedSuggestion() ||
      !this.generateSuggestion ||
      !changeInfo ||
      !this.comment ||
      !this.comment.patch_set ||
      !this.comment.path ||
      this.messageText.length === 0
    )
      return;
    this.generatedSuggestionId = uuid();
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_REQUEST, {
      uuid: this.generatedSuggestionId,
      type: 'suggest-fix',
      commentId: this.comment.id,
    });
    this.suggestionLoading = true;
    let suggestionResponse;
    try {
      suggestionResponse = await suggestionsProvider.suggestFix({
        prompt: this.messageText,
        changeInfo: changeInfo as ChangeInfo,
        patchsetNumber: this.comment?.patch_set,
        filePath: this.comment.path,
        range: this.comment.range,
        lineNumber: this.comment.line,
      });
    } finally {
      this.suggestionLoading = false;
    }

    if (!suggestionResponse) return;
    // TODO(milutin): The suggestionResponse can contain multiple suggestion
    // options. We pick the first one for now. In future we shouldn't ignore
    // other suggestions.
    this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_RESPONSE, {
      uuid: this.generatedSuggestionId,
      type: 'suggest-fix',
      commentId: this.comment.id,
      response: suggestionResponse.responseCode,
      numSuggestions: suggestionResponse.fix_suggestions.length,
    });
    const suggestion = suggestionResponse.fix_suggestions?.[0];
    if (!suggestion?.replacements || suggestion.replacements.length === 0) {
      return;
    }
    this.generatedFixSuggestion = suggestion;
    try {
      await waitUntil(() => this.getFixSuggestions() !== undefined);
      this.autoSaveTrigger$.next();
    } catch (error) {
      // Error is ok in some cases like quick save by user.
      console.warn(error);
    }
  }

  private async autocompleteComment() {
    const enabled = this.flagsService.isEnabled(
      KnownExperimentId.COMMENT_AUTOCOMPLETION
    );
    const suggestionsProvider = this.suggestionsProvider;
    const change = this.getChangeModel().getChange();
    if (
      !enabled ||
      !suggestionsProvider?.autocompleteComment ||
      !change ||
      !this.comment?.patch_set ||
      !this.comment.path ||
      this.messageText.length === 0
    ) {
      return;
    }
    const commentText = this.messageText;
    const response = await suggestionsProvider.autocompleteComment({
      id: id(this.comment),
      commentText,
      changeInfo: change as ChangeInfo,
      patchsetNumber: this.comment?.patch_set,
      filePath: this.comment.path,
      range: this.comment.range,
      lineNumber: this.comment.line,
    });
    if (!response?.completion) return;
    // Note that we are setting for `commentText` and getting for `this.messageText`.
    this.autocompleteCache.set(commentText, response.completion);
    this.autocompleteHint = this.autocompleteCache.get(this.messageText) ?? '';
  }

  private renderRobotActions() {
    if (!this.account || !isRobot(this.comment)) return;
    const endpoint = html`
      <gr-endpoint-decorator name="robot-comment-controls">
        <gr-endpoint-param name="comment" .value=${this.comment}>
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
    const fix_suggestions = (this.comment as RobotCommentInfo)?.fix_suggestions;
    if (!fix_suggestions || fix_suggestions.length === 0) return;
    return html`
      <gr-button
        link
        secondary
        class="action show-fix"
        @click=${() => this.handleShowFix()}
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
        ?disabled=${this.robotButtonDisabled}
        class="action fix"
        @click=${this.handlePleaseFix}
      >
        Please Fix
      </gr-button>
    `;
  }

  private renderConfirmDialog() {
    return html`
      <dialog id="confirmDeleteModal" tabindex="-1">
        <gr-confirm-delete-comment-dialog
          id="confirmDeleteCommentDialog"
          @confirm=${this.handleConfirmDeleteComment}
          @cancel=${this.closeDeleteCommentModal}
        >
        </gr-confirm-delete-comment-dialog>
      </dialog>
    `;
  }

  private getUrlForComment() {
    if (!this.changeNum || !this.repoName || !this.comment?.id) return '';
    return createDiffUrl({
      changeNum: this.changeNum,
      repo: this.repoName,
      commentId: this.comment.id,
    });
  }

  private firstWillUpdateDone = false;

  firstWillUpdate() {
    if (this.firstWillUpdateDone) return;
    assertIsDefined(this.comment, 'comment');
    this.firstWillUpdateDone = true;
    this.unresolved = this.comment.unresolved ?? true;
    if (this.permanentEditingMode) {
      this.edit();
    }
    if (isDraft(this.comment)) {
      this.collapsed = false;
    } else {
      this.collapsed = !!this.initiallyCollapsed;
    }
  }

  override updated(changed: PropertyValues) {
    if (changed.has('editing')) {
      if (this.editing && !this.permanentEditingMode) {
        // Note that this is a bit fragile, because we are relying on the
        // comment to become visible soonish. If that does not happen, then we
        // will be waiting indefinitely and grab focus at some point in the
        // distant future.
        whenVisible(this, () => this.textarea?.putCursorAtEnd());
      }
    }
    if (
      changed.has('changeNum') ||
      changed.has('comment') ||
      changed.has('generatedSuggestion')
    ) {
      if (
        !this.changeNum ||
        !this.comment ||
        (!hasUserSuggestion(this.comment) && !this.generatedSuggestion)
      )
        return;
      (async () => {
        this.commentedText = await this.commentModel.getCommentedCode(
          this.comment,
          this.changeNum
        );
      })();
    }
  }

  override willUpdate(changed: PropertyValues) {
    this.firstWillUpdate();
    if (changed.has('comment')) {
      if (isDraft(this.comment) && isError(this.comment)) {
        this.edit();
      }
      if (this.comment) {
        this.commentModel.updateState({
          comment: this.comment,
        });
      }
    }
    if (changed.has('editing')) {
      this.onEditingChanged();
    }
    if (changed.has('unresolved')) {
      // The <gr-comment-thread> component wants to change its color based on
      // the (dirty) unresolved state, so let's notify it about changes.
      fire(this, 'comment-unresolved-changed', {value: this.unresolved});
    }
    if (changed.has('messageText')) {
      // GrReplyDialog updates it's state when text inside patchset level
      // comment changes.
      fire(this, 'comment-text-changed', {value: this.messageText});
    }
  }

  private handlePortedMessageClick() {
    assertIsDefined(this.comment, 'comment');
    this.reporting.reportInteraction('navigate-to-original-comment', {
      line: this.comment.line,
      range: this.comment.range,
    });
  }

  private handleCopyLink() {
    fire(this, 'copy-comment-link', {});
  }

  /** Enter editing mode. */
  edit() {
    assert(isDraft(this.comment), 'only drafts are editable');
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
  async createFixPreview(
    replacement?: string
  ): Promise<OpenFixPreviewEventDetail> {
    assertIsDefined(this.comment?.patch_set, 'comment.patch_set');
    assertIsDefined(this.comment?.path, 'comment.path');

    if (hasUserSuggestion(this.comment) || replacement) {
      replacement = replacement ?? getUserSuggestion(this.comment);
      assert(!!replacement, 'malformed user suggestion');
      let commentedCode = this.commentedText;
      if (!commentedCode) {
        commentedCode = await this.getCommentedCode();
      }

      return {
        fixSuggestions: createUserFixSuggestion(
          this.comment,
          commentedCode,
          replacement
        ),
        patchNum: this.comment.patch_set,
        onCloseFixPreviewCallbacks: [
          fixApplied => {
            if (fixApplied) this.handleAppliedFix();
          },
        ],
      };
    }
    if (
      isRobot(this.comment) &&
      this.comment.fix_suggestions &&
      this.comment.fix_suggestions.length > 0
    ) {
      const id = this.comment.robot_id;
      return {
        fixSuggestions: this.comment.fix_suggestions.map(s => {
          return {
            ...s,
            description: `${id ?? ''} - ${s.description ?? ''}`,
          };
        }),
        patchNum: this.comment.patch_set,
        onCloseFixPreviewCallbacks: [],
      };
    }
    throw new Error('unable to create preview fix event');
  }

  private onEditingChanged() {
    if (this.editing) {
      this.collapsed = false;
      this.messageText = this.comment?.message ?? '';
      this.unresolved = this.comment?.unresolved ?? true;
      if (!isError(this.comment) && !isSaving(this.comment)) {
        this.originalMessage = this.messageText;
        this.originalUnresolved = this.unresolved;
      }
    }

    // Parent components such as the reply dialog might be interested in whether
    // come of their child components are in editing mode.
    fire(this, 'comment-editing-changed', {
      editing: this.editing,
      path: this.comment?.path ?? '',
    });
  }

  // private, but visible for testing
  isSaveDisabled() {
    assertIsDefined(this.comment, 'comment');
    if (isSaving(this.comment) && !this.autoSaving) return true;
    return !this.messageText?.trimEnd();
  }

  override focus() {
    // Note that this may not work as intended, because the textarea is not
    // rendered yet.
    this.textarea?.focus();
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

  private async handleSaveButtonClicked() {
    await this.save();
    if (this.permanentEditingMode) {
      this.editing = !this.editing;
    }
  }

  private handlePleaseFix() {
    const message = this.comment?.message;
    assert(!!message, 'empty message');
    const quoted = message.replace(NEWLINE_PATTERN, '\n> ');
    const eventDetail: ReplyToCommentEventDetail = {
      content: `> ${quoted}\n\nPlease fix.`,
      userWantsToEdit: false,
      unresolved: true,
    };
    // Handled by <gr-comment-thread>.
    fire(this, 'reply-to-comment', eventDetail);
  }

  private handleAppliedFix() {
    const message = this.comment?.message;
    assert(!!message, 'empty message');
    const eventDetail: ReplyToCommentEventDetail = {
      content: 'Fix applied.',
      userWantsToEdit: false,
      unresolved: false,
    };
    // Handled by <gr-comment-thread>.
    fire(this, 'reply-to-comment', eventDetail);
  }

  private async handleShowFix(replacement?: string) {
    // Handled top-level in the diff and change view components.
    fire(this, 'open-fix-preview', await this.createFixPreview(replacement));
  }

  async createSuggestEdit(e: MouseEvent) {
    e.stopPropagation();
    const line = await this.getCommentedCode();
    const addNewLine = this.messageText.length !== 0;
    this.messageText += `${
      addNewLine ? '\n' : ''
    }${USER_SUGGESTION_START_PATTERN}${line}${'\n```'}`;
  }

  // TODO(milutin): Remove once feature flag is rollout and use only model
  async getCommentedCode() {
    assertIsDefined(this.comment, 'comment');
    assertIsDefined(this.changeNum, 'changeNum');
    const file = await this.restApiService.getFileContent(
      this.changeNum,
      this.comment.path!,
      this.comment.patch_set!
    );
    assert(
      !!file && isBase64FileContent(file) && !!file.content,
      'file content for comment not found'
    );
    const line = getContentInCommentRange(file.content, this.comment);
    assert(!!line, 'file content for comment not found');
    return line;
  }

  // private, but visible for testing
  cancel() {
    assertIsDefined(this.comment, 'comment');
    assert(isDraft(this.comment), 'only drafts are editable');
    this.messageText = this.originalMessage;
    this.unresolved = this.originalUnresolved;
    this.save();
  }

  async autoSave() {
    if (isSaving(this.comment) || this.autoSaving) return;
    if (!this.editing || !this.comment) return;
    if (this.disableAutoSaving) return;
    assert(isDraft(this.comment), 'only drafts are editable');
    const messageToSave = this.messageText.trimEnd();
    if (messageToSave === '') return;
    if (!this.somethingToSave()) return;

    try {
      this.autoSaving = this.rawSave({showToast: false});
      await this.autoSaving;
    } finally {
      this.autoSaving = undefined;
    }
  }

  async discard() {
    this.messageText = '';
    await this.save();
  }

  async convertToCommentInputAndOrDiscard(): Promise<CommentInput | undefined> {
    if (!this.somethingToSave() || !this.comment) return;
    const messageToSave = this.messageText.trimEnd();
    if (messageToSave === '') {
      await this.getCommentsModel().discardDraft(id(this.comment));
      return undefined;
    } else {
      return convertToCommentInput({
        ...this.comment,
        message: this.messageText.trimEnd(),
        unresolved: this.unresolved,
      });
    }
  }

  async save() {
    assert(isDraft(this.comment), 'only drafts are editable');
    // There is a minimal chance of `isSaving()` being false between iterations
    // of the below while loop. But this will be extremely rare and just lead
    // to a harmless assertion error. So let's not bother.
    if (isSaving(this.comment) && !this.autoSaving) return;

    if (!this.permanentEditingMode) {
      this.editing = false;
    }
    if (this.autoSaving) {
      this.comment = await this.autoSaving;
    }
    // Depending on whether `messageToSave` is empty we treat this either as
    // a discard or a save action.
    const messageToSave = this.messageText.trimEnd();
    if (messageToSave === '') {
      if (!this.permanentEditingMode || this.somethingToSave()) {
        await this.getCommentsModel().discardDraft(id(this.comment));
      }
    } else {
      // No need to make a backend call when nothing has changed.
      while (this.somethingToSave()) {
        this.trackGeneratedSuggestionEdit();
        this.comment = await this.rawSave({showToast: true});
        if (isError(this.comment)) return;
      }
    }
  }

  private somethingToSave() {
    if (!this.comment) return false;
    return (
      isError(this.comment) ||
      this.messageText.trimEnd() !== this.comment.message ||
      this.unresolved !== this.comment.unresolved ||
      this.isFixSuggestionChanged()
    );
  }

  /** For sharing between save() and autoSave(). */
  private rawSave(options: {showToast: boolean}) {
    assert(isDraft(this.comment), 'only drafts are editable');
    assert(!isSaving(this.comment), 'saving already in progress');
    const draft: DraftInfo = {
      ...this.comment,
      message: this.messageText.trimEnd(),
      unresolved: this.unresolved,
    };
    if (this.isFixSuggestionChanged()) {
      draft.fix_suggestions = this.getFixSuggestions();
    }
    return this.getCommentsModel().saveDraft(draft, options.showToast);
  }

  isFixSuggestionChanged(): boolean {
    // Check to not change fix suggestion when draft is not being edited only
    // when user quickly disable generating suggestions and click save
    if (!this.editing && this.generateSuggestion) return false;
    return !deepEqual(this.comment?.fix_suggestions, this.getFixSuggestions());
  }

  getFixSuggestions(): FixSuggestionInfo[] | undefined {
    if (!this.flagsService.isEnabled(KnownExperimentId.ML_SUGGESTED_EDIT_V2))
      return undefined;
    if (!this.generateSuggestion) return undefined;
    if (!this.generatedFixSuggestion) return undefined;
    // Disable fix suggestions when the comment already has a user suggestion
    if (this.comment && hasUserSuggestion(this.comment)) return undefined;
    // we ignore fixSuggestions until they are previewed.
    if (
      this.suggestionDiffPreview &&
      !this.suggestionDiffPreview?.previewed &&
      !this.suggestionLoading
    )
      return undefined;
    return [this.generatedFixSuggestion];
  }

  private handleToggleResolved() {
    this.unresolved = !this.unresolved;
    if (!this.editing) {
      // messageText is only assigned a value if the comment reaches editing
      // state, however it is possible that the user toggles the resolved state
      // without editing the comment in which case we assign the correct value
      // to messageText here
      this.messageText = this.comment?.message ?? '';
      this.save();
    }
  }

  private openDeleteCommentModal() {
    this.confirmDeleteModal?.showModal();
    whenVisible(this.confirmDeleteDialog!, () => {
      this.confirmDeleteDialog!.resetFocus();
    });
  }

  private closeDeleteCommentModal() {
    this.confirmDeleteModal?.close();
  }

  /**
   * Deleting a *published* comment is an admin feature. It means more than just
   * discarding a draft.
   */
  // private, but visible for testing
  async handleConfirmDeleteComment() {
    if (!this.confirmDeleteDialog || !this.confirmDeleteDialog.message) {
      throw new Error('missing confirm delete dialog');
    }
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.comment, 'comment');

    await this.getCommentsModel().deleteComment(
      this.changeNum,
      this.comment,
      this.confirmDeleteDialog.message
    );
    this.closeDeleteCommentModal();
  }

  private trackGeneratedSuggestionEdit() {
    const hasUserSuggestion = this.messageText.includes(
      USER_SUGGESTION_START_PATTERN
    );
    const wasGeneratedSuggestionEdited =
      this.addedGeneratedSuggestion &&
      hasUserSuggestion &&
      !this.messageText.includes(this.addedGeneratedSuggestion);
    if (wasGeneratedSuggestionEdited) {
      this.reporting.reportInteraction(Interaction.GENERATE_SUGGESTION_EDITED, {
        uuid: this.generatedSuggestionId,
        commentId: this.comment?.id ?? '',
      });
      this.addedGeneratedSuggestion = undefined;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment': GrComment;
  }
  interface HTMLElementEventMap {
    'copy-comment-link': CustomEvent<{}>;
  }
}
