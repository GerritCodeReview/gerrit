# Simple redirect for / to Documentation/index.html

from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app

def _CreateApplication():
  return webapp.WSGIApplication([
    (r'^/$',  RedirectDocumentation),
  ],
  debug=False)

class RedirectDocumentation(webapp.RequestHandler):
  def get(self):
    self.redirect('Documentation/index.html', permanent=True)

def main():
  run_wsgi_app(application)

if __name__ == '__main__':
  application = _CreateApplication()
  main()
