/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import {assert} from '@open-wc/testing';
import {getBaseUrl} from '../../utils/url-util';
import {
  createGerritInfo,
  createServerInfo,
} from '../../test/test-data-generators';
import {ConfigModel} from './config-model';
import {testResolver} from '../../test/common-test-setup';
import {getAppContext} from '../../services/app-context';
import {changeModelToken} from '../change/change-model';
import {ServerInfo} from '../../api/rest-api';

suite('getDocsBaseUrl tests', () => {
  let model: ConfigModel;

  setup(async () => {
    model = new ConfigModel(
      testResolver(changeModelToken),
      getAppContext().restApiService
    );
  });

  test('null config', async () => {
    const probePathMock = sinon
      .stub(model.restApiService, 'probePath')
      .resolves(true);
    const docsBaseUrl = await model.getDocsBaseUrl(undefined);
    assert.equal(
      probePathMock.lastCall.args[0],
      `${getBaseUrl()}/Documentation/index.html`
    );
    assert.equal(docsBaseUrl, `${getBaseUrl()}/Documentation`);
  });

  test('no doc config', async () => {
    const probePathMock = sinon
      .stub(model.restApiService, 'probePath')
      .resolves(true);
    const config: ServerInfo = {
      ...createServerInfo(),
      gerrit: createGerritInfo(),
    };
    const docsBaseUrl = await model.getDocsBaseUrl(config);
    assert.equal(
      probePathMock.lastCall.args[0],
      `${getBaseUrl()}/Documentation/index.html`
    );
    assert.equal(docsBaseUrl, `${getBaseUrl()}/Documentation`);
  });

  test('has doc config', async () => {
    const probePathMock = sinon
      .stub(model.restApiService, 'probePath')
      .resolves(true);
    const config: ServerInfo = {
      ...createServerInfo(),
      gerrit: {...createGerritInfo(), doc_url: 'foobar'},
    };
    const docsBaseUrl = await model.getDocsBaseUrl(config);
    assert.isFalse(probePathMock.called);
    assert.equal(docsBaseUrl, 'foobar');
  });

  test('no probe', async () => {
    const probePathMock = sinon
      .stub(model.restApiService, 'probePath')
      .resolves(false);
    const docsBaseUrl = await model.getDocsBaseUrl(undefined);
    assert.equal(
      probePathMock.lastCall.args[0],
      `${getBaseUrl()}/Documentation/index.html`
    );
    assert.equal(
      docsBaseUrl,
      'https://gerrit-review.googlesource.com/Documentation'
    );
  });
});
