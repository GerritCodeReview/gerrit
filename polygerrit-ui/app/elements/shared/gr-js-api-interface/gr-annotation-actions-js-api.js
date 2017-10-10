// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
(function(window) {
  'use strict';

  function GrAnnotationActionsInterface(plugin) {
    this.plugin = plugin;
    // Return this instance when there is an annotatediff event.
    plugin.on('annotatediff', this);

    // Collect all annotation layers instantiated by getLayer. Will be used when
    // notifying their listeners in the notify function.
    this._annotationLayers = [];

    // Default impl is a no-op.
    this._addLayerFunc = annotationActionsContext => {};
  }

  // Plugins should use annotateRange() in addLayerFunc to apply CSS class to a
  // range within a line.
  GrAnnotationActionsInterface.prototype.addLayer = function(addLayerFunc) {
    this._addLayerFunc = addLayerFunc;
    return this;
  };

  // Optional: Plugins should use notify() function to report updates.
  GrAnnotationActionsInterface.prototype.addNotifier = function(notifyFunc) {
    // Register the notify function with the plugin's function.
    notifyFunc(this.notify.bind(this));
    return this;
  };

  GrAnnotationActionsInterface.prototype.notify = function(
      path, startRange, endRange, side) {
    for (const annotationLayer of this._annotationLayers) {
      // Notify only the annotation layer that is associated with the specified
      // path.
      if (annotationLayer._path === path) {
        annotationLayer.notifyListeners(startRange, endRange, side);
        break;
      }
    }
  };

  // Will be called to register annotation layers by the framework. Not
  // intended to be called by plugins.
  GrAnnotationActionsInterface.prototype.getLayer = function(
      path, changeNum, patchNum) {
    const annotationLayer = new AnnotationLayer(path, changeNum, patchNum,
                                                this._addLayerFunc);
    this._annotationLayers.push(annotationLayer);
    return annotationLayer;
  };

  // Used to create an instance of the Annotation Layer interface.
  function AnnotationLayer(path, changeNum, patchNum, addLayerFunc) {
    this._path = path;
    this._changeNum = changeNum;
    this._patchNum = patchNum;
    this._addLayerFunc = addLayerFunc;

    this._listeners = [];
  }

  // Part of the Annotation layer interface. Used to register new listeners.
  AnnotationLayer.prototype.addListener = function(fn) {
    this._listeners.push(fn);
  };

  // Part of the Annotation layer interface. Used to annotate element and line.
  AnnotationLayer.prototype.annotate = function(el, line) {
    const annotationActionsContext = new GrAnnotationActionsContext(
        el, line, this._path, this._changeNum, this._patchNum);
    this._addLayerFunc(annotationActionsContext);
  };

  // Helper method used to invoke all listeners.
  AnnotationLayer.prototype.notifyListeners = function(
      startRange, endRange, side) {
    for (const listener of this._listeners) {
      listener(startRange, endRange, side);
    }
  };

  window.GrAnnotationActionsInterface = GrAnnotationActionsInterface;
})(window);
