/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-js-api-interface.js';
import {GrPluginRestApi} from './gr-plugin-rest-api.js';
import {_testOnly_initGerritPluginApi} from './gr-gerrit.js';

const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-plugin-rest-api tests', () => {
  let instance;

  let getResponseObjectStub;
  let sendStub;
  let restApiStub;

  setup(() => {
    getResponseObjectStub = sinon.stub().returns(Promise.resolve());
    sendStub = sinon.stub().returns(Promise.resolve({status: 200}));
    restApiStub = {
      getAccount: () => { return Promise.resolve({name: 'Judy Hopps'}); },
      getResponseObject: getResponseObjectStub,
      send: sendStub,
      getLoggedIn: sinon.stub(),
      getVersion: sinon.stub(),
      getConfig: sinon.stub(),
    };
    stub('gr-rest-api-interface', Object.keys(restApiStub).reduce((a, k) => {
      a[k] = (...args) => { return restApiStub[k](...args); };
      return a;
    }, {}));
    pluginApi.install(p => {}, '0.1',
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
    restApiStub.getLoggedIn.returns(Promise.resolve(true));
    return instance.getLoggedIn().then(result => {
      assert.isTrue(restApiStub.getLoggedIn.calledOnce);
      assert.isTrue(result);
    });
  });

  test('getVersion', () => {
    restApiStub.getVersion.returns(Promise.resolve('foo bar'));
    return instance.getVersion().then(result => {
      assert.isTrue(restApiStub.getVersion.calledOnce);
      assert.equal(result, 'foo bar');
    });
  });

  test('getConfig', () => {
    restApiStub.getConfig.returns(Promise.resolve('foo bar'));
    return instance.getConfig().then(result => {
      assert.isTrue(restApiStub.getConfig.calledOnce);
      assert.equal(result, 'foo bar');
    });
  });
});

