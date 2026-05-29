/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-tooltip-content/gr-tooltip-content';
import {css, html, LitElement, nothing} from 'lit';
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

  @property({type: Boolean})
  imageAndText = false;

  static override get styles() {
    return [
      css`
        :host {
          display: inline-block;
          vertical-align: top;
          line-height: var(--line-height-normal);
          margin-right: var(--spacing-s);
        }
        a {
          color: var(--link-color);
        }
        :host([imageAndText]) img {
          margin-right: var(--spacing-s);
        }
        img {
          vertical-align: top;
          width: var(--line-height-normal);
          height: var(--line-height-normal);
        }
      `,
    ];
  }

  override render() {
    if (!this.info?.url) return nothing;
    if (!this.info?.name) return nothing;

    return html`
      <a href=${this.info.url} rel="noopener noreferrer" target="_blank">
        <gr-tooltip-content
          title=${ifDefined(this.info.tooltip)}
          ?has-tooltip=${this.info.tooltip !== undefined}
        >
          ${when(
            this.info.image_url,
            () => html`<img src=${this.info!.image_url!} />`
          )}${when(
            !this.info.image_url || this.imageAndText,
            () => html`<span>${this.info!.name}</span>`
          )}
        </gr-tooltip-content>
      </a>
    `;
  }
}
