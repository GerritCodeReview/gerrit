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
import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-cla-view_html';
import {getBaseUrl} from '../../../utils/url-util';
import { GrRestApiInterface } from '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {customElement, property} from '@polymer/decorators';
import { ServerInfo } from '../../../types/common';

export interface GrClaView {
  $: {
    restAPI: GrRestApiInterface;
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-cla-view': GrClaView;
  }
}

@customElement('gr-cla-view')
export class GrClaView extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  _groups?: Object

  @property({type: Object})
  _serverConfig?: ServerInfo;

  @property({type: String})
  _agreementsText?: string;

  @property({type: String})
  _agreementName?: string;

  @property({type: Array})
  _signedAgreements?: Array

  @property({type: Boolean})
  _showAgreements = false;

  @property({type: String})
  _agreementsUrl: string;

  /** @override */
  attached() {
    super.attached();
    this.loadData();

    this.dispatchEvent(
      new CustomEvent('title-change', {
        detail: {title: 'New Contributor Agreement'},
        composed: true,
        bubbles: true,
      })
    );
  }

  loadData() {
    const promises = [];
    promises.push(
      this.$.restAPI.getConfig(true).then(config => {
        this._serverConfig = config;
      })
    );

    promises.push(
      this.$.restAPI.getAccountGroups().then(groups => {
        this._groups = groups.sort((a, b) => a.name.localeCompare(b.name));
      })
    );

    promises.push(
      this.$.restAPI.getAccountAgreements().then(agreements => {
        this._signedAgreements = agreements || [];
      })
    );

    return Promise.all(promises);
  }

  _getAgreementsUrl(configUrl) {
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

  _handleShowAgreement(e) {
    this._agreementName = e.target.getAttribute('data-name');
    this._agreementsUrl = this._getAgreementsUrl(
      e.target.getAttribute('data-url')
    );
    this._showAgreements = true;
  }

  _handleSaveAgreements(e) {
    this._createToast('Agreement saving...');

    const name = this._agreementName;
    return this.$.restAPI.saveAccountAgreement({name}).then(res => {
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

  _createToast(message) {
    this.dispatchEvent(
      new CustomEvent('show-alert', {
        detail: {message},
        bubbles: true,
        composed: true,
      })
    );
  }

  _computeShowAgreementsClass(agreements) {
    return agreements ? 'show' : '';
  }

  _disableAgreements(item, groups, signedAgreements) {
    if (!groups) return false;
    for (const group of groups) {
      if (
        (item &&
          item.auto_verify_group &&
          item.auto_verify_group.id === group.id) ||
        signedAgreements.find(i => i.name === item.name)
      ) {
        return true;
      }
    }
    return false;
  }

  _hideAgreements(item, groups, signedAgreements) {
    return this._disableAgreements(item, groups, signedAgreements)
      ? ''
      : 'hide';
  }

  _disableAgreementsText(text) {
    return text.toLowerCase() === 'i agree' ? false : true;
  }

  // This checks for auto_verify_group,
  // if specified it returns 'hideAgreementsTextBox' which
  // then hides the text box and submit button.
  _computeHideAgreementClass(name, config) {
    if (!config) return '';
    for (const key in config) {
      if (!config.hasOwnProperty(key)) {
        continue;
      }
      for (const prop in config[key]) {
        if (!config[key].hasOwnProperty(prop)) {
          continue;
        }
        if (name === config[key].name && !config[key].auto_verify_group) {
          return 'hideAgreementsTextBox';
        }
      }
    }
  }
}
