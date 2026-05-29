/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-section';
import {GrDiffSection} from './gr-diff-section';
import {assert, fixture, html} from '@open-wc/testing';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {DiffViewMode, GrDiffLineType} from '../../../api/diff';
import {waitQueryAndAssert} from '../../../test/test-utils';
import {diffModelToken} from '../gr-diff-model/gr-diff-model';
import {testResolver} from '../../../test/common-test-setup';

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
              <tr class="moveControls movedOut">
                <td class="moveControlsLineNumCol"></td>
                <td class="moveHeader">
                  <gr-range-header icon="move_item">
                    <div>
                      <span> Moved to lines </span>
                      <a href="#1"> 1 </a>
                      <span> - </span>
                      <a href="#2"> 2 </a>
                    </div>
                  </gr-range-header>
                </td>
                <td class="moveControlsLineNumCol"></td>
                <td></td>
              </tr>
            </tbody>
          </table>
        `,
        {}
      );
    });

    test('unified', async () => {
      const diffModel = testResolver(diffModelToken);
      diffModel.updateState({renderPrefs: {view_mode: DiffViewMode.UNIFIED}});
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
              <tr class="moveControls movedOut">
                <td class="moveControlsLineNumCol"></td>
                <td class="moveControlsLineNumCol"></td>
                <td class="moveHeader">
                  <gr-range-header icon="move_item">
                    <div>
                      <span> Moved to lines </span>
                      <a href="#1"> 1 </a>
                      <span> - </span>
                      <a href="#2"> 2 </a>
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
          <tbody class="both section">
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="both content left no-intraline-info">
                <div class="contentText" data-side="left" id="left-content-1">
                  <gr-diff-text data-side="left">asdf</gr-diff-text>
                </div>
              </td>
              <td class="lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="both content no-intraline-info right">
                <div class="contentText" data-side="right" id="right-content-1">
                  <gr-diff-text data-side="right">asdf </gr-diff-text>
                </div>
              </td>
            </tr>
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="both content left no-intraline-info">
                <div class="contentText" data-side="left" id="left-content-1">
                  <gr-diff-text data-side="left"> qwer</gr-diff-text>
                </div>
              </td>
              <td class="lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="both content no-intraline-info right">
                <div class="contentText" data-side="right" id="right-content-1">
                  <gr-diff-text data-side="right">qwer </gr-diff-text>
                </div>
              </td>
            </tr>
            <tr
              aria-labelledby="left-button-1 left-content-1 right-button-1 right-content-1"
              class="diff-row side-by-side"
              left-type="both"
              right-type="both"
              tabindex="-1"
            >
              <td class="left lineNum" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="both content left no-intraline-info">
                <div class="contentText" data-side="left" id="left-content-1">
                  <gr-diff-text data-side="left">zxcv </gr-diff-text>
                </div>
              </td>
              <td class="lineNum right" data-value="1">
                <button
                  aria-label="1 unmodified"
                  class="lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="both content no-intraline-info right">
                <div class="contentText" data-side="right" id="right-content-1">
                  <gr-diff-text data-side="right">zxcv </gr-diff-text>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      `
    );
  });
});
