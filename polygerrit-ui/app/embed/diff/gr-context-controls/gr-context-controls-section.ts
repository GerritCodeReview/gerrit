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
import {getShowConfig} from './gr-context-controls';
import {ifDefined} from 'lit/directives/if-defined.js';
import {when} from 'lit/directives/when.js';
import {subscribe} from '../../../elements/lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {
  ColumnsToShow,
  diffModelToken,
  NO_COLUMNS,
} from '../gr-diff-model/gr-diff-model';

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

  @state() columns: ColumnsToShow = NO_COLUMNS;

  @state() columnCount = 0;

  private readonly getDiffModel = resolve(this, diffModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getDiffModel().viewMode$,
      viewMode => (this.viewMode = viewMode)
    );
    subscribe(
      this,
      () => this.getDiffModel().columnsToShow$,
      columnsToShow => (this.columns = columnsToShow)
    );
    subscribe(
      this,
      () => this.getDiffModel().columnCount$,
      columnCount => (this.columnCount = columnCount)
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
        class=${['contextBackground', modeClass, whereClass].join(' ')}
        left-type=${ifDefined(type)}
        right-type=${ifDefined(type)}
      >
        ${when(
          this.columns.blame,
          () => html`<td class="blame" data-line-number="0"></td>`
        )}
        ${when(
          this.columns.leftNumber,
          () => html`<td class="contextLineNum"></td>`
        )}
        ${when(this.columns.leftSign, () => html`<td class="sign"></td>`)}
        ${when(this.columns.leftContent, () => html`<td></td>`)}
        ${when(
          this.columns.rightNumber,
          () => html`<td class="contextLineNum"></td>`
        )}
        ${when(this.columns.rightSign, () => html`<td class="sign"></td>`)}
        ${when(this.columns.rightContent, () => html`<td></td>`)}
      </tr>
    `;
  }

  private isSideBySide() {
    return this.viewMode !== DiffViewMode.UNIFIED;
  }

  private createContextControlRow() {
    // Span all columns, but not the blame column.
    let colspan = this.columnCount;
    if (this.columns.blame) colspan--;
    const showConfig = getShowConfig(this.showAbove, this.showBelow);
    return html`
      <tr class=${['dividerRow', `show-${showConfig}`].join(' ')}>
        ${when(
          this.columns.blame,
          () => html`<td class="blame" data-line-number="0"></td>`
        )}
        <td class="dividerCell" colspan=${colspan}>
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
