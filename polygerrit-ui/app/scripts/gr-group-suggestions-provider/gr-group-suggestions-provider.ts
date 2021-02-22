/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {GroupBaseInfo} from '../../types/common';

export class GrGroupSuggestionsProvider {
  constructor(private restAPI: RestApiService) {}

  getSuggestions(input: string) {
    return this.restAPI.getSuggestedGroups(`${input}`).then(groups => {
      if (!groups) {
        return [];
      }
      const keys = Object.keys(groups);
      return keys.map(key => {
        return {...groups[key], name: key};
      });
    });
  }

  makeSuggestionItem(suggestion: GroupBaseInfo) {
    return {
      name: suggestion.name,
      value: {group: {name: suggestion.name, id: suggestion.id}},
    };
  }
}
