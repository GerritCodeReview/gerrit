/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-icon/gr-icon';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-thread-list/gr-thread-list';
import {
  ActionInfo,
  ChangeActionDialog,
  CommentThread,
  EDIT,
  RevisionInfo,
} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {pluralize} from '../../../utils/string-util';
import {isUnresolved} from '../../../utils/comment-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import {fontStyles} from '../../../styles/gr-font-styles';
import {subscribe} from '../../lit/subscription-controller';
import {EditRevisionInfo, ParsedChangeInfo} from '../../../types/types';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve} from '../../../models/dependency';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';
import {createChangeUrl} from '../../../models/views/change';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';

@customElement('gr-confirm-submit-dialog')
export class GrConfirmSubmitDialog
  extends LitElement
  implements ChangeActionDialog
{
  @query('#dialog')
  dialog?: GrDialog;

  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  @property({type: Object})
  action?: ActionInfo;

  @state()
  change?: ParsedChangeInfo;

  @state()
  unresolvedThreads: CommentThread[] = [];

  @state()
  initialised = false;

  @state()
  sortedRevisions: (RevisionInfo | EditRevisionInfo)[] = [];

  private getCommentsModel = resolve(this, commentsModelToken);

  private getChangeModel = resolve(this, changeModelToken);

  private getNavigation = resolve(this, navigationToken);

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        #dialog {
          min-width: 40em;
        }
        p {
          margin-bottom: var(--spacing-l);
        }
        .warningBeforeSubmit {
          color: var(--warning-foreground);
          vertical-align: top;
          margin-right: var(--spacing-s);
        }
        @media screen and (max-width: 50em) {
          #dialog {
            min-width: inherit;
            width: 100%;
          }
        }
      `,
    ];
  }

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.change = x)
    );
    subscribe(
      this,
      () => this.getCommentsModel().threadsSaved$,
      x => (this.unresolvedThreads = x.filter(isUnresolved))
    );
    subscribe(
      this,
      () => this.getChangeModel().revisions$,
      x => (this.sortedRevisions = x)
    );
  }

  private renderPrivate() {
    if (!this.change?.is_private) return '';
    return html`
      <p>
        <gr-icon icon="warning" filled class="warningBeforeSubmit"></gr-icon>
        <strong>Heads Up!</strong>
        Submitting this private change will also make it public.
      </p>
    `;
  }

  private renderUnresolvedCommentCount() {
    if (!this.unresolvedThreads?.length) return '';
    return html`
      <p>
        <gr-icon icon="warning" filled class="warningBeforeSubmit"></gr-icon>
        ${this.computeUnresolvedCommentsWarning()}
      </p>
      <gr-thread-list
        id="commentList"
        .threads=${this.unresolvedThreads}
        hide-dropdown
      >
      </gr-thread-list>
    `;
  }

  private renderChangeEdit() {
    if (!this.computeHasChangeEdit()) return '';
    const editPatchNumIndex = this.computeEditPatchNumIndex();
    return html`
      <gr-icon icon="warning" filled class="warningBeforeSubmit"></gr-icon>
      Your
      <a href="#" @click=${this.handleEditTap}>unpublished edit</a>
      ${editPatchNumIndex
        ? html` (between patchset ${editPatchNumIndex - 1} and
          ${editPatchNumIndex + 1})`
        : ''}
      will not be submitted. Did you forget to click <b>PUBLISH</b> after
      creating the <b>EDIT</b>?
    `;
  }

  /**
   * Compute EDIT patchset position in sorted revisions, return undefined if
   * EDIT is not in the sorted revisions or is the last revision.
   * This is used to avoid confusion when the EDIT is not the last revision
   */
  private computeEditPatchNumIndex() {
    const revisions = this.sortedRevisions;
    const editIndex = revisions.findIndex(rev => rev._number === EDIT);
    if (editIndex === -1 || editIndex === revisions.length - 1)
      return undefined;
    return editIndex;
  }

  private renderInitialised() {
    if (!this.initialised) return '';
    return html`
      <div class="header" slot="header">${this.action?.label}</div>
      <div class="main" slot="main">
        <gr-endpoint-decorator name="confirm-submit-change">
          <p>Ready to submit "<strong>${this.change?.subject}</strong>"?</p>
          ${this.renderPrivate()} ${this.renderUnresolvedCommentCount()}
          ${this.renderChangeEdit()}
          <gr-endpoint-param
            name="change"
            .value=${this.change}
          ></gr-endpoint-param>
          <gr-endpoint-param
            name="action"
            .value=${this.action}
          ></gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>
    `;
  }

  override render() {
    return html` <gr-dialog
      id="dialog"
      confirm-label="Continue"
      confirm-on-enter=""
      @cancel=${this.handleCancelTap}
      @confirm=${this.handleConfirmTap}
    >
      ${this.renderInitialised()}
    </gr-dialog>`;
  }

  init() {
    this.initialised = true;
  }

  resetFocus() {
    this.dialog?.resetFocus();
  }

  // Private method, but visible for testing.
  computeHasChangeEdit() {
    return Object.values(this.change?.revisions ?? {}).some(
      rev => rev._number === EDIT
    );
  }

  // Private method, but visible for testing.
  computeUnresolvedCommentsWarning() {
    const unresolvedCount = this.unresolvedThreads.length;
    if (!unresolvedCount) throw new Error('unresolved comments undefined or 0');
    return `Heads Up! ${pluralize(unresolvedCount, 'unresolved comment')}.`;
  }

  private handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubbleNoCompose(this, 'confirm', {});
  }

  private handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubbleNoCompose(this, 'cancel', {});
  }

  private handleEditTap(e: Event) {
    e.preventDefault();
    if (!this.change) return;
    const url = createChangeUrl({
      change: this.change,
      edit: true,
      forceReload: true,
    });
    this.getNavigation().setUrl(url);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-submit-dialog': GrConfirmSubmitDialog;
  }
}
