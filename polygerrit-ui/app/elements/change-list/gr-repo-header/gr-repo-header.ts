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
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {createRepoUrl} from '../../../models/views/repo';
import '../../shared/gr-weblink/gr-weblink';

@customElement('gr-repo-header')
export class GrRepoHeader extends LitElement {
  @property({type: String})
  repo?: RepoName;

  @state()
  private repoUrl: string | null = null;

  @state()
  private webLinks: WebLinkInfo[] = [];

  private readonly restApiService = getAppContext().restApiService;

  static override get styles() {
    return [
      sharedStyles,
      dashboardHeaderStyles,
      fontStyles,
      css`
        .browse {
          display: inline-block;
          font-weight: var(--font-weight-medium);
          text-align: right;
          width: 4em;
        }
        a {
          padding-right: 0.3em;
        }
      `,
    ];
  }

  private renderLinks(webLinks: WebLinkInfo[]) {
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
      <div><span>Detail:</span> <a href=${this.repoUrl!}>Repo settings</a></div>
      ${this.renderLinks(this.webLinks)}
    </div>`;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      if (this.repo) {
        this.repoUrl = createRepoUrl({repo: this.repo});
      } else {
        this.repoUrl = null;
      }
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('repo')) {
      this.repoChanged();
    }
  }

  private repoChanged() {
    const repo = this.repo;
    if (!repo) return;

    this.restApiService.getRepo(repo).then(repo => {
      if (!repo?.web_links) return;
      this.webLinks = repo.web_links;
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-header': GrRepoHeader;
  }
}
