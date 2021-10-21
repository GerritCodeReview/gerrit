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
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-comment_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {getRootElement} from '../../../scripts/rootElement';
import {appContext} from '../../../services/app-context';
import {customElement, observe, property} from '@polymer/decorators';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {GrTextarea} from '../gr-textarea/gr-textarea';
import {GrOverlay} from '../gr-overlay/gr-overlay';
import {
  AccountDetailInfo,
  BasePatchSetNum,
  ConfigInfo,
  NumericChangeId,
  PatchSetNum,
  RepoName,
} from '../../../types/common';
import {GrButton} from '../gr-button/gr-button';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {
  isDraft,
  isRobot,
  UIComment,
  UIDraft,
  UIRobot,
} from '../../../utils/comment-util';
import {OpenFixPreviewEventDetail} from '../../../types/events';
import {fire, fireAlert, fireEvent} from '../../../utils/event-util';
import {pluralize} from '../../../utils/string-util';
import {assertIsDefined} from '../../../utils/common-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {StorageLocation} from '../../../services/storage/gr-storage';
import {Interaction} from '../../../constants/reporting';

const STORAGE_DEBOUNCE_INTERVAL = 400;
const TOAST_DEBOUNCE_INTERVAL = 200;

const SAVED_MESSAGE = 'All changes saved';
const UNSAVED_MESSAGE = 'Unable to save draft';

const REPORT_CREATE_DRAFT = 'CreateDraftComment';
const REPORT_UPDATE_DRAFT = 'UpdateDraftComment';
const REPORT_DISCARD_DRAFT = 'DiscardDraftComment';

const FILE = 'FILE';

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

interface CommentOverlays {
  confirmDelete?: GrOverlay | null;
  confirmDiscard?: GrOverlay | null;
}

export interface GrComment {
  $: {
    container: HTMLDivElement;
    resolvedCheckbox: HTMLInputElement;
    header: HTMLDivElement;
  };
}

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = KeyboardShortcutMixin(PolymerElement);

