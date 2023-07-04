/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {createDiff, createEmptyDiff} from '../../../test/test-data-generators';
import './gr-diff-element';
import {GrDiffElement} from './gr-diff-element';
import {querySelectorAll} from '../../../utils/dom-util';
import '@polymer/paper-button/paper-button';
import {
  DiffContent,
  DiffInfo,
  DiffPreferencesInfo,
  DiffViewMode,
  IgnoreWhitespaceType,
} from '../../../api/diff';
import {query, queryAndAssert, waitUntil} from '../../../test/test-utils';
import {waitForEventOnce} from '../../../utils/event-util';
import {ImageInfo} from '../../../types/common';
import {fixture, html, assert} from '@open-wc/testing';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {DiffModel, diffModelToken} from '../gr-diff-model/gr-diff-model';
import {wrapInProvider} from '../../../models/di-provider-element';

const DEFAULT_PREFS = createDefaultDiffPrefs();

suite('gr-diff-element tests', () => {
  let element: GrDiffElement;
  let model: DiffModel;

  const MINIMAL_PREFS: DiffPreferencesInfo = {
    tab_size: 2,
    line_length: 80,
    font_size: 12,
    context: 3,
    ignore_whitespace: 'IGNORE_NONE',
  };

  setup(async () => {
    model = new DiffModel(document);
    element = (
      await fixture(
        wrapInProvider(
          html`<gr-diff-element></gr-diff-element>`,
          diffModelToken,
          model
        )
      )
    ).querySelector('gr-diff-element')!;
  });

  suite('rendering', () => {
    test('empty diff', async () => {
      await element.updateComplete;
      assert.lightDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer newDiff sideBySide">
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
      model.updateState({
        diff: createDiff(),
        diffPrefs: {...MINIMAL_PREFS},
        renderPrefs: {view_mode: DiffViewMode.UNIFIED},
      });
      await element.updateComplete;
      await waitForEventOnce(element, 'render');
      assert.lightDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer newDiff unified">
            <table id="diffTable">
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
                      FILE
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
                      FILE
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
      model.updateState({
        diff: createDiff(),
        diffPrefs: {...MINIMAL_PREFS},
        renderPrefs: {view_mode: DiffViewMode.SIDE_BY_SIDE},
      });
      await element.updateComplete;
      await waitForEventOnce(element, 'render');
      assert.lightDom.equal(
        element,
        /* HTML */ `
          <div class="diffContainer newDiff sideBySide">
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
                      FILE
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
                      FILE
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

  suite('not logged in', () => {
    setup(async () => {
      await element.updateComplete;
    });

    suite('binary diffs', () => {
      test('render binary diff', async () => {
        model.updateState({
          diff: {
            meta_a: {name: 'carrot.exe', content_type: 'binary', lines: 0},
            meta_b: {name: 'carrot.exe', content_type: 'binary', lines: 0},
            change_type: 'MODIFIED',
            intraline_status: 'OK',
            diff_header: [],
            content: [],
            binary: true,
          },
          diffPrefs: {...MINIMAL_PREFS},
        });
        await waitForEventOnce(element, 'render');

        assert.lightDom.equal(
          element,
          /* HTML */ `
            <div class="diffContainer newDiff sideBySide">
              <gr-diff-section class="left-FILE right-FILE"> </gr-diff-section>
              <gr-diff-row class="left-FILE right-FILE"> </gr-diff-row>
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
                        FILE
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
                        FILE
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

        element.diffPrefs = {
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
        model.updateState({
          diff: {
            meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
            meta_b: {
              name: 'carrot.jpg',
              content_type: 'image/jpeg',
              lines: 560,
            },
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
          },
          diffPrefs: {...MINIMAL_PREFS},
          renderPrefs: {view_mode: DiffViewMode.SIDE_BY_SIDE},
          baseImage: mockFile1,
          revisionImage: mockFile2,
        });

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
        model.updateState({
          diff: {
            meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
            meta_b: {
              name: 'carrot2.jpg',
              content_type: 'image/jpeg',
              lines: 560,
            },
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
          },
          diffPrefs: {...MINIMAL_PREFS},
          renderPrefs: {view_mode: DiffViewMode.SIDE_BY_SIDE},
          baseImage: {...mockFile1, _name: 'carrot.jpg'},
          revisionImage: {...mockFile1, _name: 'carrot2.jpg'},
        });

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
        model.updateState({
          diff: {
            meta_b: {
              name: 'carrot.jpg',
              content_type: 'image/jpeg',
              lines: 560,
            },
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
          },
          diffPrefs: {...MINIMAL_PREFS},
          renderPrefs: {view_mode: DiffViewMode.SIDE_BY_SIDE},
          revisionImage: mockFile2,
        });

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
        model.updateState({
          diff: {
            meta_a: {
              name: 'carrot.jpg',
              content_type: 'image/jpeg',
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
          },
          diffPrefs: {...MINIMAL_PREFS},
          renderPrefs: {view_mode: DiffViewMode.SIDE_BY_SIDE},
          baseImage: mockFile1,
        });

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
        model.updateState({
          diff: {
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
          },
          diffPrefs: {...MINIMAL_PREFS},
          renderPrefs: {view_mode: DiffViewMode.SIDE_BY_SIDE},
          baseImage: {...mockFile1, type: 'image/jpeg-evil'},
        });

        await waitForEventOnce(element, 'render');
        const imageDiffSection = queryAndAssert(element, 'tbody.image-diff');
        const leftImage = query(imageDiffSection, 'td.left img');
        assert.isNotOk(leftImage);
      });
    });
  });

  suite('diff header', () => {
    setup(async () => {
      model.updateState({
        diff: {
          meta_a: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 66},
          meta_b: {name: 'carrot.jpg', content_type: 'image/jpeg', lines: 560},
          diff_header: [],
          intraline_status: 'OK',
          change_type: 'MODIFIED',
          content: [{skip: 66}],
        },
        diffPrefs: {...MINIMAL_PREFS},
        renderPrefs: {view_mode: DiffViewMode.SIDE_BY_SIDE},
      });
      await element.updateComplete;
    });

    test('hidden', async () => {
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('diff --git a/test.jpg b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('index 2adc47d..f9c2f2c 100644');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('--- a/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('+++ b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('test');
      assert.equal(element.computeDiffHeaderItems().length, 1);
      element.requestUpdate('diff');
      await element.updateComplete;

      const header = queryAndAssert(element, '#diffHeader');
      assert.equal(header.textContent?.trim(), 'test');
    });

    test('binary files', () => {
      element.diff!.binary = true;
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('diff --git a/test.jpg b/test.jpg');
      assert.equal(element.computeDiffHeaderItems().length, 0);
      element.diff?.diff_header?.push('test');
      assert.equal(element.computeDiffHeaderItems().length, 1);
      element.diff?.diff_header?.push('Binary files differ');
      assert.equal(element.computeDiffHeaderItems().length, 1);
    });
  });

  suite('trailing newline warnings', () => {
    const NO_NEWLINE_LEFT = 'No newline at end of left file.';
    const NO_NEWLINE_RIGHT = 'No newline at end of right file.';

    const getWarning = (element: GrDiffElement) => {
      const warningElement = query(element, '.newlineWarning');
      return warningElement?.textContent ?? '';
    };

    test('shows combined warning if both sides set to warn', async () => {
      model.updateState({
        renderPrefs: {
          show_newline_warning_left: true,
          show_newline_warning_right: true,
        },
      });
      await element.updateComplete;
      assert.include(
        getWarning(element),
        NO_NEWLINE_LEFT + ' \u2014 ' + NO_NEWLINE_RIGHT
      ); // \u2014 - '—'
    });

    suite('showNewlineWarningLeft', () => {
      test('show warning if true', async () => {
        model.updateState({
          renderPrefs: {show_newline_warning_left: true},
        });
        await element.updateComplete;
        assert.include(getWarning(element), NO_NEWLINE_LEFT);
      });

      test('hide warning if false', async () => {
        model.updateState({
          renderPrefs: {show_newline_warning_left: false},
        });
        await element.updateComplete;
        assert.notInclude(getWarning(element), NO_NEWLINE_LEFT);
      });
    });

    suite('showNewlineWarningRight', () => {
      test('show warning if true', async () => {
        model.updateState({
          renderPrefs: {show_newline_warning_right: true},
        });
        await element.updateComplete;
        assert.include(getWarning(element), NO_NEWLINE_RIGHT);
      });

      test('hide warning if false', async () => {
        model.updateState({
          renderPrefs: {show_newline_warning_right: false},
        });
        await element.updateComplete;
        assert.notInclude(getWarning(element), NO_NEWLINE_RIGHT);
      });
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
    const diffPrefs = {
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
    const diff: DiffInfo = {
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
    model.updateState({diff, diffPrefs});
    await waitUntil(() => element.groups.length > 1);
    await element.updateComplete;
  };

  suite('whitespace changes only message', () => {
    test('show the message if ignore_whitespace is criteria matches', async () => {
      await setupSampleDiff({content: [{skip: 100}]});
      element.loading = false;
      assert.isTrue(element.showNoChangeMessage());
    });

    test('do not show the message for binary files', async () => {
      await setupSampleDiff({content: [{skip: 100}], binary: true});
      element.loading = false;
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show the message if still loading', async () => {
      await setupSampleDiff({content: [{skip: 100}]});
      element.loading = true;
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show the message if contains valid changes', async () => {
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
      element.loading = false;
      assert.isFalse(element.showNoChangeMessage());
    });

    test('do not show message if ignore whitespace is disabled', async () => {
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
      await setupSampleDiff({ignore_whitespace: 'IGNORE_NONE', content});
      element.loading = false;
      assert.isFalse(element.showNoChangeMessage());
    });
  });

  suite('rendering text, images and binary files', () => {
    let content: DiffContent[] = [];

    setup(() => {
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;
      element.diffPrefs = {
        ...DEFAULT_PREFS,
        context: -1,
        syntax_highlighting: true,
      };
      content = [
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
    });

    test('text', async () => {
      model.updateState({diff: {...createEmptyDiff(), content}});
      await waitUntil(() => element.groups.length > 2);
      await element.updateComplete;
      const bodies = [...(querySelectorAll(element, 'tbody') ?? [])];
      assert.equal(bodies.length, 4);
      assert.isTrue(bodies[0].innerHTML.includes('LOST'));
      assert.isTrue(bodies[1].innerHTML.includes('FILE'));
      assert.isTrue(bodies[2].innerHTML.includes('andybons a dull boy'));
      assert.isTrue(bodies[3].innerHTML.includes('Non eram nescius'));
    });

    test('image', async () => {
      model.updateState({
        diff: {
          ...createEmptyDiff(),
          content,
          binary: true,
          meta_a: {name: 'carrot1.jpg', content_type: 'image/jpeg', lines: 0},
          meta_b: {name: 'carrot2.jpg', content_type: 'image/jpeg', lines: 0},
        },
      });
      await element.updateComplete;
      const body = queryAndAssert(element, 'tbody.image-diff');
      assert.lightDom.equal(
        body,
        /* HTML */ `
          <label class="gr-diff">
            <span class="gr-diff label"> No image </span>
          </label>
          <label class="gr-diff">
            <span class="gr-diff label"> No image </span>
          </label>
        `
      );
    });

    test('binary', async () => {
      element.diff = {...createEmptyDiff(), content, binary: true};
      await element.updateComplete;
      const body = queryAndAssert(element, 'tbody.binary-diff');
      assert.lightDom.equal(
        body,
        /* HTML */ '<span>Difference in binary files</span>'
      );
    });
  });
});
