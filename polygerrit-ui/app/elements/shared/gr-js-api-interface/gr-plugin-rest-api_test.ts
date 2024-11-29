/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-js-api-interface';
import {GrPluginRestApi} from './gr-plugin-rest-api';
import {
  assertFails,
  makePrefixedJSON,
  stubRestApi,
} from '../../../test/test-utils';
import {assert} from '@open-wc/testing';
import {PluginApi} from '../../../api/plugin';
import {
  createAccountDetailWithId,
  createServerInfo,
} from '../../../test/test-data-generators';
import {HttpMethod} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {throwingErrorCallback} from '../gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';

suite('gr-plugin-rest-api tests', () => {
  let instance: GrPluginRestApi;
  let sendStub: sinon.SinonStub;

  setup(() => {
    stubRestApi('getAccount').resolves(createAccountDetailWithId());
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
    assert.isTrue(
      sendStub.calledWith(
        HttpMethod.POST,
        '/url',
        payload,
        /* errFn=*/ undefined,
        /* contentType=*/ undefined,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
    assert.equal(r.status, 200);
  });

  test('send', async () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    sendStub.resolves(new Response(makePrefixedJSON(response)));
    const r = await instance.send(HttpMethod.POST, '/url', payload);
    assert.isTrue(
      sendStub.calledWith(
        HttpMethod.POST,
        '/url',
        payload,
        throwingErrorCallback,
        /* contentType=*/ undefined,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
    assert.deepEqual(r, response);
  });

  test('get', async () => {
    const response = {foo: 'foo'};
    sendStub.resolves(new Response(makePrefixedJSON(response)));
    const r = await instance.get('/url');
    assert.isTrue(
      sendStub.calledWith(
        'GET',
        '/url',
        /* payload=*/ undefined,
        throwingErrorCallback,
        /* contentType=*/ undefined,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
    assert.deepEqual(r, response);
  });

  test('post', async () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    sendStub.resolves(new Response(makePrefixedJSON(response)));
    const r = await instance.post('/url', payload);
    assert.isTrue(
      sendStub.calledWith(
        'POST',
        '/url',
        payload,
        throwingErrorCallback,
        /* contentType=*/ undefined,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
    assert.deepEqual(r, response);
  });

  test('put', async () => {
    const payload = {foo: 'foo'};
    const response = {bar: 'bar'};
    sendStub.resolves(new Response(makePrefixedJSON(response)));
    const r = await instance.put('/url', payload);
    assert.isTrue(
      sendStub.calledWith(
        'PUT',
        '/url',
        payload,
        throwingErrorCallback,
        /* contentType=*/ undefined,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
    assert.deepEqual(r, response);
  });

  test('delete works', async () => {
    const response = {status: 204};
    sendStub.resolves(response);
    const r = await instance.delete('/url');
    assert.isTrue(
      sendStub.calledWith(
        'DELETE',
        '/url',
        /* payload=*/ undefined,
        /* errFn=*/ undefined,
        /* contentType=*/ undefined,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
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
    assert.isTrue(
      sendStub.calledWith(
        'DELETE',
        '/url',
        /* payload=*/ undefined,
        /* errFn=*/ undefined,
        /* contentType=*/ undefined,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
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
    assert.isTrue(stub.calledWith(/* requestOrigin=*/ 'plugin:testplugin'));
    assert.equal(version, 'foo bar');
  });

  test('getConfig', async () => {
    const info = createServerInfo();
    const stub = stubRestApi('getConfig').resolves(info);
    const config = await instance.getConfig();
    assert.isTrue(
      stub.calledWith(
        /* noCache=*/ false,
        /* requestOrigin=*/ 'plugin:testplugin'
      )
    );
    assert.equal(config, info);
  });
});
