/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../gr-diff/gr-diff-group';
import './gr-context-controls';
import {GrContextControls} from './gr-context-controls';
import {SyntaxBlock} from '../../../api/diff';
import {assert, fixture, html} from '@open-wc/testing';
import {waitEventLoop} from '../../../test/test-utils';
import {
  createContextGroup,
  createContextGroupWithDelta,
} from '../../../test/test-data-generators';
import {GrTooltipContent} from '../../../elements/shared/gr-tooltip-content/gr-tooltip-content';

suite('gr-context-control tests', () => {
  let element: GrContextControls;

  setup(async () => {
    element = document.createElement('gr-context-controls');
    element.lineCountLeft = 50;
    element.renderPreferences = {};
    const div = await fixture(html`<div></div>`);
    div.appendChild(element);
    await waitEventLoop();
  });

  test('no +10 buttons for 10 or less lines', async () => {
    element.group = createContextGroup({count: 10});

    await waitEventLoop();

    const buttons = element.shadowRoot!.querySelectorAll(
      'paper-button.showContext'
    );
    assert.equal(buttons.length, 1);
    assert.equal(buttons[0].textContent!.trim(), '+10 common lines');
  });

  test('context control at the top', async () => {
    element.group = createContextGroup({offset: 0, count: 20});

    await waitEventLoop();

    const buttons = element.shadowRoot!.querySelectorAll(
      'paper-button.showContext'
    );

    assert.equal(buttons.length, 2);
    assert.equal(buttons[0].textContent!.trim(), '+20 common lines');
    assert.equal(buttons[1].textContent!.trim(), '+10');

    assert.include([...buttons[0].classList.values()], 'belowButton');
    assert.include([...buttons[1].classList.values()], 'belowButton');
  });

  test('context control in the middle', async () => {
    element.group = createContextGroup({offset: 10, count: 20});

    await waitEventLoop();

    const buttons = element.shadowRoot!.querySelectorAll(
      'paper-button.showContext'
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
    element.group = createContextGroup({offset: 30, count: 20});

    await waitEventLoop();

    const buttons = element.shadowRoot!.querySelectorAll(
      'paper-button.showContext'
    );

    assert.equal(buttons.length, 2);
    assert.equal(buttons[0].textContent!.trim(), '+20 common lines');
    assert.equal(buttons[1].textContent!.trim(), '+10');

    assert.include([...buttons[0].classList.values()], 'aboveButton');
    assert.include([...buttons[1].classList.values()], 'aboveButton');
  });

  function prepareForBlockExpansion(syntaxTree: SyntaxBlock[]) {
    element.renderPreferences!.use_block_expansion = true;
    element.syntaxTreeRight = syntaxTree;
  }

  test('context control with block expansion at the top', async () => {
    prepareForBlockExpansion([]);
    element.group = createContextGroup({offset: 0, count: 20});

    await waitEventLoop();

    const fullExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.fullExpansion paper-button'
    );
    const partialExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.partialExpansion paper-button'
    );
    const blockExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.blockExpansion paper-button'
    );
    assert.equal(fullExpansionButtons.length, 1);
    assert.equal(partialExpansionButtons.length, 1);
    assert.equal(blockExpansionButtons.length, 1);
    assert.equal(
      blockExpansionButtons[0].querySelector('span')!.textContent!.trim(),
      '+Block'
    );
    assert.include(
      [...blockExpansionButtons[0].classList.values()],
      'belowButton'
    );
  });

  test('context control with block expansion in the middle', async () => {
    prepareForBlockExpansion([]);
    element.group = createContextGroup({offset: 10, count: 20});

    await waitEventLoop();

    const fullExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.fullExpansion paper-button'
    );
    const partialExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.partialExpansion paper-button'
    );
    const blockExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.blockExpansion paper-button'
    );
    assert.equal(fullExpansionButtons.length, 1);
    assert.equal(partialExpansionButtons.length, 2);
    assert.equal(blockExpansionButtons.length, 2);
    assert.equal(
      blockExpansionButtons[0].querySelector('span')!.textContent!.trim(),
      '+Block'
    );
    assert.equal(
      blockExpansionButtons[1].querySelector('span')!.textContent!.trim(),
      '+Block'
    );
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
    prepareForBlockExpansion([]);
    element.group = createContextGroup({offset: 30, count: 20});

    await waitEventLoop();

    const fullExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.fullExpansion paper-button'
    );
    const partialExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.partialExpansion paper-button'
    );
    const blockExpansionButtons = element.shadowRoot!.querySelectorAll(
      '.blockExpansion paper-button'
    );
    assert.equal(fullExpansionButtons.length, 1);
    assert.equal(partialExpansionButtons.length, 1);
    assert.equal(blockExpansionButtons.length, 1);
    assert.equal(
      blockExpansionButtons[0].querySelector('span')!.textContent!.trim(),
      '+Block'
    );
    assert.include(
      [...blockExpansionButtons[0].classList.values()],
      'aboveButton'
    );
  });

  test('+Block tooltip tooltip shows syntax block containing the target lines above and below', async () => {
    prepareForBlockExpansion([
      {
        name: 'aSpecificFunction',
        range: {start_line: 1, start_column: 0, end_line: 25, end_column: 0},
        children: [],
      },
      {
        name: 'anotherFunction',
        range: {start_line: 26, start_column: 0, end_line: 50, end_column: 0},
        children: [],
      },
    ]);
    element.group = createContextGroup({offset: 10, count: 20});

    await waitEventLoop();

    const blockExpansionButtons =
      element.shadowRoot!.querySelectorAll<GrTooltipContent>(
        '.blockExpansion .breadcrumbTooltip'
      );
    assert.equal(blockExpansionButtons[0].title?.trim(), 'aSpecificFunction');
    assert.equal(blockExpansionButtons[1].title?.trim(), 'anotherFunction');
  });

  test('+Block tooltip shows nested syntax blocks as breadcrumbs', async () => {
    prepareForBlockExpansion([
      {
        name: 'aSpecificNamespace',
        range: {start_line: 1, start_column: 0, end_line: 200, end_column: 0},
        children: [
          {
            name: 'MyClass',
            range: {
              start_line: 2,
              start_column: 0,
              end_line: 100,
              end_column: 0,
            },
            children: [
              {
                name: 'aMethod',
                range: {
                  start_line: 5,
                  start_column: 0,
                  end_line: 80,
                  end_column: 0,
                },
                children: [],
              },
            ],
          },
        ],
      },
    ]);
    element.group = createContextGroup({offset: 10, count: 20});

    await waitEventLoop();

    const blockExpansionButtons =
      element.shadowRoot!.querySelectorAll<GrTooltipContent>(
        '.blockExpansion .breadcrumbTooltip'
      );
    assert.equal(
      blockExpansionButtons[0].title?.trim(),
      'aSpecificNamespace > MyClass > aMethod'
    );
  });

  test('+Block tooltip shows (anonymous) for empty blocks', async () => {
    prepareForBlockExpansion([
      {
        name: 'aSpecificNamespace',
        range: {start_line: 1, start_column: 0, end_line: 200, end_column: 0},
        children: [
          {
            name: '',
            range: {
              start_line: 2,
              start_column: 0,
              end_line: 100,
              end_column: 0,
            },
            children: [
              {
                name: 'aMethod',
                range: {
                  start_line: 5,
                  start_column: 0,
                  end_line: 80,
                  end_column: 0,
                },
                children: [],
              },
            ],
          },
        ],
      },
    ]);
    element.group = createContextGroup({offset: 10, count: 20});
    await waitEventLoop();

    const blockExpansionButtons =
      element.shadowRoot!.querySelectorAll<GrTooltipContent>(
        '.blockExpansion .breadcrumbTooltip'
      );
    assert.equal(
      blockExpansionButtons[0].title?.trim(),
      'aSpecificNamespace > (anonymous) > aMethod'
    );
  });

  test('+Block tooltip shows "all common lines" for empty syntax tree', async () => {
    prepareForBlockExpansion([]);

    element.group = createContextGroup({offset: 10, count: 20});
    await waitEventLoop();

    const tooltip = element.shadowRoot!.querySelectorAll<GrTooltipContent>(
      '.blockExpansion .breadcrumbTooltip'
    );
    const tooltipAbove = tooltip[0];
    const tooltipBelow = tooltip[1];
    assert.equal(tooltipAbove.title?.trim(), '20 common lines');
    assert.equal(tooltipBelow.title?.trim(), '20 common lines');
    assert.isNotOk(tooltipAbove.getAttribute('position-below'));
    assert.equal(tooltipBelow.getAttribute('position-below'), '');
  });

  test('context control with delta group', async () => {
    element.group = createContextGroupWithDelta();
    await waitEventLoop();

    const buttons = element.shadowRoot!.querySelectorAll(
      'paper-button.showContext'
    );
    assert.equal(buttons.length, 1);
    assert.equal(buttons[0].textContent!.trim(), '+ Unrelated changes');
    assert.include([...buttons[0].classList.values()], 'unrelatedChanges');
  });
});
