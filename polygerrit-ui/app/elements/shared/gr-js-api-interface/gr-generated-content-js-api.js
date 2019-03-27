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
 (function(window) {
  'use strict';

  function GrGeneratedContentInterface(plugin) {
    this.plugin = plugin;
    // Return this instance when there is a generatecontent event.
    plugin.on('generatecontent', this);

    this._contentGenerators = [];
    this._contentRequiredContentGenerators = [];
  }

  GrGeneratedContentInterface.prototype.addContentGenerator = function(
      validityFunc, generatorFunc, contentType) {
    this._contentGenerators.push(new ContentGenerator(validityFunc, generatorFunc, contentType));
  };

  GrGeneratedContentInterface.prototype.addContentRequiredContentGenerator = function(validityFunc, generatorFunc, contentType) {
    this._contentRequiredContentGenerators.push(new ContentRequiredContentGenerator(
        validityFunc, generatorFunc, contentType));
  };

  GrGeneratedContentInterface.prototype.numContentRequiredContentGenerators = function() {
    return this._contentRequiredContentGenerators.length;
  };

  GrGeneratedContentInterface.prototype.getMatchingGenerators = function(path) {
    const matchingGenerators = [];
    for (const generator of this._contentGenerators) {
      if (generator.isValid(path)) {
        matchingGenerators.push(generator);
      }
    }
    return matchingGenerators;
  };

  GrGeneratedContentInterface.prototype.getMatchingContentRequiredGenerators = function(path, content) {
    const matchingGenerators = [];
    for (const generator of this._contentRequiredContentGenerators) {
      if (generator.isValid(path, content)) {
        matchingGenerators.push(generator);
      }
    }
    return matchingGenerators;
  };

  function ContentGenerator(validityFunc, generatorFunc, contentType) {
    this._validityFunc = validityFunc;
    this._generatorFunc = generatorFunc;
    this._contentType = contentType;
  }

  ContentGenerator.prototype.isValid = function(path) {
    return this._validityFunc(path);
  };

  ContentGenerator.prototype.generateContent = function(path, content) {
    return this._generatorFunc(path, content);
  };

  ContentGenerator.prototype.isImageType = function() {
    return this._contentType === 'image';
  };

  function ContentRequiredContentGenerator(
      validityFunc, generatorFunc, contentType) {
    ContentGenerator.call(this, validityFunc, generatorFunc, contentType);
  }

  ContentRequiredContentGenerator.prototype.__proto__ = ContentGenerator.prototype;

  ContentRequiredContentGenerator.prototype.isValid = function(path, content) {
    return this._validityFunc(path, content);
  };

  window.GrGeneratedContentInterface = GrGeneratedContentInterface;

 })(window);