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
import {getAppContext} from '../../../services/app-context';
import {AuthType} from '../../../constants/constants';
import {css, html} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';

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

  private readonly restApiService = getAppContext().restApiService;

  static styles = [
    sharedStyles,
    formStyles,
    css`
      tr th.emailAddressHeader,
      tr th.identityHeader {
        width: 15em;
        padding: 0 10px;
      }
      tr td.statusColumn,
      tr td.emailAddressColumn,
      tr td.identityColumn {
        word-break: break-word;
      }
      tr td.emailAddressColumn,
      tr td.identityColumn {
        padding: 4px 10px;
        width: 15em;
      }
      .deleteButton {
        float: right;
      }
      .deleteButton:not(.show) {
        display: none;
      }
      .space {
        margin-bottom: var(--spacing-l);
      }
    `,
  ];

  render() {
    return html`<div class="gr-form-styles">
        <fieldset class="space">
          <table>
            <thead>
              <tr>
                <th class="statusHeader">Status</th>
                <th class="emailAddressHeader">Email Address</th>
                <th class="identityHeader">Identity</th>
                <th class="deleteHeader"></th>
              </tr>
            </thead>
            <tbody>
              <template
                is="dom-repeat"
                items="[[_identities]]"
                filter="filterIdentities"
              >
                <tr>
                  <td class="statusColumn">
                    [[_computeIsTrusted(item.trusted)]]
                  </td>
                  <td class="emailAddressColumn">[[item.email_address]]</td>
                  <td class="identityColumn">
                    [[_computeIdentity(item.identity)]]
                  </td>
                  <td class="deleteColumn">
                    <gr-button
                      class$="deleteButton [[_computeHideDeleteClass(item.can_delete)]]"
                      on-click="_handleDeleteItem"
                    >
                      Delete
                    </gr-button>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </fieldset>
        <template is="dom-if" if="[[_showLinkAnotherIdentity]]">
          <fieldset>
            <a href$="[[_computeLinkAnotherIdentity()]]">
              <gr-button id="linkAnotherIdentity" link=""
                >Link Another Identity</gr-button
              >
            </a>
          </fieldset>
        </template>
      </div>
      <gr-overlay id="overlay" with-backdrop="">
        <gr-confirm-delete-item-dialog
          class="confirmDialog"
          on-confirm="_handleDeleteItemConfirm"
          on-cancel="_handleConfirmDialogCancel"
          item="[[_idName]]"
          itemTypeName="ID"
        ></gr-confirm-delete-item-dialog>
      </gr-overlay>`;
  }

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
