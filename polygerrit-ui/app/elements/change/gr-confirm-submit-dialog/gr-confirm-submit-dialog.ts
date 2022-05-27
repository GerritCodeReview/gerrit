/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-icon/iron-icon';
import '../../shared/gr-icons/gr-icons';
import '../../shared/gr-dialog/gr-dialog';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../gr-thread-list/gr-thread-list';
import {ActionInfo, EDIT} from '../../../types/common';
import {GrDialog} from '../../shared/gr-dialog/gr-dialog';
import {pluralize} from '../../../utils/string-util';
import {CommentThread, isUnresolved} from '../../../utils/comment-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {fontStyles} from '../../../styles/gr-font-styles';
import {subscribe} from '../../lit/subscription-controller';
import {ParsedChangeInfo} from '../../../types/types';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve} from '../../../models/dependency';

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
  action?: ActionInfo;

  @state()
  change?: ParsedChangeInfo;

  @state()
  unresolvedThreads: CommentThread[] = [];

  @state()
  initialised = false;

  private getCommentsModel = resolve(this, commentsModelToken);

  private getChangeModel = resolve(this, changeModelToken);

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
      () => this.getCommentsModel().threads$,
      x => (this.unresolvedThreads = x.filter(isUnresolved))
    );
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
    if (!this.unresolvedThreads?.length) return '';
    return html`
      <p>
        <iron-icon
          icon="gr-icons:warning"
          class="warningBeforeSubmit"
        ></iron-icon>
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
    if (!this.initialised) return '';
    return html`
      <div class="header" slot="header">${this.action?.label}</div>
      <div class="main" slot="main">
        <gr-endpoint-decorator name="confirm-submit-change">
          <p>Ready to submit “<strong>${this.change?.subject}</strong>”?</p>
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
    this.dispatchEvent(new CustomEvent('confirm', {bubbles: false}));
  }

  private handleCancelTap(e: Event) {
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
