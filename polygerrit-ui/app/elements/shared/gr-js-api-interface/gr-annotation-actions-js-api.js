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

  /**
   * Register a function to call to apply annotations. Plugins should use
   * GrAnnotationActionsContext.annotateRange to apply a CSS class to a range
   * within a line.
   * @param {Function<GrAnnotationActionsContext>} addLayerFunc The function
   *     that will be called when the AnnotationLayer is ready to annotate.
   */
  GrAnnotationActionsInterface.prototype.addLayer = function(addLayerFunc) {
    this._addLayerFunc = addLayerFunc;
    return this;
  };

  /**
   * The specified function will be called with a notify function for the plugin
   * to call when it has all required data for annotation. Optional.
   * @param {Function<Function<String, Number, Number, String>>} notifyFunc See
   *     doc of the notify function below to see what it does.
   */
  GrAnnotationActionsInterface.prototype.addNotifier = function(notifyFunc) {
    // Register the notify function with the plugin's function.
    notifyFunc(this.notify.bind(this));
    return this;
  };

  /**
   * The notify function will call the listeners of all required annotation
   * layers. Intended to be called by the plugin when all required data for
   * annotation is available.
   * @param {String} path The file path whose listeners should be notified.
   * @param {Number} start The line where the update starts.
   * @param {Number} end The line where the update ends.
   * @param {String} side The side of the update ('left' or 'right').
   */
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

  /**
   * Should be called to register annotation layers by the framework. Not
   * intended to be called by plugins.
   * @param {String} path The file path (eg: /COMMIT_MSG').
   * @param {String} changeNum The Gerrit change number.
   * @param {String} patchNum The Gerrit patch number.
   */
  GrAnnotationActionsInterface.prototype.getLayer = function(
      path, changeNum, patchNum) {
    const annotationLayer = new AnnotationLayer(path, changeNum, patchNum,
                                                this._addLayerFunc);
    this._annotationLayers.push(annotationLayer);
    return annotationLayer;
  };

  /**
   * Used to create an instance of the Annotation Layer interface.
   * @param {String} path The file path (eg: /COMMIT_MSG').
   * @param {String} changeNum The Gerrit change number.
   * @param {String} patchNum The Gerrit patch number.
   * @param {Function<GrAnnotationActionsContext>} addLayerFunc The function
   *     that will be called when the AnnotationLayer is ready to annotate.
   */
  function AnnotationLayer(path, changeNum, patchNum, addLayerFunc) {
    this._path = path;
    this._changeNum = changeNum;
    this._patchNum = patchNum;
    this._addLayerFunc = addLayerFunc;

    this._listeners = [];
  }

  /**
   * Register a listener for layer updates.
   * @param {Function<Number, Number, String>} fn The update handler function.
   *     Should accept as arguments the line numbers for the start and end of
   *     the update and the side as a string.
   */
  AnnotationLayer.prototype.addListener = function(fn) {
    this._listeners.push(fn);
  };

  /**
   * Layer method to add annotations to a line.
   * @param {HTMLElement} el The DIV.contentText element to apply the
   *     annotation to.
   * @param {GrDiffLine} line The line object.
   */
  AnnotationLayer.prototype.annotate = function(el, line) {
    const annotationActionsContext = new GrAnnotationActionsContext(
        el, line, this._path, this._changeNum, this._patchNum);
    this._addLayerFunc(annotationActionsContext);
  };

  /**
   * Notify Layer listeners of changes to annotations.
   * @param {Number} start The line where the update starts.
   * @param {Number} end The line where the update ends.
   * @param {String} side The side of the update. ('left' or 'right')
   */
  AnnotationLayer.prototype.notifyListeners = function(
      startRange, endRange, side) {
    for (const listener of this._listeners) {
      listener(startRange, endRange, side);
    }
  };

  window.GrAnnotationActionsInterface = GrAnnotationActionsInterface;
})(window);
