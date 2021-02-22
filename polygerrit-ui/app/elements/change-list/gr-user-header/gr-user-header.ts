/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import '../../plugins/gr-endpoint-param/gr-endpoint-param';
import '../../shared/gr-avatar/gr-avatar';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../../styles/dashboard-header-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-user-header_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {AccountDetailInfo, AccountId} from '../../../types/common';
import {getDisplayName} from '../../../utils/display-name-util';
import {appContext} from '../../../services/app-context';

@customElement('gr-user-header')
export class GrUserHeader extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, observer: '_accountChanged'})
  userId?: AccountId;

  @property({type: Boolean})
  showDashboardLink = false;

  @property({type: Boolean})
  loggedIn = false;

  @property({type: Object})
  _accountDetails: AccountDetailInfo | null = null;

  @property({type: String})
  _status = '';

  private readonly restApiService = appContext.restApiService;

  _accountChanged(userId?: AccountId) {
    if (!userId) {
      this._accountDetails = null;
      this._status = '';
      return;
    }

    this.restApiService.getAccountDetails(userId).then(details => {
      this._accountDetails = details ?? null;
      this._status = details?.status ?? '';
    });
  }

  _computeDetail(
    accountDetails: AccountDetailInfo | null,
    name: keyof AccountDetailInfo
  ) {
    return accountDetails ? accountDetails[name] : '';
  }

  _computeHeading(accountDetails: AccountDetailInfo | null) {
    if (!accountDetails) return '';
    return getDisplayName(undefined, accountDetails);
  }

  _computeStatusClass(status: string) {
    return status ? '' : 'hide';
  }

  _computeDashboardUrl(accountDetails: AccountDetailInfo | null) {
    if (!accountDetails) {
      return null;
    }
    const id = accountDetails._account_id;
    if (id) {
      return GerritNav.getUrlForUserDashboard(String(id));
    }
    const email = accountDetails.email;
    if (email) {
      return GerritNav.getUrlForUserDashboard(email);
    }
    return null;
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
