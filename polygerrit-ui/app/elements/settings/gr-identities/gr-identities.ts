/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../admin/gr-confirm-delete-item-dialog/gr-confirm-delete-item-dialog';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-overlay/gr-overlay';
import {getBaseUrl} from '../../../utils/url-util';
import {AccountExternalIdInfo, ServerInfo} from '../../../types/common';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {getAppContext} from '../../../services/app-context';
import {AuthType} from '../../../constants/constants';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {classMap} from 'lit/directives/class-map';
import {when} from 'lit/directives/when.js';
import {assertIsDefined} from '../../../utils/common-util';

const AUTH = [AuthType.OPENID, AuthType.OAUTH];

@customElement('gr-identities')
export class GrIdentities extends LitElement {
  @query('#overlay') overlay?: GrOverlay;

  @state() private identities: AccountExternalIdInfo[] = [];

  // temporary var for communicating with the confirmation dialog
  // private but used in test
  @state() idName?: string;

  @property({type: Object}) serverConfig?: ServerInfo;

  @state() showLinkAnotherIdentity = false;

  private readonly restApiService = getAppContext().restApiService;

  static override styles = [
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

  override render() {
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
              ${this.getIdentities().map((account, index) =>
                this.renderIdentity(account, index)
              )}
            </tbody>
          </table>
        </fieldset>
        ${when(
          this.showLinkAnotherIdentity,
          () => html`<fieldset>
            <a href=${this.computeLinkAnotherIdentity()}>
              <gr-button id="linkAnotherIdentity" link=""
                >Link Another Identity</gr-button
              >
            </a>
          </fieldset>`
        )}
      </div>
      <gr-overlay id="overlay" with-backdrop>
        <gr-confirm-delete-item-dialog
          class="confirmDialog"
          @confirm=${this.handleDeleteItemConfirm}
          @cancel=${this.handleConfirmDialogCancel}
          .item=${this.idName}
          itemtypename="ID"
        ></gr-confirm-delete-item-dialog>
      </gr-overlay>`;
  }

  private renderIdentity(account: AccountExternalIdInfo, index: number) {
    return html`<tr>
      <td class="statusColumn">${account.trusted ? '' : 'Untrusted'}</td>
      <td class="emailAddressColumn">${account.email_address}</td>
      <td class="identityColumn">
        ${account.identity.startsWith('mailto:') ? '' : account.identity}
      </td>
      <td class="deleteColumn">
        <gr-button
          data-index=${index}
          class=${classMap({
            deleteButton: true,
            show: !!account.can_delete,
          })}
          @click=${() => this.handleDeleteItem(account.identity)}
        >
          Delete
        </gr-button>
      </td>
    </tr>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('serverConfig')) {
      this.showLinkAnotherIdentity = this.computeShowLinkAnotherIdentity();
    }
  }

  // private but used in test
  getIdentities() {
    return this.identities.filter(
      account => !account.identity.startsWith('username:')
    );
  }

  loadData() {
    return this.restApiService.getExternalIds().then(ids => {
      this.identities = ids ?? [];
    });
  }

  // private but used in test
  _computeHideDeleteClass(canDelete?: boolean) {
    return canDelete ? 'show' : '';
  }

  handleDeleteItemConfirm() {
    this.overlay?.close();
    assertIsDefined(this.idName);
    return this.restApiService.deleteAccountIdentity([this.idName]).then(() => {
      this.loadData();
    });
  }

  private handleConfirmDialogCancel() {
    this.overlay?.close();
  }

  private handleDeleteItem(name: string) {
    this.idName = name;
    this.overlay?.open();
  }

  // private but used in test
  computeShowLinkAnotherIdentity() {
    if (this.serverConfig?.auth?.auth_type) {
      return AUTH.includes(this.serverConfig.auth.auth_type);
    }

    return false;
  }

  private computeLinkAnotherIdentity() {
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
