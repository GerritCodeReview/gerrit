/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-formatted-text/gr-formatted-text';
import {customElement, property} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {fontStyles} from '../../../styles/gr-font-styles';
import {LabelInfo} from '../../../api/rest-api';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-trigger-vote-hovercard')
export class GrTriggerVoteHovercard extends base {
  @property()
  labelName?: string;

  @property({type: Object})
  labelInfo?: LabelInfo;

  static override get styles() {
    return [
      fontStyles,
      base.styles || [],
      css`
        #container {
          min-width: 300px;
          max-width: 300px;
          padding: var(--spacing-xl) 0 var(--spacing-m) 0;
        }
        .row {
          display: flex;
        }
        .title {
          color: var(--deemphasized-text-color);
          margin-right: var(--spacing-m);
        }
        div.section {
          margin: 0 var(--spacing-xl) var(--spacing-m) var(--spacing-xl);
          display: flex;
          align-items: flex-start;
        }
        div.sectionIcon {
          flex: 0 0 30px;
        }
        div.sectionIcon gr-icon {
          position: relative;
          font-size: 20px;
        }
      `,
    ];
  }

  override render() {
    return html` <div id="container" role="tooltip" tabindex="-1">
      <div class="section">
        <div class="sectionContent">
          <h3 class="name heading-3">
            <span>${this.labelName}</span>
          </h3>
        </div>
      </div>
      <div class="section">
        <div class="sectionIcon">
          <gr-icon icon="info" class="small"></gr-icon></span>
        </div>
        <div class="sectionContent">
          <div class="row">
            <div class="title">Status</div>
            <div>
              <slot name="label-info"></slot>
            </div>
          </div>
        </div>
      </div>
      ${this.renderDescription()}
    </div>`;
  }

  private renderDescription() {
    const description = this.labelInfo?.description;
    if (!description) return;
    return html`<div class="section description">
      <div class="sectionIcon">
        <gr-icon icon="description"></gr-icon>
      </div>
      <div class="sectionContent">
        <gr-formatted-text
          .markdown=${true}
          .content=${description}
        ></gr-formatted-text>
      </div>
    </div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-trigger-vote-hovercard': GrTriggerVoteHovercard;
  }
}
