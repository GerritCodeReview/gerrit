/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {RenderPreferences} from '../../../api/diff';
import {LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {Side} from '../../../constants/constants';
import {DiffLayer, notUndefined} from '../../../types/types';
import {diffClasses} from '../gr-diff/gr-diff-utils';
import {GrDiffBuilder} from './gr-diff-builder';
import {BlameInfo} from '../../../types/common';
import {html, render} from 'lit';
import {GrDiffSection} from './gr-diff-section';
import '../gr-context-controls/gr-context-controls';
import './gr-diff-section';
import {GrDiffRow} from './gr-diff-row';

/**
 * Base class for builders that are creating the diff using Lit elements.
 */
export class GrDiffBuilderLit extends GrDiffBuilder {
  constructor(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    outputEl: HTMLElement,
    layers: DiffLayer[] = [],
    renderPrefs?: RenderPreferences
  ) {
    super(diff, prefs, outputEl, layers, renderPrefs);
  }

  override getContentTdByLine(
    lineNumber: LineNumber,
    side?: Side,
    _root: Element = this.outputEl
  ): HTMLTableCellElement | null {
    if (!side) return null;
    const row = this.findRow(lineNumber, side);
    return row?.getContentCell(side) ?? null;
  }

  override getLineElByNumber(lineNumber: LineNumber, side: Side) {
    const row = this.findRow(lineNumber, side);
    return row?.getLineNumberCell(side) ?? null;
  }

  private findRow(lineNumber?: LineNumber, side?: Side): GrDiffRow | undefined {
    if (!side || !lineNumber) return undefined;
    const group = this.findGroup(side, lineNumber);
    if (!group) return undefined;
    const section = this.findSection(group);
    if (!section) return undefined;
    return section.findRow(side, lineNumber);
  }

  private getDiffRows() {
    const sections = [
      ...this.outputEl.querySelectorAll<GrDiffSection>('gr-diff-section'),
    ];
    return sections.map(s => s.getDiffRows()).flat();
  }

  override getLineNumberRows(): HTMLTableRowElement[] {
    const rows = this.getDiffRows();
    return rows.map(r => r.getTableRow()).filter(notUndefined);
  }

  override getLineNumEls(side: Side): HTMLTableCellElement[] {
    const rows = this.getDiffRows();
    return rows.map(r => r.getLineNumberCell(side)).filter(notUndefined);
  }

  override getBlameTdByLine(lineNumber: number): Element | undefined {
    return this.findRow(lineNumber, Side.LEFT)?.getBlameCell();
  }

  override getContentByLine(
    lineNumber: LineNumber,
    side?: Side,
    _root?: HTMLElement
  ): HTMLElement | null {
    const cell = this.getContentTdByLine(lineNumber, side);
    return (cell?.firstChild ?? null) as HTMLElement | null;
  }

  override renderContentByRange(
    start: LineNumber,
    end: LineNumber,
    side: Side
  ) {
    // TODO: Revisit whether there is maybe a more efficient and reliable
    // approach. renderContentByRange() is only used when layers announce
    // updates. We have to look deeper into the design of layers anyway. So
    // let's defer optimizing this code until a refactor of layers in general.
    const groups = this.getGroupsByLineRange(start, end, side);
    for (const group of groups) {
      const section = this.findSection(group);
      for (const row of section?.getDiffRows() ?? []) {
        row.requestUpdate();
      }
    }
  }

  private findSection(group?: GrDiffGroup): GrDiffSection | undefined {
    if (!group) return undefined;
    const leftClass = `left-${group.lineRange.left.start_line}`;
    const rightClass = `right-${group.lineRange.right.start_line}`;
    return (
      this.outputEl.querySelector<GrDiffSection>(
        `gr-diff-section.${leftClass}.${rightClass}`
      ) ?? undefined
    );
  }

  override renderBlameByRange(
    blameInfo: BlameInfo,
    start: number,
    end: number
  ) {
    for (let lineNumber = start; lineNumber <= end; lineNumber++) {
      const row = this.findRow(lineNumber, Side.LEFT);
      if (!row) continue;
      row.blameInfo = blameInfo;
    }
  }

  // TODO: Refactor this such that adding the move controls becomes part of the
  // lit element.
  protected override getMoveControlsConfig() {
    return {
      numberOfCells: 4, // How many cells does the diff table have?
      movedOutIndex: 1, // Index of left content column in diff table.
      movedInIndex: 3, // Index of right content column in diff table.
      lineNumberCols: [0, 2], // Indices of line number columns in diff table.
    };
  }

  protected override buildSectionElement(group: GrDiffGroup) {
    const leftCl = `left-${group.lineRange.left.start_line}`;
    const rightCl = `right-${group.lineRange.right.start_line}`;
    const section = html`
      <gr-diff-section
        class="${leftCl} ${rightCl}"
        .group=${group}
        .diff=${this._diff}
        .layers=${this.layers}
        .diffPrefs=${this._prefs}
        .renderPrefs=${this.renderPrefs}
      ></gr-diff-section>
    `;
    // TODO: Refactor GrDiffBuilder.emitGroup() and buildSectionElement()
    // such that we can render directly into the correct container.
    const tempContainer = document.createElement('div');
    render(section, tempContainer);
    return tempContainer.firstElementChild as GrDiffSection;
  }

  override addColumns(outputEl: HTMLElement, lineNumberWidth: number): void {
    render(
      html`
        <colgroup>
         <col class="${diffClasses('blame')}"></col>
         <col class="${diffClasses(
           Side.LEFT
         )}" width="${lineNumberWidth}"></col>
         <col class="${diffClasses(Side.LEFT)}"></col>
         <col class="${diffClasses(
           Side.RIGHT
         )}" width="${lineNumberWidth}"></col>
         <col class="${diffClasses(Side.RIGHT)}"></col>
        </colgroup>
      `,
      outputEl
    );
  }

  protected override getNextContentOnSide(
    _content: HTMLElement,
    _side: Side
  ): HTMLElement | null {
    // TODO: getNextContentOnSide() is not required by lit based rendering.
    // So let's refactor it to be moved into gr-diff-builder-legacy.
    console.warn('unimplemented method getNextContentOnSide() called');
    return null;
  }
}
