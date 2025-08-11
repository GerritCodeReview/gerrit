/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DashboardId, DashboardInfo, RepoName} from '../../../types/common';
import {firePageError} from '../../../utils/event-util';
import {getAppContext} from '../../../services/app-context';
import {ErrorCallback} from '../../../api/rest';
import {sharedStyles} from '../../../styles/shared-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  createDashboardUrl,
  DashboardType,
} from '../../../models/views/dashboard';
import {when} from 'lit/directives/when.js';
import '../../shared/gr-list-view/gr-list-view';
import {
  createRepoUrl,
  RepoDetailView,
  RepoViewState,
} from '../../../models/views/repo';

interface DashboardRef {
  section: string;
  dashboards: DashboardInfo[];
}

@customElement('gr-repo-dashboards')
export class GrRepoDashboards extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @property({type: Object})
  params?: RepoViewState;

  @state()
  loading = true;

  @state()
  dashboards?: DashboardRef[];

  @state() offset = 0;

  @state() filter = '';

  @state() itemsPerPage = 25;

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      tableStyles,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-xxl);
        }
      `,
    ];
  }

  override render() {
    return html` <gr-list-view
      .filter=${this.filter}
      .itemsPerPage=${this.itemsPerPage}
      .items=${this.dashboards}
      .loading=${this.loading}
      .offset=${this.offset}
      .path=${createRepoUrl({
        repo: this.repo,
        detail: RepoDetailView.DASHBOARDS,
      })}
    >
      <table id="list" class="genericList">
        <tbody id="dashboards">
          <tr class="headerRow">
            <th class="topHeader">Dashboard name</th>
            <th class="topHeader">Dashboard title</th>
            <th class="topHeader">Dashboard description</th>
            <th class="topHeader">Inherited from</th>
            <th class="topHeader">Default</th>
          </tr>
          ${when(
            this.loading,
            () => html`<tr id="loadingContainer">
              <td>Loading...</td>
            </tr>`,
            () => html`
              ${(this.dashboards ?? []).map(
                item => html`
                  <tr class="groupHeader">
                    <td colspan="5">${item.section}</td>
                  </tr>
                  ${(item.dashboards ?? []).map(
                    info => html`
                      <tr class="table">
                        <td class="name">
                          <a href=${this.getUrl(info.project, info.id)}
                            >${info.path}</a
                          >
                        </td>
                        <td class="title">${info.title}</td>
                        <td class="desc">${info.description}</td>
                        <td class="inherited">
                          ${this.computeInheritedFrom(
                            info.project,
                            info.defining_project
                          )}
                        </td>
                        <td class="default">
                          ${this.computeIsDefault(info.is_default)}
                        </td>
                      </tr>
                    `
                  )}
                `
              )}
            `
          )}
        </tbody>
      </table>
    </gr-list-view>`;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.repoChanged();
    }
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('params')) {
      this.paramsChanged();
    }
  }

  async paramsChanged() {
    const params = this.params;
    this.loading = true;
    this.filter = params?.filter ?? '';
    this.offset = Number(params?.offset ?? 0);

    await this.repoChanged(this.filter, this.offset);
  }

  private repoChanged(filter?: string, offset?: number) {
    const repo = this.repo;
    this.loading = true;

    if (!repo) {
      return Promise.resolve();
    }

    const errFn: ErrorCallback = response => {
      firePageError(response);
    };

    return this.restApiService
      .getRepoDashboards(repo, errFn)
      .then((res?: DashboardInfo[]) => {
        if (!res) {
          return;
        }

        let dashboards = res.concat
          .apply([], res)
          .sort((a, b) => (a.id < b.id ? -1 : 1));

        dashboards = dashboards
          .filter(item =>
            filter === undefined
              ? true
              : item.path.toLowerCase().includes(filter.toLowerCase())
          )
          .slice(offset ?? 0, (offset ?? 0) + this.itemsPerPage);

        const dashboardsByRef: Record<string, DashboardInfo[]> = {};
        dashboards.forEach(d => {
          if (!dashboardsByRef[d.ref]) {
            dashboardsByRef[d.ref] = [];
          }
          dashboardsByRef[d.ref].push(d);
        });

        const dashboardBuilder: DashboardRef[] = [];
        Object.keys(dashboardsByRef)
          .sort()
          .forEach(ref => {
            dashboardBuilder.push({
              section: ref,
              dashboards: dashboardsByRef[ref],
            });
          });

        this.dashboards = dashboardBuilder;
        this.loading = false;
      });
  }

  private getUrl(project?: RepoName, dashboard?: DashboardId) {
    if (!project || !dashboard) return '';

    return createDashboardUrl({project, type: DashboardType.REPO, dashboard});
  }

  private computeInheritedFrom(project: RepoName, definingProject: RepoName) {
    return project === definingProject ? '' : definingProject;
  }

  private computeIsDefault(isDefault?: boolean) {
    return isDefault ? '✓' : '';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-dashboards': GrRepoDashboards;
  }
}
