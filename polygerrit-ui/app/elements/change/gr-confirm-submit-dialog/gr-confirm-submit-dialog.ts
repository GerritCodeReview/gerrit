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
import '../gr-related-changes-list/gr-related-change';
import '../../shared/gr-button/gr-button';
import {DEFAULT_NUM_CHANGES_WHEN_COLLAPSED} from '../gr-related-changes-list/gr-related-collapse';
import '@material/web/checkbox/checkbox';
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
import {truncatePath} from '../../../utils/path-list-util';
import {isUnresolved} from '../../../utils/comment-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/form-styles';
import {materialStyles} from '../../../styles/gr-material-styles';
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
  submitWholeTopic = false;

  @state()
  private serverConfig?: ServerInfo;

  @state()
  private sameTopicChanges: ChangeInfo[] = [];

  @state()
  private showAllTopicChanges = false;

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
      formStyles,
      materialStyles,
      css`
        #dialog {
          min-width: 40em;
        }
        .section {
          border-top: 1px solid var(--border-color);
          margin-top: var(--spacing-xl);
          padding-top: var(--spacing-l);
        }
        .topicChangesToggle {
          display: flex;
          padding-top: var(--spacing-s);
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
        .topicChangeLine {
          display: flex;
        }
        .checkbox-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-m);
          margin-bottom: var(--spacing-xl);
        }
        .checkbox-container:disabled {
          color: var(--disabled-foreground);
        }
        .notReady {
          color: var(--warning-foreground);
          font-weight: var(--font-weight-medium);
          margin-left: var(--spacing-xs);
          margin-right: var(--spacing-m);
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

  private isEnforcedWholeTopicSubmit() {
    return (
      this.serverConfig?.change.enforce_whole_topic_submit ||
      this.serverConfig?.change.submit_whole_topic
    );
  }

  private isSubmittingWholeTopic() {
    return this.isEnforcedWholeTopicSubmit()
      ? !!this.change?.topic
      : this.submitWholeTopic;
  }

  private hasSameTopicUnsubmittableChanges() {
    return this.sameTopicChanges.some(c => !c.submittable);
  }

  private hasUnsubmittableTopicChanges() {
    return (
      this.isSubmittingWholeTopic() && this.hasSameTopicUnsubmittableChanges()
    );
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

  private renderSubmitWholeTopic() {
    if (this.isEnforcedWholeTopicSubmit() || !this.change?.topic) return '';
    return html`
      <div class="section">
        <div class="checkbox-container">
          <md-checkbox
            id="submitWholeTopicCheckbox"
            ?checked=${this.submitWholeTopic}
            @change=${this.handleSubmitWholeTopicChange}
          ></md-checkbox>
          <label for="submitWholeTopicCheckbox">
            Also submit all changes with the same topic
            (<strong>${this.change.topic}</strong>)
          </label>
        </div>
        ${this.renderTopicWarning()} ${this.renderTopicChangesList()}
      </div>
    `;
  }

  private renderTopicChangesList() {
    if (!this.sameTopicChanges.length) return '';
    const topic = this.change?.topic;
    const collapsible =
      this.sameTopicChanges.length > DEFAULT_NUM_CHANGES_WHEN_COLLAPSED;
    const visibleChanges =
      collapsible && !this.showAllTopicChanges
        ? this.sameTopicChanges.slice(0, DEFAULT_NUM_CHANGES_WHEN_COLLAPSED)
        : this.sameTopicChanges;
    return html`
      <p>Changes with topic <strong>${topic}</strong>:</p>
      <div>
        ${visibleChanges.map(change => {
          const truncatedRepo = truncatePath(change.project, 2);
          return html`
            <div class="topicChangeLine">
              <gr-related-change
                .label=${`${change.project}: ${change.branch}: ${change.subject}`}
                .change=${change}
                .href=${createChangeUrl({change, usp: 'confirm-submit'})}
                show-submittable-check
                ><span slot="name">${change.subject}</span
                ><span slot="extra"
                  >${!change.submittable
                    ? html`<span class="notReady">(Not ready)</span>`
                    : ''}<span title=${change.project}>${truncatedRepo}</span
                  >&nbsp;|&nbsp;${change.branch}&nbsp;</span
                ></gr-related-change
              >
            </div>
          `;
        })}
      </div>
      ${collapsible
        ? html`
            <div class="topicChangesToggle">
              <gr-button
                link
                @click=${() => {
                  this.showAllTopicChanges = !this.showAllTopicChanges;
                }}
              >
                ${this.showAllTopicChanges
                  ? html`Show less <gr-icon icon="expand_less"></gr-icon>`
                  : html`Show all (${this.sameTopicChanges.length})
                      <gr-icon icon="expand_more"></gr-icon>`}
              </gr-button>
            </div>
          `
        : ''}
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

  private handleSubmitWholeTopicChange(e: Event) {
    this.submitWholeTopic = (e.target as HTMLInputElement).checked;
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
          ${this.renderWarnings()} ${this.renderSubmitWholeTopic()}
          ${this.isEnforcedWholeTopicSubmit() && this.change?.topic
            ? html`<div class="section">
                ${this.renderTopicWarning()} ${this.renderTopicChangesList()}
              </div>`
            : ''}
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
      .disabled=${this.hasUnsubmittableTopicChanges()}
      @cancel=${this.handleCancelTap}
      @confirm=${this.handleConfirmTap}
    >
      ${this.renderInitialised()}
    </gr-dialog>`;
  }

  init() {
    this.initialised = true;
    this.submitWholeTopic = false;
    this.showAllTopicChanges = false;
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
    fireNoBubbleNoCompose(this, 'confirm', {
      submitWholeTopic: this.submitWholeTopic,
    });
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
