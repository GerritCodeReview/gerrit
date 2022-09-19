/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import {CommitInfo, ServerInfo} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {createSearchUrl} from '../../../models/views/search';
import {getPatchSetWeblink} from '../../../utils/weblink-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-commit-info': GrCommitInfo;
  }
}

@customElement('gr-commit-info')
export class GrCommitInfo extends LitElement {
  // TODO(TS): Maybe limit to StandaloneCommitInfo.
  @property({type: Object})
  commitInfo?: CommitInfo;

  @state() serverConfig?: ServerInfo;

  private readonly getConfigModel = resolve(this, configModelToken);

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

  constructor() {
    super();
    subscribe(
      this,
      () => this.getConfigModel().serverConfig$,
      config => (this.serverConfig = config)
    );
  }

  override render() {
    const commit = this.commitInfo?.commit;
    if (!commit) return nothing;
    return html` <div class="container">
      <a target="_blank" rel="noopener" href=${this.computeCommitLink()}
        >${this.getWeblink()?.name ?? ''}</a
      >
      <gr-copy-clipboard
        hastooltip
        .buttonTitle=${'Copy full SHA to clipboard'}
        hideinput
        .text=${commit}
      >
      </gr-copy-clipboard>
    </div>`;
  }

  getWeblink() {
    return getPatchSetWeblink(
      this.commitInfo?.commit,
      this.commitInfo?.web_links,
      this.serverConfig
    );
  }

  computeCommitLink() {
    const weblink = this.getWeblink();
    if (weblink?.url) return weblink.url;

    const hash = weblink?.name;
    return hash ? createSearchUrl({query: hash}) : '';
  }
}
