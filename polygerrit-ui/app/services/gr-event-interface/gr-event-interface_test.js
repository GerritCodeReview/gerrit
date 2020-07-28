/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import '../../elements/shared/gr-js-api-interface/gr-js-api-interface.js';
import {EventEmitter} from './gr-event-interface_impl.js';
import {_testOnly_initGerritPluginApi} from '../../elements/shared/gr-js-api-interface/gr-gerrit.js';

const basicFixture = fixtureFromElement('gr-js-api-interface');

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-event-interface tests', () => {
  setup(() => {

  });

  suite('test on Gerrit', () => {
    setup(() => {
      basicFixture.instantiate();
      pluginApi.removeAllListeners();
    });

    test('communicate between plugin and Gerrit', done => {
      const eventName = 'test-plugin-event';
      let p;
      pluginApi.on(eventName, e => {
        assert.equal(e.value, 'test');
        assert.equal(e.plugin, p);
        done();
      });
      pluginApi.install(plugin => {
        p = plugin;
        pluginApi.emit(eventName, {value: 'test', plugin});
      }, '0.1',
      'http://test.com/plugins/testplugin/static/test.js');
    });

    test('listen on events from core', done => {
      const eventName = 'test-plugin-event';
      pluginApi.on(eventName, e => {
        assert.equal(e.value, 'test');
        done();
      });

      pluginApi.emit(eventName, {value: 'test'});
    });

    test('communicate across plugins', done => {
      const eventName = 'test-plugin-event';
      pluginApi.install(plugin => {
        pluginApi.on(eventName, e => {
          assert.equal(e.plugin.getPluginName(), 'testB');
          done();
        });
      }, '0.1',
      'http://test.com/plugins/testA/static/testA.js');

      pluginApi.install(plugin => {
        pluginApi.emit(eventName, {plugin});
      }, '0.1',
      'http://test.com/plugins/testB/static/testB.js');
    });
  });

  suite('test on interfaces', () => {
    let testObj;

    class TestClass extends EventEmitter {
    }

    setup(() => {
      testObj = new TestClass();
    });

    test('on', () => {
      const cbStub = sinon.stub();
      testObj.on('test', cbStub);
      testObj.emit('test');
      testObj.emit('test');
      assert.isTrue(cbStub.calledTwice);
    });

    test('once', () => {
      const cbStub = sinon.stub();
      testObj.once('test', cbStub);
      testObj.emit('test');
      testObj.emit('test');
      assert.isTrue(cbStub.calledOnce);
    });

    test('unsubscribe', () => {
      const cbStub = sinon.stub();
      const unsubscribe = testObj.on('test', cbStub);
      testObj.emit('test');
      unsubscribe();
      testObj.emit('test');
      assert.isTrue(cbStub.calledOnce);
    });

    test('off', () => {
      const cbStub = sinon.stub();
      testObj.on('test', cbStub);
      testObj.emit('test');
      testObj.off('test', cbStub);
      testObj.emit('test');
      assert.isTrue(cbStub.calledOnce);
    });

    test('removeAllListeners', () => {
      const cbStub = sinon.stub();
      testObj.on('test', cbStub);
      testObj.removeAllListeners('test');
      testObj.emit('test');
      assert.isTrue(cbStub.notCalled);
    });
  });
});

