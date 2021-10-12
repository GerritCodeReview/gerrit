/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '@polymer/iron-icon/iron-icon';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-dialog/gr-dialog';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-thread-list/gr-thread-list';
import {ChangeInfo, ActionInfo} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {pluralize} from '../../../utils/string-util';
import {CommentThread, isUnresolved} from '../../../utils/comment-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property, query} from 'lit/decorators';
import {fontStyles} from '../../../styles/gr-font-styles';

@customElement('gr-confirm-submit-dialog')
export class GrConfirmSubmitDialog extends LitElement {
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
  change?: ChangeInfo;

  @property({type: Object})
  action?: ActionInfo;

  @property({type: Array})
  commentThreads?: CommentThread[] = [];

  @property({type: Boolean})
  _initialised = false;

  static get styles() {
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

  private renderPrivate() {
    if (!this.change?.is_private) return '';
    return html`
      <p>
        <iron-icon
          icon="gr-icons:warning"
          class="warningBeforeSubmit"
        ></iron-icon>
        <strong>Heads Up!</strong>
        Submitting this private change will also make it public.
      </p>
    `;
  }

  private renderUnresolvedCommentCount() {
    if (!this.change?.unresolved_comment_count) return '';
    return html`
      <p>
        <iron-icon
          icon="gr-icons:warning"
          class="warningBeforeSubmit"
        ></iron-icon>
        ${this._computeUnresolvedCommentsWarning(this.change)}
      </p>
      <gr-thread-list
        id="commentList"
        .threads="${this._computeUnresolvedThreads(this.commentThreads)}"
        .change="${this.change}"
        .changeNum="${this.change?._number}"
        logged-in
        hide-dropdown
      >
      </gr-thread-list>
    `;
  }

  private renderChangeEdit() {
    if (!this._computeHasChangeEdit(this.change)) return '';
    return html`
      <iron-icon
        icon="gr-icons:warning"
        class="warningBeforeSubmit"
      ></iron-icon>
      Your unpublished edit will not be submitted. Did you forget to click
      <b>PUBLISH</b>
    `;
  }

  private renderInitialised() {
    if (!this._initialised) return '';
    return html`
      <div class="header" slot="header">${this.action?.label}</div>
      <div class="main" slot="main">
        <gr-endpoint-decorator name="confirm-submit-change">
          <p>Ready to submit “<strong>${this.change?.subject}</strong>”?</p>
          ${this.renderPrivate()} ${this.renderUnresolvedCommentCount()}
          ${this.renderChangeEdit()}
          <gr-endpoint-param
            name="change"
            .value="${this.change}"
          ></gr-endpoint-param>
          <gr-endpoint-param
            name="action"
            .value="${this.action}"
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
      @cancel=${this._handleCancelTap}
      @confirm=${this._handleConfirmTap}
    >
      ${this.renderInitialised()}
    </gr-dialog>`;
  }

  init() {
    this._initialised = true;
  }

  resetFocus() {
    this.dialog?.resetFocus();
  }

  _computeHasChangeEdit(change?: ChangeInfo) {
    return (
      !!change &&
      !!change.revisions &&
      Object.values(change.revisions).some(rev => rev._number === 'edit')
    );
  }

  _computeUnresolvedThreads(commentThreads?: CommentThread[]) {
    if (!commentThreads) return [];
    return commentThreads.filter(thread => isUnresolved(thread));
  }

  _computeUnresolvedCommentsWarning(change?: ChangeInfo) {
    if (!change) return '';
    const unresolvedCount = change.unresolved_comment_count;
    if (!unresolvedCount) throw new Error('unresolved comments undefined or 0');
    return `Heads Up! ${pluralize(unresolvedCount, 'unresolved comment')}.`;
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('confirm', {bubbles: false}));
  }

  _handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel', {bubbles: false}));
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-submit-dialog': GrConfirmSubmitDialog;
  }
}
