/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup';
import {EventEmitter} from './gr-event-interface_impl';
import {assert} from '@open-wc/testing';

suite('gr-event-interface tests', () => {
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

