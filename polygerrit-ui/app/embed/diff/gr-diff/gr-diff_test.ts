/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createConfig,
  createDiff,
  createEmptyDiff,
} from '../../../test/test-data-generators';
import './gr-diff';
import {getComputedStyleValue} from '../../../utils/dom-util';
import '@polymer/paper-button/paper-button';
import {
  DiffContent,
  DiffLayer,
  DiffPreferencesInfo,
  DiffViewMode,
  GrDiffLineType,
  IgnoreWhitespaceType,
  Side,
} from '../../../api/diff';
import {
  mouseDown,
  queryAll,
  stubBaseUrl,
  stubRestApi,
  waitQueryAndAssert,
  waitUntil,
} from '../../../test/test-utils';
import {AbortStop} from '../../../api/core';
import {GrDiff} from './gr-diff';
import {GrRangedCommentHint} from '../gr-ranged-comment-hint/gr-ranged-comment-hint';
import {fixture, html, assert} from '@open-wc/testing';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  GrAnnotationImpl,
  getStringLength,
} from '../gr-diff-highlight/gr-annotation';
import {GrDiffLine} from './gr-diff-line';

const DEFAULT_PREFS = createDefaultDiffPrefs();

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    assert.isAccessible(await fixture(html`<gr-diff></gr-diff>`));
  });
});

