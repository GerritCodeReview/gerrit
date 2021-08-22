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
import '../../../styles/gr-form-styles';
import '../../admin/gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-overlay/gr-overlay';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-identities_html';
import {getBaseUrl} from '../../../utils/url-util';
import {customElement, property} from '@polymer/decorators';
import {AccountExternalIdInfo, ServerInfo} from '../../../types/common';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {PolymerDomRepeatEvent} from '../../../types/types';
import {appContext} from '../../../services/app-context';

const AUTH = [AuthType.OPENID, AuthType.OAUTH];

export interface GrIdentities {
  $: {
    overlay: GrOverlay;
  };
}

@customElement('gr-identities')
export class GrIdentities extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Array})
  _identities: AccountExternalIdInfo[] = [];

  @property({type: String})
  _idName?: string;

  @property({type: Object})
  serverConfig?: ServerInfo;

  @property({
    type: Boolean,
    computed: '_computeShowLinkAnotherIdentity(serverConfig)',
  })
  _showLinkAnotherIdentity?: boolean;

  private readonly restApiService = appContext.restApiService;

  loadData() {
    return this.restApiService.getExternalIds().then(id => {
      this._identities = id ?? [];
    });
  }

  _computeIdentity(id: string) {
    return id && id.startsWith('mailto:') ? '' : id;
  }

  _computeHideDeleteClass(canDelete?: boolean) {
    return canDelete ? 'show' : '';
  }

  _handleDeleteItemConfirm() {
    this.$.overlay.close();
    return this.restApiService
      .deleteAccountIdentity([this._idName!])
      .then(() => {
        this.loadData();
      });
  }

  _handleConfirmDialogCancel() {
    this.$.overlay.close();
  }

  _handleDeleteItem(e: PolymerDomRepeatEvent<AccountExternalIdInfo>) {
    const name = e.model.item.identity;
    if (!name) {
      return;
    }
    this._idName = name;
    this.$.overlay.open();
  }

  _computeIsTrusted(item?: boolean) {
    return item ? '' : 'Untrusted';
  }

  filterIdentities(item: AccountExternalIdInfo) {
    return !item.identity.startsWith('username:');
  }

  _computeShowLinkAnotherIdentity(config?: ServerInfo) {
    if (config?.auth?.auth_type) {
      return AUTH.includes(config.auth.auth_type);
    }

    return false;
  }

  _computeLinkAnotherIdentity() {
    const baseUrl = getBaseUrl() || '';
    let pathname = window.location.pathname;
    if (baseUrl) {
      pathname = '/' + pathname.substring(baseUrl.length);
    }
    return baseUrl + '/login/' + encodeURIComponent(pathname) + '?link';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-identities': GrIdentities;
  }
}
