# mitmdump -s add-header.py
def response(flow):
    if flow.request.host == 'gerrit-review.googlesource.com' and flow.request.path == "/c/92000?1":
        #flow.response.headers['any'] = '<meta.rdf>; rel=meta'
        flow.response.headers['Link'] = '</changes/98000/detail?O=11640c>;rel="preload";crossorigin;'
