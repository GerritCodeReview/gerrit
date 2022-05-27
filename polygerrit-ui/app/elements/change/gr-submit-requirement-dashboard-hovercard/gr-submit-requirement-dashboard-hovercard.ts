/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-submit-requirements/gr-submit-requirements';
import {customElement, property} from 'lit/decorators';
import {css, html, LitElement} from 'lit';
import {HovercardMixin} from '../../../mixins/hovercard-mixin/hovercard-mixin';
import {ParsedChangeInfo} from '../../../types/types';

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = HovercardMixin(LitElement);

@customElement('gr-submit-requirement-dashboard-hovercard')
export class GrSubmitRequirementDashboardHovercard extends base {
  @property({type: Object})
  change?: ParsedChangeInfo;

  static override get styles() {
    return [
      base.styles || [],
      css`
        #container {
          padding: var(--spacing-xl);
          padding-left: var(--spacing-s);
        }
      `,
    ];
  }

  override render() {
    return html`<div id="container" role="tooltip" tabindex="-1">
      <gr-submit-requirements
        .change=${this.change}
        disable-hovercards
        suppress-title
        disable-endpoints
      ></gr-submit-requirements>
    </div>`;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-submit-requirement-dashboard-hovercard': GrSubmitRequirementDashboardHovercard;
  }
}
