/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {BehaviorSubject} from 'rxjs';
import {SuggestionsService} from '../../services/suggestions/suggestions-service';
import {createFixSuggestionInfo} from '../test-data-generators';

export const suggestionsServiceMock: SuggestionsService = {
  suggestionsServiceUpdated$: new BehaviorSubject(false),
  isGeneratedSuggestedFixEnabled: () => true,
  isGeneratedSuggestedFixEnabledForComment: () => true,
  generateSuggestedFix: () => Promise.resolve(createFixSuggestionInfo()),
  finalize(): void {},

  generateSuggestedFixForComment: () =>
    Promise.resolve(createFixSuggestionInfo()),
  autocompleteComment: () => Promise.resolve(undefined),
};
