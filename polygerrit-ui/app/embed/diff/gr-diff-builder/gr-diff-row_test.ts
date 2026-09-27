/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {LitElement} from 'lit';
import './gr-diff-row';
import {GrDiffRow} from './gr-diff-row';
import {assert, fixture, html} from '@open-wc/testing';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {DiffViewMode, GrDiffLineType, Side} from '../../../api/diff';
import {diffModelToken} from '../gr-diff-model/gr-diff-model';
import {testResolver} from '../../../test/common-test-setup';

interface GrDiffRowPrivate {
  layersApplied: boolean;
  updateLayers(side: Side): Promise<void>;
}

suite('gr-diff-row test', () => {
  let element: GrDiffRow;

  setup(async () => {
    element = await fixture<GrDiffRow>(html`<gr-diff-row></gr-diff-row>`);
    element.addTableWrapperForTesting = true;
    await element.updateComplete;
  });

  test('both', async () => {
    const line = new GrDiffLine(GrDiffLineType.BOTH, 1, 1);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = line;
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
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
                <div
                  class="contentText"
                  data-side="left"
                  id="left-content-1"
                >
                  <gr-diff-text data-side="left"> lorem ipsum </gr-diff-text>
                </div>
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
                <div
                  class="contentText"
                  data-side="right"
                  id="right-content-1"
                >
                  <gr-diff-text data-side="right"> lorem ipsum </gr-diff-text>
                </div>
              </td>
            </tr>
            <slot name="post-left-line-1"></slot>
            <slot name="post-right-line-1"></slot>
          </tbody>
        </table>
      `
    );
  });

  test('both unified', async () => {
    const line = new GrDiffLine(GrDiffLineType.BOTH, 1, 1);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = line;
    const diffModel = testResolver(diffModelToken);
    diffModel.updateState({renderPrefs: {view_mode: DiffViewMode.UNIFIED}});
    element.unifiedDiff = true;
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
            <tr
              aria-labelledby="left-button-1 right-button-1 right-content-1"
              class="both diff-row unified"
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
                  <gr-diff-text data-side="right"> lorem ipsum </gr-diff-text>
                </div>
              </td>
            </tr>
            <slot name="post-left-line-1"></slot>
            <slot name="post-right-line-1"></slot>
          </tbody>
        </table>
      `
    );
  });

  test('add', async () => {
    const line = new GrDiffLine(GrDiffLineType.ADD, 0, 1);
    line.text = 'lorem ipsum';
    element.left = new GrDiffLine(GrDiffLineType.BLANK);
    element.right = line;
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
            <tr
              aria-labelledby="right-button-1 right-content-1"
              class="diff-row side-by-side"
              left-type="blank"
              right-type="add"
              tabindex="-1"
            >
              <td class="blankLineNum left"></td>
              <td class="blank left no-intraline-info">
                <div class="contentText" data-side="left">
                  <gr-diff-text data-side="left"></gr-diff-text>
                </div>
              </td>
              <td class="lineNum right" data-value="1">
                <button
                  aria-label="1 added"
                  class="lineNumButton right"
                  data-value="1"
                  id="right-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="add content no-intraline-info right">
                <div class="contentText" data-side="right" id="right-content-1">
                  <gr-diff-text data-side="right"> lorem ipsum </gr-diff-text>
                </div>
              </td>
              <slot name="post-right-line-1"></slot>
            </tr>
          </tbody>
        </table>
      `
    );
  });

  test('remove', async () => {
    const line = new GrDiffLine(GrDiffLineType.REMOVE, 1, 0);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = new GrDiffLine(GrDiffLineType.BLANK);
    await element.updateComplete;
    assert.lightDom.equal(
      element,
      /* HTML */ `
        <table>
          <tbody>
            <tr
              aria-labelledby="left-button-1 left-content-1"
              class="diff-row side-by-side"
              left-type="remove"
              right-type="blank"
              tabindex="-1"
            >
              <td class="left lineNum" data-value="1">
                <button
                  aria-label="1 removed"
                  class="left lineNumButton"
                  data-value="1"
                  id="left-button-1"
                  tabindex="-1"
                >
                  1
                </button>
              </td>
              <td class="content left no-intraline-info remove">
                <div class="contentText" data-side="left" id="left-content-1">
                  <gr-diff-text data-side="left"> lorem ipsum </gr-diff-text>
                </div>
              </td>
              <td class="blankLineNum right"></td>
              <td class="blank no-intraline-info right">
                <div class="contentText" data-side="right">
                  <gr-diff-text data-side="right"></gr-diff-text>
                </div>
              </td>
            </tr>
            <slot name="post-left-line-1"></slot>
          </tbody>
        </table>
      `
    );
  });

  test('renders revert button when showRevertButton is true', async () => {
    const line = new GrDiffLine(GrDiffLineType.REMOVE, 1, 0);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = new GrDiffLine(GrDiffLineType.BLANK);
    element.showRevertButton = true;
    await element.updateComplete;

    const revertBtn = element.querySelector('.revert-btn');
    assert.isNotNull(revertBtn);
  });

  test('does not render revert button when showRevertButton is false', async () => {
    const line = new GrDiffLine(GrDiffLineType.REMOVE, 1, 0);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = new GrDiffLine(GrDiffLineType.BLANK);
    element.showRevertButton = false;
    await element.updateComplete;

    const revertBtn = element.querySelector('.revert-btn');
    assert.isNull(revertBtn);
  });

  test('fires revert-delta event on button click', async () => {
    const line = new GrDiffLine(GrDiffLineType.REMOVE, 1, 0);
    line.text = 'lorem ipsum';
    const group = new GrDiffGroup({
      type: GrDiffGroupType.DELTA,
      lines: [line],
    });
    element.left = line;
    element.right = new GrDiffLine(GrDiffLineType.BLANK);
    element.group = group;
    element.showRevertButton = true;
    await element.updateComplete;

    let eventDetail: {group: GrDiffGroup; onComplete?: () => void} | undefined;
    element.addEventListener('revert-delta', (e: CustomEvent) => {
      eventDetail = e.detail;
    });

    const revertBtn = element.querySelector<HTMLButtonElement>('.revert-btn')!;
    assert.isNotNull(revertBtn);
    revertBtn.click();
    await element.updateComplete;

    assert.isDefined(eventDetail);
    assert.equal(eventDetail?.group, group);
    assert.isTrue(revertBtn.classList.contains('loading'));
    assert.isNotNull(revertBtn.querySelector('.loadingSpin'));
    assert.isNull(revertBtn.querySelector('gr-icon'));

    eventDetail?.onComplete?.();
    await element.updateComplete;
    assert.isFalse(revertBtn.classList.contains('loading'));
    assert.isNull(revertBtn.querySelector('.loadingSpin'));
    assert.isNotNull(revertBtn.querySelector('gr-icon'));
  });

  test('updateLayers aborts when DOM element references change during await', async () => {
    const line = new GrDiffLine(GrDiffLineType.BOTH, 1, 1);
    line.text = 'lorem ipsum';
    element.left = line;
    element.right = line;
    let annotateCalled = false;
    element.layers = [
      {
        annotate() {
          annotateCalled = true;
        },
      },
    ];
    await element.updateComplete;
    await new Promise(resolve => setTimeout(resolve, 0));
    annotateCalled = false;

    // Create a mock content element with a controllable updateComplete promise
    let resolveUpdate: () => void;
    const updatePromise = new Promise<boolean>(r => {
      resolveUpdate = () => r(true);
    });
    const oldContentEl = {
      updateComplete: updatePromise,
    } as unknown as LitElement;
    element.contentLeftRef = {value: oldContentEl};

    const privElement = element as unknown as GrDiffRowPrivate;
    privElement.layersApplied = false;
    const updateLayersPromise = privElement.updateLayers(Side.LEFT);

    // Swap the ref while updateLayers is awaiting updateComplete
    element.contentLeftRef = {
      value: document.createElement('div') as unknown as LitElement,
    };
    resolveUpdate!();
    await updateLayersPromise;

    assert.isFalse(annotateCalled);
    assert.isFalse(privElement.layersApplied);
  });
});
