/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../elements/shared/gr-button/gr-button';
import {html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {DiffInfo, DiffViewMode, RenderPreferences} from '../../../api/diff';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {diffClasses} from '../gr-diff/gr-diff-utils';
import {getShowConfig} from './gr-context-controls';
import {ifDefined} from 'lit/directives/if-defined.js';

@customElement('gr-context-controls-section')
export class GrContextControlsSection extends LitElement {
  /** Should context controls be rendered for expanding above the section? */
  @property({type: Boolean}) showAbove = false;

  /** Should context controls be rendered for expanding below the section? */
  @property({type: Boolean}) showBelow = false;

  @property({type: Object}) viewMode = DiffViewMode.SIDE_BY_SIDE;

  /** Must be of type GrDiffGroupType.CONTEXT_CONTROL. */
  @property({type: Object})
  group?: GrDiffGroup;

  @property({type: Object})
  diff?: DiffInfo;

  @property({type: Object})
  renderPrefs?: RenderPreferences;

  /**
   * Semantic DOM diff testing does not work with just table fragments, so when
   * running such tests the render() method has to wrap the DOM in a proper
   * <table> element.
   */
  @state()
  addTableWrapperForTesting = false;

  /**
   * The browser API for handling selection does not (yet) work for selection
   * across multiple shadow DOM elements. So we are rendering gr-diff components
   * into the light DOM instead of the shadow DOM by overriding this method,
   * which was the recommended workaround by the lit team.
   * See also https://github.com/WICG/webcomponents/issues/79.
   */
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
        class=${diffClasses('contextBackground', modeClass, whereClass)}
        left-type=${ifDefined(type)}
        right-type=${ifDefined(type)}
      >
        <td class=${diffClasses('blame')} data-line-number="0"></td>
        <td class=${diffClasses('contextLineNum')}></td>
        <td class=${diffClasses('sign')}></td>
        ${sideBySide ? html`<td class=${diffClasses()}></td>` : ''}
        <td class=${diffClasses('contextLineNum')}></td>
        <td class=${diffClasses('sign')}></td>
        <td class=${diffClasses()}></td>
      </tr>
    `;
  }

  private createContextControlRow() {
    const sideBySide = this.viewMode === DiffViewMode.SIDE_BY_SIDE;
    // Note that <td> table cells that have `display: none` don't count!
    const colspan = this.renderPrefs?.show_sign_col ? '5' : '3';
    const showConfig = getShowConfig(this.showAbove, this.showBelow);
    return html`
      <tr class=${diffClasses('dividerRow', `show-${showConfig}`)}>
        <td class=${diffClasses('blame')} data-line-number="0"></td>
        ${sideBySide ? html`<td class=${diffClasses()}></td>` : ''}
        <td class=${diffClasses('dividerCell')} colspan=${colspan}>
          <gr-context-controls
            class=${diffClasses()}
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
    const rows = html`
      ${this.renderPaddingRow('above')} ${this.createContextControlRow()}
      ${this.renderPaddingRow('below')}
    `;
    if (this.addTableWrapperForTesting) {
      return html`<table>
        ${rows}
      </table>`;
    }
    return rows;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-context-controls-section': GrContextControlsSection;
  }
}
