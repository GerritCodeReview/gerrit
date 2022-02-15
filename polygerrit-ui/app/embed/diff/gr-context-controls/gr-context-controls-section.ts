/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../elements/shared/gr-button/gr-button';
import {html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {DiffInfo, DiffViewMode, RenderPreferences} from '../../../api/diff';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {diffClasses} from '../gr-diff/gr-diff-utils';
import {getShowConfig} from './gr-context-controls';
import {ifDefined} from 'lit/directives/if-defined';

@customElement('gr-context-controls-section')
export class GrContextControlsSection extends LitElement {
  @property({type: Boolean}) showAbove = false;

  @property({type: Object}) showBelow = false;

  @property({type: Object}) viewMode = DiffViewMode.SIDE_BY_SIDE;

  @property({type: Object})
  group?: GrDiffGroup;

  @property({type: Object})
  diff?: DiffInfo;

  @property({type: Object})
  renderPrefs?: RenderPreferences;

  override createRenderRoot() {
    return this;
  }

  private renderPaddingRow(whereClass: 'above' | 'below') {
    if (!this.showAbove && whereClass === 'above') return;
    if (!this.showBelow && whereClass === 'below') return;
    const sideBySide = this.viewMode === DiffViewMode.SIDE_BY_SIDE;
    const modeClass = sideBySide ? 'side-by-side' : 'unified';
    const type = sideBySide ? GrDiffGroupType.CONTEXT_CONTROL : undefined;
    return html`
      <tr
        class="${diffClasses('contextBackground', modeClass, whereClass)}"
        left-type=${ifDefined(type)}
        right-type=${ifDefined(type)}
      >
        <td class="${diffClasses('blame')}" data-line-number="0"></td>
        <td class="${diffClasses('contextLineNum')}"></td>
        ${sideBySide ? html`<td class="${diffClasses()}"></td>` : ''}
        <td class="${diffClasses('contextLineNum')}"></td>
        <td class="${diffClasses()}"></td>
      </tr>
    `;
  }

  private createContextControlRow() {
    const sideBySide = this.viewMode === DiffViewMode.SIDE_BY_SIDE;
    const showConfig = getShowConfig(this.showAbove, this.showBelow);
    return html`
      <tr class="${diffClasses('dividerRow', `show-${showConfig}`)}">
        <td class="${diffClasses('blame')}" data-line-number="0"></td>
        ${sideBySide ? html`<td class="${diffClasses()}"></td>` : ''}
        <td class="${diffClasses('dividerCell')}" colspan="3">
          <gr-context-controls
            .diff=${this.diff}
            .renderPreferences=${this.renderPrefs}
            .group=${this.group}
            .showConfig=${showConfig}
          >
          </gr-context-controls>
        </td>
      </tr>
    `;
  }

  override render() {
    return html`
      ${this.renderPaddingRow('above')} ${this.createContextControlRow()}
      ${this.renderPaddingRow('below')}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-context-controls-section': GrContextControlsSection;
  }
}
