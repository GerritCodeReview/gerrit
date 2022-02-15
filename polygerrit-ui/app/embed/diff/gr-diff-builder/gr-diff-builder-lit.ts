/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {LineRange, RenderPreferences} from '../../../api/diff';
import {LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
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
    const group = this.findGroup(side, lineNumber);
    if (!group) return null;
    const section = this.findSection(group);
    if (!section) return null;
    const row = section.findRow(side, lineNumber);
    if (!row) return null;
    return row.findContentCell(side) ?? null;
  }

  override getLineElByNumber(lineNumber: LineNumber, side: Side) {
    if (!side) return null;
    const group = this.findGroup(side, lineNumber);
    if (!group) return null;
    const section = this.findSection(group);
    if (!section) return null;
    const row = section.findRow(side, lineNumber);
    if (!row) return null;
    return row.findLineNumberCell(side) ?? null;
  }

  override getLineNumberRows() {
    const sections = [
      ...this.outputEl.querySelectorAll<GrDiffSection>('gr-diff-section'),
    ];
    const rows = sections.map(s => s.getDiffRows()).flat();
    return rows.map(r => r.getTableRow()).filter(notUndefined);
  }

  override getLineNumEls(side: Side): HTMLTableCellElement[] {
    const sections = [
      ...this.outputEl.querySelectorAll<GrDiffSection>('gr-diff-section'),
    ];
    const rows = sections.map(s => s.getDiffRows()).flat();
    return rows.map(r => r.getLineNumberCell(side)).filter(notUndefined);
  }

  override getBlameTdByLine(lineNumber: number): Element | undefined {
    const group = this.findGroup(Side.LEFT, lineNumber);
    if (!group) return undefined;
    const section = this.findSection(group);
    if (!section) return undefined;
    const row = section.findRow(Side.LEFT, lineNumber);
    if (!row) return undefined;
    return row.findBlameCell();
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
    console.log(`renderContentByRange ${side} ${start}-${end}`);
    const groups = this.getGroupsByLineRange(start, end, side);
    for (const group of groups) {
      const oldSection = this.findSection(group);
      const newSection = this.buildSectionElement(group);
      oldSection?.replaceWith(newSection);
    }
  }

  private findSection(group?: GrDiffGroup): GrDiffSection | undefined {
    if (!group) return undefined;
    const leftCl = `left-${group.lineRange.left.start_line}`;
    const rightCl = `right-${group.lineRange.right.start_line}`;
    return (this.outputEl.querySelector(
      `gr-diff-section.${leftCl}.${rightCl}`
    ) ?? undefined) as GrDiffSection | undefined;
  }

  override renderBlameByRange(_blame: BlameInfo, _start: number, _end: number) {
    // TODO: Implement method.
    console.warn('unimplemented method renderBlameByRange() called');
  }

  protected override getMoveControlsConfig() {
    return {
      numberOfCells: 4,
      movedOutIndex: 1,
      movedInIndex: 3,
      lineNumberCols: [0, 2],
    };
  }

  protected override buildSectionElement(group: GrDiffGroup) {
    console.log(`buildSectionElement ${JSON.stringify(group.lineRange)}`);
    const leftCl = `left-${group.lineRange.left.start_line}`;
    const rightCl = `right-${group.lineRange.right.start_line}`;
    const section = html`
      <gr-diff-section
        class="${leftCl} ${rightCl}"
        .group=${group}
        .diff=${this._diff}
        .layers=${this.layers}
        .renderPrefs=${this.renderPrefs}
      >
        ${this.renderSlots(group, Side.LEFT, group.lineRange.left)}
        ${this.renderSlots(group, Side.RIGHT, group.lineRange.right)}
      </gr-diff-section>
    `;
    const tempContainer = document.createElement('div');
    render(section, tempContainer);
    return tempContainer.firstElementChild as GrDiffSection;
  }

  /**
   * Slots are used for comment threads. Here we are just re-targeting:
   * Thread elements are added to gr-diff with `slot=...`. The `name` of the
   * rendered slots makes sure that the thread elements are slotted here.
   * The `slot` attribute makes sure that the thread elements end up in the
   * right place within <gr-diff-section>.
   */
  private renderSlots(group: GrDiffGroup, side: Side, range: LineRange) {
    if (group.type === GrDiffGroupType.CONTEXT_CONTROL) return;
    const slots: string[] = [];
    for (let line = range.start_line; line <= range.end_line; line++) {
      slots.push(`${side}-${line}`);
    }
    return slots.map(slot => html`<slot name=${slot} slot=${slot}></slot>`);
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
    // TODO: Implement method.
    console.warn('unimplemented method getNextContentOnSide() called');
    return null;
  }
}
