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
import '../gr-date-formatter/gr-date-formatter';
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
  NumericChangeId,
  ConfigInfo,
  PatchSetNum,
  RepoName,
  BasePatchSetNum,
} from '../../../types/common';
import {GrButton} from '../gr-button/gr-button';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';
import {GrDialog} from '../gr-dialog/gr-dialog';
import {
  isDraft,
  UIComment,
  UIDraft,
  UIRobot,
} from '../../../utils/comment-util';
import {OpenFixPreviewEventDetail} from '../../../types/events';
import {fireAlert} from '../../../utils/event-util';
import {pluralize} from '../../../utils/string-util';
import {assertIsDefined} from '../../../utils/common-util';
import {debounce, DelayedTask} from '../../../utils/async-util';
import {StorageLocation} from '../../../services/storage/gr-storage';

const FILE = 'FILE';

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

export interface GrComment {
  $: {
    container: HTMLDivElement;
    header: HTMLDivElement;
  };
}

@customElement('gr-comment')
export class GrComment extends KeyboardShortcutMixin(PolymerElement) {
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

  @property({type: Boolean, observer: '_draftChanged'})
  draft = false;

  @property({type: Boolean, observer: '_editingChanged'})
  editing = false;

  @property({type: Boolean, reflectToAttribute: true})
  discarding = false;

  @property({type: Boolean})
  hasChildren?: boolean;

  @property({type: String})
  patchNum?: PatchSetNum;

  @property({type: Boolean})
  showActions?: boolean;

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
  robotButtonDisabled?: boolean;

  @property({type: Boolean})
  _hasHumanReply?: boolean;

  @property({type: Boolean})
  _isAdmin = false;

  @property({type: String})
  side?: string;

  @property({type: Boolean})
  _showRespectfulTip = false;

  @property({type: Boolean})
  showPatchset = true;

  @property({type: String})
  _respectfulReviewTip?: string;

  @property({type: Boolean})
  _respectfulTipDismissed = false;

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

  reporting = appContext.reportingService;

  /** @override */
  connectedCallback() {
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

  /** @override */
  disconnectedCallback() {
    if (this.textarea) {
      this.textarea.closeDropdown();
    }
    super.disconnectedCallback();
  }

  _getAuthor(comment: UIComment) {
    return comment.author || this._selfAccount;
  }

  _getUrlForComment(comment: UIComment) {
    if (!this.changeNum || !this.projectName) return '';
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
    const cachedVisibilityOfRespectfulTip = this.storage.getRespectfulTipVisibility();
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

    this._showRobotActions = showActions && isRobotComment;
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

  _commentChanged(comment: UIComment) {
    this.editing = isDraft(comment) && !!comment.__editing;
    this.resolved = !comment.unresolved;
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

  _fireSave() {
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
    this._messageText = message || '';
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

  _hasNoFix(comment: UIComment) {
    return !comment || !(comment as UIRobot).fix_suggestions;
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

    // Only apply local drafts to comments that haven't been saved
    // remotely, and haven't been given a default message already.
    if (!comment || comment.id || comment.message || !comment.path) {
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
      this.set('comment.message', draft.message);
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

  _computeHideRunDetails(comment: UIRobot, collapsed: boolean) {
    if (!comment) return true;
    return !(comment.robot_id && comment.url && !collapsed);
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
