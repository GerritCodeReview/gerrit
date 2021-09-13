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

import '@polymer/iron-input/iron-input';
import '../../../styles/gr-font-styles';
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-cla-view_html';
import {getBaseUrl} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import {
  ServerInfo,
  GroupInfo,
  ContributorAgreementInfo,
} from '../../../types/common';
import {fireAlert, fireTitleChange} from '../../../utils/event-util';
import {appContext} from '../../../services/app-context';

declare global {
  interface HTMLElementTagNameMap {
    'gr-cla-view': GrClaView;
  }
}

@customElement('gr-cla-view')
export class GrClaView extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  _groups?: GroupInfo[];

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({type: String})
  _agreementsText?: string;

  @property({type: String})
  _agreementName?: string;

  @property({type: Array})
  _signedAgreements?: ContributorAgreementInfo[];

  @property({type: Boolean})
  _showAgreements = false;

  @property({type: String})
  _agreementsUrl?: string;

  private readonly restApiService = appContext.restApiService;

  override connectedCallback() {
    super.connectedCallback();
    this.loadData();

    fireTitleChange(this, 'New Contributor Agreement');
  }

  loadData() {
    const promises = [];
    promises.push(
      this.restApiService.getConfig(true).then(config => {
        this._serverConfig = config;
      })
    );

    promises.push(
      this.restApiService.getAccountGroups().then(groups => {
        if (!groups) return;
        this._groups = groups.sort((a, b) =>
          (a.name || '').localeCompare(b.name || '')
        );
      })
    );

    promises.push(
      this.restApiService
        .getAccountAgreements()
        .then((agreements: ContributorAgreementInfo[] | undefined) => {
          this._signedAgreements = agreements || [];
        })
    );

    return Promise.all(promises);
  }

  _getAgreementsUrl(configUrl: string) {
    let url;
    if (!configUrl) {
      return '';
    }
    if (configUrl.startsWith('http:') || configUrl.startsWith('https:')) {
      url = configUrl;
    } else {
      url = getBaseUrl() + '/' + configUrl;
    }

    return url;
  }

  _handleShowAgreement(e: Event) {
    this._agreementName = (e.target as HTMLInputElement).getAttribute(
      'data-name'
    )!;
    const url = (e.target as HTMLInputElement).getAttribute('data-url')!;
    this._agreementsUrl = this._getAgreementsUrl(url);
    this._showAgreements = true;
  }

  _handleSaveAgreements() {
    this._createToast('Agreement saving...');

    const name = this._agreementName;
    return this.restApiService.saveAccountAgreement({name}).then(res => {
      let message = 'Agreement failed to be submitted, please try again';
      if (res.status === 200) {
        message = 'Agreement has been successfully submitted.';
      }
      this._createToast(message);
      this.loadData();
      this._agreementsText = '';
      this._showAgreements = false;
    });
  }

  _createToast(message: string) {
    fireAlert(this, message);
  }

  _computeShowAgreementsClass(showAgreements: boolean) {
    return showAgreements ? 'show' : '';
  }

  _disableAgreements(
    item: ContributorAgreementInfo,
    groups?: GroupInfo[],
    signedAgreements?: ContributorAgreementInfo[]
  ) {
    if (!groups) return false;
    for (const group of groups) {
      if (
        item?.auto_verify_group?.id === group.id ||
        signedAgreements?.find(i => i.name === item.name)
      ) {
        return true;
      }
    }
    return false;
  }

  _hideAgreements(
    item: ContributorAgreementInfo,
    groups?: GroupInfo[],
    signedAgreements?: ContributorAgreementInfo[]
  ) {
    return this._disableAgreements(item, groups, signedAgreements)
      ? ''
      : 'hide';
  }

  _disableAgreementsText(text?: string) {
    return text?.toLowerCase() === 'i agree' ? false : true;
  }

  // This checks for auto_verify_group,
  // if specified it returns 'hideAgreementsTextBox' which
  // then hides the text box and submit button.
  _computeHideAgreementClass(
    name?: string,
    contributorAgreements?: ContributorAgreementInfo[]
  ) {
    if (!name || !contributorAgreements) return '';
    return contributorAgreements.some(
      (contributorAgreement: ContributorAgreementInfo) =>
        name === contributorAgreement.name &&
        !contributorAgreement.auto_verify_group
    )
      ? 'hideAgreementsTextBox'
      : '';
  }
}
