/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-metadata';
import {fixture, html} from '@open-wc/testing';
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrChangeMetadata} from './gr-change-metadata';
import {
  createAccountDetailWithId,
  createCommit,
  createConfig,
  createParsedChange,
  createServerInfo,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {userModelToken} from '../../../models/user/user-model';
import {configModelToken} from '../../../models/config/config-model';
import {changeModelToken} from '../../../models/change/change-model';
import {relatedChangesModelToken} from '../../../models/change/related-changes-model';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {
  CommitId,
  Hashtag,
  NumericChangeId,
  RevisionPatchSetNum,
  Timestamp,
  TopicName,
} from '../../../types/common';
import {LoadingStatus, ParsedChangeInfo} from '../../../types/types';

suite('gr-change-metadata screenshot tests', () => {
  let element: GrChangeMetadata;

  setup(async () => {
    const configModel = testResolver(configModelToken);
    const userModel = testResolver(userModelToken);
    const changeModel = testResolver(changeModelToken);
    testResolver(relatedChangesModelToken);

    configModel.setState({
      serverConfig: createServerInfo(),
      repoConfig: createConfig(),
    });
    changeModel.setState({
      change: createParsedChange(),
      loadingStatus: LoadingStatus.LOADED,
    });
    userModel.setState({
      account: createAccountDetailWithId(),
      accountLoaded: true,
    });

    element = await fixture<GrChangeMetadata>(
      html`<gr-change-metadata></gr-change-metadata>`
    );
    await element.updateComplete;
  });

  test('normal view', async () => {
    await visualDiff(element, 'gr-change-metadata');
    await visualDiffDarkTheme(element, 'gr-change-metadata-dark');
  });

  test('show all sections with more data', async () => {
    const changeModel = testResolver(changeModelToken);
    const detailedChange: ParsedChangeInfo = {
      ...createParsedChange(),
      topic: 'my-topic' as TopicName,
      hashtags: ['my-hashtag' as Hashtag],
      cherry_pick_of_change: 123 as NumericChangeId,
      cherry_pick_of_patch_set: 1 as RevisionPatchSetNum,
      submitted: '2015-12-25 18:43:40.383000000' as Timestamp,
      revert_of: 456 as NumericChangeId,
    };
    detailedChange.revisions[
      detailedChange.current_revision
    ].commit!.parents.push({...createCommit(), commit: 'p2' as CommitId});
    changeModel.setState({
      change: detailedChange,
      loadingStatus: LoadingStatus.LOADED,
    });
    element.showAllSections = true;
    await element.updateComplete;
    await visualDiff(element, 'gr-change-metadata-show-all');
    await visualDiffDarkTheme(element, 'gr-change-metadata-show-all-dark');
  });
});
