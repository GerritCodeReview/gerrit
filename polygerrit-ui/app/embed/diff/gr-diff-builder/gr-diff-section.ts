/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {property, queryAll, state} from 'lit/decorators.js';
import {
  DiffInfo,
  DiffLayer,
  DiffPreferencesInfo,
  DiffViewMode,
  LineNumber,
  RenderPreferences,
  Side,
} from '../../../api/diff';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {getResponsiveMode} from '../gr-diff/gr-diff-utils';
import {GrDiffRow} from './gr-diff-row';
import '../gr-context-controls/gr-context-controls-section';
import '../gr-context-controls/gr-context-controls';
import '../gr-range-header/gr-range-header';
import './gr-diff-row';
import {when} from 'lit/directives/when.js';
import {fire} from '../../../utils/event-util';
import {resolve} from '../../../models/dependency';
import {
  ColumnsToShow,
  diffModelToken,
  NO_COLUMNS,
} from '../gr-diff-model/gr-diff-model';
import {subscribe} from '../../../elements/lit/subscription-controller';

export class GrDiffSection extends LitElement {
  @queryAll('gr-diff-row')
  diffRows?: NodeListOf<GrDiffRow>;

  @property({type: Object})
  group?: GrDiffGroup;

  @state()
  diff?: DiffInfo;

  @state()
  renderPrefs?: RenderPreferences;

  @state()
  diffPrefs?: DiffPreferencesInfo;

  @state()
  layers: DiffLayer[] = [];

  @state()
  lineLength = 100;

  @state() columns: ColumnsToShow = NO_COLUMNS;

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
      () => this.getDiffModel().lineLength$,
      lineLength => (this.lineLength = lineLength)
    );
    subscribe(
      this,
      () => this.getDiffModel().viewMode$,
      viewMode => (this.viewMode = viewMode)
    );
    subscribe(
      this,
      () => this.getDiffModel().diff$,
      diff => (this.diff = diff)
    );
    subscribe(
      this,
      () => this.getDiffModel().renderPrefs$,
      renderPrefs => (this.renderPrefs = renderPrefs)
    );
    subscribe(
      this,
      () => this.getDiffModel().diffPrefs$,
      diffPrefs => (this.diffPrefs = diffPrefs)
    );
    subscribe(
      this,
      () => this.getDiffModel().layers$,
      layers => (this.layers = layers)
    );
    subscribe(
      this,
      () => this.getDiffModel().columnsToShow$,
      columnsToShow => (this.columns = columnsToShow)
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

  protected override async getUpdateComplete(): Promise<boolean> {
    const result = await super.getUpdateComplete();
    const rows = [...(this.diffRows ?? [])];
    await Promise.all(rows.map(row => row.updateComplete));
    return result;
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

    const pairs = this.getLinePairs();
    const responsiveMode = getResponsiveMode(this.diffPrefs, this.renderPrefs);
    const hideFileCommentButton =
      this.diffPrefs?.show_file_comment_button === false ||
      this.renderPrefs?.show_file_comment_button === false;
    const body = html`
      <tbody class=${extras.join(' ')}>
        ${this.renderContextControls()} ${this.renderMoveControls()}
        ${pairs.map(pair => {
          const leftClass = `left-${pair.left.lineNumber(Side.LEFT)}`;
          const rightClass = `right-${pair.right.lineNumber(Side.RIGHT)}`;
          return html`
            <gr-diff-row
              class="${leftClass} ${rightClass}"
              .left=${pair.left}
              .right=${pair.right}
              .layers=${this.layers}
              .lineLength=${this.diffPrefs?.line_length ?? 80}
              .tabSize=${this.diffPrefs?.tab_size ?? 2}
              .unifiedDiff=${this.isUnifiedDiff()}
              .responsiveMode=${responsiveMode}
              .hideFileCommentButton=${hideFileCommentButton}
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

  private isUnifiedDiff() {
    return this.viewMode === DiffViewMode.UNIFIED;
  }

  getLinePairs() {
    if (!this.group) return [];
    const isControl = this.group.type === GrDiffGroupType.CONTEXT_CONTROL;
    if (isControl) return [];
    return this.isUnifiedDiff()
      ? this.group.getUnifiedPairs()
      : this.group.getSideBySidePairs();
  }

  getDiffRows(): GrDiffRow[] {
    return [...this.querySelectorAll<GrDiffRow>('gr-diff-row')];
  }

  private renderContextControls() {
    if (this.group?.type !== GrDiffGroupType.CONTEXT_CONTROL) return;
    return html`
      <gr-context-controls-section .group=${this.group}>
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
    const plainCell = html`<td></td>`;
    const moveCell = html`
      <td class="moveHeader">
        <gr-range-header icon="move_item">
          ${this.renderMoveDescription(movedIn)}
        </gr-range-header>
      </td>
    `;
    return html`
      <tr class=${['moveControls', movedIn ? 'movedIn' : 'movedOut'].join(' ')}>
        ${when(this.columns.blame, () => html`<td class="blame"></td>`)}
        ${when(
          this.columns.leftNumber,
          () => html`<td class="moveControlsLineNumCol"></td>`
        )}
        ${when(this.columns.leftSign, () => html`<td class="sign"></td>`)}
        ${when(this.columns.leftContent, () =>
          movedIn ? plainCell : moveCell
        )}
        ${when(
          this.columns.rightNumber,
          () => html`<td class="moveControlsLineNumCol"></td>`
        )}
        ${when(this.columns.rightSign, () => html`<td class="sign"></td>`)}
        ${when(this.columns.rightContent, () =>
          movedIn || this.isUnifiedDiff() ? moveCell : plainCell
        )}
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
        <div>
          <span>${textLabel}</span>
          ${this.renderMovedLineAnchor(range.start, otherSide)}
          <span> - </span>
          ${this.renderMovedLineAnchor(range.end, otherSide)}
        </div>
      `;
    }

    return html`
      <div>
        <span>${movedIn ? 'Moved in' : 'Moved out'}</span>
      </div>
    `;
  }

  private renderMovedLineAnchor(line: number, side: Side) {
    const listener = (e: MouseEvent) => {
      e.preventDefault();
      this.handleMovedLineAnchorClick(e.target, side, line);
    };
    // `href` is not actually used but important for Screen Readers
    return html`<a href=${`#${line}`} @click=${listener}>${line}</a>`;
  }

  private handleMovedLineAnchorClick(
    anchor: EventTarget | null,
    side: Side,
    line: number
  ) {
    if (!anchor) return;
    fire(anchor, 'moved-link-clicked', {
      lineNum: line,
      side,
    });
  }
}

customElements.define('gr-diff-section', GrDiffSection);

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-section': GrDiffSection;
  }
}
