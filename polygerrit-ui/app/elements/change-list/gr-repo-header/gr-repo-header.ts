/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RepoName} from '../../../types/common';
import {WebLinkInfo} from '../../../types/diff';
import {getAppContext} from '../../../services/app-context';
import {sharedStyles} from '../../../styles/shared-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {dashboardHeaderStyles} from '../../../styles/dashboard-header-styles';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {createRepoUrl} from '../../../models/views/repo';
import '../../shared/gr-weblink/gr-weblink';

@customElement('gr-repo-header')
export class GrRepoHeader extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @property({type: String})
  _repoUrl: string | null = null;

  @property({type: Array})
  _webLinks: WebLinkInfo[] = [];

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      dashboardHeaderStyles,
      fontStyles,
      css`
        .browse {
          display: inline-block;
          font-weight: var(--font-weight-bold);
          text-align: right;
          width: 4em;
        }
        a {
          padding-right: 0.3em;
        }
      `,
    ];
  }

  _renderLinks(webLinks: WebLinkInfo[]) {
    if (!webLinks) return;
    return html`<div>
      <span class="browse">Browse:</span>
      ${webLinks.map(
        info => html`<gr-weblink imageAndText .info=${info}></gr-weblink>`
      )}
    </div> `;
  }

  override render() {
    return html` <div class="info">
      <h1 class="heading-1">${this.repo}</h1>
      <hr />
      <div>
        <span>Detail:</span> <a href=${this._repoUrl!}>Repo settings</a>
      </div>
      ${this._renderLinks(this._webLinks)}
    </div>`;
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this._repoChanged();
    }
  }

  _repoChanged() {
    const repo = this.repo;
    if (!repo) {
      this._repoUrl = null;
      return;
    }

    this._repoUrl = createRepoUrl({repo});

    this.restApiService.getRepo(repo).then(repo => {
      if (!repo?.web_links) return;
      this._webLinks = repo.web_links;
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-header': GrRepoHeader;
  }
}
