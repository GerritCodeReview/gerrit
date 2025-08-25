/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-flow';
import {assert, fixture, html} from '@open-wc/testing';
import {GrCreateFlow} from './gr-create-flow';
import {
  mockPromise,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {NumericChangeId} from '../../../types/common';
import {GrButton} from '../../shared/gr-button/gr-button';
import {GrSearchBar} from '../../core/gr-search-bar/gr-search-bar';

suite('gr-create-flow tests', () => {
  let element: GrCreateFlow;

  setup(async () => {
    element = await fixture<GrCreateFlow>(
      html`<gr-create-flow></gr-create-flow>`
    );
    element.changeNum = 123 as NumericChangeId;
    await element.updateComplete;
  });

  test('renders initially', () => {
    const searchBar = queryAndAssert<GrSearchBar>(element, 'gr-search-bar');
    assert.equal(searchBar.placeholder, 'Create condition');
    assert.isDefined(queryAndAssert(element, 'input[placeholder="Action"]'));
    assert.isDefined(
      queryAndAssert(element, 'gr-button[aria-label="Add Stage"]')
    );
    assert.isDefined(
      queryAndAssert(element, 'gr-button[aria-label="Create Flow"]')
    );
  });

  test('adds and removes stages', async () => {
    const searchBar = queryAndAssert<GrSearchBar>(element, 'gr-search-bar');
    const actionInput = queryAndAssert<HTMLInputElement>(
      element,
      'input[placeholder="Action"]'
    );
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    searchBar.value = 'cond 1';
    searchBar.dispatchEvent(
      new CustomEvent('handle-search', {detail: {inputVal: 'cond 1'}})
    );
    actionInput.value = 'act 1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {condition: 'Gerrit:cond 1', action: 'act 1'},
    ]);
    assert.equal(element['currentCondition'], '');
    assert.equal(element['currentAction'], '');

    searchBar.value = 'cond 2';
    searchBar.dispatchEvent(
      new CustomEvent('handle-search', {detail: {inputVal: 'cond 2'}})
    );
    actionInput.value = 'act 2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {condition: 'Gerrit:cond 1', action: 'act 1'},
      {condition: 'Gerrit:cond 2', action: 'act 2'},
    ]);

    let removeButtons = queryAll<GrButton>(element, 'li gr-button');
    assert.lengthOf(removeButtons, 2);

    removeButtons[0].click();
    await element.updateComplete;

    assert.deepEqual(element['stages'], [
      {condition: 'Gerrit:cond 2', action: 'act 2'},
    ]);
    removeButtons = queryAll<GrButton>(element, 'li gr-button');
    assert.lengthOf(removeButtons, 1);
  });

  test('creates a flow with one stage', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());

    const searchBar = queryAndAssert<GrSearchBar>(element, 'gr-search-bar');
    const actionInput = queryAndAssert<HTMLInputElement>(
      element,
      'input[placeholder="Action"]'
    );
    searchBar.value = 'single condition';
    searchBar.dispatchEvent(
      new CustomEvent('handle-search', {detail: {inputVal: 'single condition'}})
    );
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
    const flowInput = createFlowStub.lastCall.args[1];
    assert.deepEqual(flowInput.stage_expressions, [
      {condition: 'Gerrit:single condition', action: {name: 'single action'}},
    ]);
  });

  test('creates a flow with multiple stages', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());

    const searchBar = queryAndAssert<GrSearchBar>(element, 'gr-search-bar');
    const actionInput = queryAndAssert<HTMLInputElement>(
      element,
      'input[placeholder="Action"]'
    );
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    searchBar.value = 'cond 1';
    searchBar.dispatchEvent(
      new CustomEvent('handle-search', {detail: {inputVal: 'cond 1'}})
    );
    actionInput.value = 'act 1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    searchBar.value = 'cond 2';
    searchBar.dispatchEvent(
      new CustomEvent('handle-search', {detail: {inputVal: 'cond 2'}})
    );
    actionInput.value = 'act 2';
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
    const flowInput = createFlowStub.lastCall.args[1];
    assert.deepEqual(flowInput.stage_expressions, [
      {condition: 'Gerrit:cond 1', action: {name: 'act 1'}},
      {condition: 'Gerrit:cond 2', action: {name: 'act 2'}},
    ]);
  });

  test('create flow with added stages and current input', async () => {
    const createFlowStub = stubRestApi('createFlow').returns(mockPromise());

    const searchBar = queryAndAssert<GrSearchBar>(element, 'gr-search-bar');
    const actionInput = queryAndAssert<HTMLInputElement>(
      element,
      'input[placeholder="Action"]'
    );
    const addButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Add Stage"]'
    );

    searchBar.value = 'cond 1';
    searchBar.dispatchEvent(
      new CustomEvent('handle-search', {detail: {inputVal: 'cond 1'}})
    );
    actionInput.value = 'act 1';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;
    addButton.click();
    await element.updateComplete;

    searchBar.value = 'cond 2';
    searchBar.dispatchEvent(
      new CustomEvent('handle-search', {detail: {inputVal: 'cond 2'}})
    );
    actionInput.value = 'act 2';
    actionInput.dispatchEvent(new Event('input'));
    await element.updateComplete;

    const createButton = queryAndAssert<GrButton>(
      element,
      'gr-button[aria-label="Create Flow"]'
    );
    createButton.click();
    await element.updateComplete;

    assert.isTrue(createFlowStub.calledOnce);
    const flowInput = createFlowStub.lastCall.args[1];
    assert.deepEqual(flowInput.stage_expressions, [
      {condition: 'Gerrit:cond 1', action: {name: 'act 1'}},
      {condition: 'Gerrit:cond 2', action: {name: 'act 2'}},
    ]);
  });
});
