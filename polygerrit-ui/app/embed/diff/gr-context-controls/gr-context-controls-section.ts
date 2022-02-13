/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../elements/shared/gr-button/gr-button';
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {DiffViewMode} from '../../../api/diff';
import {GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {diffClasses} from '../gr-diff/gr-diff-utils';
import {getShowConfig} from './gr-context-controls';
import {ifDefined} from 'lit/directives/if-defined';

@customElement('gr-context-controls-section')
export class GrContextControlsSection extends LitElement {
  @property({type: Boolean}) showAbove = false;

  @property({type: Object}) showBelow = false;

  @property({type: Object}) viewMode = DiffViewMode.SIDE_BY_SIDE;

  static override styles = css`
    :host {
    }
  `;

  private renderPaddingRow(whereClass: 'above' | 'below') {
    if (!this.showAbove && whereClass === 'above') return;
    if (!this.showBelow && whereClass === 'below') return;
    const sideBySide = this.viewMode === DiffViewMode.SIDE_BY_SIDE;
    const modeClass = sideBySide ? 'side-by-side' : 'unified';
    const type = sideBySide ? GrDiffGroupType.CONTEXT_CONTROL : undefined;
    return html`
      <tr
        ${diffClasses('contextBackground', modeClass, whereClass)}
        left-type=${ifDefined(type)}
        right-type=${ifDefined(type)}
      >
        <td ${diffClasses('blame')} data-line-number="0"></td>
        <td ${diffClasses('contextLineNum')}></td>
        ${sideBySide ? html`<td ${diffClasses()}></td>` : ''}
        <td ${diffClasses('contextLineNum')}></td>
        <td ${diffClasses()}></td>
      </tr>
    `;
  }

  private createContextControlRow() {
    const sideBySide = this.viewMode === DiffViewMode.SIDE_BY_SIDE;
    const showConfig = getShowConfig(this.showAbove, this.showBelow);
    return html`
      <tr ${diffClasses('dividerRow', `show-${showConfig}`)}>
        <td ${diffClasses('blame')} data-line-number="0"></td>
        ${sideBySide ? html`<td ${diffClasses()}></td>` : ''}
        <td ${diffClasses('dividerCell')} colspan="3">
          <slot></slot>
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