suite('gr-diff tests', () => {
  let element: GrDiff;

  const MINIMAL_PREFS: DiffPreferencesInfo = {
    tab_size: 2,
    line_length: 80,
    font_size: 12,
    context: 3,
    ignore_whitespace: 'IGNORE_NONE',
  };

  setup(async () => {
    element = await fixture<GrDiff>(html`<gr-diff></gr-diff>`);
  });

<<<<<<< PATCH SET (9175de Move comment awareness into gr-diff-row)
  suite('rendering', () => {
    test('empty diff', async () => {
      await element.updateComplete;
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer sideBySide">
            <table id="diffTable">
              <colgroup>
                <col class="blame gr-diff" />
                <col class="gr-diff left" width="48" />
                <col class="gr-diff left sign" />
                <col class="gr-diff left" />
                <col class="gr-diff right" width="48" />
                <col class="gr-diff right sign" />
                <col class="gr-diff right" />
              </colgroup>
            </table>
          </div>
        `
      );
    });

    test('a unified diff lit', async () => {
      element.viewMode = DiffViewMode.UNIFIED;
      element.prefs = {...MINIMAL_PREFS};
      element.diff = createDiff();
      await element.updateComplete;
      await waitForEventOnce(element, 'render');
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer unified">
            <table class="selected-right" id="diffTable">
              <colgroup>
                <col class="blame gr-diff" />
                <col class="gr-diff" width="48" />
                <col class="gr-diff" width="48" />
                <col class="gr-diff" />
              </colgroup>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-LOST right-button-LOST right-content-LOST"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="LOST"></td>
                  <td class="gr-diff left lineNum" data-value="LOST"></td>
                  <td class="gr-diff lineNum right" data-value="LOST"></td>
                  <td
                    class="both content gr-diff lost no-intraline-info right"
                  ></td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-FILE right-button-FILE right-content-FILE"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="FILE"></td>
                  <td class="gr-diff left lineNum" data-value="FILE">
                    <button
                      aria-label="Add file comment"
                      class="gr-diff left lineNumButton"
                      data-value="FILE"
                      id="left-button-FILE"
                      tabindex="-1"
                    >
                      File
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="FILE">
                    <button
                      aria-label="Add file comment"
                      class="gr-diff lineNumButton right"
                      data-value="FILE"
                      id="right-button-FILE"
                      tabindex="-1"
                    >
                      File
                    </button>
                  </td>
                  <td
                    class="both content file gr-diff no-intraline-info right"
                  ></td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-1 right-button-1 right-content-1"
                  class="both diff-row gr-diff unified"
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
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-1"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-2 right-button-2 right-content-2"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="2"></td>
                  <td class="gr-diff left lineNum" data-value="2">
                    <button
                      aria-label="2 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="2"
                      id="left-button-2"
                      tabindex="-1"
                    >
                      2
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="2">
                    <button
                      aria-label="2 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="2"
                      id="right-button-2"
                      tabindex="-1"
                    >
                      2
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-2"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-3 right-button-3 right-content-3"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="3"></td>
                  <td class="gr-diff left lineNum" data-value="3">
                    <button
                      aria-label="3 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="3"
                      id="left-button-3"
                      tabindex="-1"
                    >
                      3
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="3">
                    <button
                      aria-label="3 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="3"
                      id="right-button-3"
                      tabindex="-1"
                    >
                      3
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-3"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-4 right-button-4 right-content-4"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="4"></td>
                  <td class="gr-diff left lineNum" data-value="4">
                    <button
                      aria-label="4 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="4"
                      id="left-button-4"
                      tabindex="-1"
                    >
                      4
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="4">
                    <button
                      aria-label="4 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="4"
                      id="right-button-4"
                      tabindex="-1"
                    >
                      4
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-4"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="right-button-5 right-content-5"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="5">
                    <button
                      aria-label="5 added"
                      class="gr-diff lineNumButton right"
                      data-value="5"
                      id="right-button-5"
                      tabindex="-1"
                    >
                      5
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-5"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-6 right-content-6"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="6">
                    <button
                      aria-label="6 added"
                      class="gr-diff lineNumButton right"
                      data-value="6"
                      id="right-button-6"
                      tabindex="-1"
                    >
                      6
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-6"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-7 right-content-7"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="7">
                    <button
                      aria-label="7 added"
                      class="gr-diff lineNumButton right"
                      data-value="7"
                      id="right-button-7"
                      tabindex="-1"
                    >
                      7
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-7"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-5 right-button-8 right-content-8"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="5"></td>
                  <td class="gr-diff left lineNum" data-value="5">
                    <button
                      aria-label="5 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="5"
                      id="left-button-5"
                      tabindex="-1"
                    >
                      5
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="8">
                    <button
                      aria-label="8 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="8"
                      id="right-button-8"
                      tabindex="-1"
                    >
                      8
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-8"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-6 right-button-9 right-content-9"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="6"></td>
                  <td class="gr-diff left lineNum" data-value="6">
                    <button
                      aria-label="6 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="6"
                      id="left-button-6"
                      tabindex="-1"
                    >
                      6
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="9">
                    <button
                      aria-label="9 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="9"
                      id="right-button-9"
                      tabindex="-1"
                    >
                      9
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-9"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-7 right-button-10 right-content-10"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="7"></td>
                  <td class="gr-diff left lineNum" data-value="7">
                    <button
                      aria-label="7 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="7"
                      id="left-button-7"
                      tabindex="-1"
                    >
                      7
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="10">
                    <button
                      aria-label="10 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="10"
                      id="right-button-10"
                      tabindex="-1"
                    >
                      10
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-10"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-8 right-button-11 right-content-11"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="8"></td>
                  <td class="gr-diff left lineNum" data-value="8">
                    <button
                      aria-label="8 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="8"
                      id="left-button-8"
                      tabindex="-1"
                    >
                      8
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="11">
                    <button
                      aria-label="11 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="11"
                      id="right-button-11"
                      tabindex="-1"
                    >
                      11
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-11"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-9 right-button-12 right-content-12"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="9"></td>
                  <td class="gr-diff left lineNum" data-value="9">
                    <button
                      aria-label="9 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="9"
                      id="left-button-9"
                      tabindex="-1"
                    >
                      9
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="12">
                    <button
                      aria-label="12 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="12"
                      id="right-button-12"
                      tabindex="-1"
                    >
                      12
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-12"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="left-button-10 left-content-10"
                  class="diff-row gr-diff remove unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="10"></td>
                  <td class="gr-diff left lineNum" data-value="10">
                    <button
                      aria-label="10 removed"
                      class="gr-diff left lineNumButton"
                      data-value="10"
                      id="left-button-10"
                      tabindex="-1"
                    >
                      10
                    </button>
                  </td>
                  <td class="gr-diff right"></td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-10"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-11 left-content-11"
                  class="diff-row gr-diff remove unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="11"></td>
                  <td class="gr-diff left lineNum" data-value="11">
                    <button
                      aria-label="11 removed"
                      class="gr-diff left lineNumButton"
                      data-value="11"
                      id="left-button-11"
                      tabindex="-1"
                    >
                      11
                    </button>
                  </td>
                  <td class="gr-diff right"></td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-11"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-12 left-content-12"
                  class="diff-row gr-diff remove unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="12"></td>
                  <td class="gr-diff left lineNum" data-value="12">
                    <button
                      aria-label="12 removed"
                      class="gr-diff left lineNumButton"
                      data-value="12"
                      id="left-button-12"
                      tabindex="-1"
                    >
                      12
                    </button>
                  </td>
                  <td class="gr-diff right"></td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-12"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-13 left-content-13"
                  class="diff-row gr-diff remove unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="13"></td>
                  <td class="gr-diff left lineNum" data-value="13">
                    <button
                      aria-label="13 removed"
                      class="gr-diff left lineNumButton"
                      data-value="13"
                      id="left-button-13"
                      tabindex="-1"
                    >
                      13
                    </button>
                  </td>
                  <td class="gr-diff right"></td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-13"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff ignoredWhitespaceOnly section">
                <tr
                  aria-labelledby="right-button-13 right-content-13"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="13">
                    <button
                      aria-label="13 added"
                      class="gr-diff lineNumButton right"
                      data-value="13"
                      id="right-button-13"
                      tabindex="-1"
                    >
                      13
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-13"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-14 right-content-14"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="14">
                    <button
                      aria-label="14 added"
                      class="gr-diff lineNumButton right"
                      data-value="14"
                      id="right-button-14"
                      tabindex="-1"
                    >
                      14
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-14"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section">
                <tr
                  aria-labelledby="left-button-16 left-content-16"
                  class="diff-row gr-diff remove unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="16"></td>
                  <td class="gr-diff left lineNum" data-value="16">
                    <button
                      aria-label="16 removed"
                      class="gr-diff left lineNumButton"
                      data-value="16"
                      id="left-button-16"
                      tabindex="-1"
                    >
                      16
                    </button>
                  </td>
                  <td class="gr-diff right"></td>
                  <td class="content gr-diff left remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-16"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-15 right-content-15"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="15">
                    <button
                      aria-label="15 added"
                      class="gr-diff lineNumButton right"
                      data-value="15"
                      id="right-button-15"
                      tabindex="-1"
                    >
                      15
                    </button>
                  </td>
                  <td class="add content gr-diff right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-15"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-17 right-button-16 right-content-16"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="17"></td>
                  <td class="gr-diff left lineNum" data-value="17">
                    <button
                      aria-label="17 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="17"
                      id="left-button-17"
                      tabindex="-1"
                    >
                      17
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="16">
                    <button
                      aria-label="16 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="16"
                      id="right-button-16"
                      tabindex="-1"
                    >
                      16
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-16"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-18 right-button-17 right-content-17"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="18"></td>
                  <td class="gr-diff left lineNum" data-value="18">
                    <button
                      aria-label="18 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="18"
                      id="left-button-18"
                      tabindex="-1"
                    >
                      18
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="17">
                    <button
                      aria-label="17 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="17"
                      id="right-button-17"
                      tabindex="-1"
                    >
                      17
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-17"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-19 right-button-18 right-content-18"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="19"></td>
                  <td class="gr-diff left lineNum" data-value="19">
                    <button
                      aria-label="19 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="19"
                      id="left-button-19"
                      tabindex="-1"
                    >
                      19
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="18">
                    <button
                      aria-label="18 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="18"
                      id="right-button-18"
                      tabindex="-1"
                    >
                      18
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-18"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="contextControl gr-diff section">
                <tr class="above contextBackground gr-diff unified">
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff"></td>
                </tr>
                <tr class="dividerRow gr-diff show-both">
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="dividerCell gr-diff" colspan="3">
                    <gr-context-controls class="gr-diff" showconfig="both">
                    </gr-context-controls>
                  </td>
                </tr>
                <tr class="below contextBackground gr-diff unified">
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff"></td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-38 right-button-37 right-content-37"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="38"></td>
                  <td class="gr-diff left lineNum" data-value="38">
                    <button
                      aria-label="38 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="38"
                      id="left-button-38"
                      tabindex="-1"
                    >
                      38
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="37">
                    <button
                      aria-label="37 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="37"
                      id="right-button-37"
                      tabindex="-1"
                    >
                      37
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-37"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-39 right-button-38 right-content-38"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="39"></td>
                  <td class="gr-diff left lineNum" data-value="39">
                    <button
                      aria-label="39 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="39"
                      id="left-button-39"
                      tabindex="-1"
                    >
                      39
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="38">
                    <button
                      aria-label="38 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="38"
                      id="right-button-38"
                      tabindex="-1"
                    >
                      38
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-38"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-40 right-button-39 right-content-39"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="40"></td>
                  <td class="gr-diff left lineNum" data-value="40">
                    <button
                      aria-label="40 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="40"
                      id="left-button-40"
                      tabindex="-1"
                    >
                      40
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="39">
                    <button
                      aria-label="39 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="39"
                      id="right-button-39"
                      tabindex="-1"
                    >
                      39
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-39"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="right-button-40 right-content-40"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="40">
                    <button
                      aria-label="40 added"
                      class="gr-diff lineNumButton right"
                      data-value="40"
                      id="right-button-40"
                      tabindex="-1"
                    >
                      40
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-40"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-41 right-content-41"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="41">
                    <button
                      aria-label="41 added"
                      class="gr-diff lineNumButton right"
                      data-value="41"
                      id="right-button-41"
                      tabindex="-1"
                    >
                      41
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-41"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-42 right-content-42"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="42">
                    <button
                      aria-label="42 added"
                      class="gr-diff lineNumButton right"
                      data-value="42"
                      id="right-button-42"
                      tabindex="-1"
                    >
                      42
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-42"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-43 right-content-43"
                  class="add diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff left"></td>
                  <td class="gr-diff lineNum right" data-value="43">
                    <button
                      aria-label="43 added"
                      class="gr-diff lineNumButton right"
                      data-value="43"
                      id="right-button-43"
                      tabindex="-1"
                    >
                      43
                    </button>
                  </td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-43"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-41 right-button-44 right-content-44"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="41"></td>
                  <td class="gr-diff left lineNum" data-value="41">
                    <button
                      aria-label="41 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="41"
                      id="left-button-41"
                      tabindex="-1"
                    >
                      41
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="44">
                    <button
                      aria-label="44 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="44"
                      id="right-button-44"
                      tabindex="-1"
                    >
                      44
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-44"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-42 right-button-45 right-content-45"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="42"></td>
                  <td class="gr-diff left lineNum" data-value="42">
                    <button
                      aria-label="42 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="42"
                      id="left-button-42"
                      tabindex="-1"
                    >
                      42
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="45">
                    <button
                      aria-label="45 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="45"
                      id="right-button-45"
                      tabindex="-1"
                    >
                      45
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-45"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-43 right-button-46 right-content-46"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="43"></td>
                  <td class="gr-diff left lineNum" data-value="43">
                    <button
                      aria-label="43 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="43"
                      id="left-button-43"
                      tabindex="-1"
                    >
                      43
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="46">
                    <button
                      aria-label="46 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="46"
                      id="right-button-46"
                      tabindex="-1"
                    >
                      46
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-46"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-44 right-button-47 right-content-47"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="44"></td>
                  <td class="gr-diff left lineNum" data-value="44">
                    <button
                      aria-label="44 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="44"
                      id="left-button-44"
                      tabindex="-1"
                    >
                      44
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="47">
                    <button
                      aria-label="47 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="47"
                      id="right-button-47"
                      tabindex="-1"
                    >
                      47
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-47"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-45 right-button-48 right-content-48"
                  class="both diff-row gr-diff unified"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="45"></td>
                  <td class="gr-diff left lineNum" data-value="45">
                    <button
                      aria-label="45 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="45"
                      id="left-button-45"
                      tabindex="-1"
                    >
                      45
                    </button>
                  </td>
                  <td class="gr-diff lineNum right" data-value="48">
                    <button
                      aria-label="48 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="48"
                      id="right-button-48"
                      tabindex="-1"
                    >
                      48
                    </button>
                  </td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-48"
                    ></div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        `,
        {
          ignoreTags: [
            'gr-context-controls-section',
            'gr-diff-section',
            'gr-diff-row',
            'gr-diff-text',
            'gr-legacy-text',
            'slot',
          ],
        }
      );
    });

    test('a normal diff lit', async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.diff = createDiff();
      await element.updateComplete;
      await waitForEventOnce(element, 'render');
      assert.shadowDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer sideBySide">
            <table class="selected-right" id="diffTable">
              <colgroup>
                <col class="blame gr-diff" />
                <col class="gr-diff left" width="48" />
                <col class="gr-diff left sign" />
                <col class="gr-diff left" />
                <col class="gr-diff right" width="48" />
                <col class="gr-diff right sign" />
                <col class="gr-diff right" />
              </colgroup>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-LOST left-content-LOST right-button-LOST right-content-LOST"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="LOST"></td>
                  <td class="gr-diff left lineNum" data-value="LOST"></td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td
                    class="both content gr-diff left lost no-intraline-info"
                  ></td>
                  <td class="gr-diff lineNum right" data-value="LOST"></td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td
                    class="both content gr-diff lost no-intraline-info right"
                  ></td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-FILE left-content-FILE right-button-FILE right-content-FILE"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="FILE"></td>
                  <td class="gr-diff left lineNum" data-value="FILE">
                    <button
                      aria-label="Add file comment"
                      class="gr-diff left lineNumButton"
                      data-value="FILE"
                      id="left-button-FILE"
                      tabindex="-1"
                    >
                      File
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td
                    class="both content file gr-diff left no-intraline-info"
                  ></td>
                  <td class="gr-diff lineNum right" data-value="FILE">
                    <button
                      aria-label="Add file comment"
                      class="gr-diff lineNumButton right"
                      data-value="FILE"
                      id="right-button-FILE"
                      tabindex="-1"
                    >
                      File
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td
                    class="both content file gr-diff no-intraline-info right"
                  ></td>
                </tr>
              </tbody>
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
                    ></div>
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
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-2 left-content-2 right-button-2 right-content-2"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="2"></td>
                  <td class="gr-diff left lineNum" data-value="2">
                    <button
                      aria-label="2 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="2"
                      id="left-button-2"
                      tabindex="-1"
                    >
                      2
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-2"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="2">
                    <button
                      aria-label="2 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="2"
                      id="right-button-2"
                      tabindex="-1"
                    >
                      2
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-2"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-3 left-content-3 right-button-3 right-content-3"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="3"></td>
                  <td class="gr-diff left lineNum" data-value="3">
                    <button
                      aria-label="3 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="3"
                      id="left-button-3"
                      tabindex="-1"
                    >
                      3
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-3"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="3">
                    <button
                      aria-label="3 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="3"
                      id="right-button-3"
                      tabindex="-1"
                    >
                      3
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-3"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-4 left-content-4 right-button-4 right-content-4"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="4"></td>
                  <td class="gr-diff left lineNum" data-value="4">
                    <button
                      aria-label="4 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="4"
                      id="left-button-4"
                      tabindex="-1"
                    >
                      4
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-4"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="4">
                    <button
                      aria-label="4 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="4"
                      id="right-button-4"
                      tabindex="-1"
                    >
                      4
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-4"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="right-button-5 right-content-5"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="5">
                    <button
                      aria-label="5 added"
                      class="gr-diff lineNumButton right"
                      data-value="5"
                      id="right-button-5"
                      tabindex="-1"
                    >
                      5
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-5"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-6 right-content-6"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="6">
                    <button
                      aria-label="6 added"
                      class="gr-diff lineNumButton right"
                      data-value="6"
                      id="right-button-6"
                      tabindex="-1"
                    >
                      6
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-6"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-7 right-content-7"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="7">
                    <button
                      aria-label="7 added"
                      class="gr-diff lineNumButton right"
                      data-value="7"
                      id="right-button-7"
                      tabindex="-1"
                    >
                      7
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-7"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-5 left-content-5 right-button-8 right-content-8"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="5"></td>
                  <td class="gr-diff left lineNum" data-value="5">
                    <button
                      aria-label="5 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="5"
                      id="left-button-5"
                      tabindex="-1"
                    >
                      5
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-5"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="8">
                    <button
                      aria-label="8 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="8"
                      id="right-button-8"
                      tabindex="-1"
                    >
                      8
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-8"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-6 left-content-6 right-button-9 right-content-9"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="6"></td>
                  <td class="gr-diff left lineNum" data-value="6">
                    <button
                      aria-label="6 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="6"
                      id="left-button-6"
                      tabindex="-1"
                    >
                      6
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-6"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="9">
                    <button
                      aria-label="9 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="9"
                      id="right-button-9"
                      tabindex="-1"
                    >
                      9
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-9"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-7 left-content-7 right-button-10 right-content-10"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="7"></td>
                  <td class="gr-diff left lineNum" data-value="7">
                    <button
                      aria-label="7 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="7"
                      id="left-button-7"
                      tabindex="-1"
                    >
                      7
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-7"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="10">
                    <button
                      aria-label="10 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="10"
                      id="right-button-10"
                      tabindex="-1"
                    >
                      10
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-10"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-8 left-content-8 right-button-11 right-content-11"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="8"></td>
                  <td class="gr-diff left lineNum" data-value="8">
                    <button
                      aria-label="8 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="8"
                      id="left-button-8"
                      tabindex="-1"
                    >
                      8
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-8"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="11">
                    <button
                      aria-label="11 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="11"
                      id="right-button-11"
                      tabindex="-1"
                    >
                      11
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-11"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-9 left-content-9 right-button-12 right-content-12"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="9"></td>
                  <td class="gr-diff left lineNum" data-value="9">
                    <button
                      aria-label="9 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="9"
                      id="left-button-9"
                      tabindex="-1"
                    >
                      9
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-9"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="12">
                    <button
                      aria-label="12 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="12"
                      id="right-button-12"
                      tabindex="-1"
                    >
                      12
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-12"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="left-button-10 left-content-10"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="10"></td>
                  <td class="gr-diff left lineNum" data-value="10">
                    <button
                      aria-label="10 removed"
                      class="gr-diff left lineNumButton"
                      data-value="10"
                      id="left-button-10"
                      tabindex="-1"
                    >
                      10
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-10"
                    ></div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-11 left-content-11"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="11"></td>
                  <td class="gr-diff left lineNum" data-value="11">
                    <button
                      aria-label="11 removed"
                      class="gr-diff left lineNumButton"
                      data-value="11"
                      id="left-button-11"
                      tabindex="-1"
                    >
                      11
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-11"
                    ></div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-12 left-content-12"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="12"></td>
                  <td class="gr-diff left lineNum" data-value="12">
                    <button
                      aria-label="12 removed"
                      class="gr-diff left lineNumButton"
                      data-value="12"
                      id="left-button-12"
                      tabindex="-1"
                    >
                      12
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-12"
                    ></div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-13 left-content-13"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="blank"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="13"></td>
                  <td class="gr-diff left lineNum" data-value="13">
                    <button
                      aria-label="13 removed"
                      class="gr-diff left lineNumButton"
                      data-value="13"
                      id="left-button-13"
                      tabindex="-1"
                    >
                      13
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-13"
                    ></div>
                  </td>
                  <td class="blankLineNum gr-diff right"></td>
                  <td class="blank gr-diff no-intraline-info right sign"></td>
                  <td class="blank gr-diff no-intraline-info right">
                    <div class="contentText gr-diff" data-side="right"></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff ignoredWhitespaceOnly section">
                <tr
                  aria-labelledby="left-button-14 left-content-14 right-button-13 right-content-13"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="14"></td>
                  <td class="gr-diff left lineNum" data-value="14">
                    <button
                      aria-label="14 removed"
                      class="gr-diff left lineNumButton"
                      data-value="14"
                      id="left-button-14"
                      tabindex="-1"
                    >
                      14
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-14"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="13">
                    <button
                      aria-label="13 added"
                      class="gr-diff lineNumButton right"
                      data-value="13"
                      id="right-button-13"
                      tabindex="-1"
                    >
                      13
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-13"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-15 left-content-15 right-button-14 right-content-14"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="15"></td>
                  <td class="gr-diff left lineNum" data-value="15">
                    <button
                      aria-label="15 removed"
                      class="gr-diff left lineNumButton"
                      data-value="15"
                      id="left-button-15"
                      tabindex="-1"
                    >
                      15
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info remove sign">-</td>
                  <td class="content gr-diff left no-intraline-info remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-15"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="14">
                    <button
                      aria-label="14 added"
                      class="gr-diff lineNumButton right"
                      data-value="14"
                      id="right-button-14"
                      tabindex="-1"
                    >
                      14
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-14"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section">
                <tr
                  aria-labelledby="left-button-16 left-content-16 right-button-15 right-content-15"
                  class="diff-row gr-diff side-by-side"
                  left-type="remove"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="16"></td>
                  <td class="gr-diff left lineNum" data-value="16">
                    <button
                      aria-label="16 removed"
                      class="gr-diff left lineNumButton"
                      data-value="16"
                      id="left-button-16"
                      tabindex="-1"
                    >
                      16
                    </button>
                  </td>
                  <td class="gr-diff left remove sign">-</td>
                  <td class="content gr-diff left remove">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-16"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="15">
                    <button
                      aria-label="15 added"
                      class="gr-diff lineNumButton right"
                      data-value="15"
                      id="right-button-15"
                      tabindex="-1"
                    >
                      15
                    </button>
                  </td>
                  <td class="add gr-diff right sign">+</td>
                  <td class="add content gr-diff right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-15"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-17 left-content-17 right-button-16 right-content-16"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="17"></td>
                  <td class="gr-diff left lineNum" data-value="17">
                    <button
                      aria-label="17 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="17"
                      id="left-button-17"
                      tabindex="-1"
                    >
                      17
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-17"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="16">
                    <button
                      aria-label="16 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="16"
                      id="right-button-16"
                      tabindex="-1"
                    >
                      16
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-16"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-18 left-content-18 right-button-17 right-content-17"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="18"></td>
                  <td class="gr-diff left lineNum" data-value="18">
                    <button
                      aria-label="18 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="18"
                      id="left-button-18"
                      tabindex="-1"
                    >
                      18
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-18"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="17">
                    <button
                      aria-label="17 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="17"
                      id="right-button-17"
                      tabindex="-1"
                    >
                      17
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-17"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-19 left-content-19 right-button-18 right-content-18"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="19"></td>
                  <td class="gr-diff left lineNum" data-value="19">
                    <button
                      aria-label="19 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="19"
                      id="left-button-19"
                      tabindex="-1"
                    >
                      19
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-19"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="18">
                    <button
                      aria-label="18 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="18"
                      id="right-button-18"
                      tabindex="-1"
                    >
                      18
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-18"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="contextControl gr-diff section">
                <tr
                  class="above contextBackground gr-diff side-by-side"
                  left-type="contextControl"
                  right-type="contextControl"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                </tr>
                <tr class="dividerRow gr-diff show-both">
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="gr-diff"></td>
                  <td class="dividerCell gr-diff" colspan="3">
                    <gr-context-controls
                      class="gr-diff"
                      showconfig="both"
                    ></gr-context-controls>
                  </td>
                </tr>
                <tr
                  class="below contextBackground gr-diff side-by-side"
                  left-type="contextControl"
                  right-type="contextControl"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                  <td class="contextLineNum gr-diff"></td>
                  <td class="gr-diff sign"></td>
                  <td class="gr-diff"></td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-38 left-content-38 right-button-37 right-content-37"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="38"></td>
                  <td class="gr-diff left lineNum" data-value="38">
                    <button
                      aria-label="38 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="38"
                      id="left-button-38"
                      tabindex="-1"
                    >
                      38
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-38"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="37">
                    <button
                      aria-label="37 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="37"
                      id="right-button-37"
                      tabindex="-1"
                    >
                      37
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-37"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-39 left-content-39 right-button-38 right-content-38"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="39"></td>
                  <td class="gr-diff left lineNum" data-value="39">
                    <button
                      aria-label="39 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="39"
                      id="left-button-39"
                      tabindex="-1"
                    >
                      39
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-39"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="38">
                    <button
                      aria-label="38 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="38"
                      id="right-button-38"
                      tabindex="-1"
                    >
                      38
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-38"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-40 left-content-40 right-button-39 right-content-39"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="40"></td>
                  <td class="gr-diff left lineNum" data-value="40">
                    <button
                      aria-label="40 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="40"
                      id="left-button-40"
                      tabindex="-1"
                    >
                      40
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-40"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="39">
                    <button
                      aria-label="39 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="39"
                      id="right-button-39"
                      tabindex="-1"
                    >
                      39
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-39"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="delta gr-diff section total">
                <tr
                  aria-labelledby="right-button-40 right-content-40"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="40">
                    <button
                      aria-label="40 added"
                      class="gr-diff lineNumButton right"
                      data-value="40"
                      id="right-button-40"
                      tabindex="-1"
                    >
                      40
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-40"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-41 right-content-41"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="41">
                    <button
                      aria-label="41 added"
                      class="gr-diff lineNumButton right"
                      data-value="41"
                      id="right-button-41"
                      tabindex="-1"
                    >
                      41
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-41"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-42 right-content-42"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="42">
                    <button
                      aria-label="42 added"
                      class="gr-diff lineNumButton right"
                      data-value="42"
                      id="right-button-42"
                      tabindex="-1"
                    >
                      42
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-42"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="right-button-43 right-content-43"
                  class="diff-row gr-diff side-by-side"
                  left-type="blank"
                  right-type="add"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="0"></td>
                  <td class="blankLineNum gr-diff left"></td>
                  <td class="blank gr-diff left no-intraline-info sign"></td>
                  <td class="blank gr-diff left no-intraline-info">
                    <div class="contentText gr-diff" data-side="left"></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="43">
                    <button
                      aria-label="43 added"
                      class="gr-diff lineNumButton right"
                      data-value="43"
                      id="right-button-43"
                      tabindex="-1"
                    >
                      43
                    </button>
                  </td>
                  <td class="add gr-diff no-intraline-info right sign">+</td>
                  <td class="add content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-43"
                    ></div>
                  </td>
                </tr>
              </tbody>
              <tbody class="both gr-diff section">
                <tr
                  aria-labelledby="left-button-41 left-content-41 right-button-44 right-content-44"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="41"></td>
                  <td class="gr-diff left lineNum" data-value="41">
                    <button
                      aria-label="41 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="41"
                      id="left-button-41"
                      tabindex="-1"
                    >
                      41
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-41"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="44">
                    <button
                      aria-label="44 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="44"
                      id="right-button-44"
                      tabindex="-1"
                    >
                      44
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-44"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-42 left-content-42 right-button-45 right-content-45"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="42"></td>
                  <td class="gr-diff left lineNum" data-value="42">
                    <button
                      aria-label="42 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="42"
                      id="left-button-42"
                      tabindex="-1"
                    >
                      42
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-42"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="45">
                    <button
                      aria-label="45 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="45"
                      id="right-button-45"
                      tabindex="-1"
                    >
                      45
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-45"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-43 left-content-43 right-button-46 right-content-46"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="43"></td>
                  <td class="gr-diff left lineNum" data-value="43">
                    <button
                      aria-label="43 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="43"
                      id="left-button-43"
                      tabindex="-1"
                    >
                      43
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-43"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="46">
                    <button
                      aria-label="46 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="46"
                      id="right-button-46"
                      tabindex="-1"
                    >
                      46
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-46"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-44 left-content-44 right-button-47 right-content-47"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="44"></td>
                  <td class="gr-diff left lineNum" data-value="44">
                    <button
                      aria-label="44 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="44"
                      id="left-button-44"
                      tabindex="-1"
                    >
                      44
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-44"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="47">
                    <button
                      aria-label="47 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="47"
                      id="right-button-47"
                      tabindex="-1"
                    >
                      47
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-47"
                    ></div>
                  </td>
                </tr>
                <tr
                  aria-labelledby="left-button-45 left-content-45 right-button-48 right-content-48"
                  class="diff-row gr-diff side-by-side"
                  left-type="both"
                  right-type="both"
                  tabindex="-1"
                >
                  <td class="blame gr-diff" data-line-number="45"></td>
                  <td class="gr-diff left lineNum" data-value="45">
                    <button
                      aria-label="45 unmodified"
                      class="gr-diff left lineNumButton"
                      data-value="45"
                      id="left-button-45"
                      tabindex="-1"
                    >
                      45
                    </button>
                  </td>
                  <td class="gr-diff left no-intraline-info sign"></td>
                  <td class="both content gr-diff left no-intraline-info">
                    <div
                      class="contentText gr-diff"
                      data-side="left"
                      id="left-content-45"
                    ></div>
                  </td>
                  <td class="gr-diff lineNum right" data-value="48">
                    <button
                      aria-label="48 unmodified"
                      class="gr-diff lineNumButton right"
                      data-value="48"
                      id="right-button-48"
                      tabindex="-1"
                    >
                      48
                    </button>
                  </td>
                  <td class="gr-diff no-intraline-info right sign"></td>
                  <td class="both content gr-diff no-intraline-info right">
                    <div
                      class="contentText gr-diff"
                      data-side="right"
                      id="right-content-48"
                    ></div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        `,
        {
          ignoreTags: [
            'gr-context-controls-section',
            'gr-diff-section',
            'gr-diff-row',
            'gr-diff-text',
            'gr-legacy-text',
            'slot',
          ],
        }
      );
    });
  });

=======
>>>>>>> BASE      (1d03ce Simplify `isTargetBlank` in gr-diff-cursor)
  suite('selectionchange event handling', () => {
    let handleSelectionChangeStub: sinon.SinonSpy;

    const emulateSelection = function () {
      document.dispatchEvent(new CustomEvent('selectionchange'));
    };

    setup(async () => {
      handleSelectionChangeStub = sinon.spy(
        element.highlights,
        'handleSelectionChange'
      );
    });

    test('enabled if logged in', async () => {
      element.loggedIn = true;
      await element.updateComplete;
      emulateSelection();
      assert.isTrue(handleSelectionChangeStub.called);
    });

    test('ignored if logged out', async () => {
      element.loggedIn = false;
      await element.updateComplete;
      emulateSelection();
      assert.isFalse(handleSelectionChangeStub.called);
    });
  });

  test('line limit with line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: true};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '80ch');
  });

  test('line limit without line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: false};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '-1px');
  });

  suite('FULL_RESPONSIVE mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'FULL_RESPONSIVE'};
      await element.updateComplete;
    });

    test('line limit is based on line_length', async () => {
      element.prefs = {...element.prefs!, line_length: 100};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--line-limit-marker', element),
        '100ch'
      );
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });
  });

  suite('SHRINK_ONLY mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'SHRINK_ONLY'};
      await element.updateComplete;
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });

    test('max-width considers two content columns in side-by-side', async () => {
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers one content column in unified', async () => {
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(1 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers font-size', async () => {
      element.prefs = {...element.prefs!, font_size: 13};
      await element.updateComplete;
      // Each line number column: 4 * 13 = 52px
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 52px + 0ch + 1px + 2px)'
      );
    });

    test('sign cols are considered if show_sign_col is true', async () => {
      element.renderPrefs = {...element.renderPrefs, show_sign_col: true};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 2ch + 1px + 2px)'
      );
    });
  });

  suite('not logged in', () => {
    setup(async () => {
      element.loggedIn = false;
      await element.updateComplete;
    });

    test('toggleLeftDiff', () => {
      element.toggleLeftDiff();
      assert.isTrue(element.classList.contains('no-left'));
      element.toggleLeftDiff();
      assert.isFalse(element.classList.contains('no-left'));
    });

<<<<<<< PATCH SET (9175de Move comment awareness into gr-diff-row)
    suite('binary diffs', () => {
      test('render binary diff', async () => {
        element.prefs = {
          ...MINIMAL_PREFS,
        };
        element.diff = {
          meta_a: {name: 'carrot.exe', content_type: 'binary', lines: 0},
          meta_b: {name: 'carrot.exe', content_type: 'binary', lines: 0},
          change_type: 'MODIFIED',
          intraline_status: 'OK',
          diff_header: [],
          content: [],
          binary: true,
        };
        await waitForEventOnce(element, 'render');

        assert.shadowDom.equal(
          element,
          /* HTML */ `
            <div class="diffContainer sideBySide">
              <gr-diff-section class="left-FILE right-FILE"> </gr-diff-section>
              <gr-diff-row class="left-FILE right-FILE"> </gr-diff-row>
              <table class="selected-right" id="diffTable">
                <colgroup>
                  <col class="blame gr-diff" />
                  <col class="gr-diff left" width="48" />
                  <col class="gr-diff left sign" />
                  <col class="gr-diff left" />
                  <col class="gr-diff right" width="48" />
                  <col class="gr-diff right sign" />
                  <col class="gr-diff right" />
                </colgroup>
                <tbody class="both gr-diff section">
                  <tr
                    aria-labelledby="left-button-FILE left-content-FILE right-button-FILE right-content-FILE"
                    class="diff-row gr-diff side-by-side"
                    left-type="both"
                    right-type="both"
                    tabindex="-1"
                  >
                    <td class="blame gr-diff" data-line-number="FILE"></td>
                    <td class="gr-diff left lineNum" data-value="FILE">
                      <button
                        aria-label="Add file comment"
                        class="gr-diff left lineNumButton"
                        data-value="FILE"
                        id="left-button-FILE"
                        tabindex="-1"
                      >
                        File
                      </button>
                    </td>
                    <td class="gr-diff left no-intraline-info sign"></td>
                    <td
                      class="both content file gr-diff left no-intraline-info"
                    ></td>
                    <td class="gr-diff lineNum right" data-value="FILE">
                      <button
                        aria-label="Add file comment"
                        class="gr-diff lineNumButton right"
                        data-value="FILE"
                        id="right-button-FILE"
                        tabindex="-1"
                      >
                        File
                      </button>
                    </td>
                    <td class="gr-diff no-intraline-info right sign"></td>
                    <td
                      class="both content file gr-diff no-intraline-info right"
                    ></td>
                  </tr>
                </tbody>
                <tbody class="binary-diff gr-diff">
                  <tr class="gr-diff">
                    <td class="gr-diff" colspan="5">
                      <span> Difference in binary files </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          `
        );
      });
    });

    suite('image diffs', () => {
      let mockFile1: ImageInfo;
      let mockFile2: ImageInfo;
      setup(() => {
        mockFile1 = {
          body:
            'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
            'wsAAAAAAAAAAAAAAAAA/w==',
          type: 'image/bmp',
        };
        mockFile2 = {
          body:
            'Qk06AAAAAAAAADYAAAAoAAAAAQAAAP////8BACAAAAAAAAAAAAATCwAAE' +
            'wsAAAAAAAAAAAAA/////w==',
          type: 'image/bmp',
        };

        element.isImageDiff = true;
        element.prefs = {
          context: 10,
          cursor_blink_rate: 0,
          font_size: 12,
          ignore_whitespace: 'IGNORE_NONE',
          line_length: 100,
          line_wrapping: false,
          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          tab_size: 8,
        };
      });

      test('render image diff', async () => {
        element.baseImage = mockFile1;
        element.revisionImage = mockFile2;
        element.diff = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };

        await waitForEventOnce(element, 'render');
        const imageDiffSection = queryAndAssert(element, 'tbody.image-diff');
        assert.lightDom.equal(
          imageDiffSection,
          /* HTML */ `
            <tbody class="gr-diff image-diff">
              <tr class="gr-diff">
                <td class="blank gr-diff left lineNum"></td>
                <td class="gr-diff left">
                  <img
                    class="gr-diff left"
                    src="data:image/bmp;base64,${mockFile1.body}"
                  />
                </td>
                <td class="blank gr-diff lineNum right"></td>
                <td class="gr-diff right">
                  <img
                    class="gr-diff right"
                    src="data:image/bmp;base64,${mockFile2.body}"
                  />
                </td>
              </tr>
              <tr class="gr-diff">
                <td class="blank gr-diff left lineNum"></td>
                <td class="gr-diff left">
                  <label class="gr-diff">
                    <span class="gr-diff label"> 1×1 image/bmp </span>
                  </label>
                </td>
                <td class="blank gr-diff lineNum right"></td>
                <td class="gr-diff right">
                  <label class="gr-diff">
                    <span class="gr-diff label"> 1×1 image/bmp </span>
                  </label>
                </td>
              </tr>
            </tbody>
          `
        );
        const endpoint = queryAndAssert(element, 'tbody.endpoint');
        assert.dom.equal(
          endpoint,
          /* HTML */ `
            <tbody class="gr-diff endpoint">
              <tr class="gr-diff">
                <gr-endpoint-decorator class="gr-diff" name="image-diff">
                  <gr-endpoint-param class="gr-diff" name="baseImage">
                  </gr-endpoint-param>
                  <gr-endpoint-param class="gr-diff" name="revisionImage">
                  </gr-endpoint-param>
                </gr-endpoint-decorator>
              </tr>
            </tbody>
          `
        );
      });

      test('renders image diffs with a different file name', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot2.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot2.jpg',
            'index 2adc47d..f9c2f2c 100644',
            '--- a/carrot.jpg',
            '+++ b/carrot2.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };

        element.baseImage = mockFile1;
        element.baseImage._name = mockDiff.meta_a!.name;
        element.revisionImage = mockFile2;
        element.revisionImage._name = mockDiff.meta_b!.name;
        element.diff = mockDiff;

        await waitForEventOnce(element, 'render');
        const imageDiffSection = queryAndAssert(element, 'tbody.image-diff');
        const leftLabel = queryAndAssert(imageDiffSection, 'td.left label');
        const rightLabel = queryAndAssert(imageDiffSection, 'td.right label');
        assert.dom.equal(
          leftLabel,
          /* HTML */ `
            <label class="gr-diff">
              <span class="gr-diff name"> carrot.jpg </span>
              <br class="gr-diff" />
              <span class="gr-diff label"> 1×1 image/bmp </span>
            </label>
          `
        );
        assert.dom.equal(
          rightLabel,
          /* HTML */ `
            <label class="gr-diff">
              <span class="gr-diff name"> carrot2.jpg </span>
              <br class="gr-diff" />
              <span class="gr-diff label"> 1×1 image/bmp </span>
            </label>
          `
        );
      });

      test('renders added image', async () => {
        const mockDiff: DiffInfo = {
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'ADDED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index 0000000..f9c2f2c 100644',
            '--- /dev/null',
            '+++ b/carrot.jpg',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        element.revisionImage = mockFile2;
        element.diff = mockDiff;

        await waitForEventOnce(element, 'render');
        const imageDiffSection = queryAndAssert(element, 'tbody.image-diff');
        const leftImage = query(imageDiffSection, 'td.left img');
        const rightImage = queryAndAssert(imageDiffSection, 'td.right img');
        assert.isNotOk(leftImage);
        assert.dom.equal(
          rightImage,
          /* HTML */ `
            <img
              class="gr-diff right"
              src="data:image/bmp;base64,${mockFile2.body}"
            />
          `
        );
      });

      test('renders removed image', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        element.baseImage = mockFile1;
        element.diff = mockDiff;

        await waitForEventOnce(element, 'render');
        const imageDiffSection = queryAndAssert(element, 'tbody.image-diff');
        const leftImage = queryAndAssert(imageDiffSection, 'td.left img');
        const rightImage = query(imageDiffSection, 'td.right img');
        assert.isNotOk(rightImage);
        assert.dom.equal(
          leftImage,
          /* HTML */ `
            <img
              class="gr-diff left"
              src="data:image/bmp;base64,${mockFile1.body}"
            />
          `
        );
      });

      test('does not render disallowed image type', async () => {
        const mockDiff: DiffInfo = {
          meta_a: {
            name: 'carrot.jpg',
            content_type: 'image/jpeg-evil',
            lines: 560,
          },
          intraline_status: 'OK',
          change_type: 'DELETED',
          diff_header: [
            'diff --git a/carrot.jpg b/carrot.jpg',
            'index f9c2f2c..0000000 100644',
            '--- a/carrot.jpg',
            '+++ /dev/null',
            'Binary files differ',
          ],
          content: [{skip: 66}],
          binary: true,
        };
        mockFile1.type = 'image/jpeg-evil';
        element.baseImage = mockFile1;
        element.diff = mockDiff;

        await waitForEventOnce(element, 'render');
        const imageDiffSection = queryAndAssert(element, 'tbody.image-diff');
        const leftImage = query(imageDiffSection, 'td.left img');
        assert.isNotOk(leftImage);
      });
    });

=======
>>>>>>> BASE      (1d03ce Simplify `isTargetBlank` in gr-diff-cursor)
    suite('getCursorStops', () => {
      async function setupDiff() {
        element.diff = createDiff();
        element.prefs = {
          context: 10,
          tab_size: 8,
          font_size: 12,
          line_length: 100,
          cursor_blink_rate: 0,
          line_wrapping: false,

          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          ignore_whitespace: 'IGNORE_NONE',
        };
        await element.updateComplete;
      }

      test('returns [] when hidden and noAutoRender', async () => {
        element.noAutoRender = true;
        await setupDiff();
        element.loading = false;
        await element.updateComplete;
        element.hidden = true;
        await element.updateComplete;
        assert.equal(element.getCursorStops().length, 0);
      });

      test('returns one stop per line and one for the file row', async () => {
        await setupDiff();
        element.loading = false;
        await waitUntil(() => element.groups.length > 2);
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        const LOST_ROW = 1;
        assert.equal(
          element.getCursorStops().length,
          ROWS + FILE_ROW + LOST_ROW
        );
      });

      test('returns an additional AbortStop when still loading', async () => {
        await setupDiff();
        element.loading = true;
        await waitUntil(() => element.groups.length > 2);
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        const LOST_ROW = 1;
        element.loading = true;
        const actual = element.getCursorStops();
        assert.equal(actual.length, ROWS + FILE_ROW + LOST_ROW + 1);
        assert.isTrue(actual[actual.length - 1] instanceof AbortStop);
      });
    });
  });

  suite('logged in', async () => {
    setup(async () => {
      element.loggedIn = true;
      await element.updateComplete;
    });

    test('adds long range comment hint', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(threadEl);

      const hint = await waitQueryAndAssert<GrRangedCommentHint>(
        element,
        'gr-ranged-comment-hint'
      );
      assert.deepEqual(hint.range, range);
    });

    test('no duplicate range hint for same thread', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const firstHint = document.createElement('gr-ranged-comment-hint');
      firstHint.range = range;
      firstHint.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(firstHint);
      element.appendChild(threadEl);

      assert.equal(
        element.querySelectorAll('gr-ranged-comment-hint').length,
        1
      );
    });

    test('removes long range comment hint when comment is discarded', async () => {
      const range = {
        start_line: 1,
        end_line: 7,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const content = [
        {
          ab: Array(8).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(threadEl);
      await waitUntil(() => element.commentRanges.length === 1);

      threadEl.remove();
      await waitUntil(() => element.commentRanges.length === 0);

      assert.isEmpty(element.querySelectorAll('gr-ranged-comment-hint'));
    });
  });

  suite('blame', () => {
    test('unsetting', async () => {
      element.blame = [];
      const setBlameSpy = sinon.spy(element, 'setBlame');
      element.classList.add('showBlame');
      element.blame = null;
      await element.updateComplete;
      assert.isTrue(setBlameSpy.calledWithExactly([]));
      assert.isFalse(element.classList.contains('showBlame'));
    });

    test('setting', async () => {
      element.blame = [
        {
          author: 'test-author',
          time: 12345,
          commit_msg: '',
          id: 'commit id',
          ranges: [{start: 1, end: 2}],
        },
      ];
      await element.updateComplete;
      assert.isTrue(element.classList.contains('showBlame'));
    });
  });

  const setupSampleDiff = async function (params: {
    content: DiffContent[];
    ignore_whitespace?: IgnoreWhitespaceType;
    binary?: boolean;
  }) {
    const {ignore_whitespace, content} = params;
    // binary can't be undefined, use false if not set
    const binary = params.binary || false;
    element.prefs = {
      ignore_whitespace: ignore_whitespace || 'IGNORE_ALL',
      context: 10,
      cursor_blink_rate: 0,
      font_size: 12,

      line_length: 100,
      line_wrapping: false,
      show_line_endings: true,
      show_tabs: true,
      show_whitespace_errors: true,
      syntax_highlighting: true,
      tab_size: 8,
    };
    element.diff = {
      intraline_status: 'OK',
      change_type: 'MODIFIED',
      diff_header: [
        'diff --git a/carrot.js b/carrot.js',
        'index 2adc47d..f9c2f2c 100644',
        '--- a/carrot.js',
        '+++ b/carrot.jjs',
        'file differ',
      ],
      content,
      binary,
    };
    await waitUntil(() => element.groups.length > 1);
    await element.updateComplete;
  };

  suite('selection test', () => {
    test('user-select set correctly on side-by-side view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});

      // We are selecting "Non eram nescius..." on the left side.
      // The default is `selected-right`, so we will have to click.
      const diffLine = queryAll<HTMLElement>(element, '.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });

    test('user-select set correctly on unified view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      element.viewMode = DiffViewMode.UNIFIED;
      await setupSampleDiff({content});

      // We are selecting "all work and no play..." on the left side.
      // The default is `selected-right`, so we will have to click.
      const diffLine = queryAll<HTMLElement>(element, '.contentText')[0];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });
  });
});

suite('former gr-diff-builder tests', () => {
  let element: GrDiff;

  const line = (text: string) => {
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    line.text = text;
    return line;
  };

  setup(async () => {
    element = await fixture<GrDiff>(html`<gr-diff></gr-diff>`);
    element.diff = createEmptyDiff();
    await element.updateComplete;
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getProjectConfig').returns(Promise.resolve(createConfig()));
    stubBaseUrl('/r');
  });

  suite('intraline differences', () => {
    let el: HTMLElement;
    let str: string;
    let annotateElementSpy: sinon.SinonSpy;
    let layer: DiffLayer;
    const lineNumberEl = document.createElement('td');

    function slice(str: string, start: number, end?: number) {
      return Array.from(str).slice(start, end).join('');
    }

    setup(async () => {
      el = await fixture(html`
        <div>Lorem ipsum dolor sit amet, suspendisse inceptos vehicula</div>
      `);
      str = el.textContent ?? '';
      annotateElementSpy = sinon.spy(GrAnnotationImpl, 'annotateElement');
      layer = element.createIntralineLayer();
    });

    test('annotate no highlights', () => {
      layer.annotate(el, lineNumberEl, line(str), Side.LEFT);

      // The content is unchanged.
      assert.isFalse(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 1);
      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(str, el.childNodes[0].textContent);
    });

    test('annotate with highlights', () => {
      const l = line(str);
      l.highlights = [
        {contentIndex: 0, startIndex: 6, endIndex: 12},
        {contentIndex: 0, startIndex: 18, endIndex: 22},
      ];
      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6, 12);
      const str2 = slice(str, 12, 18);
      const str3 = slice(str, 18, 22);
      const str4 = slice(str, 22);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 5);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);

      assert.instanceOf(el.childNodes[2], Text);
      assert.equal(el.childNodes[2].textContent, str2);

      assert.notInstanceOf(el.childNodes[3], Text);
      assert.equal(el.childNodes[3].textContent, str3);

      assert.instanceOf(el.childNodes[4], Text);
      assert.equal(el.childNodes[4].textContent, str4);
    });

    test('annotate without endIndex', () => {
      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 28}];

      const str0 = slice(str, 0, 28);
      const str1 = slice(str, 28);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 2);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);
    });

    test('annotate ignores empty highlights', () => {
      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 28, endIndex: 28}];

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 1);
    });

    test('annotate handles unicode', () => {
      // Put some unicode into the string:
      str = str.replace(/\s/g, '💢');
      el.textContent = str;
      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 6, endIndex: 12}];

      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6, 12);
      const str2 = slice(str, 12);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 3);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);

      assert.instanceOf(el.childNodes[2], Text);
      assert.equal(el.childNodes[2].textContent, str2);
    });

    test('annotate handles unicode w/o endIndex', () => {
      // Put some unicode into the string:
      str = str.replace(/\s/g, '💢');
      el.textContent = str;

      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 6}];

      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6);
      const numHighlightedChars = getStringLength(str1);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.calledWith(el, 6, numHighlightedChars));
      assert.equal(el.childNodes.length, 2);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);
    });
  });

  suite('tab indicators', () => {
    let layer: DiffLayer;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      element.prefs = {...DEFAULT_PREFS, show_tabs: true};
      layer = element.createTabIndicatorLayer();
    });

    test('does nothing with empty line', () => {
      const l = line('');
      const el = document.createElement('div');
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no tabs', () => {
      const str = 'lorem ipsum no tabs';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates tab at beginning', () => {
      const str = '\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 0, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });

    test('does not annotate when disabled', () => {
      element.prefs = {...DEFAULT_PREFS, show_tabs: false};

      const str = '\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates multiple in beginning', () => {
      const str = '\t\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 2);

      let args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 0, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');

      args = annotateElementStub.getCalls()[1].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 1, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });

    test('annotates intermediate tabs', () => {
      const str = 'lorem\tupsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 5, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });
  });

  suite('trailing whitespace', () => {
    let layer: DiffLayer;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      element.prefs = {
        ...createDefaultDiffPrefs(),
        show_whitespace_errors: true,
      };
      layer = element.createTrailingWhitespaceLayer();
    });

    test('does nothing with empty line', () => {
      const l = line('');
      const el = document.createElement('div');
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no trailing whitespace', () => {
      const str = 'lorem ipsum blah blah';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });

    test('annotates trailing spaces', () => {
      const str = 'lorem ipsum   ';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('annotates trailing tabs', () => {
      const str = 'lorem ipsum\t\t\t';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('annotates mixed trailing whitespace', () => {
      const str = 'lorem ipsum\t \t';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('unicode preceding trailing whitespace', () => {
      const str = '💢\t';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 1);
      assert.equal(annotateElementStub.lastCall.args[2], 1);
    });

    test('does not annotate when disabled', () => {
      element.prefs = {
        ...createDefaultDiffPrefs(),
        show_whitespace_errors: false,
      };
      const str = 'lorem upsum\t \t ';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });
  });

  suite('context hiding and expanding', () => {
    setup(async () => {
      element.diff = {
        ...createEmptyDiff(),
        content: [
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${i}`)},
          {a: ['before'], b: ['after']},
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${10 + i}`)},
        ],
      };
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;
      element.prefs = {
        ...DEFAULT_PREFS,
        context: 1,
      };
      await waitUntil(() => element.groups.length > 2);
      await element.updateComplete;
    });

    test('hides lines behind two context controls', () => {
      const contextControls = queryAll(element, 'gr-context-controls');
      assert.equal(contextControls.length, 2);

      const diffRows = queryAll(element, '.diff-row');
      // The first two are LOST and FILE line
      assert.equal(diffRows.length, 2 + 1 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 10');
      assert.include(diffRows[3].textContent, 'before');
      assert.include(diffRows[3].textContent, 'after');
      assert.include(diffRows[4].textContent, 'unchanged 11');
    });

    test('clicking +x common lines expands those lines', async () => {
      const contextControls = queryAll(element, 'gr-context-controls');
      const topExpandCommonButton =
        contextControls[0].shadowRoot?.querySelectorAll<HTMLElement>(
          '.showContext'
        )[0];
      assert.isOk(topExpandCommonButton);
      assert.include(topExpandCommonButton!.textContent, '+9 common lines');
      let diffRows = queryAll(element, '.diff-row');
      // 5 lines:
      // FILE, LOST, the changed line plus one line of context in each direction
      assert.equal(diffRows.length, 5);

      topExpandCommonButton!.click();

      await waitUntil(() => {
        diffRows = queryAll(element, '.diff-row');
        return diffRows.length === 14;
      });
      // 14 lines: The 5 above plus the 9 unchanged lines that were expanded
      assert.equal(diffRows.length, 14);
      assert.include(diffRows[2].textContent, 'unchanged 1');
      assert.include(diffRows[3].textContent, 'unchanged 2');
      assert.include(diffRows[4].textContent, 'unchanged 3');
      assert.include(diffRows[5].textContent, 'unchanged 4');
      assert.include(diffRows[6].textContent, 'unchanged 5');
      assert.include(diffRows[7].textContent, 'unchanged 6');
      assert.include(diffRows[8].textContent, 'unchanged 7');
      assert.include(diffRows[9].textContent, 'unchanged 8');
      assert.include(diffRows[10].textContent, 'unchanged 9');
      assert.include(diffRows[11].textContent, 'unchanged 10');
      assert.include(diffRows[12].textContent, 'before');
      assert.include(diffRows[12].textContent, 'after');
      assert.include(diffRows[13].textContent, 'unchanged 11');
    });

    test('unhideLine shows the line with context', async () => {
      element.unhideLine(4, Side.LEFT);

      await waitUntil(() => {
        const rows = queryAll(element, '.diff-row');
        return rows.length === 2 + 5 + 1 + 1 + 1;
      });

      const diffRows = queryAll(element, '.diff-row');
      // The first two are LOST and FILE line
      // Lines 3-5 (Line 4 plus 1 context in each direction) will be expanded
      // Because context expanders do not hide <3 lines, lines 1-2 will also
      // be shown.
      // Lines 6-9 continue to be hidden
      assert.equal(diffRows.length, 2 + 5 + 1 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 1');
      assert.include(diffRows[3].textContent, 'unchanged 2');
      assert.include(diffRows[4].textContent, 'unchanged 3');
      assert.include(diffRows[5].textContent, 'unchanged 4');
      assert.include(diffRows[6].textContent, 'unchanged 5');
      assert.include(diffRows[7].textContent, 'unchanged 10');
      assert.include(diffRows[8].textContent, 'before');
      assert.include(diffRows[8].textContent, 'after');
      assert.include(diffRows[9].textContent, 'unchanged 11');
    });
  });
});
