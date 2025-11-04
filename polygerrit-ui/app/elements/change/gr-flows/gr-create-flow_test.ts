/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-flow';
import {assert, fixture, html} from '@open-wc/testing';
import {GrCreateFlow} from './gr-create-flow';
import {queryAll, queryAndAssert} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrAutogrowTextarea} from '../../shared/gr-autogrow-textarea/gr-autogrow-textarea';
import {GrSearchAutocomplete} from '../../core/gr-search-autocomplete/gr-search-autocomplete';
import {FlowsModel, flowsModelToken} from '../../../models/flows/flows-model';
import {testResolver} from '../../../test/common-test-setup';
import {MdOutlinedTextField} from '@material/web/textfield/outlined-text-field';

suite('gr-create-flow tests', () => {
  let element: GrCreateFlow;
  let flowsModel: FlowsModel;

  setup(async () => {
    flowsModel = testResolver(flowsModelToken);
    element = await fixture<GrCreateFlow>(
      html`<gr-create-flow></gr-create-flow>`
    );
    element.changeNum = 123 as NumericChangeId;
    await element.updateComplete;
    element.hostUrl =
      'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321';
  });

  test('renders initially', () => {
    assert.isDefined(queryAndAssert(element, 'gr-search-autocomplete'));
    assert.isDefined(
      queryAndAssert(element, 'md-outlined-text-field[label="Action"]')
    );
    assert.isDefined(
      queryAndAssert(element, 'md-outlined-text-field[label="Parameters"]')
    );
    assert.isDefined(
      queryAndAssert(element, 'gr-button[aria-label="Add Stage"]')
    );
    assert.isDefined(
      queryAndAssert(element, 'gr-button[aria-label="Create Flow"]')
    );
  });

  test('adds and removes stages', async () => {
    const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
      element,
      'gr-search-autocomplete'
    );
    const actionInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Action"]'
    );
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    searchAutocomplete.value = 'cond 1';
    await element.updateComplete;
    actionInput.value = 'act-1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
        action: 'act-1',
        parameterStr: '',
      },
    ]);
    assert.equal(element['currentCondition'], '');
    assert.equal(element['currentAction'], '');

    searchAutocomplete.value = 'cond 2';
    await element.updateComplete;
    actionInput.value = 'act-2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
        action: 'act-1',
        parameterStr: '',
      },
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
        action: 'act-2',
        parameterStr: '',
      },
    ]);

    let removeButtons = queryAll<GrButton>(element, 'tr gr-button');
    assert.lengthOf(removeButtons, 2);

    removeButtons[0].click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
        action: 'act-2',
        parameterStr: '',
      },
    ]);
    removeButtons = queryAll<GrButton>(element, 'tr gr-button');
    assert.lengthOf(removeButtons, 1);
  });

  test('creates a flow with one stage', async () => {
    const createFlowStub = sinon.stub(flowsModel, 'createFlow');

    const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
      element,
      'gr-search-autocomplete'
    );
    const actionInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Action"]'
    );
    searchAutocomplete.value = 'single condition';
    await element.updateComplete;
    actionInput.value = 'single action';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[0];
    assert.deepEqual(flowInput.stage_expressions, [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is single condition',
        action: {name: 'single action'},
      },
    ]);
  });

  test('creates a flow with parameters', async () => {
    const createFlowStub = sinon.stub(flowsModel, 'createFlow');

    const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
      element,
      'gr-search-autocomplete'
    );
    const actionInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Action"]'
    );
    const parametersInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Parameters"]'
    );
    searchAutocomplete.value = 'single condition';
    await element.updateComplete;
    actionInput.value = 'single action';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    parametersInput.value = 'param1 param2';
    parametersInput.dispatchEvent(new Event('input'));
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[0];
    assert.deepEqual(flowInput.stage_expressions, [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is single condition',
        action: {name: 'single action', parameters: ['param1', 'param2']},
      },
    ]);
  });

  test('creates a flow with multiple stages', async () => {
    const createFlowStub = sinon.stub(flowsModel, 'createFlow');

    const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
      element,
      'gr-search-autocomplete'
    );
    const actionInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Action"]'
    );
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    searchAutocomplete.value = 'cond 1';
    await element.updateComplete;
    actionInput.value = 'act-1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    searchAutocomplete.value = 'cond 2';
    await element.updateComplete;
    actionInput.value = 'act-2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[0];
    assert.deepEqual(flowInput.stage_expressions, [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
        action: {name: 'act-1'},
      },
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
        action: {name: 'act-2'},
      },
    ]);
  });

  test('create flow with added stages and current input', async () => {
    const createFlowStub = sinon.stub(flowsModel, 'createFlow');

    const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
      element,
      'gr-search-autocomplete'
    );
    const actionInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Action"]'
    );
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    searchAutocomplete.value = 'cond 1';
    await element.updateComplete;
    actionInput.value = 'act-1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;
    searchAutocomplete.value = 'cond 2';
    await element.updateComplete;
    actionInput.value = 'act-2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[0];
    assert.deepEqual(flowInput.stage_expressions, [
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1',
        action: {name: 'act-1'},
      },
      {
        condition:
          'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2',
        action: {name: 'act-2'},
      },
    ]);
  });

  test('raw flow textarea is updated', async () => {
    const rawFlowTextarea = queryAndAssert<GrAutogrowTextarea>(
      element,
      'gr-autogrow-textarea[label="Raw Flow"]'
    );
    assert.isDefined(rawFlowTextarea);
    assert.equal(rawFlowTextarea.value, '');

    const searchAutocomplete = queryAndAssert<GrSearchAutocomplete>(
      element,
      'gr-search-autocomplete'
    );
    const actionInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Action"]'
    );
    const paramsInput = queryAndAssert<MdOutlinedTextField>(
      element,
      'md-outlined-text-field[label="Parameters"]'
    );
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    // Add first stage
    searchAutocomplete.value = 'cond 1';
    await element.updateComplete;
    actionInput.value = 'act-1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.equal(
      element.flowString,
      'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1 -> act-1'
    );

    // Add second stage with parameters
    searchAutocomplete.value = 'cond 2';
    await element.updateComplete;
    actionInput.value = 'act-2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    paramsInput.value = 'param';
    paramsInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.equal(
      element.flowString,
      'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 1 -> act-1;https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2 -> act-2 param'
    );

    // Remove first stage
    const removeButtons = queryAll<GrButton>(element, 'tr gr-button');
    removeButtons[0].click();
    await element.updateComplete;

    assert.equal(
      element.flowString,
      'https://gerrit-review.googlesource.com/c/plugins/code-owners/+/441321 is cond 2 -> act-2 param'
    );
  });

  suite('parseStagesFromRawFlow tests', () => {
    test('parses a single condition', async () => {
      const rawFlow = 'cond 1';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element['stages'], [
        {
          condition: 'cond 1',
          action: '',
          parameterStr: '',
        },
      ]);
    });

    test('parses a single condition with action', async () => {
      const rawFlow = 'cond 1 -> act-1';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element['stages'], [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: '',
        },
      ]);
    });

    test('parses a single condition with action and params', async () => {
      const rawFlow = 'cond 1 -> act-1 param1 param2';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element['stages'], [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: 'param1 param2',
        },
      ]);
    });

    test('parses multiple stages', async () => {
      const rawFlow = 'cond 1 -> act-1; cond 2 -> act-2 p2; cond 3';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element['stages'], [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: '',
        },
        {
          condition: 'cond 2',
          action: 'act-2',
          parameterStr: 'p2',
        },
        {
          condition: 'cond 3',
          action: '',
          parameterStr: '',
        },
      ]);
    });

    test('parses an empty string', async () => {
      const rawFlow = '';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element['stages'], []);
    });

    test('parses with extra spacing', async () => {
      const rawFlow = '  cond 1   ->  act-1  p1 ;  cond 2  ';
      element['parseStagesFromRawFlow'](rawFlow);
      await element.updateComplete;
      assert.deepEqual(element['stages'], [
        {
          condition: 'cond 1',
          action: 'act-1',
          parameterStr: 'p1',
        },
        {
          condition: 'cond 2',
          action: '',
          parameterStr: '',
        },
      ]);
    });
  });
});
