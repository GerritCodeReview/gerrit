/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-avatar/gr-avatar';
import '../../shared/gr-date-formatter/gr-date-formatter';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {AccountDetailInfo, AccountId} from '../../../types/common';
import {getDisplayName} from '../../../utils/display-name-util';
import {getAppContext} from '../../../services/app-context';
import {dashboardHeaderStyles} from '../../../styles/dashboard-header-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators';

@customElement('gr-user-header')
export class GrUserHeader extends LitElement {
  @property({type: String})
  userId?: AccountId;

  @property({type: Boolean})
  showDashboardLink = false;

  @property({type: Boolean})
  loggedIn = false;

  @property({type: Object})
  _accountDetails: AccountDetailInfo | undefined;

  @property({type: String})
  _status = '';

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
        .account=${this._accountDetails}
        .imageSize=${100}
        aria-label="Account avatar"
      ></gr-avatar>
      <div class="info">
        <h1 class="heading-1">${this._computeHeading(this._accountDetails)}</h1>
        <hr />
        <div class="status ${this._computeStatusClass(this._status)}">
          <span>Status:</span> ${this._status}
        </div>
        <div>
          <span>Email:</span>
          <a href="mailto:${this._computeDetail(this._accountDetails, 'email')}"
            ><!--
          -->${this._computeDetail(this._accountDetails, 'email')}</a
          >
        </div>
        <div>
          <span>Joined:</span>
          <gr-date-formatter
            dateStr=${this._computeDetail(
              this._accountDetails,
              'registered_on'
            )}
          >
          </gr-date-formatter>
        </div>
        <gr-endpoint-decorator name="user-header">
          <gr-endpoint-param
            name="accountDetails"
            .value=${this._accountDetails}
          >
          </gr-endpoint-param>
          <gr-endpoint-param name="loggedIn" .value=${this.loggedIn}>
          </gr-endpoint-param>
        </gr-endpoint-decorator>
      </div>
      <div class="info">
        <div
          class=${this._computeDashboardLinkClass(
            this.showDashboardLink,
            this.loggedIn
          )}
        >
          <a href=${this._computeDashboardUrl(this._accountDetails)}
            >View dashboard</a
          >
        </div>
      </div>`;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('userId')) {
      this._accountChanged(this.userId);
    }
  }

  _accountChanged(userId?: AccountId) {
    if (!userId) {
      this._accountDetails = undefined;
      this._status = '';
      return;
    }

    this.restApiService.getAccountDetails(userId).then(details => {
      this._accountDetails = details ?? undefined;
      this._status = details?.status ?? '';
    });
  }

  _computeDetail(
    accountDetails: AccountDetailInfo | undefined,
    name: keyof AccountDetailInfo
  ) {
    return accountDetails ? String(accountDetails[name]) : '';
  }

  _computeHeading(accountDetails: AccountDetailInfo | undefined) {
    if (!accountDetails) return '';
    return getDisplayName(undefined, accountDetails);
  }

  _computeStatusClass(status: string) {
    return status ? '' : 'hide';
  }

  _computeDashboardUrl(accountDetails: AccountDetailInfo | undefined) {
    if (!accountDetails) {
      return undefined;
    }
    const id = accountDetails._account_id;
    if (id) {
      return GerritNav.getUrlForUserDashboard(String(id));
    }
    const email = accountDetails.email;
    if (email) {
      return GerritNav.getUrlForUserDashboard(email);
    }
    return undefined;
  }

  _computeDashboardLinkClass(showDashboardLink: boolean, loggedIn: boolean) {
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
