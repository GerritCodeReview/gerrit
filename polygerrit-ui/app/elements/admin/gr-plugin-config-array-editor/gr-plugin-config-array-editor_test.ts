/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {ConfigParameterInfoType} from '../../../constants/constants';
import '../../../test/common-test-setup';
import './gr-plugin-config-array-editor';
import {GrPluginConfigArrayEditor} from './gr-plugin-config-array-editor';
import {queryAll, queryAndAssert, pressKey} from '../../../test/test-utils';
import {GrButton} from '../../shared/gr-button/gr-button';
import {fixture, html, assert} from '@open-wc/testing';
import {Key} from '../../../utils/dom-util';

suite('gr-plugin-config-array-editor tests', () => {
  let element: GrPluginConfigArrayEditor;

  let dispatchStub: sinon.SinonStub;

  setup(async () => {
    element = await fixture<GrPluginConfigArrayEditor>(html`
      <gr-plugin-config-array-editor></gr-plugin-config-array-editor>
    `);
    element.pluginOption = {
      _key: 'test-key',
      info: {
        type: ConfigParameterInfoType.ARRAY,
        values: [],
      },
    };
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="gr-form-styles wrapper">
          <div class="placeholder row">None configured.</div>
          <div class="row">
            <iron-input>
              <input id="input" />
            </iron-input>
            <gr-button
              aria-disabled="true"
              disabled=""
              id="addButton"
              link=""
              role="button"
              tabindex="-1"
            >
              Add
            </gr-button>
          </div>
        </div>
      `
    );
  });

  suite('adding', () => {
    setup(() => {
      dispatchStub = sinon.stub(element, 'dispatchChanged');
    });

    test('with enter', async () => {
      element.newValue = '';
      await element.updateComplete;
      pressKey(queryAndAssert<HTMLInputElement>(element, '#input'), Key.ENTER);
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(element, '#input').hasAttribute(
          'disabled'
        )
      );
      await element.updateComplete;

      assert.isFalse(dispatchStub.called);
      element.newValue = 'test';
      await element.updateComplete;

      pressKey(queryAndAssert<HTMLInputElement>(element, '#input'), Key.ENTER);
      await element.updateComplete;
      assert.isFalse(
        queryAndAssert<HTMLInputElement>(element, '#input').hasAttribute(
          'disabled'
        )
      );
      await element.updateComplete;

      assert.isTrue(dispatchStub.called);
      assert.equal(dispatchStub.lastCall.args[0], 'test');
      assert.equal(element.newValue, '');
    });

    test('with add btn', async () => {
      element.newValue = '';
      queryAndAssert<GrButton>(element, '#addButton').click();
      await element.updateComplete;

      assert.isFalse(dispatchStub.called);

      element.newValue = 'test';
      await element.updateComplete;

      queryAndAssert<GrButton>(element, '#addButton').click();
      await element.updateComplete;

      assert.isTrue(dispatchStub.called);
      assert.equal(dispatchStub.lastCall.args[0], 'test');
      assert.equal(element.newValue, '');
    });
  });

  test('deleting', async () => {
    dispatchStub = sinon.stub(element, 'dispatchChanged');
    element.pluginOption = {
      _key: '',
      info: {type: ConfigParameterInfoType.ARRAY, values: ['test', 'test2']},
    };
    element.disabled = true;
    await element.updateComplete;

    const rows = queryAll(element, '.existingItems .row');
    assert.equal(rows.length, 2);
    const button = queryAndAssert<GrButton>(rows[0], 'gr-button');

    button.click();
    await element.updateComplete;

    assert.isFalse(dispatchStub.called);
    element.disabled = false;
    await element.updateComplete;

    button.click();
    await element.updateComplete;

    assert.isTrue(dispatchStub.called);
    assert.deepEqual(dispatchStub.lastCall.args[0], ['test2']);
  });

  test('dispatchChanged', () => {
    const eventStub = sinon.stub(element, 'dispatchEvent');
    element.dispatchChanged(['new-test-value']);

    assert.isTrue(eventStub.called);
    const {detail} = eventStub.lastCall.args[0] as CustomEvent;
    assert.equal(detail._key, 'test-key');
    assert.deepEqual(detail.info, {type: 'ARRAY', values: ['new-test-value']});
    assert.equal(detail.notifyPath, 'test-key.values');
  });
});
