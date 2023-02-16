/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {customElement, property, state} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {fontStyles} from '../../../styles/gr-font-styles';
import {DropdownItem} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {PatchSet} from '../../../utils/patch-set-util';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {CheckRun, checksModelToken} from '../../../models/checks/checks-model';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-patchset-hovercard')
export class GrPatchsetHovercard extends base {
  @property({type: Array})
  item?: DropdownItem;

  @state()
  runs: CheckRun[] = [];

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

  private readonly getChecksModel = resolve(this, checksModelToken);

  constructor() {
    super();
    // TODO - change patchset
    subscribe(
      this,
      () => this.getChecksModel().allRunsLatestPatchsetLatestAttempt$,
      x => (this.runs = x)
    );
  }

  override render() {
    if (!this.item) return;
    const patchset = this.item.patchset;
    if (!patchset) return;
    return html` <div id="container" role="tooltip" tabindex="-1">
      <div class="section">
        <div class="sectionContent">
          <h3 class="name heading-3">
            <span>${this.item.triggerText}</span>
          </h3>
        </div>
      </div>
      ${this.renderDescription(patchset)}
      <div class="section">
        <div class="sectionIcon">
          <gr-icon icon="info" class="small"></gr-icon></span>
        </div>
        <div class="sectionContent">
          <div class="row">
            <div class="title">Status</div>
            <div>
              
            </div>
          </div>
        </div>
      </div>
      <div class="section">
        <gr-change-summary .patchset=${patchset}></gr-change-summary>
      </div>
    </div>`;
  }

  private renderDescription(patchset: PatchSet) {
    const description = patchset.desc;
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
    'gr-patchset-hovercard': GrPatchsetHovercard;
  }
}
