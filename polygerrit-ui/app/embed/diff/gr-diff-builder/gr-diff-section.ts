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
} from '../../../api/diff';
import {getShowConfig} from '../gr-context-controls/gr-context-controls';
import {GrDiffGroup, GrDiffGroupType, isTotal} from '../gr-diff/gr-diff-group';
import {countLines, diffClasses} from '../gr-diff/gr-diff-utils';

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
    }
  `;

  override render() {
    if (!this.group) return;

    const extras: string[] = [];
    extras.push('section');
    extras.push(this.group.type);
    if (isTotal(this.group)) extras.push('total');
    if (this.group.dueToRebase) extras.push('dueToRebase');
    if (this.group.moveDetails) extras.push('dueToMove');
    if (this.group.ignoredWhitespaceOnly) extras.push('ignoredWhitespaceOnly');

    const isControl = this.group.type === GrDiffGroupType.CONTEXT_CONTROL;
    const pairs = isControl ? this.group.getSideBySidePairs() : [];
    return html`
      <tbody ${diffClasses(...extras)}>
        ${this.renderContextControls()} ${this.renderMoveControls()}
        ${pairs.map(
          pair =>
            html`
              <gr-diff-row
                .left=${pair.left}
                .right=${pair.right}
                .layers=${this.layers}
              ></gr-diff-row>
            `
        )}
      </tbody>
    `;
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

  private renderMoveControls() {
    if (!this.group?.moveDetails) return;
    const movedIn = this.group.adds.length > 0;
    const plainCell = html`<td ${diffClasses()}></td>`;
    const lineNumberCell = html`
      <td ${diffClasses('moveControlsLineNumCol')}></td>
    `;
    const moveCell = html`
      <td ${diffClasses('moveHeader')}>
        <gr-range-header ${diffClasses()} icon="gr-icons:move-item">
          ${this.renderMoveDescription(movedIn)}
        </gr-range-header>
      </td>
    `;
    return html`
      <tr ${diffClasses('moveControls', movedIn ? 'movedIn' : 'movedOut')}>
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
        <div ${diffClasses()}>
          <span ${diffClasses()}>${textLabel}</span>
          ${this.renderMovedLineAnchor(range.start, otherSide)}
          <span ${diffClasses()}> - </span>
          ${this.renderMovedLineAnchor(range.end, otherSide)}
        </div>
      `;
    }

    return html`
      <div ${diffClasses()}>
        <span ${diffClasses()}>${movedIn ? 'Moved in' : 'Moved out'}</span>
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
      <a ${diffClasses()} href="${`#${line}`}" @click=${listener}>${line}</a>
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
