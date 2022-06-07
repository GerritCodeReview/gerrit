/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma.js';
import {mockPromise} from '../../test/test-utils.js';
import {EventEmitter} from './gr-event-interface_impl.js';

suite('gr-event-interface tests', () => {
  let gerrit;
  setup(() => {
    gerrit = window.Gerrit;
  });

  suite('test on Gerrit', () => {
    setup(() => {
      gerrit.removeAllListeners();
    });

    test('communicate between plugin and Gerrit', async () => {
      const eventName = 'test-plugin-event';
      let p;
      const promise = mockPromise();
      gerrit.on(eventName, e => {
        assert.equal(e.value, 'test');
        assert.equal(e.plugin, p);
        promise.resolve();
      });
      gerrit.install(plugin => {
        p = plugin;
        gerrit.emit(eventName, {value: 'test', plugin});
      }, '0.1',
      'http://test.com/plugins/testplugin/static/test.js');
      await promise;
    });

    test('listen on events from core', async () => {
      const eventName = 'test-plugin-event';
      const promise = mockPromise();
      gerrit.on(eventName, e => {
        assert.equal(e.value, 'test');
        promise.resolve();
      });

      gerrit.emit(eventName, {value: 'test'});
      await promise;
    });

    test('communicate across plugins', async () => {
      const eventName = 'test-plugin-event';
      const promise = mockPromise();
      gerrit.install(plugin => {
        gerrit.on(eventName, e => {
          assert.equal(e.plugin.getPluginName(), 'testB');
          promise.resolve();
        });
      }, '0.1',
      'http://test.com/plugins/testA/static/testA.js');

      gerrit.install(plugin => {
        gerrit.emit(eventName, {plugin});
      }, '0.1',
      'http://test.com/plugins/testB/static/testB.js');
      await promise;
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

