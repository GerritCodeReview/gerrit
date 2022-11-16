/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-avatar/gr-avatar';
import '../gr-button/gr-button';
import '../gr-icon/gr-icon';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import {getAppContext} from '../../../services/app-context';
import {
  accountKey,
  computeVoteableText,
  isAccountEmailOnly,
  isSelf,
} from '../../../utils/account-util';
import {customElement, property, state} from 'lit/decorators.js';
import {
  AccountInfo,
  ChangeInfo,
  ServerInfo,
  ReviewInput,
} from '../../../types/common';
import {
  canHaveAttention,
  getAddedByReason,
  getLastUpdate,
  getReason,
  getRemovedByReason,
  hasAttention,
} from '../../../utils/attention-set-util';
import {ReviewerState} from '../../../constants/constants';
import {CURRENT} from '../../../utils/patch-set-util';
import {isInvolved, isRemovableReviewer} from '../../../utils/change-util';
import {assertIsDefined} from '../../../utils/common-util';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, nothing} from 'lit';
import {ifDefined} from 'lit/directives/if-defined.js';
import {EventType} from '../../../types/events';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {createSearchUrl} from '../../../models/views/search';
import {createDashboardUrl} from '../../../models/views/dashboard';
import {fire, fireEvent} from '../../../utils/event-util';
import {userModelToken} from '../../../models/user/user-model';

@customElement('gr-hovercard-account-contents')
export class GrHovercardAccountContents extends LitElement {
  @property({type: Object})
  account!: AccountInfo;

  @state()
  selfAccount?: AccountInfo;

  /**
   * Optional ChangeInfo object, typically comes from the change page or
   * from a row in a list of search results. This is needed for some change
   * related features like adding the user as a reviewer.
   */
  @property({type: Object})
  change?: ChangeInfo;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @state()
  serverConfig?: ServerInfo;

  private readonly restApiService = getAppContext().restApiService;

  private readonly reporting = getAppContext().reportingService;

  private readonly getUserModel = resolve(this, userModelToken);

