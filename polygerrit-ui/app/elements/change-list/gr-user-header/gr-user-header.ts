/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-avatar/gr-avatar';
import '../../shared/gr-date-formatter/gr-date-formatter';
import {AccountDetailInfo, UserId} from '../../../types/common';
import {getDisplayName} from '../../../utils/display-name-util';
import {getAppContext} from '../../../services/app-context';
import {dashboardHeaderStyles} from '../../../styles/dashboard-header-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  createDashboardUrl,
  DashboardType,
} from '../../../models/views/dashboard';

@customElement('gr-user-header')
export class GrUserHeader extends LitElement {
  @property({type: String})
  userId?: UserId;

  @property({type: Boolean})
  showDashboardLink = false;

  @property({type: Boolean})
  loggedIn = false;

  @state()
  private accountDetails: AccountDetailInfo | undefined;

  @state()
  private status = '';

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      dashboardHeaderStyles,
      fontStyles,
      css`
        .status.hide,
        .name.hide,
        .dashboardLink.hide {
          display: none;
        }
      `,
    ];
  }

  override render() {
    return html`<gr-avatar
        .account=${this.accountDetails}
        .imageSize=${100}
        aria-label="Account avatar"
      ></gr-avatar>
      <div class="info">
        <h1 class="heading-1">${this.computeHeading(this.accountDetails)}</h1>
        <hr />
        <div class="status ${this.computeStatusClass(this.status)}">
          <span>Status:</span> ${this.status}
        </div>
        <div>
          <span>Email:</span>
          <a href="mailto:${this.computeDetail(this.accountDetails, 'email')}"
            ><!--
          -->${this.computeDetail(this.accountDetails, 'email')}</a
          >
        </div>
        <div>
          <span>Joined:</span>
          <gr-date-formatter
            dateStr=${this.computeDetail(this.accountDetails, 'registered_on')}
          >
          </gr-date-formatter>
        </div>
        <gr-endpoint-decorator name="user-header">
          <gr-endpoint-param
            name="accountDetails"
            .value=${this.accountDetails}
          >
          </gr-endpoint-param>
          <gr-endpoint-param name="loggedIn" .value=${this.loggedIn}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>
      <div class="info">
        <div
          class=${this.computeDashboardLinkClass(
            this.showDashboardLink,
            this.loggedIn
          )}
        >
          <a href=${this.computeDashboardUrl(this.accountDetails)}
            >View dashboard</a
          >
        </div>
      </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('userId')) {
      this.accountChanged(this.userId);
    }
  }

  private accountChanged(userId?: UserId) {
    if (!userId) {
      this.accountDetails = undefined;
      this.status = '';
      return;
    }

    this.restApiService
      .getAccountDetails(userId, () => {})
      .then(details => {
        this.accountDetails = details ?? undefined;
        this.status = details?.status ?? '';
      });
  }

  private computeDetail(
    accountDetails: AccountDetailInfo | undefined,
    name: keyof AccountDetailInfo
  ) {
    return accountDetails ? String(accountDetails[name]) : '';
  }

  private computeHeading(accountDetails: AccountDetailInfo | undefined) {
    if (!accountDetails) return '';
    return getDisplayName(undefined, accountDetails);
  }

  private computeStatusClass(status: string) {
    return status ? '' : 'hide';
  }

  private computeDashboardUrl(accountDetails: AccountDetailInfo | undefined) {
    if (!accountDetails) return '';

    const id = accountDetails._account_id;
    if (id) return createDashboardUrl({type: DashboardType.USER, user: id});

    const email = accountDetails.email;
    if (email)
      return createDashboardUrl({type: DashboardType.USER, user: email});

    return '';
  }

  private computeDashboardLinkClass(
    showDashboardLink: boolean,
    loggedIn: boolean
  ) {
    return showDashboardLink && loggedIn
      ? 'dashboardLink'
      : 'dashboardLink hide';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-user-header': GrUserHeader;
  }
}
