/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-icon/iron-icon';
import '../gr-avatar/gr-avatar';
import '../gr-hovercard-account/gr-hovercard-account';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import {getAppContext} from '../../../services/app-context';
import {getDisplayName} from '../../../utils/display-name-util';
import {isSelf, isServiceUser} from '../../../utils/account-util';
import {ChangeInfo, AccountInfo, ServerInfo} from '../../../types/common';
import {hasOwnProperty} from '../../../utils/common-util';
import {fireEvent} from '../../../utils/event-util';
import {isInvolved} from '../../../utils/change-util';
import {ShowAlertEventDetail} from '../../../types/events';
import {LitElement, css, html, TemplateResult} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {classMap} from 'lit/directives/class-map';
import {getRemovedByIconClickReason} from '../../../utils/attention-set-util';
import {ifDefined} from 'lit/directives/if-defined';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';

@customElement('gr-account-label')
export class GrAccountLabel extends LitElement {
  @property({type: Object})
  account?: AccountInfo;

  @property({type: Object})
  _selfAccount?: AccountInfo;

  /**
   * Optional ChangeInfo object, typically comes from the change page or
   * from a row in a list of search results. This is needed for some change
   * related features like adding the user as a reviewer.
   */
  @property({type: Object})
  change?: ChangeInfo;

  @property({type: String})
  voteableText?: string;

  /**
   * Should this user be considered to be in the attention set, regardless
   * of the current state of the change object?
   */
  @property({type: Boolean})
  forceAttention = false;

  /**
   * Only show the first name in the account label.
   */
  @property({type: Boolean})
  firstName = false;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @property({type: Boolean})
  hideHovercard = false;

  @property({type: Boolean})
  hideAvatar = false;

  @state()
  _config?: ServerInfo;

  @property({type: Boolean, reflect: true})
  selectionChipStyle = false;

  @property({type: Boolean, reflect: true})
  noStatusIcons = false;

  @property({
    type: Boolean,
    reflect: true,
  })
  selected = false;

  @property({type: Boolean, reflect: true})
  deselected = false;

  @property({type: Boolean, reflect: true})
  clickable = false;

  @property({type: Boolean, reflect: true})
  attentionIconShown = false;

  @property({type: Boolean, reflect: true})
  avatarShown = false;

