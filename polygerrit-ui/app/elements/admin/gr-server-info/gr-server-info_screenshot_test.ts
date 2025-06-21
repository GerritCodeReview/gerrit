/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-server-info';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrServerInfo} from './gr-server-info';
import {
  ConfigModel,
  configModelToken,
} from '../../../models/config/config-model';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {ServerInfo} from '../../../types/common';
import {BehaviorSubject} from 'rxjs';
import {
  AuthType,
  DefaultDisplayNameConfig,
  MergeabilityComputationBehavior,
} from '../../../api/rest-api';
import {
  DIProviderElement,
  wrapInProvider,
} from '../../../models/di-provider-element';

const testServerInfo: ServerInfo = {
  auth: {
    auth_type: AuthType.OAUTH,
    login_url: '/login',
    login_text: 'Sign in with Google',
    editable_account_fields: [],
  },
  gerrit: {
    all_projects: 'All-Projects',
    all_users: 'All-Users',
    report_bug_url: 'https://bugs.chromium.org/p/gerrit/issues/entry',
    doc_url: 'https://gerrit-review.googlesource.com/Documentation/',
    primary_weblink_name: 'gitiles',
    doc_search: false,
    project_state_predicate_enabled: false,
  },
  user: {
    anonymous_coward_name: 'Anonymous Coward',
  },
  download: {
    schemes: {
      http: {
        url: 'https://gerrit-review.googlesource.com',
        is_auth_required: true,
        is_auth_supported: true,
        commands: '',
        clone_commands: {},
      },
    },
    archives: ['zip', 'tar', 'tar.gz'],
  },
  accounts: {
    visibility: 'ALL',
    default_display_name: DefaultDisplayNameConfig.USERNAME,
  },
  change: {
    large_change: 500,
    update_delay: 100,
    mergeability_computation_behavior:
      MergeabilityComputationBehavior.API_REF_UPDATED_AND_CHANGE_REINDEX,
  },
  plugin: {
    has_avatars: false,
    js_resource_paths: [],
  },
  suggest: {
    from: 5,
  },
  metadata: [
    {
      name: 'Gerrit version',
      value: '3.9.0.1',
    },
    {
      name: 'API version',
      value: '3.9',
    },
  ],
};

suite('gr-server-info screenshot tests', () => {
  let element: GrServerInfo;

  setup(async () => {
    const serverConfigSubject = new BehaviorSubject<ServerInfo | undefined>(
      testServerInfo
    );
    const configModel = {
      serverConfig$: serverConfigSubject.asObservable(),
    } as ConfigModel;

    const provider = await fixture<DIProviderElement>(
      wrapInProvider(
        html`<gr-server-info></gr-server-info>`,
        configModelToken,
        configModel
      )
    );
    element = provider.element as GrServerInfo;
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-server-info');
    await visualDiffDarkTheme(element, 'gr-server-info');
  });
});
