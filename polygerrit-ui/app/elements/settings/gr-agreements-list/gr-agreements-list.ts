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

import '../../../styles/gr-form-styles';
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-agreements-list_html';
import {getBaseUrl} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import {ContributorAgreementInfo} from '../../../types/common';
import {appContext} from '../../../services/app-context';

@customElement('gr-agreements-list')
export class GrAgreementsList extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  _agreements?: ContributorAgreementInfo[];

  private readonly restApiService = appContext.restApiService;

  /** @override */
  connectedCallback() {
    super.connectedCallback();
    this.loadData();
  }

  loadData() {
    return this.restApiService.getAccountAgreements().then(agreements => {
      this._agreements = agreements;
    });
  }

  getUrl() {
    return `${getBaseUrl()}/settings/new-agreement`;
  }

  getUrlBase(item: string) {
    return `${getBaseUrl()}/${item}`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-agreements-list': GrAgreementsList;
  }
}
