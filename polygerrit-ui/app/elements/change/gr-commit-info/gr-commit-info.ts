/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {ChangeInfo, CommitInfo, ServerInfo} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html} from 'lit';
import {customElement, property} from 'lit/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-commit-info': GrCommitInfo;
  }
}

@customElement('gr-commit-info')
export class GrCommitInfo extends LitElement {
  // TODO(TS): can not use `?` here as @computed require dependencies as
  // not optional
  @property({type: Object})
  change: ChangeInfo | undefined;

  // TODO(TS): maybe limit to StandaloneCommitInfo if never pass in
  // with commit inside RevisionInfo
  @property({type: Object})
  commitInfo: CommitInfo | undefined;

  @property({type: Object})
  serverConfig: ServerInfo | undefined;

  static override get styles() {
    return [
      sharedStyles,
      css`
        .container {
          align-items: center;
          display: flex;
        }
      `,
    ];
  }

  override render() {
    return html` <div class="container">
      <a
        target="_blank"
        rel="noopener"
        href=${this.computeCommitLink(
          this._webLink,
          this.change,
          this.commitInfo,
          this.serverConfig
        )}
        >${this._computeShortHash(
          this.change,
          this.commitInfo,
          this.serverConfig
        )}</a
      >
      <gr-copy-clipboard
        hastooltip
        .buttonTitle=${'Copy full SHA to clipboard'}
        hideinput
        .text=${this.commitInfo?.commit}
      >
      </gr-copy-clipboard>
    </div>`;
  }

  /**
   * Used only within the tests.
   */
  get _showWebLink(): boolean {
    if (!this.change || !this.commitInfo || !this.serverConfig) {
      return false;
    }

    const weblink = this._getWeblink(
      this.change,
      this.commitInfo,
      this.serverConfig
    );
    return !!weblink && !!weblink.url;
  }

  get _webLink(): string | undefined {
    if (!this.change || !this.commitInfo || !this.serverConfig) {
      return '';
    }

    // TODO(TS): if getPatchSetWeblink always return a valid WebLink,
    // can remove the fallback here
    const {url} =
      this._getWeblink(this.change, this.commitInfo, this.serverConfig) || {};
    return url;
  }

  _getWeblink(change: ChangeInfo, commitInfo: CommitInfo, config: ServerInfo) {
    return GerritNav.getPatchSetWeblink(change.project, commitInfo.commit, {
      weblinks: commitInfo.web_links,
      config,
    });
  }

  computeCommitLink(
    webLink?: string,
    change?: ChangeInfo,
    commitInfo?: CommitInfo,
    serverConfig?: ServerInfo
  ) {
    if (webLink) return webLink;
    const hash = this._computeShortHash(change, commitInfo, serverConfig);
    if (hash === undefined) return '';
    return GerritNav.getUrlForSearchQuery(hash);
  }

  _computeShortHash(
    change?: ChangeInfo,
    commitInfo?: CommitInfo,
    serverConfig?: ServerInfo
  ) {
    if (!change || !commitInfo || !serverConfig) {
      return '';
    }

    const weblink = this._getWeblink(change, commitInfo, serverConfig);
    return weblink?.name ?? '';
  }
}
