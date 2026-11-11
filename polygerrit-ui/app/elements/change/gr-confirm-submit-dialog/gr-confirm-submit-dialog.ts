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
import '../../shared/gr-button/gr-button';
import {
  ActionInfo,
  ChangeActionDialog,
  ChangeInfo,
  CommentThread,
  EDIT,
  RevisionInfo,
  ServerInfo,
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
import {configModelToken} from '../../../models/config/config-model';
import {relatedChangesModelToken} from '../../../models/change/related-changes-model';
import {resolve} from '../../../models/dependency';
import {fireNoBubbleNoCompose} from '../../../utils/event-util';
import {createSearchUrl} from '../../../models/views/search';
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
  private serverConfig?: ServerInfo;

  @state()
  private sameTopicChanges: ChangeInfo[] = [];

  @state()
  sortedRevisions: (RevisionInfo | EditRevisionInfo)[] = [];

  private getCommentsModel = resolve(this, commentsModelToken);

  private getChangeModel = resolve(this, changeModelToken);

  private getConfigModel = resolve(this, configModelToken);

  private getNavigation = resolve(this, navigationToken);

  private getRelatedChangesModel = resolve(this, relatedChangesModelToken);

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        #dialog {
          min-width: 40em;
        }
        .topicDashboardLink {
          display: block;
          margin-top: var(--spacing-m);
        }
        .warningBox {
          display: flex;
          align-items: flex-start;
          gap: var(--spacing-s);
          background-color: var(--warning-background);
          color: var(--warning-foreground);
          border: 1px solid var(--warning-foreground);
          border-radius: var(--border-radius);
          padding: var(--spacing-m) var(--spacing-l);
          margin-bottom: var(--spacing-l);
        }
        .warningBox gr-icon {
          color: var(--warning-foreground);
          flex-shrink: 0;
          margin-top: 2px;
        }

        .footer-buttons {
          display: flex;
          align-items: center;
          gap: var(--spacing-l);
          flex: 1;
        }
        .footer-space {
          flex-grow: 1;
        }
        p {
          margin-bottom: var(--spacing-l);
        }
        p:last-child {
          margin-bottom: 0;
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
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      x => (this.serverConfig = x)
    );
    subscribe(
      this,
      () => this.getRelatedChangesModel().sameTopicChanges$,
      x => (this.sameTopicChanges = x ?? [])
    );
  }

  private isWholeTopicEnforced() {
    return (
      this.serverConfig?.change.submit_whole_topic ||
      this.serverConfig?.change.enforce_whole_topic_submit
    );
  }

  private hasSameTopicUnsubmittableChanges() {
    return this.sameTopicChanges.some(c => !c.submittable);
  }

  private renderTopicWarning() {
    if (
      !this.sameTopicChanges.length ||
      !this.hasSameTopicUnsubmittableChanges()
    )
      return '';
    return html`
      <div class="warningBox">
        <gr-icon icon="warning" filled></gr-icon>
        Some changes in this topic are not ready to submit.
      </div>
    `;
  }

  private renderTopicSection() {
    const topic = this.change?.topic;
    if (!topic) return '';
    const dashboardUrl = createSearchUrl({topic});
    return html`
      <div class="section">
        ${this.renderTopicWarning()}
        <a
          class="topicDashboardLink"
          href=${dashboardUrl}
          target="_blank"
          rel="noopener noreferrer"
          >View all changes with topic <strong>${topic}</strong></a
        >
      </div>
    `;
  }

  private renderWarnings() {
    const private_ = this.renderPrivate();
    const unresolvedComments = this.renderUnresolvedCommentCount();
    const changeEdit = this.renderChangeEdit();
    if (!private_ && !unresolvedComments && !changeEdit) return '';
    return html`
      <div class="section">${private_}${unresolvedComments}${changeEdit}</div>
    `;
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
    const hasTopicWithChanges =
      !!this.change?.topic && this.sameTopicChanges.length > 0;
    const submitWholeTopicDisabled = this.hasSameTopicUnsubmittableChanges();
    const showSubmitButton =
      !hasTopicWithChanges || !this.isWholeTopicEnforced();
    return html`
      <div class="header" slot="header">${this.action?.label}</div>
      <div class="main" slot="main">
        <gr-endpoint-decorator name="confirm-submit-change">
          <p>Ready to submit "<strong>${this.change?.subject}</strong>"?</p>
          ${this.renderWarnings()} ${this.renderTopicSection()}
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
      <div class="footer-buttons" slot="footer">
        <div class="footer-space"></div>
        <gr-button link @click=${this.handleCancelTap}>Cancel</gr-button>
        ${showSubmitButton
          ? html`<gr-button primary @click=${this.handleConfirmTap}
              >Submit</gr-button
            >`
          : ''}
        ${hasTopicWithChanges
          ? html`<gr-button
              primary
              ?disabled=${submitWholeTopicDisabled}
              @click=${this.handleSubmitWholeTopicTap}
              >Submit Whole Topic</gr-button
            >`
          : ''}
      </div>
    `;
  }

  override render() {
    return html` <gr-dialog
      id="dialog"
      confirm-label=""
      cancel-label=""
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
    // Buttons are rendered directly in slot="footer", not by gr-dialog,
    // so focus the Submit button directly instead of delegating to gr-dialog.
    const submitBtn = this.shadowRoot?.querySelector<HTMLElement>(
      '.footer-buttons gr-button[primary]'
    );
    submitBtn?.focus();
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
    fireNoBubbleNoCompose(this, 'confirm', {submitWholeTopic: false});
  }

  private handleSubmitWholeTopicTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    fireNoBubbleNoCompose(this, 'confirm', {submitWholeTopic: true});
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
      patchNum: EDIT,
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