@customElement('gr-comment')
export class GrComment extends base {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: String})
  projectName?: RepoName;

  @property({type: Object, notify: true, observer: '_commentChanged'})
  comment?: UIComment;

  @property({type: Array})
  comments?: UIComment[];

  @property({type: Boolean, reflectToAttribute: true})
  isRobotComment = false;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: Boolean, observer: '_draftChanged'})
  draft = false;

  @property({type: Boolean, observer: '_editingChanged'})
  editing = false;

  // Assigns a css property to the comment hiding the comment while it's being
  // discarded
  @property({
    type: Boolean,
    reflectToAttribute: true,
  })
  discarding = false;

  @property({type: Boolean})
  hasChildren?: boolean;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean})
  showActions?: boolean;

  @property({type: Boolean})
  _showHumanActions?: boolean;

  @property({type: Boolean})
  _showRobotActions?: boolean;

  @property({
    type: Boolean,
    reflectToAttribute: true,
    observer: '_toggleCollapseClass',
  })
  collapsed = true;

  @property({type: Object})
  projectConfig?: ConfigInfo;

  @property({type: Boolean})
  robotButtonDisabled = false;

  @property({type: Boolean})
  _hasHumanReply?: boolean;

  @property({type: Boolean})
  _isAdmin = false;

  @property({type: Object})
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  _xhrPromise?: Promise<any>; // Used for testing.

  @property({type: String, observer: '_messageTextChanged'})
  _messageText = '';

  @property({type: String})
  side?: string;

  @property({type: Boolean})
  resolved = false;

  // Intentional to share the object across instances.
  @property({type: Object})
  _numPendingDraftRequests: {number: number} = {number: 0};

  @property({type: Boolean})
  _enableOverlay = false;

  /**
   * Property for storing references to overlay elements. When the overlays
   * are moved to getRootElement() to be shown they are no-longer
   * children, so they can't be queried along the tree, so they are stored
   * here.
   */
  @property({type: Object})
  _overlays: CommentOverlays = {};

  @property({type: Boolean})
  _showRespectfulTip = false;

  @property({type: Boolean})
  showPatchset = true;

  @property({type: String})
  _respectfulReviewTip?: string;

  @property({type: Boolean})
  _respectfulTipDismissed = false;

  @property({type: Boolean})
  _unableToSave = false;

  @property({type: Object})
  _selfAccount?: AccountDetailInfo;

  @property({type: Boolean})
  showPortedComment = false;

  get keyBindings() {
    return {
      'ctrl+enter meta+enter ctrl+s meta+s': '_handleSaveKey',
      esc: '_handleEsc',
    };
  }

  private readonly restApiService = appContext.restApiService;

  private readonly storage = appContext.storageService;

  private readonly reporting = appContext.reportingService;

  private readonly commentsService = appContext.commentsService;

  private fireUpdateTask?: DelayedTask;

  private storeTask?: DelayedTask;

  private draftToastTask?: DelayedTask;

  override connectedCallback() {
    super.connectedCallback();
    this.restApiService.getAccount().then(account => {
      this._selfAccount = account;
    });
    if (this.editing) {
      this.collapsed = false;
    } else if (this.comment) {
      this.collapsed = !!this.comment.collapsed;
    }
    this._getIsAdmin().then(isAdmin => {
      this._isAdmin = !!isAdmin;
    });
  }

  override disconnectedCallback() {
    this.fireUpdateTask?.cancel();
    this.storeTask?.cancel();
    this.draftToastTask?.cancel();
    if (this.textarea) {
      this.textarea.closeDropdown();
    }
    super.disconnectedCallback();
  }

  /** 2nd argument is for *triggering* the computation only. */
  _getAuthor(comment?: UIComment, _?: unknown) {
    return comment?.author || this._selfAccount;
  }

  _getUrlForComment(comment?: UIComment) {
    if (!comment || !this.changeNum || !this.projectName) return '';
    if (!comment.id) throw new Error('comment must have an id');
    return GerritNav.getUrlForComment(
      this.changeNum as NumericChangeId,
      this.projectName,
      comment.id
    );
  }

  _handlePortedMessageClick() {
    assertIsDefined(this.comment, 'comment');
    this.reporting.reportInteraction('navigate-to-original-comment', {
      line: this.comment.line,
      range: this.comment.range,
    });
  }

  @observe('editing')
  _onEditingChange(editing?: boolean) {
    this.dispatchEvent(
      new CustomEvent('comment-editing-changed', {
        detail: !!editing,
        bubbles: true,
        composed: true,
      })
    );
    if (!editing) return;
    // visibility based on cache this will make sure we only and always show
    // a tip once every Math.max(a day, period between creating comments)
    const cachedVisibilityOfRespectfulTip =
      this.storage.getRespectfulTipVisibility();
    if (!cachedVisibilityOfRespectfulTip) {
      // we still want to show the tip with a probability of 30%
      if (this.getRandomNum(0, 3) >= 1) return;
      this._showRespectfulTip = true;
      const randomIdx = this.getRandomNum(0, RESPECTFUL_REVIEW_TIPS.length);
      this._respectfulReviewTip = RESPECTFUL_REVIEW_TIPS[randomIdx];
      this.reporting.reportInteraction('respectful-tip-appeared', {
        tip: this._respectfulReviewTip,
      });
      // update cache
      this.storage.setRespectfulTipVisibility();
    }
  }

  /** Set as a separate method so easy to stub. */
  getRandomNum(min: number, max: number) {
    return Math.floor(Math.random() * (max - min) + min);
  }

  _computeVisibilityOfTip(showTip: boolean, tipDismissed: boolean) {
    return showTip && !tipDismissed;
  }

  _dismissRespectfulTip() {
    this._respectfulTipDismissed = true;
    this.reporting.reportInteraction('respectful-tip-dismissed', {
      tip: this._respectfulReviewTip,
    });
    // add a 14-day delay to the tip cache
    this.storage.setRespectfulTipVisibility(/* delayDays= */ 14);
  }

  _onRespectfulReadMoreClick() {
    this.reporting.reportInteraction('respectful-read-more-clicked');
  }

  get textarea(): GrTextarea | null {
    return this.shadowRoot?.querySelector('#editTextarea') as GrTextarea | null;
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

  _computeShowHideIcon(collapsed: boolean) {
    return collapsed ? 'gr-icons:expand-more' : 'gr-icons:expand-less';
  }

  _computeShowHideAriaLabel(collapsed: boolean) {
    return collapsed ? 'Expand' : 'Collapse';
  }

  @observe('showActions', 'isRobotComment')
  _calculateActionstoShow(showActions?: boolean, isRobotComment?: boolean) {
    // Polymer 2: check for undefined
    if ([showActions, isRobotComment].includes(undefined)) {
      return;
    }

    this._showHumanActions = showActions && !isRobotComment;
    this._showRobotActions = showActions && isRobotComment;
  }

  hasPublishedComment(comments?: UIComment[]) {
    if (!comments?.length) return false;
    return comments.length > 1 || !isDraft(comments[0]);
  }

  @observe('comment')
  _isRobotComment(comment: UIRobot) {
    this.isRobotComment = !!comment.robot_id;
  }

  isOnParent() {
    return this.side === 'PARENT';
  }

  _getIsAdmin() {
    return this.restApiService.getIsAdmin();
  }

  _computeDraftTooltip(unableToSave: boolean) {
    return unableToSave
      ? 'Unable to save draft. Please try to save again.'
      : "This draft is only visible to you. To publish drafts, click the 'Reply'" +
          "or 'Start review' button at the top of the change or press the 'A' key.";
  }

  _computeDraftText(unableToSave: boolean) {
    return 'DRAFT' + (unableToSave ? '(Failed to save)' : '');
  }

  handleCopyLink() {
    fireEvent(this, 'copy-comment-link');
  }

  save(opt_comment?: UIComment) {
    let comment = opt_comment;
    if (!comment) {
      comment = this.comment;
    }

    this.set('comment.message', this._messageText);
    this.editing = false;
    this.disabled = true;

    if (!this._messageText) {
      return this._discardDraft();
    }

    const details = this.commentDetailsForReporting();
    this.reporting.reportInteraction(Interaction.SAVE_COMMENT, details);
    this._xhrPromise = this._saveDraft(comment)
      .then(response => {
        this.disabled = false;
        if (!response.ok) {
          return;
        }

        this._eraseDraftCommentFromStorage();
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
          this._fireSave();
          return obj;
        });
      })
      .catch(err => {
        this.disabled = false;
        throw err;
      });

    return this._xhrPromise;
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

  _eraseDraftCommentFromStorage() {
    // Prevents a race condition in which removing the draft comment occurs
    // prior to it being saved.
    this.storeTask?.cancel();

    assertIsDefined(this.comment?.path, 'comment.path');
    assertIsDefined(this.changeNum, 'changeNum');
    this.storage.eraseDraftComment({
      changeNum: this.changeNum,
      patchNum: this._getPatchNum(),
      path: this.comment.path,
      line: this.comment.line,
      range: this.comment.range,
    });
  }

  _commentChanged(comment: UIComment) {
    this.editing = isDraft(comment) && !!comment.__editing;
    this.resolved = !comment.unresolved;
    this.discarding = false;
    if (this.editing) {
      // It's a new draft/reply, notify.
      this._fireUpdate();
    }
  }

  @observe('comment', 'comments.*')
  _computeHasHumanReply() {
    const comment = this.comment;
    if (!comment || !this.comments) return;
    // hide please fix button for robot comment that has human reply
    this._hasHumanReply = this.comments.some(
      c =>
        c.in_reply_to &&
        c.in_reply_to === comment.id &&
        !(c as UIRobot).robot_id
    );
  }

  _getEventPayload(): OpenFixPreviewEventDetail {
    return {comment: this.comment, patchNum: this.patchNum};
  }

  _fireEdit() {
    if (this.comment) this.commentsService.editDraft(this.comment);
    this.dispatchEvent(
      new CustomEvent('comment-edit', {
        detail: this._getEventPayload(),
        composed: true,
        bubbles: true,
      })
    );
  }

  _fireSave() {
    if (this.comment) this.commentsService.addDraft(this.comment);
    this.dispatchEvent(
      new CustomEvent('comment-save', {
        detail: this._getEventPayload(),
        composed: true,
        bubbles: true,
      })
    );
  }

  _fireUpdate() {
    this.fireUpdateTask = debounce(this.fireUpdateTask, () => {
      this.dispatchEvent(
        new CustomEvent('comment-update', {
          detail: this._getEventPayload(),
          composed: true,
          bubbles: true,
        })
      );
    });
  }

  _computeAccountLabelClass(draft: boolean) {
    return draft ? 'draft' : '';
  }

  _draftChanged(draft: boolean) {
    this.$.container.classList.toggle('draft', draft);
  }

  _editingChanged(editing?: boolean, previousValue?: boolean) {
    // Polymer 2: observer fires when at least one property is defined.
    // Do nothing to prevent comment.__editing being overwritten
    // if previousValue is undefined
    if (previousValue === undefined) return;

    this.$.container.classList.toggle('editing', editing);
    if (this.comment && this.comment.id) {
      const cancelButton = this.shadowRoot?.querySelector(
        '.cancel'
      ) as GrButton | null;
      if (cancelButton) {
        cancelButton.hidden = !editing;
      }
    }
    if (isDraft(this.comment)) {
      this.comment.__editing = this.editing;
    }
    if (!!editing !== !!previousValue) {
      // To prevent event firing on comment creation.
      this._fireUpdate();
    }
    if (editing) {
      setTimeout(() => {
        flush();
        this.textarea && this.textarea.putCursorAtEnd();
      }, 1);
    }
  }

  _computeDeleteButtonClass(isAdmin: boolean, draft: boolean) {
    return isAdmin && !draft ? 'showDeleteButtons' : '';
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
      !this._computeSaveDisabled(this._messageText, this.comment, this.resolved)
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

  _handleToggleCollapsed() {
    this.collapsed = !this.collapsed;
  }

  _toggleCollapseClass(collapsed: boolean) {
    if (collapsed) {
      this.$.container.classList.add('collapsed');
    } else {
      this.$.container.classList.remove('collapsed');
    }
  }

  @observe('comment.message')
  _commentMessageChanged(message: string) {
    /*
     * Only overwrite the message text user has typed if there is no existing
     * text typed by the user. This prevents the bug where creating another
     * comment triggered a recomputation of comments and the text written by
     * the user was lost.
     */
    if (!this._messageText || !this.editing) this._messageText = message || '';
  }

  _messageTextChanged(_: string, oldValue: string) {
    // Only store comments that are being edited in local storage.
    if (
      !this.comment ||
      (this.comment.id && (!isDraft(this.comment) || !this.comment.__editing))
    ) {
      return;
    }

    const patchNum = this.comment.patch_set
      ? this.comment.patch_set
      : this._getPatchNum();
    const {path, line, range} = this.comment;
    if (!path) return;
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

  _handleAnchorClick(e: Event) {
    e.preventDefault();
    if (!this.comment) return;
    this.dispatchEvent(
      new CustomEvent('comment-anchor-tap', {
        bubbles: true,
        composed: true,
        detail: {
          number: this.comment.line || FILE,
          side: this.side,
        },
      })
    );
  }

  _handleEdit(e: Event) {
    e.preventDefault();
    if (this.comment?.message) this._messageText = this.comment.message;
    this.editing = true;
    this._fireEdit();
    this.reporting.recordDraftInteraction();
  }

  _handleSave(e: Event) {
    e.preventDefault();

    // Ignore saves started while already saving.
    if (this.disabled) return;
    const timingLabel = this.comment?.id
      ? REPORT_UPDATE_DRAFT
      : REPORT_CREATE_DRAFT;
    const timer = this.reporting.getTimer(timingLabel);
    this.set('comment.__editing', false);
    return this.save().then(() => {
      timer.end({id: this.comment?.id});
    });
  }

  _handleCancel(e: Event) {
    e.preventDefault();
    if (!this.comment) return;
    if (!this.comment.id) {
      // Ensures we update the discarded draft message before deleting the draft
      this.set('comment.message', this._messageText);
      this._fireDiscard();
    } else {
      this.set('comment.__editing', false);
      this.commentsService.cancelDraft(this.comment);
      this.editing = false;
    }
  }

  _fireDiscard() {
    if (this.comment) this.commentsService.deleteDraft(this.comment);
    this.fireUpdateTask?.cancel();
    this.dispatchEvent(
      new CustomEvent('comment-discard', {
        detail: this._getEventPayload(),
        composed: true,
        bubbles: true,
      })
    );
  }

  _handleFix() {
    this.dispatchEvent(
      new CustomEvent('create-fix-comment', {
        bubbles: true,
        composed: true,
        detail: this._getEventPayload(),
      })
    );
  }

  _handleShowFix() {
    this.dispatchEvent(
      new CustomEvent('open-fix-preview', {
        bubbles: true,
        composed: true,
        detail: this._getEventPayload(),
      })
    );
  }

  _hasNoFix(comment?: UIComment) {
    return !comment || !(comment as UIRobot).fix_suggestions;
  }

  _handleDiscard(e: Event) {
    e.preventDefault();
    this.reporting.recordDraftInteraction();

    this._discardDraft();
  }

  _discardDraft() {
    if (!this.comment) return Promise.reject(new Error('undefined comment'));
    if (!isDraft(this.comment)) {
      return Promise.reject(new Error('Cannot discard a non-draft comment.'));
    }
    this.discarding = true;
    const timer = this.reporting.getTimer(REPORT_DISCARD_DRAFT);
    this.editing = false;
    this.disabled = true;
    this._eraseDraftCommentFromStorage();

    if (!this.comment.id) {
      this.disabled = false;
      this._fireDiscard();
      return Promise.resolve();
    }

    this._xhrPromise = this._deleteDraft(this.comment)
      .then(response => {
        this.disabled = false;
        if (!response.ok) {
          this.discarding = false;
        }
        timer.end({id: this.comment?.id});
        this._fireDiscard();
        return response;
      })
      .catch(err => {
        this.disabled = false;
        throw err;
      });

    return this._xhrPromise;
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

  _showStartRequest() {
    const numPending = ++this._numPendingDraftRequests.number;
    this._updateRequestToast(numPending);
  }

  _showEndRequest() {
    const numPending = --this._numPendingDraftRequests.number;
    this._updateRequestToast(numPending);
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

  _handleDraftFailure() {
    this.$.container.classList.add('unableToSave');
    this._unableToSave = true;
    this._handleFailedDraftRequest();
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

  _getPatchNum(): PatchSetNum {
    const patchNum = this.isOnParent()
      ? ('PARENT' as BasePatchSetNum)
      : this.patchNum;
    if (patchNum === undefined) throw new Error('patchNum undefined');
    return patchNum;
  }

  @observe('changeNum', 'patchNum', 'comment')
  _loadLocalDraft(
    changeNum: number,
    patchNum?: PatchSetNum,
    comment?: UIComment
  ) {
    // Polymer 2: check for undefined
    if ([changeNum, patchNum, comment].includes(undefined)) {
      return;
    }

    // Only apply local drafts to comments that are drafts and are currently
    // being edited.
    if (
      !comment ||
      !comment.path ||
      comment.message ||
      !isDraft(comment) ||
      !comment.__editing
    ) {
      return;
    }

    const draft = this.storage.getDraftComment({
      changeNum,
      patchNum: this._getPatchNum(),
      path: comment.path,
      line: comment.line,
      range: comment.range,
    });

    if (draft) {
      this._messageText = draft.message || '';
    }
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

  _handleCommentDelete() {
    this._openOverlay(this.confirmDeleteOverlay);
  }

  _handleCancelDeleteComment() {
    this._closeOverlay(this.confirmDeleteOverlay);
  }

  _openOverlay(overlay?: GrOverlay | null) {
    if (!overlay) {
      return Promise.reject(new Error('undefined overlay'));
    }
    getRootElement().appendChild(overlay);
    return overlay.open();
  }

  _computeHideRunDetails(comment: UIComment | undefined, collapsed: boolean) {
    if (!comment) return true;
    if (!isRobot(comment)) return true;
    return !comment.url || collapsed;
  }

  _closeOverlay(overlay?: GrOverlay | null) {
    if (overlay) {
      getRootElement().removeChild(overlay);
      overlay.close();
    }
  }

  _handleConfirmDeleteComment() {
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
        this._handleCancelDeleteComment();
        this.comment = newComment;
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment': GrComment;
  }
}
