---
title: "Design Doc - ${title} - Solution - ${solution-name}"
sidebar: gerritdoc_sidebar
permalink: design-doc-${folder-name}-solution-${solution-name}.html
hide_sidebar: true
hide_navtoggle: true
toc: false
folder: design-docs/${folder-name}
---

# Solution - ${solution-name}

## <a id="overview"> Overview

High-level overview; put details in the next section and background in
the 'Background' section (see dev-design-doc-use-cases-template.txt).

Should be understandable by engineers that are not working on Gerrit.

If a solution is a variant of another solution, that other solution
should be linked here.

## <a id="detailed-design"> Detailed Design

How does the overall design work? Details about the algorithms,
storage format, APIs, etc., should be included here.

For the initial review, it is ok for this to lack implementation
details of minor importance.

### <a id="scalability"> Scalability

How does the solution scale?

If applicable, consider:

* data size increase
* traffic increase
* effects on replication across sites (master-replica and master-master)

## <a id="alternatives-considered"> Alternatives Considered

Within the scope of this solution you may need to describe what you did
not do or why simpler approaches don't work. Mention other things to
watch out for (if any).

Do not describe alternative solutions in this section, as each solution
should be described in a separate file.

## <a id="pros-and-cons"> Pros and Cons

Objectively list all points that speak in favor/against this solution.

## <a id="implementation-plan"> Implementation Plan

If known, say who would be willing to drive the implementation.

It is possible to contribute solutions without having resources to do
the implementation. In this case, say so here.

If mentor support is desired, say so here. Also briefly describe any
circumstances that can help with finding a suitable mentor.

## <a id="time-estimation"> Time Estimation

A rough itemized estimation of how much time it takes to implement this
feature. Break down the feature into work items and estimate each item
separately.

If a mentor is assigned, this section must define a maximum time frame
after which the mentorship automatically ends even if the feature isn't
fully done yet.