  readonly reporting = getAppContext().reportingService;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          vertical-align: top;
          position: relative;
          border-radius: var(--label-border-radius);
          box-sizing: border-box;
          white-space: nowrap;
          padding-left: var(--account-label-padding-left, 0);
        }
        :host([avatarShown]:not([attentionIconShown])) {
          padding-left: var(--account-label-circle-padding-left, 0);
        }
        :host([attentionIconShown]) {
          padding-left: var(--account-label-padding-left, 0);
        }
        .rightSidePadding {
          padding-right: var(--account-label-padding-right, 0);
          /* The existence of this element will also add 2(!) flexbox gaps */
          margin-left: -6px;
        }
        .container {
          display: flex;
          align-items: center;
          gap: 3px;
        }
        :host::after {
          content: var(--account-label-suffix);
        }
        :host([deselected][selectionChipStyle]) {
          background-color: var(--background-color-primary);
          border: 1px solid var(--comment-separator-color);
          border-radius: 8px;
          color: var(--deemphasized-text-color);
        }
        :host([selected][selectionChipStyle]) {
          background-color: var(--chip-selected-background-color);
          border: 1px solid var(--chip-selected-background-color);
          border-radius: 8px;
          color: var(--chip-selected-text-color);
        }
        :host([selected]) iron-icon.attention {
          color: var(--chip-selected-text-color);
        }
        gr-avatar {
          height: calc(var(--line-height-normal) - 2px);
          width: calc(var(--line-height-normal) - 2px);
        }
        .accountStatusDecorator,
        .hovercardTargetWrapper {
          display: contents;
        }
        #attentionButton {
          /* This negates the 4px horizontal padding, which we appreciate as a
         larger click target, but which we don't want to consume space. :-) */
          margin: 0 -4px 0 -4px;
          vertical-align: top;
        }
        iron-icon.attention {
          color: var(--deemphasized-text-color);
          width: 12px;
          height: 12px;
        }
        .name {
          display: inline-block;
          vertical-align: top;
          overflow: hidden;
          text-overflow: ellipsis;
          max-width: var(--account-max-length, 180px);
        }
        .hasAttention .name {
          font-weight: var(--font-weight-bold);
        }
        a.ownerLink {
          text-decoration: none;
          color: var(--primary-text-color);
          display: flex;
          align-items: center;
          gap: 3px;
        }
        :host([clickable]) a.ownerLink:hover .name {
          text-decoration: underline;
        }
      `,
    ];
  }

  override render() {
    const {account, change, highlightAttention, forceAttention, _config} = this;
    if (!account) return;
    this.attentionIconShown =
      forceAttention ||
      this.hasUnforcedAttention(highlightAttention, account, change);
    this.deselected = !this.selected;
    const hasAvatars = !!_config?.plugin?.has_avatars;
    this.avatarShown = !this.hideAvatar && hasAvatars;

    return html`
      <div class="container">
        ${!this.hideHovercard
          ? html`<gr-hovercard-account
              for="hovercardTarget"
              .account=${account}
              .change=${change}
              .highlightAttention=${highlightAttention}
              .voteableText=${this.voteableText}
            ></gr-hovercard-account>`
          : ''}
        ${this.attentionIconShown
          ? html` <gr-tooltip-content
              ?has-tooltip=${this.computeAttentionButtonEnabled(
                highlightAttention,
                account,
                change,
                false,
                this._selfAccount
              )}
              title=${this.computeAttentionIconTitle(
                highlightAttention,
                account,
                change,
                forceAttention,
                this.selected,
                this._selfAccount
              )}
            >
              <gr-button
                id="attentionButton"
                link=""
                aria-label="Remove user from attention set"
                @click=${this.handleRemoveAttentionClick}
                ?disabled=${!this.computeAttentionButtonEnabled(
                  highlightAttention,
                  account,
                  change,
                  this.selected,
                  this._selfAccount
                )}
                ><iron-icon
                  class="attention"
                  icon="gr-icons:attention"
                ></iron-icon>
              </gr-button>
            </gr-tooltip-content>`
          : ''}
        ${this.maybeRenderLink(html`
          <span
            class=${classMap({
              hovercardTargetWrapper: true,
              hasAttention: this.attentionIconShown,
            })}
          >
            ${this.avatarShown
              ? html`<gr-avatar .account=${account} imageSize="32"></gr-avatar>`
              : ''}
            <span
              tabindex=${this.hideHovercard ? '-1' : '0'}
              role=${ifDefined(this.hideHovercard ? undefined : 'button')}
              id="hovercardTarget"
              class="name"
              part="gr-account-label-text"
            >
              ${this.computeName(account, this.firstName, this._config)}
            </span>
            ${this.renderAccountStatusPlugins()}
          </span>
        `)}
      </div>
    `;
  }

  constructor() {
    super();
    this.restApiService.getConfig().then(config => {
      this._config = config;
    });
    this.restApiService.getAccount().then(account => {
      this._selfAccount = account;
    });
    this.addEventListener('attention-set-updated', () => {
      // For re-evaluation of everything that depends on 'change'.
      if (this.change) this.change = {...this.change};
    });
  }

  private maybeRenderLink(span: TemplateResult) {
    if (!this.clickable || !this.account) return span;
    const url = GerritNav.getUrlForOwner(
      this.account.email ||
        this.account.username ||
        this.account.name ||
        `${this.account._account_id}`
    );
    if (!url) return span;
    return html`<a class="ownerLink" href=${url} tabindex="-1">${span}</a>`;
  }

  private renderAccountStatusPlugins() {
    if (!this.account?._account_id || this.noStatusIcons) {
      return;
    }
    return html`
      <gr-endpoint-decorator
        class="accountStatusDecorator"
        name="account-status-icon"
      >
        <gr-endpoint-param
          name="accountId"
          .value=${this.account._account_id}
        ></gr-endpoint-param>
        <span class="rightSidePadding"></span>
      </gr-endpoint-decorator>
    `;
  }

  private isAttentionSetEnabled(
    highlight: boolean,
    account: AccountInfo,
    change?: ChangeInfo
  ) {
    return highlight && !!change && !!account && !isServiceUser(account);
  }

  private hasUnforcedAttention(
    highlight: boolean,
    account: AccountInfo,
    change?: ChangeInfo
  ): boolean {
    return !!(
      this.isAttentionSetEnabled(highlight, account, change) &&
      change &&
      change.attention_set &&
      !!account._account_id &&
      hasOwnProperty(change.attention_set, account._account_id)
    );
  }

  // Private but used in tests.
  computeName(account?: AccountInfo, firstName?: boolean, config?: ServerInfo) {
    return getDisplayName(config, account, firstName);
  }

  private handleRemoveAttentionClick(e: MouseEvent) {
    if (!this.account || !this.change) return;
    if (this.selected) return;
    e.preventDefault();
    e.stopPropagation();
    if (!this.account._account_id) return;

    this.dispatchEvent(
      new CustomEvent<ShowAlertEventDetail>('show-alert', {
        detail: {
          message: 'Saving attention set update ...',
          dismissOnNavigation: true,
        },
        composed: true,
        bubbles: true,
      })
    );

    // We are deliberately updating the UI before making the API call. It is a
    // risk that we are taking to achieve a better UX for 99.9% of the cases.
    const reason = getRemovedByIconClickReason(this._selfAccount, this._config);
    if (this.change.attention_set)
      delete this.change.attention_set[this.account._account_id];
    // For re-evaluation of everything that depends on 'change'.
    this.change = {...this.change};

    this.reporting.reportInteraction(
      'attention-icon-remove',
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
  }

  private reportingDetails() {
    if (!this.account) return;
    const targetId = this.account._account_id;
    const ownerId =
      (this.change && this.change.owner && this.change.owner._account_id) || -1;
    const selfId = this._selfAccount?._account_id || -1;
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

  private computeAttentionButtonEnabled(
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo | undefined,
    selected: boolean,
    selfAccount?: AccountInfo
  ) {
    if (selected) return true;
    return (
      !!this.hasUnforcedAttention(highlight, account, change) &&
      (isInvolved(change, selfAccount) || isSelf(account, selfAccount))
    );
  }

  private computeAttentionIconTitle(
    highlight: boolean,
    account: AccountInfo,
    change: ChangeInfo | undefined,
    force: boolean,
    selected: boolean,
    selfAccount?: AccountInfo
  ) {
    const enabled = this.computeAttentionButtonEnabled(
      highlight,
      account,
      change,
      selected,
      selfAccount
    );
    return enabled
      ? 'Click to remove the user from the attention set'
      : force
      ? 'Disabled. Use "Modify" to make changes.'
      : 'Disabled. Only involved users can change.';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-label': GrAccountLabel;
  }
}
