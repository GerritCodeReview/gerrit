/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-repo-plugin-config';
import {GrRepoPluginConfig} from './gr-repo-plugin-config';
import {PluginParameterToConfigParameterInfoMap} from '../../../types/common';
import {ConfigParameterInfoType} from '../../../constants/constants';
import {queryAndAssert} from '../../../test/test-utils';
import {GrPluginConfigArrayEditor} from '../gr-plugin-config-array-editor/gr-plugin-config-array-editor';
import {PaperToggleButtonElement} from '@polymer/paper-toggle-button/paper-toggle-button';

const basicFixture = fixtureFromElement('gr-repo-plugin-config');

suite('gr-repo-plugin-config tests', () => {
  let element: GrRepoPluginConfig;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
  });

  test('_computePluginConfigOptions', () => {
    assert.deepEqual(
      element._computePluginConfigOptions({
        name: 'testInfo',
        config: {
          testKey: {display_name: 'testInfo plugin', type: 'STRING'},
        } as PluginParameterToConfigParameterInfoMap,
      }),
      [
        {
          _key: 'testKey',
          info: {
            display_name: 'testInfo plugin',
            type: 'STRING' as ConfigParameterInfoType,
          },
        },
      ]
    );
  });

  test('_handleChange', () => {
    const eventStub = sinon.stub(element, 'dispatchEvent');
    element.pluginData = {
      name: 'testName',
      config: {
        plugin: {type: 'STRING' as ConfigParameterInfoType, value: 'test'},
      },
    };
    element._handleChange({
      _key: 'plugin',
      info: {type: 'STRING' as ConfigParameterInfoType, value: 'newTest'},
    });

    assert.isTrue(eventStub.called);

    const {detail} = eventStub.lastCall.args[0] as CustomEvent;
    assert.equal(detail.name, 'testName');
    assert.deepEqual(detail.config, {
      plugin: {type: 'STRING' as ConfigParameterInfoType, value: 'newTest'},
    });
  });

  suite('option types', () => {
    let changeStub: sinon.SinonStub;
    let buildStub: sinon.SinonStub;

    setup(() => {
      changeStub = sinon.stub(element, '_handleChange');
      buildStub = sinon.stub(element, '_buildConfigChangeInfo');
    });

    test('ARRAY type option', async () => {
      element.pluginData = {
        name: 'testName',
        config: {
          plugin: {
            value: 'test',
            type: 'ARRAY' as ConfigParameterInfoType,
            editable: true,
          },
        },
      };
      await element.updateComplete;

      const editor = queryAndAssert<GrPluginConfigArrayEditor>(
        element,
        'gr-plugin-config-array-editor'
      );
      assert.ok(editor);
      element._handleArrayChange({detail: 'test'} as CustomEvent);
      assert.isTrue(changeStub.called);
      assert.equal(changeStub.lastCall.args[0], 'test');
    });

    test('BOOLEAN type option', async () => {
      element.pluginData = {
        name: 'testName',
        config: {
          plugin: {
            value: 'true',
            type: 'BOOLEAN' as ConfigParameterInfoType,
            editable: true,
          },
        },
      };
      await element.updateComplete;

      const toggle = queryAndAssert<PaperToggleButtonElement>(
        element,
        'paper-toggle-button'
      );
      assert.ok(toggle);
      toggle.click();
      await element.updateComplete;

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['false', 'plugin']);

      assert.isTrue(changeStub.called);
    });

    test('INT/LONG/STRING type option', async () => {
      element.pluginData = {
        name: 'testName',
        config: {
          plugin: {
            value: 'test',
            type: 'STRING' as ConfigParameterInfoType,
            editable: true,
          },
        },
      };
      await element.updateComplete;

      const input = queryAndAssert<HTMLInputElement>(element, 'input');
      assert.ok(input);
      input.value = 'newTest';
      input.dispatchEvent(new Event('input'));
      await element.updateComplete;

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['newTest', 'plugin']);

      assert.isTrue(changeStub.called);
    });

    test('LIST type option', async () => {
      const permitted_values = ['test', 'newTest'];
      element.pluginData = {
        name: 'testName',
        config: {
          plugin: {
            value: 'test',
            type: 'LIST' as ConfigParameterInfoType,
            editable: true,
            permitted_values,
          },
        },
      };
      await element.updateComplete;

      const select = queryAndAssert<HTMLSelectElement>(element, 'select');
      assert.ok(select);
      select.value = 'newTest';
      select.dispatchEvent(
        new Event('change', {bubbles: true, composed: true})
      );
      await element.updateComplete;

      assert.isTrue(buildStub.called);
      assert.deepEqual(buildStub.lastCall.args, ['newTest', 'plugin']);

      assert.isTrue(changeStub.called);
    });
  });

  test('_buildConfigChangeInfo', () => {
    element.pluginData = {
      name: 'testName',
      config: {
        plugin: {type: 'STRING' as ConfigParameterInfoType, value: 'test'},
      },
    };
    const detail = element._buildConfigChangeInfo('newTest', 'plugin');
    assert.equal(detail._key, 'plugin');
    assert.deepEqual(detail.info, {
      type: 'STRING' as ConfigParameterInfoType,
      value: 'newTest',
    });
  });
});
