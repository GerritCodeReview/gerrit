/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-tooltip-content/gr-tooltip-content';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {WebLinkInfo} from '../../../api/rest-api';
import {ifDefined} from 'lit/directives/if-defined.js';
import {when} from 'lit/directives/when.js';

declare global {
  interface HTMLElementTagNameMap {
    'gr-weblink': GrWeblink;
  }
}
@customElement('gr-weblink')
export class GrWeblink extends LitElement {
  @property({type: Object})
  info?: WebLinkInfo;

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          vertical-align: top;
          line-height: var(--line-height-normal);
        }
        a {
          color: var(--link-color);
        }
        img {
          width: var(--line-height-normal);
          height: var(--line-height-normal);
        }
      `,
    ];
  }

  override render() {
    const info = this.info;
    if (!info) return nothing;
    if (!info.url) return nothing;

    return html`
      <a href=${info.url} rel="noopener" target="_blank">
        <gr-tooltip-content
          title=${ifDefined(info.tooltip)}
          ?has-tooltip=${info.tooltip !== undefined}
        >
          ${when(
            info.image_url,
            () => html`<img src=${info.image_url!} />`,
            () => html`<span>${info.name ?? 'browse'}</span>`
          )}
        </gr-tooltip-content>
      </a>
    `;
  }
}
