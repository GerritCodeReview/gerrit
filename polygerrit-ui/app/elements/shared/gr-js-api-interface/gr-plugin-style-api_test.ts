/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {GrPluginRestApi} from './gr-plugin-rest-api';
import {assertFails, stubRestApi} from '../../../test/test-utils';
import {assert} from '@open-wc/testing';
import {PluginApi} from '../../../api/plugin';
import {
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {HttpMethod} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';

suite('gr-plugin-rest-api tests', () => {
  let instance: GrPluginRestApi;
  let getResponseObjectStub: sinon.SinonStub;
  let sendStub: sinon.SinonStub;

  setup(() => {
    stubRestApi('getAccount').resolves(createAccountDetailWithId());
    getResponseObjectStub = stubRestApi('getResponseObject').resolves();
    sendStub = stubRestApi('send').resolves({...new Response(), status: 200});
    let pluginApi: PluginApi;
    window.Gerrit.install(
      p => {
        pluginApi = p;
      },
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    instance = new GrPluginRestApi(
      getAppContext().restApiService,
      getAppContext().reportingService,
      pluginApi!
    );
  });

  test('fetch', async () => {
    const payload = {foo: 'foo'};
    const r = await instance.fetch(HttpMethod.POST, '/url', payload);
    assert.isTrue(sendStub.calledWith(HttpMethod.POST, '/url', payload));
    assert.equal(r.status, 200);
    assert.isFalse(getResponseObjectStub.called);
  });

  test('send', async () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.resolves(response);
    const r = await instance.send(HttpMethod.POST, '/url', payload);
    assert.isTrue(sendStub.calledWith(HttpMethod.POST, '/url', payload));
    assert.strictEqual(r, response);
  });

  test('get', async () => {
    const response = {foo: 'foo'};
    getResponseObjectStub.resolves(response);
    const r = await instance.get('/url');
    assert.isTrue(sendStub.calledWith('GET', '/url'));
    assert.strictEqual(r, response);
  });

  test('post', async () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.resolves(response);
    const r = await instance.post('/url', payload);
    assert.isTrue(sendStub.calledWith('POST', '/url', payload));
    assert.strictEqual(r, response);
  });

  test('put', async () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    getResponseObjectStub.resolves(response);
    const r = await instance.put('/url', payload);
    assert.isTrue(sendStub.calledWith('PUT', '/url', payload));
    assert.strictEqual(r, response);
  });

  test('delete works', async () => {
    const response = {status: 204};
    sendStub.resolves(response);
    const r = await instance.delete('/url');
    assert.isTrue(sendStub.calledWith('DELETE', '/url'));
    assert.strictEqual(r, response);
  });

  test('delete fails', async () => {
    sendStub.resolves({
      status: 400,
      text() {
        return Promise.resolve('text');
      },
    });
    const error = await assertFails(instance.delete('/url'));
    assert.equal('text', (error as Error).message);
    assert.isTrue(sendStub.calledWith('DELETE', '/url'));
  });

  test('getLoggedIn', async () => {
    const stub = stubRestApi('getLoggedIn').resolves(true);
    const loggedIn = await instance.getLoggedIn();
    assert.isTrue(stub.calledOnce);
    assert.isTrue(loggedIn);
  });

  test('getVersion', async () => {
    const stub = stubRestApi('getVersion').resolves('foo bar');
    const version = await instance.getVersion();
    assert.isTrue(stub.calledOnce);
    assert.equal(version, 'foo bar');
  });

  test('getConfig', async () => {
    const info = createServerInfo();
    const stub = stubRestApi('getConfig').resolves(info);
    const config = await instance.getConfig();
    assert.isTrue(stub.calledOnce);
    assert.equal(config, info);
  });
});
