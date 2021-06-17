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
import '../../../styles/shared-styles';
import '../gr-_lastComment/gr-comment';
import '../../diff/gr-diff/gr-diff';
import {dom, EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-comment-thread_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {
  computeDiffFromContext,
  isDraft,
  isRobot,
  sortComments,
  UIComment,
  UIDraft,
  UIRobot,
} from '../../../utils/comment-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {appContext} from '../../../services/app-context';
import {
  CommentSide,
  createDefaultDiffPrefs,
  Side,
  SpecialFilePath,
} from '../../../constants/constants';
import {computeDisplayPath} from '../../../utils/path-list-util';
import {computed, customElement, observe, property} from '@polymer/decorators';
import {
  AccountDetailInfo,
  CommentRange,
  ConfigInfo,
  NumericChangeId,
  PatchSetNum,
  RepoName,
  UrlEncodedCommentId,
} from '../../../types/common';
import {GrComment} from '../gr-comment/gr-comment';
import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import {CustomKeyboardEvent, OpenFixPreviewEvent} from '../../../types/events';
import {LineNumber, FILE} from '../../diff/gr-diff/gr-diff-line';
import {GrButton} from '../gr-button/gr-button';
import {KnownExperimentId} from '../../../services/flags/flags';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {RenderPreferences} from '../../../api/diff';
import {check, assertIsDefined} from '../../../utils/common-util';
import {fireAlert, waitForEventOnce} from '../../../utils/event-util';
import {GrSyntaxLayer} from '../../diff/gr-syntax-layer/gr-syntax-layer';
import {StorageLocation} from '../../../services/storage/gr-storage';
import {TokenHighlightLayer} from '../../diff/gr-diff-builder/token-highlight-layer';
import {anyLineTooLong} from '../../diff/gr-diff/gr-diff-utils';
import {getUserName} from '../../../utils/display-name-util';
import {getRootElement} from '../../../scripts/rootElement';
import {GrOverlay} from '../gr-overlay/gr-overlay';
import {GrDialog} from '../gr-dialog/gr-dialog';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {pluralize} from '../../../utils/string-util';

const UNRESOLVED_EXPAND_COUNT = 5;
const NEWLINE_PATTERN = /\n/g;

const REPORT_CREATE_DRAFT = 'CreateDraftComment';
const REPORT_UPDATE_DRAFT = 'UpdateDraftComment';
const REPORT_DISCARD_DRAFT = 'DiscardDraftComment';
const STORAGE_DEBOUNCE_INTERVAL = 400;

const TOAST_DEBOUNCE_INTERVAL = 200;

const SAVED_MESSAGE = 'All changes saved';
const UNSAVED_MESSAGE = 'Unable to save draft';

export const __testOnly_UNSAVED_MESSAGE = UNSAVED_MESSAGE;

export interface GrCommentThread {
  $: {
    replyBtn: GrButton;
    quoteBtn: GrButton;
    resolvedCheckbox: HTMLInputElement;
  };
}

interface CommentOverlays {
  confirmDelete?: GrOverlay | null;
  confirmDiscard?: GrOverlay | null;
}

@customElement('gr-comment-thread')
export class GrCommentThread extends KeyboardShortcutMixin(PolymerElement) {
  // KeyboardShortcutMixin Not used in this element rather other elements tests

  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the thread should be discarded.
   *
   * @event thread-discard
   */

  /**
   * Fired when a comment in the thread is permanently modified.
   *
   * @event thread-changed
   */

  /**
   * gr-comment-thread exposes the following attributes that allow a
   * diff widget like gr-diff to show the thread in the right location:
   *
   * line-num:
   *     1-based line number or 'FILE' if it refers to the entire file.
   *
   * diff-side:
   *     "left" or "right". These indicate which of the two diffed versions
   *     the comment relates to. In the case of unified diff, the left
   *     version is the one whose line number column is further to the left.
   *
   * range:
   *     The range of text that the comment refers to (start_line,
   *     start_character, end_line, end_character), serialized as JSON. If
   *     set, range's end_line will have the same value as line-num. Line
   *     numbers are 1-based, char numbers are 0-based. The start position
   *     (start_line, start_character) is inclusive, and the end position
   *     (end_line, end_character) is exclusive.
   */
  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Array})
  comments: UIComment[] = [];

  @property({type: Object, reflectToAttribute: true})
  range?: CommentRange;

  @property({type: Object})
  keyEventTarget: HTMLElement = document.body;

  @property({type: String, reflectToAttribute: true})
  diffSide?: Side;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: String})
  path: string | undefined;

  @property({type: String, observer: '_projectNameChanged'})
  projectName?: RepoName;

  @property({type: Boolean, notify: true, reflectToAttribute: true})
  hasDraft?: boolean;

  @property({type: Boolean})
  isOnParent = false;

  @property({type: Number})
  parentIndex: number | null = null;

  @property({
    type: String,
    notify: true,
    computed: '_computeRootId(comments.*)',
  })
  rootId?: UrlEncodedCommentId;

  @property({type: Boolean})
  showFilePath = false;

  @property({type: Object, reflectToAttribute: true})
  lineNum?: LineNumber;

  @property({type: Boolean, notify: true, reflectToAttribute: true})
  unresolved?: boolean;

  @property({type: Boolean})
  _showActions?: boolean;

  @property({type: Object})
  _lastComment?: UIComment;

  @property({type: Array})
  _orderedComments: UIComment[] = [];

  @property({type: Object})
  _projectConfig?: ConfigInfo;

  @property({type: Object})
  _prefs: DiffPreferencesInfo = createDefaultDiffPrefs();

  @property({type: Object})
  _renderPrefs: RenderPreferences = {
    hide_left_side: true,
    disable_context_control_buttons: true,
    show_file_comment_button: false,
    hide_line_length_indicator: true,
  };

  @property({type: Boolean, reflectToAttribute: true})
  isRobotComment = false;

  @property({type: Boolean})
  showFileName = true;

  @property({type: Boolean})
  showPortedComment = false;

  @property({type: Boolean})
  showPatchset = true;

  @property({type: Boolean})
  showCommentContext = false;

  @property({type: Object})
  _selfAccount?: AccountDetailInfo;

  @property({type: Boolean})
  resolved?: boolean;

  @property({type: Boolean})
  _unableToSave = false;

  /**
   * Property for storing references to overlay elements. When the overlays
   * are moved to getRootElement() to be shown they are no-longer
   * children, so they can't be queried along the tree, so they are stored
   * here.
   */
  @property({type: Object})
  _overlays: CommentOverlays = {};

  @property({type: Boolean})
  _enableOverlay = false;

  @property({type: String, observer: '_messageTextChanged'})
  _messageText = '';

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: Object})
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  _xhrPromise?: Promise<any>; // Used for testing.

  // Intentional to share the object across instances.
  @property({type: Object})
  _numPendingDraftRequests: {number: number} = {number: 0};

  get keyBindings() {
    return {
      'e shift+e': '_handleEKey',
    };
  }

  get confirmDeleteOverlay() {
    if (!this._overlays.confirmDelete) {
      this._enableOverlay = true;
      flush();
      this._overlays.confirmDelete = this.shadowRoot?.querySelector(
        '#confirmDeleteOverlay'
      ) as GrOverlay | null;
    }
    return this._overlays.confirmDelete;
  }

  get confirmDiscardOverlay() {
    if (!this._overlays.confirmDiscard) {
      this._enableOverlay = true;
      flush();
      this._overlays.confirmDiscard = this.shadowRoot?.querySelector(
        '#confirmDiscardOverlay'
      ) as GrOverlay | null;
    }
    return this._overlays.confirmDiscard;
  }

  reporting = appContext.reportingService;

  flagsService = appContext.flagsService;

  readonly storage = appContext.storageService;

  private readonly syntaxLayer = new GrSyntaxLayer();

  readonly restApiService = appContext.restApiService;

  private fireUpdateTask?: DelayedTask;

  private storeTask?: DelayedTask;

  private draftToastTask?: DelayedTask;

  constructor() {
    super();
    this.addEventListener('comment-update', e =>
      this._handleCommentUpdate(e as CustomEvent)
    );
  }

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this._getLoggedIn().then(loggedIn => {
      this._showActions = loggedIn;
    });
    this.restApiService.getDiffPreferences().then(prefs => {
      if (!prefs) return;
      this._prefs = {
        ...prefs,
        // set line_wrapping to true so that the context can take all the
        // remaining space after comment card has rendered
        line_wrapping: true,
      };
      this.syntaxLayer.setEnabled(!!prefs.syntax_highlighting);
    });
    this.restApiService.getAccount().then(account => {
      this._selfAccount = account;
    });
    this._setInitialExpandedState();
  }

  disconnectedCallback() {
    this.fireUpdateTask?.cancel();
    this.storeTask?.cancel();
    this.draftToastTask?.cancel();
    super.disconnectedCallback();
  }

  @computed('comments', 'path')
  get _diff() {
    if (this.comments === undefined || this.path === undefined) return;
    if (!this.comments[0]?.context_lines?.length) return;
    const diff = computeDiffFromContext(
      this.comments[0].context_lines,
      this.path,
      this.comments[0].source_content_type
    );
    if (!anyLineTooLong(diff)) {
      this.syntaxLayer.init(diff);
      waitForEventOnce(this, 'render').then(() => {
        this.syntaxLayer.process();
      });
    }
    return diff;
  }

  _shouldShowCommentContext(diff?: DiffInfo) {
    return this.showCommentContext && !!diff;
  }

  addOrEditDraft(lineNum?: LineNumber, rangeParam?: CommentRange) {
    const lastComment = this.comments[this.comments.length - 1] || {};
    if (isDraft(lastComment)) {
      const commentEl = this._commentElWithDraftID(
        lastComment.id || lastComment.__draftID
      );
      if (!commentEl) throw new Error('Failed to find draft.');
      commentEl.editing = true;

      // If the comment was collapsed, re-open it to make it clear which
      // actions are available.
      commentEl.collapsed = false;
    } else {
      const range = rangeParam
        ? rangeParam
        : lastComment
        ? lastComment.range
        : undefined;
      const unresolved = lastComment ? lastComment.unresolved : undefined;
      this.addDraft(lineNum, range, unresolved);
    }
  }

  addDraft(lineNum?: LineNumber, range?: CommentRange, unresolved?: boolean) {
    const draft = this._newDraft(lineNum, range);
    draft.__editing = true;
    draft.unresolved = unresolved === false ? unresolved : true;
    this.push('comments', draft);
  }

  fireRemoveSelf() {
    this.dispatchEvent(
      new CustomEvent('thread-discard', {
        detail: {rootId: this.rootId},
        bubbles: false,
      })
    );
  }

  _getDiffUrlForPath(
    projectName?: RepoName,
    changeNum?: NumericChangeId,
    path?: string,
    patchNum?: PatchSetNum
  ) {
    if (!changeNum || !projectName || !path) return undefined;
    if (isDraft(this.comments[0])) {
      return GerritNav.getUrlForDiffById(
        changeNum,
        projectName,
        path,
        patchNum
      );
    }
    const id = this.comments[0].id;
    if (!id) throw new Error('A published _lastComment is missing the id.');
    return GerritNav.getUrlForComment(changeNum, projectName, id);
  }

  getHighlightRange() {
    const comment = this.comments?.[0];
    if (!comment) return undefined;
    if (comment.range) return comment.range;
    if (comment.line) {
      return {
        start_line: comment.line,
        start_character: 0,
        end_line: comment.line,
        end_character: 0,
      };
    }
    return undefined;
  }

  _getLayers(diff?: DiffInfo) {
    if (!diff) return [];
    const layers = [];
    if (
      appContext.flagsService.isEnabled(KnownExperimentId.TOKEN_HIGHLIGHTING)
    ) {
      layers.push(new TokenHighlightLayer());
    }
    layers.push(this.syntaxLayer);
    return layers;
  }

  _getUrlForViewDiff(comments: UIComment[]) {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.projectName, 'projectName');
    check(comments.length > 0, 'comment not found');
    return GerritNav.getUrlForComment(
      this.changeNum,
      this.projectName,
      comments[0].id!
    );
  }

  _getDiffUrlForComment(
    projectName?: RepoName,
    changeNum?: NumericChangeId,
    path?: string,
    patchNum?: PatchSetNum
  ) {
    if (!projectName || !changeNum || !path) return undefined;
    if (
      (this.comments.length && this.comments[0].side === 'PARENT') ||
      isDraft(this.comments[0])
    ) {
      if (this.lineNum === 'LOST') throw new Error('invalid lineNum lost');
      return GerritNav.getUrlForDiffById(
        changeNum,
        projectName,
        path,
        patchNum,
        undefined,
        this.lineNum === FILE ? undefined : this.lineNum
      );
    }
    const id = this.comments[0].id;
    if (!id) throw new Error('A published comment is missing the id.');
    return GerritNav.getUrlForComment(changeNum, projectName, id);
  }

  _isPatchsetLevelComment(path: string) {
    return path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
  }

  _computeShowPortedComment(comment: UIComment) {
    if (this._orderedComments.length === 0) return false;
    return this.showPortedComment && comment.id === this._orderedComments[0].id;
  }

  _computeDisplayPath(path: string) {
    const displayPath = computeDisplayPath(path);
    if (displayPath === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
      return 'Patchset';
    }
    return displayPath;
  }

  _computeDisplayLine(lineNum?: LineNumber, range?: CommentRange) {
    if (lineNum === FILE) {
      if (this.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
        return '';
      }
      return FILE;
    }
    if (lineNum) return `#${lineNum}`;
    // If range is set, then lineNum equals the end line of the range.
    if (range) return `#${range.end_line}`;
    return '';
  }

  _getLoggedIn() {
    return this.restApiService.getLoggedIn();
  }

  _getUnresolvedLabel(unresolved?: boolean) {
    return unresolved ? 'Unresolved' : 'Resolved';
  }

  @observe('comments.*')
  _commentsChanged() {
    this._orderedComments = sortComments(this.comments);
    this.updateThreadProperties();
  }

  updateThreadProperties() {
    if (this._orderedComments.length) {
      this._lastComment = this._getLastComment();
      this.unresolved = this._lastComment.unresolved;
      this.hasDraft = isDraft(this._lastComment);
      this.isRobotComment = isRobot(this._lastComment);
    }
  }

  _shouldDisableAction(_showActions?: boolean, _lastComment?: UIComment) {
    return !_showActions || !_lastComment || isDraft(_lastComment);
  }

  _hideActions(_showActions?: boolean, _lastComment?: UIComment) {
    return (
      this._shouldDisableAction(_showActions, _lastComment) ||
      isRobot(_lastComment)
    );
  }

  hideHumanActions(_showActions?: boolean, _lastComment?: UIComment) {
    return !_showActions || isRobot(_lastComment);
  }

  _handleToggleResolved() {
    this.reporting.recordDraftInteraction();
    this.resolved = !this.resolved;
    // Modify payload instead of this.comment, as this.comment is passed from
    // the parent by ref.
    const payload = this._getEventPayload();
    if (!payload.comment) {
      throw new Error('comment not defined in payload');
    }
    payload.comment.unresolved = !this.$.resolvedCheckbox.checked;
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

  _getEventPayload(): OpenFixPreviewEvent {
    assertIsDefined(this._lastComment, '_lastComment');
    return {comment: this._lastComment, patchNum: this.patchNum};
  }

  _handleCancel(e: Event) {
    e.preventDefault();

    if (
      !this._lastComment?.message ||
      this._lastComment.message.trim().length === 0 ||
      !this._lastComment.id
    ) {
      this._fireDiscard();
      return;
    }
    this._messageText = this._lastComment.message;
    this.editing = false;
  }

  _handleEdit(e: Event) {
    e.preventDefault();
    if (this._lastComment?.message)
      this._messageText = this._lastComment.message;
    this.editing = true;
    this.reporting.recordDraftInteraction();
  }

  _handleFailedDraftRequest() {
    this._numPendingDraftRequests.number--;

    // Cancel the debouncer so that error toasts from the error-manager will
    // not be overridden.
    this.draftToastTask?.cancel();
    this._updateRequestToast(
      this._numPendingDraftRequests.number,
      /* requestFailed=*/ true
    );
  }

  _handleDraftFailure() {
    this.$.container.classList.add('unableToSave');
    this._unableToSave = true;
    this._handleFailedDraftRequest();
  }

  _handleSave(e: Event) {
    e.preventDefault();

    // Ignore saves started while already saving.
    if (this.disabled) {
      return;
    }
    const timingLabel = this._lastComment?.id
      ? REPORT_UPDATE_DRAFT
      : REPORT_CREATE_DRAFT;
    const timer = this.reporting.getTimer(timingLabel);
    this.set('_lastComment.__editing', false);
    return this.save().then(() => {
      timer.end();
    });
  }

  _getSavingMessage(numPending: number, requestFailed?: boolean) {
    if (requestFailed) {
      return UNSAVED_MESSAGE;
    }
    if (numPending === 0) {
      return SAVED_MESSAGE;
    }
    return `Saving ${pluralize(numPending, 'draft')}...`;
  }

  _updateRequestToast(numPending: number, requestFailed?: boolean) {
    const message = this._getSavingMessage(numPending, requestFailed);
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

  _showStartRequest() {
    const numPending = ++this._numPendingDraftRequests.number;
    this._updateRequestToast(numPending);
  }

  _showEndRequest() {
    const numPending = --this._numPendingDraftRequests.number;
    this._updateRequestToast(numPending);
  }

  _saveDraft(draft?: UIComment) {
    if (!draft || this.changeNum === undefined || this.patchNum === undefined) {
      throw new Error('undefined draft or changeNum or patchNum');
    }
    this._showStartRequest();
    return this.restApiService
      .saveDiffDraft(this.changeNum, this.patchNum, draft)
      .then(result => {
        if (result.ok) {
          // remove
          this._unableToSave = false;
          this.$.container.classList.remove('unableToSave');
          this._showEndRequest();
        } else {
          this._handleDraftFailure();
        }
        return result;
      })
      .catch(err => {
        this._handleDraftFailure();
        throw err;
      });
  }

  _deleteDraft(draft: UIComment) {
    if (this.changeNum === undefined || this.patchNum === undefined) {
      throw new Error('undefined changeNum or patchNum');
    }
    this._showStartRequest();
    if (!draft.id) throw new Error('Missing id in comment draft.');
    return this.restApiService
      .deleteDiffDraft(this.changeNum, this.patchNum, {id: draft.id})
      .then(result => {
        if (result.ok) {
          this._showEndRequest();
        } else {
          this._handleFailedDraftRequest();
        }
        return result;
      });
  }

  save(opt_comment?: UIComment) {
    let comment = opt_comment;
    if (!comment) {
      comment = this._lastComment;
    }

    this.set('comment.message', this._messageText);
    this.editing = false;
    this.disabled = true;

    if (!this._messageText) {
      return this._discardDraft();
    }

    this._xhrPromise = this._saveDraft(comment)
      .then(response => {
        this.disabled = false;
        if (!response.ok) {
          return;
        }

        this._eraseDraftComment();
        return this.restApiService.getResponseObject(response).then(obj => {
          const resComment = (obj as unknown) as UIDraft;
          if (!isDraft(this._lastComment))
            throw new Error('Can only save drafts.');
          resComment.__draft = true;
          // Maintain the ephemeral draft ID for identification by other
          // elements.
          if (this._lastComment?.__draftID) {
            resComment.__draftID = this._lastComment.__draftID;
          }
          this._lastComment = resComment;
          this._handleCommentSavedOrDiscarded();
          return obj;
        });
      })
      .catch(err => {
        this.disabled = false;
        throw err;
      });

    return this._xhrPromise;
  }

  _eraseDraftComment() {
    // Prevents a race condition in which removing the draft comment occurs
    // prior to it being saved.
    this.storeTask?.cancel();

    assertIsDefined(this._lastComment?.path, '_lastComment.path');
    assertIsDefined(this.changeNum, 'changeNum');
    this.storage.eraseDraftComment({
      changeNum: this.changeNum,
      patchNum: this._getPatchNum(),
      path: this._lastComment.path,
      line: this._lastComment.line,
      range: this._lastComment.range,
    });
  }

  _fireDiscard() {
    this.fireUpdateTask?.cancel();
    this.dispatchEvent(
      new CustomEvent('comment-discard', {
        detail: this._getEventPayload(),
        composed: true,
        bubbles: true,
      })
    );
  }

  _computeSaveDisabled(
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

  _handleSaveKey(e: Event) {
    if (
      !this._computeSaveDisabled(
        this._messageText,
        this._lastComment,
        this.resolved
      )
    ) {
      e.preventDefault();
      this._handleSave(e);
    }
  }

  _handleEsc(e: Event) {
    if (!this._messageText.length) {
      e.preventDefault();
      this._handleCancel(e);
    }
  }

  _handleDiscard(e: Event) {
    e.preventDefault();
    this.reporting.recordDraftInteraction();

    if (!this._messageText) {
      this._discardDraft();
      return;
    }

    this._openOverlay(this.confirmDiscardOverlay).then(() => {
      const dialog = this.confirmDiscardOverlay?.querySelector(
        '#confirmDiscardDialog'
      ) as GrDialog | null;
      if (dialog) dialog.resetFocus();
    });
  }

  _messageTextChanged(_: string, oldValue: string) {
    if (!this._lastComment || (this._lastComment && this._lastComment.id)) {
      return;
    }

    const patchNum = this._lastComment.patch_set
      ? this._lastComment.patch_set
      : this._getPatchNum();
    const {path, line, range} = this._lastComment;
    if (path) {
      this.storeTask = debounce(
        this.storeTask,
        () => {
          const message = this._messageText;
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
  }

  _handleConfirmDiscard(e: Event) {
    e.preventDefault();
    const timer = this.reporting.getTimer(REPORT_DISCARD_DRAFT);
    this._closeConfirmDiscardOverlay();
    return this._discardDraft().then(() => {
      timer.end();
    });
  }

  _discardDraft() {
    if (!this._lastComment)
      return Promise.reject(new Error('undefined _lastComment'));
    if (!isDraft(this._lastComment)) {
      return Promise.reject(
        new Error('Cannot discard a non-draft _lastComment.')
      );
    }
    this.discarding = true;
    this.editing = false;
    this.disabled = true;
    this._eraseDraftComment();

    if (!this._lastComment.id) {
      this.disabled = false;
      this._fireDiscard();
      return Promise.resolve();
    }

    this._xhrPromise = this._deleteDraft(this._lastComment)
      .then(response => {
        this.disabled = false;
        if (!response.ok) {
          this.discarding = false;
        }

        this._fireDiscard();
        return response;
      })
      .catch(err => {
        this.disabled = false;
        throw err;
      });

    return this._xhrPromise;
  }

  _closeConfirmDiscardOverlay() {
    this._closeOverlay(this.confirmDiscardOverlay);
  }

  _openOverlay(overlay?: GrOverlay | null) {
    if (!overlay) {
      return Promise.reject(new Error('undefined overlay'));
    }
    getRootElement().appendChild(overlay);
    return overlay.open();
  }

  _getLastComment() {
    return this._orderedComments[this._orderedComments.length - 1] || {};
  }

  _handleEKey(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }

    // Don’t preventDefault in this case because it will render the event
    // useless for other handlers (other gr-comment-thread elements).
    if (e.detail.keyboardEvent?.shiftKey) {
      this._expandCollapseComments(true);
    } else {
      if (this.modifierPressed(e)) {
        return;
      }
      this._expandCollapseComments(false);
    }
  }

  _expandCollapseComments(actionIsCollapse: boolean) {
    const comments = this.root?.querySelectorAll('gr-comment');
    if (!comments) return;
    for (const comment of comments) {
      comment.collapsed = actionIsCollapse;
    }
  }

  /**
   * Sets the initial state of the comment thread.
   * Expands the thread if one of the following is true:
   * - last {UNRESOLVED_EXPAND_COUNT} comments expanded by default if the
   * thread is unresolved,
   * - it's a robot comment.
   */
  _setInitialExpandedState() {
    if (this._orderedComments) {
      for (let i = 0; i < this._orderedComments.length; i++) {
        const comment = this._orderedComments[i];
        const isRobotComment = !!(comment as UIRobot).robot_id;
        // False if it's an unresolved comment under UNRESOLVED_EXPAND_COUNT.
        const resolvedThread =
          !this.unresolved ||
          this._orderedComments.length - i - 1 >= UNRESOLVED_EXPAND_COUNT;
        if (comment.collapsed === undefined) {
          comment.collapsed = !isRobotComment && resolvedThread;
        }
      }
    }
  }

  _createReplyComment(
    content?: string,
    isEditing?: boolean,
    unresolved?: boolean
  ) {
    this.reporting.recordDraftInteraction();
    const id = this._orderedComments[this._orderedComments.length - 1].id;
    if (!id) throw new Error('Cannot reply to comment without id.');
    const reply = this._newReply(id, content, unresolved);

    if (isEditing) {
      reply.__editing = true;
    }

    this.push('comments', reply);

    if (!isEditing) {
      // Allow the reply to render in the dom-repeat.
      setTimeout(() => {
        const commentEl = this._commentElWithDraftID(reply.__draftID);
        if (commentEl) commentEl.save();
      }, 1);
    }
  }

  _isDraft(comment: UIComment) {
    return isDraft(comment);
  }

  _processCommentReply(quote?: boolean) {
    const comment = this._lastComment;
    if (!comment) throw new Error('Failed to find last comment.');
    let content = undefined;
    if (quote) {
      const msg = comment.message;
      if (!msg) throw new Error('Quoting empty comment.');
      content = '> ' + msg.replace(NEWLINE_PATTERN, '\n> ') + '\n\n';
    }
    this._createReplyComment(content, true, comment.unresolved);
  }

  _handleCommentReply() {
    this._processCommentReply();
  }

  _handleCommentQuote() {
    this._processCommentReply(true);
  }

  _handleCommentAck() {
    this._createReplyComment('Ack', false, false);
  }

  _handleCommentDone() {
    this._createReplyComment('Done', false, false);
  }

  _handleCommentFix(e: CustomEvent) {
    const comment = e.detail.comment;
    const msg = comment.message;
    const quoted = msg.replace(NEWLINE_PATTERN, '\n> ') as string;
    const quoteStr = '> ' + quoted + '\n\n';
    const response = quoteStr + 'Please fix.';
    this._createReplyComment(response, false, true);
  }

  _commentElWithDraftID(id?: string): GrComment | null {
    if (!id) return null;
    const els = this.root?.querySelectorAll('gr-comment');
    if (!els) return null;
    for (const el of els) {
      const c = el.comment;
      if (isRobot(c)) continue;
      if (c?.id === id || (isDraft(c) && c?.__draftID === id)) return el;
    }
    return null;
  }

  _newReply(
    inReplyTo: UrlEncodedCommentId,
    message?: string,
    unresolved?: boolean
  ) {
    const d = this._newDraft();
    d.in_reply_to = inReplyTo;
    if (message !== undefined) {
      d.message = message;
    }
    if (unresolved !== undefined) {
      d.unresolved = unresolved;
    }
    return d;
  }

  _newDraft(lineNum?: LineNumber, range?: CommentRange) {
    const d: UIDraft = {
      __draft: true,
      __draftID: Math.random().toString(36),
      __date: new Date(),
    };
    if (lineNum === 'LOST') throw new Error('invalid lineNum lost');
    // For replies, always use same meta info as root.
    if (this.comments && this.comments.length >= 1) {
      const rootComment = this.comments[0];
      if (rootComment.path !== undefined) d.path = rootComment.path;
      if (rootComment.patch_set !== undefined)
        d.patch_set = rootComment.patch_set;
      if (rootComment.side !== undefined) d.side = rootComment.side;
      if (rootComment.line !== undefined) d.line = rootComment.line;
      if (rootComment.range !== undefined) d.range = rootComment.range;
      if (rootComment.parent !== undefined) d.parent = rootComment.parent;
    } else {
      // Set meta info for root comment.
      d.path = this.path;
      d.patch_set = this.patchNum;
      d.side = this._getSide(this.isOnParent);

      if (lineNum && lineNum !== FILE) {
        d.line = lineNum;
      }
      if (range) {
        d.range = range;
      }
      if (this.parentIndex) {
        d.parent = this.parentIndex;
      }
    }
    return d;
  }

  _getSide(isOnParent: boolean): CommentSide {
    return isOnParent ? CommentSide.PARENT : CommentSide.REVISION;
  }

  _computeRootId(comments: PolymerDeepPropertyChange<UIComment[], unknown>) {
    // Keep the root ID even if the comment was removed, so that notification
    // to sync will know which thread to remove.
    if (!comments.base.length) {
      return this.rootId;
    }
    const rootComment = comments.base[0];
    if (rootComment.id) return rootComment.id;
    if (isDraft(rootComment)) return rootComment.__draftID;
    throw new Error('Missing id in root comment.');
  }

  _handleCommentDiscard(e: Event) {
    assertIsDefined(this.changeNum, 'changeNum');
    assertIsDefined(this.patchNum, 'patchNum');
    const diffCommentEl = (dom(e) as EventApi).rootTarget as GrComment;
    const comment = diffCommentEl.comment;
    const idx = this._indexOf(comment, this.comments);
    if (idx === -1) {
      throw new Error(
        'Cannot find comment ' + JSON.stringify(diffCommentEl.comment)
      );
    }
    this.splice('comments', idx, 1);
    if (this.comments.length === 0) {
      this.fireRemoveSelf();
    }
    this._handleCommentSavedOrDiscarded();

    // Check to see if there are any other open comments getting edited and
    // set the local storage value to its message value.
    for (const changeComment of this.comments) {
      if (isDraft(changeComment) && changeComment.__editing) {
        const commentLocation: StorageLocation = {
          changeNum: this.changeNum,
          patchNum: this.patchNum,
          path: changeComment.path,
          line: changeComment.line,
        };
        this.storage.setDraftComment(
          commentLocation,
          changeComment.message ?? ''
        );
      }
    }
  }

  _handleCommentSavedOrDiscarded() {
    this.dispatchEvent(
      new CustomEvent('thread-changed', {
        detail: {rootId: this.rootId, path: this.path},
        bubbles: false,
      })
    );
  }

  _handleCommentUpdate(e: CustomEvent) {
    const comment = e.detail.comment;
    const index = this._indexOf(comment, this.comments);
    if (index === -1) {
      // This should never happen: comment belongs to another thread.
      this.reporting.error(
        new Error(`Comment update for another comment thread: ${comment}`)
      );
      return;
    }
    this.set(['comments', index], comment);
    // Because of the way we pass these comment objects around by-ref, in
    // combination with the fact that Polymer does dirty checking in
    // observers, the this.set() call above will not cause a thread update in
    // some situations.
    this.updateThreadProperties();
  }

  _indexOf(comment: UIComment | undefined, arr: UIComment[]) {
    if (!comment) return -1;
    for (let i = 0; i < arr.length; i++) {
      const c = arr[i];
      if (
        (isDraft(c) && isDraft(comment) && c.__draftID === comment.__draftID) ||
        (c.id && c.id === comment.id)
      ) {
        return i;
      }
    }
    return -1;
  }

  _computeHostClass(unresolved?: boolean) {
    if (this.isRobotComment) {
      return 'robotComment';
    }
    return unresolved ? 'unresolved' : '';
  }

  /**
   * Load the project config when a project name has been provided.
   *
   * @param name The project name.
   */
  _projectNameChanged(name?: RepoName) {
    if (!name) {
      return;
    }
    this.restApiService.getProjectConfig(name).then(config => {
      this._projectConfig = config;
    });
  }

  _computeAriaHeading(_orderedComments: UIComment[]) {
    const firstComment = _orderedComments[0];
    const author = firstComment?.author ?? this._selfAccount;
    const lastComment = _orderedComments[_orderedComments.length - 1] || {};
    const status = [
      lastComment.unresolved ? 'Unresolved' : '',
      isDraft(lastComment) ? 'Draft' : '',
    ].join(' ');
    return `${status} Comment thread by ${getUserName(undefined, author)}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-thread': GrCommentThread;
  }
}
