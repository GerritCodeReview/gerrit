/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import '../gr-diff/gr-diff-group';
import './gr-context-controls';
import {GrContextControls} from './gr-context-controls';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';

import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {DiffFileMetaInfo, DiffInfo} from '../../../api/diff';

const blankFixture = fixtureFromElement('div');

suite('gr-context-control tests', () => {
  let outerDiv: HTMLElement;
  let element: GrContextControls;

  setup(async () => {
    element = document.createElement('gr-context-controls');
    element.diff = ({content: []} as any) as DiffInfo;
    element.renderPreferences = {};
    element.section = document.createElement('div');
    outerDiv = blankFixture.instantiate();
    outerDiv.appendChild(element);
  });

  function createContextGroups(options: {offset?: number; count?: number}) {
    const offset = options.offset || 0;
    const numLines = options.count || 10;
    const lines = [];
    for (let i = 0; i < numLines; i++) {
      const line = new GrDiffLine(GrDiffLineType.BOTH);
      line.beforeNumber = offset + i + 1;
      line.afterNumber = offset + i + 1;
      line.text = 'lorem upsum';
      lines.push(line);
    }

    return [new GrDiffGroup(GrDiffGroupType.BOTH, lines)];
  }

  test('no +10 buttons for 10 or less lines', async () => {
    element.contextGroups = createContextGroups({count: 10});
    await flush();

    const buttons = element.shadowRoot!.querySelectorAll(
      'gr-button.showContext'
    );
    assert.equal(buttons.length, 1);
    assert.equal(buttons[0].textContent!.trim(), '+10 common lines');
  });

  test('context control at the top', async () => {
    element.contextGroups = createContextGroups({offset: 0, count: 20});
    element.showBelow = true;
    await flush();

    const buttons = element.shadowRoot!.querySelectorAll(
      'gr-button.showContext'
    );

    assert.equal(buttons.length, 2);
    assert.equal(buttons[0].textContent!.trim(), '+20 common lines');
    assert.equal(buttons[1].textContent!.trim(), '+10');

    assert.include([...buttons[0].classList.values()], 'belowButton');
    assert.include([...buttons[1].classList.values()], 'belowButton');
  });

  test('context control in the middle', async () => {
    element.contextGroups = createContextGroups({offset: 10, count: 20});
    element.showAbove = true;
    element.showBelow = true;
    await flush();

    const buttons = element.shadowRoot!.querySelectorAll(
      'gr-button.showContext'
    );

    assert.equal(buttons.length, 3);
    assert.equal(buttons[0].textContent!.trim(), '+20 common lines');
    assert.equal(buttons[1].textContent!.trim(), '+10');
    assert.equal(buttons[2].textContent!.trim(), '+10');

    assert.include([...buttons[0].classList.values()], 'centeredButton');
    assert.include([...buttons[1].classList.values()], 'aboveButton');
    assert.include([...buttons[2].classList.values()], 'belowButton');
  });

  test('context control at the bottom', async () => {
    element.contextGroups = createContextGroups({offset: 30, count: 20});
    element.showAbove = true;
    await flush();

    const buttons = element.shadowRoot!.querySelectorAll(
      'gr-button.showContext'
    );

    assert.equal(buttons.length, 2);
    assert.equal(buttons[0].textContent!.trim(), '+20 common lines');
    assert.equal(buttons[1].textContent!.trim(), '+10');

    assert.include([...buttons[0].classList.values()], 'aboveButton');
    assert.include([...buttons[1].classList.values()], 'aboveButton');
  });

  test('context control with block expansion at the top', async () => {
    element.renderPreferences!.use_block_expansion = true;
    element.diff!.meta_b = ({
      syntax_tree: [],
    } as any) as DiffFileMetaInfo;
    element.contextGroups = createContextGroups({offset: 0, count: 20});
    element.showBelow = true;
    await flush();

    const fullExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.fullExpansion gr-button'
    );
    const partialExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.partialExpansion gr-button'
    );
    const blockExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.blockExpansion gr-button'
    );
    assert.equal(fullExpansionButtons.length, 1);
    assert.equal(partialExpansionButtons.length, 1);
    assert.equal(blockExpansionButtons.length, 1);
    assert.equal(blockExpansionButtons[0].textContent!.trim(), '+Block');
    assert.include(
      [...blockExpansionButtons[0].classList.values()],
      'belowButton'
    );
  });

  test('context control with block expansion in the middle', async () => {
    element.renderPreferences!.use_block_expansion = true;
    element.diff!.meta_b = ({
      syntax_tree: [],
    } as any) as DiffFileMetaInfo;

    element.contextGroups = createContextGroups({offset: 10, count: 20});
    element.showAbove = true;
    element.showBelow = true;
    await flush();

    const fullExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.fullExpansion gr-button'
    );
    const partialExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.partialExpansion gr-button'
    );
    const blockExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.blockExpansion gr-button'
    );
    assert.equal(fullExpansionButtons.length, 1);
    assert.equal(partialExpansionButtons.length, 2);
    assert.equal(blockExpansionButtons.length, 2);
    assert.equal(blockExpansionButtons[0].textContent!.trim(), '+Block');
    assert.equal(blockExpansionButtons[1].textContent!.trim(), '+Block');
    assert.include(
      [...blockExpansionButtons[0].classList.values()],
      'aboveButton'
    );
    assert.include(
      [...blockExpansionButtons[1].classList.values()],
      'belowButton'
    );
  });

  test('context control with block expansion at the bottom', async () => {
    element.renderPreferences!.use_block_expansion = true;
    element.diff!.meta_b = ({
      syntax_tree: [],
    } as any) as DiffFileMetaInfo;
    element.contextGroups = createContextGroups({offset: 30, count: 20});
    element.showAbove = true;
    await flush();

    const fullExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.fullExpansion gr-button'
    );
    const partialExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.partialExpansion gr-button'
    );
    const blockExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.blockExpansion gr-button'
    );
    assert.equal(fullExpansionButtons.length, 1);
    assert.equal(partialExpansionButtons.length, 1);
    assert.equal(blockExpansionButtons.length, 1);
    assert.equal(blockExpansionButtons[0].textContent!.trim(), '+Block');
    assert.include(
      [...blockExpansionButtons[0].classList.values()],
      'aboveButton'
    );
  });
});
