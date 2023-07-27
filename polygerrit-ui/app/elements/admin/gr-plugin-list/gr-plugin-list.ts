/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-list-view/gr-list-view';
import {PluginInfo} from '../../../types/common';
import {firePageError, fireTitleChange} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {encodeURL, getBaseUrl} from '../../../utils/url-util';
import {tableStyles} from '../../../styles/gr-table-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, css, html} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  AdminChildView,
  AdminViewState,
  createAdminUrl,
} from '../../../models/views/admin';

// Exported for tests
export interface PluginInfoWithName extends PluginInfo {
  name: string;
}

@customElement('gr-plugin-list')
export class GrPluginList extends LitElement {
  /**
   * URL params passed from the router.
   */
  @property({type: Object})
  params?: AdminViewState;

  /**
   * Offset of currently visible query results.
   */
  @state() offset = 0;

  @state() plugins?: PluginInfoWithName[];

  @state() pluginsPerPage = 25;

  @state() loading = true;

  @state() filter = '';

  private readonly restApiService = getAppContext().restApiService;

  override connectedCallback() {
    super.connectedCallback();
    fireTitleChange('Plugins');
  }

  static override get styles() {
    return [
      tableStyles,
      sharedStyles,
      css`
        .placeholder {
          color: var(--deemphasized-text-color);
        }
      `,
    ];
  }

  override render() {
    return html`
      <gr-list-view
        .filter=${this.filter}
        .itemsPerPage=${this.pluginsPerPage}
        .items=${this.plugins}
        .loading=${this.loading}
        .offset=${this.offset}
        .path=${createAdminUrl({adminView: AdminChildView.PLUGINS})}
      >
        <table id="list" class="genericList">
          <tbody>
            <tr class="headerRow">
              <th class="name topHeader">Plugin Name</th>
              <th class="version topHeader">Version</th>
              <th class="apiVersion topHeader">API Version</th>
              <th class="status topHeader">Status</th>
            </tr>
            ${this.renderLoading()}
          </tbody>
          ${this.renderPluginListsTable()}
        </table>
      </gr-list-view>
    `;
  }

  private renderLoading() {
    if (!this.loading) return;

    return html`
      <tr id="loading" class="loadingMsg loading">
        <td>Loading...</td>
      </tr>
    `;
  }

  private renderPluginListsTable() {
    if (this.loading) return;

    return html`
      <tbody>
        ${this.plugins
          ?.slice(0, this.pluginsPerPage)
          .map(plugin => this.renderPluginList(plugin))}
      </tbody>
    `;
  }

  private renderPluginList(plugin: PluginInfoWithName) {
    return html`
      <tr class="table">
        <td class="name">
          ${plugin.index_url
            ? html`<a href=${this.computePluginUrl(plugin.index_url)}
                >${plugin.id}</a
              >`
            : plugin.id}
        </td>
        <td class="version">
          ${plugin.version
            ? plugin.version
            : html`<span class="placeholder">--</span>`}
        </td>
        <td class="apiVersion">
          ${plugin.api_version
            ? plugin.api_version
            : html`<span class="placeholder">--</span>`}
        </td>
        <td class="status">
          ${plugin.disabled === true ? 'Disabled' : 'Enabled'}
        </td>
      </tr>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  // private but used in test
  paramsChanged() {
    this.loading = true;
    this.filter = this.params?.filter ?? '';
    this.offset = Number(this.params?.offset ?? 0);

    return this.getPlugins(this.filter, this.pluginsPerPage, this.offset);
  }

  private getPlugins(filter: string, pluginsPerPage: number, offset?: number) {
    const errFn: ErrorCallback = response => {
      firePageError(response);
    };
    return this.restApiService
      .getPlugins(filter, pluginsPerPage, offset, errFn)
      .then(plugins => {
        if (!plugins) {
          this.plugins = [];
          return;
        }
        this.plugins = Object.keys(plugins).map(key => {
          return {...plugins[key], name: key};
        });
      })
      .finally(() => {
        this.loading = false;
      });
  }

  private computePluginUrl(id: string) {
    return getBaseUrl() + '/' + encodeURL(id);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-list': GrPluginList;
  }
}
