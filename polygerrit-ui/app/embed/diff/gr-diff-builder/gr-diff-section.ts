/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  DiffInfo,
  DiffLayer,
  DiffViewMode,
  MovedLinkClickedEventDetail,
  RenderPreferences,
  Side,
  LineNumber,
  DiffPreferencesInfo,
} from '../../../api/diff';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {countLines, diffClasses} from '../gr-diff/gr-diff-utils';
import {GrDiffRow} from './gr-diff-row';
import '../gr-context-controls/gr-context-controls-section';
import '../gr-context-controls/gr-context-controls';
import '../gr-range-header/gr-range-header';
import './gr-diff-row';
import {whenVisible} from '../../../utils/dom-util';

@customElement('gr-diff-section')
export class GrDiffSection extends LitElement {
  @property({type: Object})
  group?: GrDiffGroup;

  @property({type: Object})
  diff?: DiffInfo;

  @property({type: Object})
  renderPrefs?: RenderPreferences;

  @property({type: Object})
  diffPrefs?: DiffPreferencesInfo;

  @property({type: Object})
  layers: DiffLayer[] = [];

  /**
   * While not visible we are trying to optimize rendering performance by
   * rendering a simpler version of the diff.
   */
  @state()
  isVisible = false;

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

  override connectedCallback() {
    super.connectedCallback();
    // TODO: Refine this obviously simplistic approach to optimized rendering.
    whenVisible(this.parentElement!, () => (this.isVisible = true), 1000);
  }

  override render() {
    if (!this.group) return;
    const extras: string[] = [];
    extras.push('section');
    extras.push(this.group.type);
    if (this.group.isTotal()) extras.push('total');
    if (this.group.dueToRebase) extras.push('dueToRebase');
    if (this.group.moveDetails) extras.push('dueToMove');
    if (this.group.moveDetails?.changed) extras.push('changed');
    if (this.group.ignoredWhitespaceOnly) extras.push('ignoredWhitespaceOnly');

    const isControl = this.group.type === GrDiffGroupType.CONTEXT_CONTROL;
    const pairs = isControl ? [] : this.group.getSideBySidePairs();
    const body = html`
      <tbody class=${diffClasses(...extras)}>
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
              .lineLength=${this.diffPrefs?.line_length ?? 80}
              .tabSize=${this.diffPrefs?.tab_size ?? 2}
              .isVisible=${this.isVisible}
            >
            </gr-diff-row>
          `;
        })}
      </tbody>
    `;
    if (this.addTableWrapperForTesting) {
      return html`<table>
        ${body}
      </table>`;
    }
    return body;
  }

  getDiffRows(): GrDiffRow[] {
    return [...this.querySelectorAll<GrDiffRow>('gr-diff-row')];
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

    return html`
      <gr-context-controls-section
        .showAbove=${showAbove}
        .showBelow=${showBelow}
        .group=${this.group}
        .diff=${this.diff}
        .renderPrefs=${this.renderPrefs}
        .viewMode=${DiffViewMode.SIDE_BY_SIDE}
      >
      </gr-context-controls-section>
    `;
  }

  findRow(side: Side, lineNumber: LineNumber): GrDiffRow | undefined {
    return (
      this.querySelector<GrDiffRow>(`gr-diff-row.${side}-${lineNumber}`) ??
      undefined
    );
  }

  private renderMoveControls() {
    if (!this.group?.moveDetails) return;
    const movedIn = this.group.adds.length > 0;
    const plainCell = html`<td class=${diffClasses()}></td>`;
    const lineNumberCell = html`
      <td class=${diffClasses('moveControlsLineNumCol')}></td>
    `;
    const moveCell = html`
      <td class=${diffClasses('moveHeader')}>
        <gr-range-header class=${diffClasses()} icon="gr-icons:move-item">
          ${this.renderMoveDescription(movedIn)}
        </gr-range-header>
      </td>
    `;
    return html`
      <tr
        class=${diffClasses('moveControls', movedIn ? 'movedIn' : 'movedOut')}
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
        <div class=${diffClasses()}>
          <span class=${diffClasses()}>${textLabel}</span>
          ${this.renderMovedLineAnchor(range.start, otherSide)}
          <span class=${diffClasses()}> - </span>
          ${this.renderMovedLineAnchor(range.end, otherSide)}
        </div>
      `;
    }

    return html`
      <div class=${diffClasses()}>
        <span class=${diffClasses()}
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
      <a class=${diffClasses()} href=${`#${line}`} @click=${listener}
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
