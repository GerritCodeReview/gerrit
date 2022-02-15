/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  DiffInfo,
  DiffLayer,
  DiffViewMode,
  MovedLinkClickedEventDetail,
  RenderPreferences,
  Side,
  LineNumber,
} from '../../../api/diff';
import {getShowConfig} from '../gr-context-controls/gr-context-controls';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {countLines, diffClasses} from '../gr-diff/gr-diff-utils';
import {GrDiffRow} from './gr-diff-row';
import '../gr-context-controls/gr-context-controls-section';
import '../gr-context-controls/gr-context-controls';
import '../gr-range-header/gr-range-header';
import './gr-diff-row';

@customElement('gr-diff-section')
export class GrDiffSection extends LitElement {
  @property({type: Object})
  group?: GrDiffGroup;

  @property({type: Object})
  diff?: DiffInfo;

  @property({type: Object})
  renderPrefs?: RenderPreferences;

  @property({type: Object})
  layers: DiffLayer[] = [];

  static override styles = css`
    :host {
      display: contents;
    }
    /*
      Context controls break up the table visually, so we set the right border
      on individual sections to leave a gap for the divider.

      Also taken into account for max-width calculations in SHRINK_ONLY
      mode (check GrDiff._updatePreferenceStyles).
      */
    tbody.section {
      border-right: 1px solid var(--border-color);
    }
    tbody.section.contextControl {
      display: var(--context-control-display, table-row-group);
      background-color: transparent;
      border: none;
      --divider-height: var(--spacing-s);
      --divider-border: 1px;
    }
  `;

  override render() {
    console.log(`render diff section ${JSON.stringify(this.group?.lineRange)}`);
    if (!this.group) return;

    const extras: string[] = [];
    extras.push('section');
    extras.push(this.group.type);
    if (this.group.isTotal()) extras.push('total');
    if (this.group.dueToRebase) extras.push('dueToRebase');
    if (this.group.moveDetails) extras.push('dueToMove');
    if (this.group.ignoredWhitespaceOnly) extras.push('ignoredWhitespaceOnly');

    const isControl = this.group.type === GrDiffGroupType.CONTEXT_CONTROL;
    const pairs = isControl ? [] : this.group.getSideBySidePairs();
    return html`
      <tbody class="${diffClasses(...extras)}">
        ${this.renderContextControls()} ${this.renderMoveControls()}
        ${pairs.map(pair => {
          const leftCl = `left-${pair.left.lineNumber(Side.LEFT)}`;
          const rightCl = `right-${pair.right.lineNumber(Side.RIGHT)}`;
          return html`
            <gr-diff-row
              class="${leftCl} ${rightCl}"
              .left=${pair.left}
              .right=${pair.right}
              .layers=${this.layers}
            >
              ${this.renderSlots(leftCl, rightCl)}
            </gr-diff-row>
          `;
        })}
      </tbody>
    `;
  }

  /**
   * Slots are used for comment threads. Here we are just re-targeting:
   * Thread elements are added to gr-diff-section with `slot=...`. The `name`
   * of the rendered slots makes sure that the thread elements are slotted here.
   * The `slot` attribute makes sure that the thread elements end up in the
   * right place within <gr-diff-row>.
   */
  private renderSlots(leftSlotName: string, rightSlotName: string) {
    return html`
      <slot name=${leftSlotName} slot="left"></slot>
      <slot name=${rightSlotName} slot="right"></slot>
    `;
  }

  getDiffRows(): GrDiffRow[] {
    return [...this.shadowRoot!.querySelectorAll<GrDiffRow>('gr-diff-row')];
  }

