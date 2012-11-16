// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

import com.google.gerrit.extensions.registration.DynamicMap;

/**
 * A collection of resources accessible through a REST API.
 * <p>
 * To build a collection declare a resource, the map in a module, and the
 * collection itself accepting the map:
 *
 * <pre>
 * public class MyResource implements RestResource {
 *   public static final TypeLiteral&lt;RestView&lt;MyResource&gt;&gt; MY_KIND =
 *       new TypeLiteral&lt;RestView&lt;MyResource&gt;&gt;() {};
 * }
 *
 * public class MyModule extends AbstractModule {
 *   &#064;Override
 *   protected void configure() {
 *     DynamicMap.mapOf(binder(), MyResource.MY_KIND);
 *
 *     bind(MyResource.MY_KIND)
 *       .annotatedWith(Exports.named(&quot;GET.action&quot;))
 *       .to(MyAction.class);
 *   }
 * }
 *
 * public class MyCollection extends RestCollection&lt;TopLevelResource, MyResource&gt; {
 *   private final DynamicMap&lt;RestView&lt;MyResource&gt;&gt; views;
 *
 *   &#064;Inject
 *   MyCollection(DynamicMap&lt;RestView&lt;MyResource&gt;&gt; views) {
 *     this.views = views;
 *   }
 *
 *   public DynamicMap&lt;RestView&lt;MyResource&gt;&gt; views() {
 *     return views;
 *   }
 * }
 * </pre>
 *
 * <p>
 * To build a nested collection, declare the nested collection as a view bound
 * with {@code GET.childname}. The child view implementation class:
 *
 * <pre>
 * public class ChildCollection implements RestView&lt;ParentType&gt;,
 *     RestCollection&lt;ParentType, ChildType&gt; {
 * }
 * </pre>
 *
 * @param <P> type of the parent resource. For a top level collection this
 *        should always be {@link TopLevelResource}. For children contained
 *        within another resource, this is the type of resource handle the child
 *        view needs to select the correct children.
 * @param <R> type of resource operated on by each view.
 */
public interface RestCollection<P extends RestResource, R extends RestResource> {
  /**
   * Create a view to list the contents of the collection.
   * <p>
   * The returned view should accept the parent type to scope the search, and
   * may want to take a "q" parameter option to narrow the results.
   *
   * @return view to list the collection.
   * @throws ResourceNotFoundException if the collection cannot be listed.
   */
  RestView<P> list() throws ResourceNotFoundException;

  /**
   * Parse a path component into a resource handle.
   *
   * @param parent the handle to the collection.
   * @param id string identifier supplied by the client. In a URL such as
   *        {@code /changes/1234/abandon} this string is {@code "1234"}.
   * @return a resource handle for the identified object.
   * @throws ResourceNotFoundException the object does not exist, or the caller
   *         is not permitted to know if the resource exists.
   * @throws Exception if the implementation had any errors converting to a
   *         resource handle. This results in an HTTP 500 Internal Server Error.
   */
  R parse(P parent, String id) throws ResourceNotFoundException, Exception;

  /**
   * Get the views that support this collection.
   * <p>
   * Within a resource the views are accessed as {@code RESOURCE/plugin~view}.
   *
   * @return map of views.
   */
  DynamicMap<RestView<R>> views();
}
