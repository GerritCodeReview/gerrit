/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma.js';
import './gr-js-api-interface.js';
import {GrPluginRestApi} from './gr-plugin-rest-api.js';
import {stubRestApi} from '../../../test/test-utils.js';

suite('gr-plugin-rest-api tests', () => {
  let instance;
  let getResponseObjectStub;
  let sendStub;

  setup(() => {
    stubRestApi('getAccount').returns(Promise.resolve({name: 'Judy Hopps'}));
    getResponseObjectStub = stubRestApi('getResponseObject').returns(
        Promise.resolve());
    sendStub = stubRestApi('send').returns(Promise.resolve({status: 200}));
    window.Gerrit.install(p => {}, '0.1',
        'http://test.com/plugins/testplugin/static/test.js');
    instance = new GrPluginRestApi();
  });

  test('fetch', () => {
    const payload = {foo: 'foo'};
    return instance.fetch('HTTP_METHOD', '/url', payload).then(r => {
      assert.isTrue(sendStub.calledWith('HTTP_METHOD', '/url', payload));
      assert.equal(r.status, 200);
      assert.isFalse(getResponseObjectStub.called);
    });
  });

  test('send', () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return instance.send('HTTP_METHOD', '/url', payload).then(r => {
      assert.isTrue(sendStub.calledWith('HTTP_METHOD', '/url', payload));
      assert.strictEqual(r, response);
    });
  });

  test('get', () => {
    const response = {foo: 'foo'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return instance.get('/url').then(r => {
      assert.isTrue(sendStub.calledWith('GET', '/url'));
      assert.strictEqual(r, response);
    });
  });

  test('post', () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return instance.post('/url', payload).then(r => {
      assert.isTrue(sendStub.calledWith('POST', '/url', payload));
      assert.strictEqual(r, response);
    });
  });

  test('put', () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.returns(Promise.resolve(response));
    return instance.put('/url', payload).then(r => {
      assert.isTrue(sendStub.calledWith('PUT', '/url', payload));
      assert.strictEqual(r, response);
    });
  });

  test('delete works', () => {
    const response = {status: 204};
    sendStub.returns(Promise.resolve(response));
    return instance.delete('/url').then(r => {
      assert.isTrue(sendStub.calledWith('DELETE', '/url'));
      assert.strictEqual(r, response);
    });
  });

  test('delete fails', () => {
    sendStub.returns(Promise.resolve(
        {status: 400, text() { return Promise.resolve('text'); }}));
    return instance.delete('/url').then(r => {
      throw new Error('Should not resolve');
    })
        .catch(err => {
          assert.isTrue(sendStub.calledWith('DELETE', '/url'));
          assert.equal('text', err.message);
        });
  });

  test('getLoggedIn', () => {
    const stub = stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    return instance.getLoggedIn().then(result => {
      assert.isTrue(stub.calledOnce);
      assert.isTrue(result);
    });
  });

  test('getVersion', () => {
    const stub = stubRestApi('getVersion').returns(Promise.resolve('foo bar'));
    return instance.getVersion().then(result => {
      assert.isTrue(stub.calledOnce);
      assert.equal(result, 'foo bar');
    });
  });

  test('getConfig', () => {
    const stub = stubRestApi('getConfig').returns(Promise.resolve('foo bar'));
    return instance.getConfig().then(result => {
      assert.isTrue(stub.calledOnce);
      assert.equal(result, 'foo bar');
    });
  });
});

