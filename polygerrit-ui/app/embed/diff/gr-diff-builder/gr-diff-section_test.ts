/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-section';
import {GrDiffSection} from './gr-diff-section';
import {fixture, html, assert} from '@open-wc/testing';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {DiffViewMode, GrDiffLineType} from '../../../api/diff';
import {waitQueryAndAssert} from '../../../test/test-utils';

suite('gr-diff-section test', () => {
  let element: GrDiffSection;

  setup(async () => {
    element = await fixture<GrDiffSection>(
      html`<gr-diff-section></gr-diff-section>`
    );
    element.addTableWrapperForTesting = true;
    await element.updateComplete;
  });

  suite('move controls', async () => {
    setup(async () => {
      const lines = [new GrDiffLine(GrDiffLineType.BOTH, 1, 1)];
      lines[0].text = 'asdf';
      const group = new GrDiffGroup({
        type: GrDiffGroupType.BOTH,
        lines,
        moveDetails: {changed: false, range: {start: 1, end: 2}},
      });
      element.group = group;
      await element.updateComplete;
    });

    test('side-by-side', async () => {
      const row = await waitQueryAndAssert(element, 'tr.moveControls');
      // Semantic dom diff has a problem with just comparing table rows or
      // cells directly. So as a workaround put the row into an empty test
      // table.
      const testTable = document.createElement('table');
      testTable.appendChild(row);
      assert.dom.equal(
        testTable,
        /* HTML */ `
          <table>
            <tbody>
              <tr class="gr-diff moveControls movedOut">
                <td class="gr-diff moveControlsLineNumCol"></td>
                <td class="gr-diff sign"></td>
                <td class="gr-diff moveHeader">
                  <gr-range-header class="gr-diff" icon="move_item">
                    <div class="gr-diff">
                      <span class="gr-diff"> Moved to lines </span>
                      <a class="gr-diff" href="#1"> 1 </a>
                      <span class="gr-diff"> - </span>
                      <a class="gr-diff" href="#2"> 2 </a>
                    </div>
                  </gr-range-header>
                </td>
                <td class="gr-diff moveControlsLineNumCol"></td>
                <td class="gr-diff sign"></td>
                <td class="gr-diff"></td>
              </tr>
            </tbody>
          </table>
        `,
        {}
      );
    });

    test('unified', async () => {
      element.viewMode = DiffViewMode.UNIFIED;
      const row = await waitQueryAndAssert(element, 'tr.moveControls');
      // Semantic dom diff has a problem with just comparing table rows or
      // cells directly. So as a workaround put the row into an empty test
      // table.
      const testTable = document.createElement('table');
      testTable.appendChild(row);
      assert.dom.equal(
        testTable,
        /* HTML */ `
          <table>
            <tbody>
              <tr class="gr-diff moveControls movedOut">
                <td class="gr-diff moveControlsLineNumCol"></td>
                <td class="gr-diff moveControlsLineNumCol"></td>
                <td class="gr-diff moveHeader">
                  <gr-range-header class="gr-diff" icon="move_item">
                    <div class="gr-diff">
                      <span class="gr-diff"> Moved to lines </span>
                      <a class="gr-diff" href="#1"> 1 </a>
                      <span class="gr-diff"> - </span>
                      <a class="gr-diff" href="#2"> 2 </a>
                    </div>
                  </gr-range-header>
                </td>
              </tr>
            </tbody>
          </table>
        `,
        {}
      );
    });
  });

  test('3 normal unchanged rows', async () => {
    const lines = [
      new GrDiffLine(GrDiffLineType.BOTH, 1, 1),
      new GrDiffLine(GrDiffLineType.BOTH, 1, 1),
      new GrDiffLine(GrDiffLineType.BOTH, 1, 1),
    ];
    lines[0].text = 'asdf';
    lines[1].text = 'qwer';
    lines[2].text = 'zxcv';
    const group = new GrDiffGroup({type: GrDiffGroupType.BOTH, lines});
    element.group = group;
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <slot name="post-left-line-1"></slot>
        <slot name="post-right-line-1"></slot>
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <slot name="post-left-line-1"></slot>
        <slot name="post-right-line-1"></slot>
        <gr-diff-row class="left-1 right-1"> </gr-diff-row>
        <slot name="post-left-line-1"></slot>
        <slot name="post-right-line-1"></slot>
        <table>
          <tbody class="both gr-diff section">
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row gr-diff side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff" data-line-number="1"></td>
              <td class="gr-diff left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff left no-intraline-info sign"></td>
              <td class="both content gr-diff left no-intraline-info">
                <div
                  class="contentText gr-diff"
                  data-side="left"
                  id="left-content-1"
                >
                  <gr-diff-text>asdf</gr-diff-text>
                </div>
              </td>
              <td class="gr-diff lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff no-intraline-info right sign"></td>
              <td class="both content gr-diff no-intraline-info right">
                <div
                  class="contentText gr-diff"
                  data-side="right"
                  id="right-content-1"
                >
                  <gr-diff-text>asdf </gr-diff-text>
                </div>
              </td>
            </tr>
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row gr-diff side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff" data-line-number="1"></td>
              <td class="gr-diff left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff left no-intraline-info sign"></td>
              <td class="both content gr-diff left no-intraline-info">
                <div
                  class="contentText gr-diff"
                  data-side="left"
                  id="left-content-1"
                >
                  <gr-diff-text> qwer</gr-diff-text>
                </div>
              </td>
              <td class="gr-diff lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff no-intraline-info right sign"></td>
              <td class="both content gr-diff no-intraline-info right">
                <div
                  class="contentText gr-diff"
                  data-side="right"
                  id="right-content-1"
                >
                  <gr-diff-text>qwer </gr-diff-text>
                </div>
              </td>
            </tr>
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row gr-diff side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="blame gr-diff" data-line-number="1"></td>
              <td class="gr-diff left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff left no-intraline-info sign"></td>
              <td class="both content gr-diff left no-intraline-info">
                <div
                  class="contentText gr-diff"
                  data-side="left"
                  id="left-content-1"
                >
                  <gr-diff-text>zxcv </gr-diff-text>
                </div>
              </td>
              <td class="gr-diff lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="gr-diff lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="gr-diff no-intraline-info right sign"></td>
              <td class="both content gr-diff no-intraline-info right">
                <div
                  class="contentText gr-diff"
                  data-side="right"
                  id="right-content-1"
                >
                  <gr-diff-text>zxcv </gr-diff-text>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      `
    );
  });
});
