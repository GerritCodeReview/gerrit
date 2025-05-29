/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-copy-clipboard/gr-copy-clipboard';
import '../../shared/gr-weblink/gr-weblink';
import {
  CommitId,
  CommitInfo,
  ServerInfo,
  WebLinkInfo,
} from '../../../types/common';
import {sharedStyles} from '../../../styles/shared-styles';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {configModelToken} from '../../../models/config/config-model';
import {createSearchUrl} from '../../../models/views/search';
import {getBrowseCommitWeblink} from '../../../utils/weblink-util';
import {shorten} from '../../../utils/patch-set-util';
import {when} from 'lit/directives/when.js';

declare global {
  interface HTMLElementTagNameMap {
    'gr-commit-info': GrCommitInfo;
  }
}

@customElement('gr-commit-info')
export class GrCommitInfo extends LitElement {
  @property({type: Object})
  commitInfo?: Partial<CommitInfo>;

  @property({type: Boolean})
  showCopyButton = true;

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
        gr-weblink {
          margin-right: 0;
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
      <gr-weblink imageAndText .info=${this.getWeblink(commit)}></gr-weblink>
      ${when(
        this.showCopyButton,
        () => html`
          <gr-copy-clipboard
            hastooltip
            .buttonTitle=${'Copy full SHA to clipboard'}
            hideinput
            .text=${commit}
          >
          </gr-copy-clipboard>
        `
      )}
    </div>`;
  }

  /**
   * Looks up the primary patchset weblink, but replaces its name by the
   * shortened commit hash. And falls back to a search query, if no weblink
   * is configured.
   */
  getWeblink(commit: CommitId): WebLinkInfo | undefined {
    if (!commit) return undefined;
    const name = shorten(commit)!;
    const primaryLink = getBrowseCommitWeblink(
      this.commitInfo?.web_links,
      this.serverConfig
    );
    if (primaryLink) return {...primaryLink, name};
    return {name, url: createSearchUrl({query: commit})};
  }
}
