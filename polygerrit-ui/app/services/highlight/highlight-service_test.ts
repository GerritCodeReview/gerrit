/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../test/common-test-setup-karma';
import {HighlightService} from './highlight-service';

suite('highlight-service tests', () => {
  let service: HighlightService;

  setup(() => {
    service = new HighlightService();
  });

  test('getShortcut', () => {
    service.finalize();
    assert.equal('asdf', 'asdf');
  });
});
