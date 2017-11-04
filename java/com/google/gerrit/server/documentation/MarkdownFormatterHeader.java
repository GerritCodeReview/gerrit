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

package com.google.gerrit.server.documentation;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ext.anchorlink.AnchorLink;
import com.vladsch.flexmark.ext.anchorlink.internal.AnchorLinkNodeRenderer;
import com.vladsch.flexmark.html.CustomNodeRenderer;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.HtmlRenderer.HtmlRendererExtension;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.DelegatingNodeRendererFactory;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRendererFactory;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.profiles.pegdown.Extensions;
import com.vladsch.flexmark.profiles.pegdown.PegdownOptionsAdapter;
import com.vladsch.flexmark.util.options.DataHolder;
import com.vladsch.flexmark.util.options.MutableDataHolder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkdownFormatterHeader {
  private static final Logger log = LoggerFactory.getLogger(MarkdownFormatterHeader.class);

  static class HeadingExtension implements HtmlRendererExtension {
    @Override
    public void rendererOptions(final MutableDataHolder options) {
      // add any configuration settings to options you want to apply to everything, here
    }

    @Override
    public void extend(final HtmlRenderer.Builder rendererBuilder, final String rendererType) {
      rendererBuilder.nodeRendererFactory(new HeadingNodeRenderer.Factory());
    }

    static HeadingExtension create() {
      return new HeadingExtension();
    }
  }

  static class HeadingNodeRenderer implements NodeRenderer {
    public HeadingNodeRenderer(DataHolder options) {}

    @Override
    public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
      return new HashSet<NodeRenderingHandler<? extends Node>>(
          Arrays.asList(
              new NodeRenderingHandler<AnchorLink>(
                  AnchorLink.class,
                  new CustomNodeRenderer<AnchorLink>() {
                    @Override
                    public void render(
                        AnchorLink node, NodeRendererContext context, HtmlWriter html) {
                      HeadingNodeRenderer.this.render(node, context, html);
                    }
                  }),
              new NodeRenderingHandler<Heading>(
                  Heading.class,
                  new CustomNodeRenderer<Heading>() {
                    @Override
                    public void render(Heading node, NodeRendererContext context, HtmlWriter html) {
                      HeadingNodeRenderer.this.render(node, context, html);
                    }
                  })));
    }

    void render(final AnchorLink node, final NodeRendererContext context, final HtmlWriter html) {
      Node parent = node.getParent();

      if (parent instanceof Heading && ((Heading) parent).getLevel() == 1) {
        // render without anchor link
        context.renderChildren(node);
      } else {
        context.delegateRender();
      }
    }

    static boolean haveExtension(int extensions, int flags) {
      return (extensions & flags) != 0;
    }

    static boolean haveAllExtensions(int extensions, int flags) {
      return (extensions & flags) == flags;
    }

    void render(final Heading node, final NodeRendererContext context, final HtmlWriter html) {
      if (node.getLevel() == 1) {
        // render without anchor link
        final int extensions = context.getOptions().get(PegdownOptionsAdapter.PEGDOWN_EXTENSIONS);
        if (context.getHtmlOptions().renderHeaderId
            || haveExtension(extensions, Extensions.ANCHORLINKS)
            || haveAllExtensions(
                extensions, Extensions.EXTANCHORLINKS | Extensions.EXTANCHORLINKS_WRAP)) {
          String id = context.getNodeId(node);
          if (id != null) {
            html.attr("id", id);
          }
        }

        if (context.getHtmlOptions().sourcePositionParagraphLines) {
          html.srcPos(node.getChars())
              .withAttr()
              .tagLine(
                  "h" + node.getLevel(),
                  new Runnable() {
                    @Override
                    public void run() {
                      html.srcPos(node.getText()).withAttr().tag("span");
                      context.renderChildren(node);
                      html.tag("/span");
                    }
                  });
        } else {
          html.srcPos(node.getText())
              .withAttr()
              .tagLine(
                  "h" + node.getLevel(),
                  new Runnable() {
                    @Override
                    public void run() {
                      context.renderChildren(node);
                    }
                  });
        }
      } else {
        context.delegateRender();
      }
    }

    public static class Factory implements DelegatingNodeRendererFactory {
      @Override
      public NodeRenderer create(final DataHolder options) {
        return new HeadingNodeRenderer(options);
      }

      @Override
      public Set<Class<? extends NodeRendererFactory>> getDelegates() {
        Set<Class<? extends NodeRendererFactory>> delegates =
            new HashSet<Class<? extends NodeRendererFactory>>();
        delegates.add(AnchorLinkNodeRenderer.Factory.class);
        return delegates;
      }
    }
  }
}
