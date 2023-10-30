/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../elements/shared/gr-button/gr-button';
import {html, LitElement} from 'lit';
import {property, state} from 'lit/decorators.js';
import {DiffInfo, DiffViewMode, RenderPreferences} from '../../../api/diff';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {diffClasses} from '../gr-diff/gr-diff-utils';
import {getShowConfig} from './gr-context-controls';
import {ifDefined} from 'lit/directives/if-defined.js';
import {when} from 'lit/directives/when.js';
import {subscribe} from '../../../elements/lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {diffModelToken} from '../gr-diff-model/gr-diff-model';

export class GrContextControlsSection extends LitElement {
  /** Should context controls be rendered for expanding above the section? */
  @property({type: Boolean}) showAbove = false;

  /** Should context controls be rendered for expanding below the section? */
  @property({type: Boolean}) showBelow = false;

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

  @state() viewMode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE;

  private readonly getDiffModel = resolve(this, diffModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getDiffModel().viewMode$,
      viewMode => (this.viewMode = viewMode)
    );
  }

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
    const modeClass = this.isSideBySide() ? 'side-by-side' : 'unified';
    const type = this.isSideBySide()
      ? GrDiffGroupType.CONTEXT_CONTROL
      : undefined;
    return html`
      <tr
        class=${diffClasses('contextBackground', modeClass, whereClass)}
        left-type=${ifDefined(type)}
        right-type=${ifDefined(type)}
      >
        <td class=${diffClasses('blame')} data-line-number="0"></td>
        <td class=${diffClasses('contextLineNum')}></td>
        ${when(
          this.isSideBySide(),
          () => html`
            <td class=${diffClasses('sign')}></td>
            <td class=${diffClasses()}></td>
          `
        )}
        <td class=${diffClasses('contextLineNum')}></td>
        ${when(
          this.isSideBySide(),
          () => html`<td class=${diffClasses('sign')}></td>`
        )}
        <td class=${diffClasses()}></td>
      </tr>
    `;
  }

  private isSideBySide() {
    return this.viewMode !== DiffViewMode.UNIFIED;
  }

  /**
   * The context control table cell should span all the columns, but not the blame column.
   * The tricky bit is to figure out, which of the 6 other table columns are actually shown or not.
   */
  private computeColSpan() {
    const hideLeft = !!this.renderPrefs?.hide_left_side;
    const showSign = !!this.renderPrefs?.show_sign_col;
    const unified = !this.isSideBySide();

    const hideLeftNumberCol = hideLeft;
    const hideLeftSignCol = hideLeft || !showSign || unified;
    const hideLeftContentCol = hideLeft || unified;
    const hideRightNumberCol = false;
    const hideRightSignCol = !showSign || unified;
    const hideRightContentCol = false;

    const hiddenCols = [
      hideLeftNumberCol,
      hideLeftSignCol,
      hideLeftContentCol,
      hideRightNumberCol,
      hideRightSignCol,
      hideRightContentCol,
    ];
    const colspan = hiddenCols.filter(hide => !hide).length;
    return colspan;
  }

  private createContextControlRow() {
    const showConfig = getShowConfig(this.showAbove, this.showBelow);
    return html`
      <tr class=${diffClasses('dividerRow', `show-${showConfig}`)}>
        <td class=${diffClasses('blame')} data-line-number="0"></td>
        <td
          class=${diffClasses('dividerCell')}
          colspan=${this.computeColSpan()}
        >
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

customElements.define('gr-context-controls-section', GrContextControlsSection);

declare global {
  interface HTMLElementTagNameMap {
    'gr-context-controls-section': GrContextControlsSection;
  }
}
