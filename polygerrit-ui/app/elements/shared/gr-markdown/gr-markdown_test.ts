/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import {assert, fixture, html} from '@open-wc/testing';
import {changeModelToken} from '../../../models/change/change-model';
import {
  ConfigModel,
  configModelToken,
} from '../../../models/config/config-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {getAppContext} from '../../../services/app-context';
import './gr-markdown';
import {GrMarkdown} from './gr-markdown';
import {createConfig} from '../../../test/test-data-generators';
import {stubRestApi, waitUntilObserved} from '../../../test/test-utils';
import {CommentLinks} from '../../../api/rest-api';

suite('gr-markdown tests', () => {
  let element: GrMarkdown;
  let configModel: ConfigModel;

  async function setCommentLinks(commentlinks: CommentLinks) {
    configModel.updateRepoConfig({...createConfig(), commentlinks});
    await waitUntilObserved(
      configModel.repoCommentLinks$,
      links => links === commentlinks
    );
  }

  setup(async () => {
    configModel = new ConfigModel(
      testResolver(changeModelToken),
      getAppContext().restApiService
    );
    stubRestApi('getProjectConfig').resolves({
      ...createConfig(),
      commentlinks: {
        'foo-capitalizer': {
          match: 'foo',
          html: '<div>FOO</div>',
        },
      },
    });
    element = (
      await fixture(
        wrapInProvider(
          html`<gr-markdown></gr-markdown>`,
          configModelToken,
          configModel
        )
      )
    ).querySelector('gr-markdown')!;
  });

  test('renders markdown with links and rewrites', async () => {
    element.markdown = '# Heading\ngoogle.com foo\n> A quote';
    await setCommentLinks({
      'foo-capitalizer': {
        match: 'foo',
        html: '<div>FOO</div>',
      },
    });
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <marked-element>
          <div slot="markdown-html">
            <h1 id="heading">Heading</h1>
            <p>
              <a href="http://google.com" rel="noopener" target="_blank">
                google.com
              </a>
              <div>FOO</div>
            </p>
            <blockquote>
              <p>
                A quote
              </p>
            </blockquote>
          </div>
        </marked-element>
      `
    );
  });
});