  private renderContextControls() {
    if (this.group?.type !== GrDiffGroupType.CONTEXT_CONTROL) return;

    const leftStart = this.group.lineRange.left.start_line;
    const leftEnd = this.group.lineRange.left.end_line;
    const firstGroupIsSkipped = !!this.group.contextGroups[0].skip;
    const lastGroupIsSkipped =
      !!this.group.contextGroups[this.group.contextGroups.length - 1].skip;
    const lineCountLeft = countLines(this.diff, Side.LEFT);
    const containsWholeFile = lineCountLeft === leftEnd - leftStart + 1;
    const showAbove =
      (leftStart > 1 && !firstGroupIsSkipped) || containsWholeFile;
    const showBelow = leftEnd < lineCountLeft && !lastGroupIsSkipped;
    const showConfig = getShowConfig(showAbove, showBelow);

    // TODO: Pass `section` to <gr-context-controls> or find an alternative.
    return html`
      <gr-context-controls-section
        .showAbove=${showAbove}
        .showBelow=${showBelow}
        .group=${this.group}
        .viewMode=${DiffViewMode.SIDE_BY_SIDE}
      >
        <gr-context-controls
          .diff=${this.diff}
          .renderPreferences=${this.renderPrefs}
          .group=${this.group}
          .showConfig=${showConfig}
        >
        </gr-context-controls>
      </gr-context-controls-section>
    `;
  }

  findRow(side: Side, lineNumber: LineNumber): GrDiffRow | undefined {
    return (
      this.shadowRoot?.querySelector(`gr-diff-row.${side}-${lineNumber}`) ??
      undefined
    );
  }

  private renderMoveControls() {
    if (!this.group?.moveDetails) return;
    const movedIn = this.group.adds.length > 0;
    const plainCell = html`<td class="${diffClasses()}"></td>`;
    const lineNumberCell = html`
      <td class="${diffClasses('moveControlsLineNumCol')}"></td>
    `;
    const moveCell = html`
      <td class="${diffClasses('moveHeader')}">
        <gr-range-header class="${diffClasses()}" icon="gr-icons:move-item">
          ${this.renderMoveDescription(movedIn)}
        </gr-range-header>
      </td>
    `;
    return html`
      <tr
        class="${diffClasses('moveControls', movedIn ? 'movedIn' : 'movedOut')}"
      >
        ${lineNumberCell} ${movedIn ? plainCell : moveCell} ${lineNumberCell}
        ${movedIn ? moveCell : plainCell}
      </tr>
    `;
  }

  private renderMoveDescription(movedIn: boolean) {
    if (this.group?.moveDetails?.range) {
      const {changed, range} = this.group.moveDetails;
      const otherSide = movedIn ? Side.LEFT : Side.RIGHT;
      const andChangedLabel = changed ? 'and changed ' : '';
      const direction = movedIn ? 'from' : 'to';
      const textLabel = `Moved ${andChangedLabel}${direction} lines `;
      return html`
        <div class="${diffClasses()}">
          <span class="${diffClasses()}">${textLabel}</span>
          ${this.renderMovedLineAnchor(range.start, otherSide)}
          <span class="${diffClasses()}"> - </span>
          ${this.renderMovedLineAnchor(range.end, otherSide)}
        </div>
      `;
    }

    return html`
      <div class="${diffClasses()}">
        <span class="${diffClasses()}"
          >${movedIn ? 'Moved in' : 'Moved out'}</span
        >
      </div>
    `;
  }

  private renderMovedLineAnchor(line: number, side: Side) {
    const listener = (e: MouseEvent) => {
      e.preventDefault();
      this.handleMovedLineAnchorClick(e.target, side, line);
    };
    // `href` is not actually used but important for Screen Readers
    return html`
      <a class="${diffClasses()}" href="${`#${line}`}" @click=${listener}
        >${line}</a
      >
    `;
  }

  private handleMovedLineAnchorClick(
    anchor: EventTarget | null,
    side: Side,
    line: number
  ) {
    anchor?.dispatchEvent(
      new CustomEvent<MovedLinkClickedEventDetail>('moved-link-clicked', {
        detail: {
          lineNum: line,
          side,
        },
        composed: true,
        bubbles: true,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-section': GrDiffSection;
  }
}