  private readonly getConfigModel = resolve(this, configModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.selfAccount = x)
    );
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => {
        this.serverConfig = config;
      }
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        .top,
        .attention,
        .status,
        .voteable {
          padding: var(--spacing-s) var(--spacing-l);
        }
        .links {
          padding: var(--spacing-m) 0px var(--spacing-l) var(--spacing-xxl);
        }
        .top {
          display: flex;
          padding-top: var(--spacing-xl);
          min-width: 300px;
        }
        gr-avatar {
          height: 48px;
          width: 48px;
          margin-right: var(--spacing-l);
        }
        .title,
        .email {
          color: var(--deemphasized-text-color);
        }
        .action {
          border-top: 1px solid var(--border-color);
          padding: var(--spacing-s) var(--spacing-l);
          --gr-button-padding: var(--spacing-s) var(--spacing-m);
        }
        .attention {
          background-color: var(--emphasis-color);
        }
        .attention a {
          text-decoration: none;
        }
        .status gr-icon {
          font-size: 14px;
          position: relative;
          top: 2px;
        }
        gr-icon.attentionIcon {
          transform: scaleX(0.8);
        }
        gr-icon.linkIcon {
          font-size: var(--line-height-normal, 20px);
          color: var(--deemphasized-text-color);
          padding-right: 12px;
        }
        .links a {
          color: var(--link-color);
          padding: 0px 4px;
        }
        .reason {
          padding-top: var(--spacing-s);
        }
        .status .value {
          white-space: pre-wrap;
        }
      `,
    ];
  }

  override render() {
    return html`
      <div class="top">
        <div class="avatar">
          <gr-avatar .account=${this.account} .imageSize=${56}></gr-avatar>
        </div>
        <div class="account">
          <h3 class="name heading-3">${this.account.name}</h3>
          <div class="email">${this.account.email}</div>
        </div>
      </div>
      ${this.renderAccountStatusPlugins()} ${this.renderAccountStatus()}
      ${this.renderLinks()} ${this.renderChangeRelatedInfoAndActions()}
    `;
  }

  private renderChangeRelatedInfoAndActions() {
    if (this.change === undefined) {
      return nothing;
    }
    const voteableText = computeVoteableText(this.change, this.account);
    return html`
      ${voteableText
        ? html`
            <div class="voteable">
              <span class="title">Voteable:</span>
              <span class="value">${voteableText}</span>
            </div>
          `
        : ''}
      ${this.renderNeedsAttention()} ${this.renderAddToAttention()}
      ${this.renderRemoveFromAttention()} ${this.renderReviewerOrCcActions()}
    `;
  }

  private renderReviewerOrCcActions() {
    // `selfAccount` is required so that logged out users can't perform actions.
    if (!this.selfAccount || !isRemovableReviewer(this.change, this.account))
      return nothing;
    return html`
      <div class="action">
        <gr-button
          class="removeReviewerOrCC"
          link
          no-uppercase
          @click=${this.handleRemoveReviewerOrCC}
        >
          Remove ${this.computeReviewerOrCCText()}
        </gr-button>
      </div>
      <div class="action">
        <gr-button
          class="changeReviewerOrCC"
          link
          no-uppercase
          @click=${this.handleChangeReviewerOrCCStatus}
        >
          ${this.computeChangeReviewerOrCCText()}
        </gr-button>
      </div>
    `;
  }

  private renderAccountStatusPlugins() {
    return html`
      <gr-endpoint-decorator name="hovercard-status">
        <gr-endpoint-param
          name="account"
          .value=${this.account}
        ></gr-endpoint-param>
      </gr-endpoint-decorator>
    `;
  }

  private renderLinks() {
    if (!this.account || isAccountEmailOnly(this.account)) return nothing;
    return html` <div class="links">
      <gr-icon icon="link" class="linkIcon"></gr-icon>
      <a
        href=${ifDefined(this.computeOwnerChangesLink())}
        @click=${() => {
          fireEvent(this, 'link-clicked');
        }}
        @enter=${() => {
          fireEvent(this, 'link-clicked');
        }}
      >
        Changes
      </a>
      ·
      <a
        href=${ifDefined(this.computeOwnerDashboardLink())}
        @click=${() => {
          fireEvent(this, 'link-clicked');
        }}
        @enter=${() => {
          fireEvent(this, 'link-clicked');
        }}
      >
        Dashboard
      </a>
    </div>`;
  }

  private renderAccountStatus() {
    if (!this.account.status) return nothing;
    return html`
      <div class="status">
        <span class="title">About me:</span>
        <span class="value">${this.account.status}</span>
      </div>
    `;
  }

  private renderNeedsAttention() {
    if (!(this.isAttentionEnabled && this.hasUserAttention)) return nothing;
    const lastUpdate = getLastUpdate(this.account, this.change);
    return html`
      <div class="attention">
        <div>
          <gr-icon
            icon="label_important"
            filled
            small
            class="attentionIcon"
          ></gr-icon>
          <span> ${this.computePronoun()} turn to take action. </span>
          <a
            href="https://gerrit-review.googlesource.com/Documentation/user-attention-set.html"
            target="_blank"
          >
            <gr-icon icon="help" title="read documentation"></gr-icon>
          </a>
        </div>
        <div class="reason">
          <span class="title">Reason:</span>
          <span class="value">
            ${getReason(this.serverConfig, this.account, this.change)}
          </span>
          ${lastUpdate
            ? html` (
                <gr-date-formatter
                  withTooltip
                  .dateStr=${lastUpdate}
                ></gr-date-formatter>
                )`
            : ''}
        </div>
      </div>
    `;
  }

  private renderAddToAttention() {
    if (!this.computeShowActionAddToAttentionSet()) return nothing;
    return html`
      <div class="action">
        <gr-button
          class="addToAttentionSet"
          link
          no-uppercase
          @click=${this.handleClickAddToAttentionSet}
        >
          Add to attention set
        </gr-button>
      </div>
    `;
  }

  private renderRemoveFromAttention() {
    if (!this.computeShowActionRemoveFromAttentionSet()) return nothing;
    return html`
      <div class="action">
        <gr-button
          class="removeFromAttentionSet"
          link
          no-uppercase
          @click=${this.handleClickRemoveFromAttentionSet}
        >
          Remove from attention set
        </gr-button>
      </div>
    `;
  }

  // private but used by tests
  computePronoun() {
    if (!this.account || !this.selfAccount) return '';
    return isSelf(this.account, this.selfAccount) ? 'Your' : 'Their';
  }

  computeOwnerChangesLink() {
    if (!this.account) return undefined;
    return createSearchUrl({
      owner:
        this.account.email ||
        this.account.username ||
        this.account.name ||
        `${this.account._account_id}`,
    });
  }

  computeOwnerDashboardLink() {
    if (!this.account) return undefined;
    if (this.account._account_id)
      return createDashboardUrl({user: `${this.account._account_id}`});
    if (this.account.email)
      return createDashboardUrl({user: this.account.email});
    return undefined;
  }

  get isAttentionEnabled() {
    return (
      !!this.highlightAttention &&
      !!this.change &&
      canHaveAttention(this.account)
    );
  }

  get hasUserAttention() {
    return hasAttention(this.account, this.change);
  }

  private getReviewerState(change: ChangeInfo) {
    if (
      change.reviewers[ReviewerState.REVIEWER]?.some(
        (reviewer: AccountInfo) =>
          reviewer._account_id === this.account._account_id
      )
    ) {
      return ReviewerState.REVIEWER;
    }
    return ReviewerState.CC;
  }

  private computeReviewerOrCCText() {
    if (!this.change || !this.account) return '';
    return this.getReviewerState(this.change) === ReviewerState.REVIEWER
      ? 'Reviewer'
      : 'CC';
  }

  private computeChangeReviewerOrCCText() {
    if (!this.change || !this.account) return '';
    return this.getReviewerState(this.change) === ReviewerState.REVIEWER
      ? 'Move Reviewer to CC'
      : 'Move CC to Reviewer';
  }

  private handleChangeReviewerOrCCStatus() {
    assertIsDefined(this.change, 'change');
    // accountKey() throws an error if _account_id & email is not found, which
    // we want to check before showing reloading toast
    const _accountKey = accountKey(this.account);
    fire(this, EventType.SHOW_ALERT, {
      message: 'Reloading page...',
    });
    const reviewInput: Partial<ReviewInput> = {};
    reviewInput.reviewers = [
      {
        reviewer: _accountKey,
        state:
          this.getReviewerState(this.change) === ReviewerState.CC
            ? ReviewerState.REVIEWER
            : ReviewerState.CC,
      },
    ];

    this.restApiService
      .saveChangeReview(this.change._number, CURRENT, reviewInput)
      .then(response => {
        if (!response || !response.ok) {
          throw new Error(
            'something went wrong when toggling' +
              this.getReviewerState(this.change!)
          );
        }
        fire(this, 'reload', {clearPatchset: true});
      });
  }

  private handleRemoveReviewerOrCC() {
    if (!this.change || !(this.account?._account_id || this.account?.email))
      throw new Error('Missing change or account.');
    fire(this, EventType.SHOW_ALERT, {
      message: 'Reloading page...',
    });
    this.restApiService
      .removeChangeReviewer(
        this.change._number,
        (this.account?._account_id || this.account?.email)!
      )
      .then((response: Response | undefined) => {
        if (!response || !response.ok) {
          throw new Error('something went wrong when removing user');
        }
        fire(this, 'reload', {clearPatchset: true});
        return response;
      });
  }

  private computeShowActionAddToAttentionSet() {
    const involvedOrSelf =
      isInvolved(this.change, this.selfAccount) ||
      isSelf(this.account, this.selfAccount);
    return involvedOrSelf && this.isAttentionEnabled && !this.hasUserAttention;
  }

  private computeShowActionRemoveFromAttentionSet() {
    const involvedOrSelf =
      isInvolved(this.change, this.selfAccount) ||
      isSelf(this.account, this.selfAccount);
    return involvedOrSelf && this.isAttentionEnabled && this.hasUserAttention;
  }

  private handleClickAddToAttentionSet() {
    if (!this.change || !this.account._account_id) return;
    fire(this, EventType.SHOW_ALERT, {
      message: 'Reloading page...',
      dismissOnNavigation: true,
    });

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.
    const reason = getAddedByReason(this.selfAccount, this.serverConfig);

    if (!this.change.attention_set) this.change.attention_set = {};
    this.change.attention_set[this.account._account_id] = {
      account: this.account,
      reason,
      reason_account: this.selfAccount,
    };
    fireEvent(this, 'attention-set-updated');

    this.reporting.reportInteraction(
      'attention-hovercard-add',
      this.reportingDetails()
    );
    this.restApiService
      .addToAttentionSet(this.change._number, this.account._account_id, reason)
      .then(() => {
        fireEvent(this, 'hide-alert');
      });
    fireEvent(this, 'action-taken');
  }

  private handleClickRemoveFromAttentionSet() {
    if (!this.change || !this.account._account_id) return;
    fire(this, EventType.SHOW_ALERT, {
      message: 'Saving attention set update ...',
      dismissOnNavigation: true,
    });

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.

    const reason = getRemovedByReason(this.selfAccount, this.serverConfig);
    if (this.change.attention_set)
      delete this.change.attention_set[this.account._account_id];
    fireEvent(this, 'attention-set-updated');

    this.reporting.reportInteraction(
      'attention-hovercard-remove',
      this.reportingDetails()
    );
    this.restApiService
      .removeFromAttentionSet(
        this.change._number,
        this.account._account_id,
        reason
      )
      .then(() => {
        fireEvent(this, 'hide-alert');
      });
    fireEvent(this, 'action-taken');
  }

  private reportingDetails() {
    const targetId = this.account._account_id;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;
    const selfId = (this.selfAccount && this.selfAccount._account_id) || -1;
    const reviewers =
      this.change && this.change.reviewers && this.change.reviewers.REVIEWER
        ? [...this.change.reviewers.REVIEWER]
        : [];
    const reviewerIds = reviewers
      .map(r => r._account_id)
      .filter(rId => rId !== ownerId);
    return {
      actionByOwner: selfId === ownerId,
      actionByReviewer: selfId !== -1 && reviewerIds.includes(selfId),
      targetIsOwner: targetId === ownerId,
      targetIsReviewer: reviewerIds.includes(targetId),
      targetIsSelf: targetId === selfId,
    };
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-hovercard-account-contents': GrHovercardAccountContents;
  }
}
