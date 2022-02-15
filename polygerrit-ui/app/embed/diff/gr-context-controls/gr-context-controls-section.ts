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
      display: contents;
    }
    /*
     * Padding rows behind context controls. The diff is styled to be cut into
     * two halves by the negative space of the divider on which the context
     * control buttons are anchored.
     */
    .contextBackground {
      border-right: 1px solid var(--border-color);
    }
    .contextBackground.above {
      border-bottom: 1px solid var(--border-color);
    }
    .contextBackground.below {
      border-top: 1px solid var(--border-color);
    }
    /*
     * Padding rows behind context controls. Styled as a continuation of the
     * line gutters and code area.
     */
    .contextBackground > .contextLineNum {
      background-color: var(--diff-blank-background-color);
    }
    .contextBackground > td:not(.contextLineNum) {
      background-color: var(--view-background-color);
    }
    .contextBackground {
      /*
       * One line of background behind the context expanders which they can
       * render on top of, plus some padding.
       */
      height: calc(var(--line-height-normal) + var(--spacing-s));
    }

    .dividerCell {
      vertical-align: top;
    }
    .dividerRow.show-both .dividerCell {
      height: var(--divider-height);
    }
    .dividerRow.show-above .dividerCell,
    .dividerRow.show-above .dividerCell {
      height: 0;
    }

    td {
      padding: 0;
    }
    td.blame {
      display: none;
    }
    :host(.showBlame) td.blame {
      display: table-cell;
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
