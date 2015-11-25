// Copyright (C) 2015 The Android Open Source Project
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

package main

import (
	"bufio"
	"compress/gzip"
	"errors"
	"flag"
	"io"
	"log"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strings"
)

var (
	restHost = flag.String("host", "gerrit-review.googlesource.com", "Host to proxy requests to")
	port     = flag.String("port", ":8081", "Port to serve HTTP requests on")
	prod     = flag.Bool("prod", false, "Serve production assets")
)

func main() {
	flag.Parse()

	if *prod {
		http.Handle("/", http.FileServer(http.Dir("dist")))
	} else {
		http.Handle("/bower_components/",
			http.StripPrefix("/bower_components/", http.FileServer(http.Dir("bower_components"))))
		http.Handle("/", http.FileServer(http.Dir("app")))
	}

	http.HandleFunc("/changes/", handleRESTProxy)
	http.HandleFunc("/accounts/", handleRESTProxy)
	log.Println("Serving on port", *port)
	log.Fatal(http.ListenAndServe(*port, &server{}))
}

func handleRESTProxy(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	req := &http.Request{
		Method: "GET",
		URL: &url.URL{
			Scheme:   "https",
			Host:     *restHost,
			Opaque:   r.URL.EscapedPath(),
			RawQuery: r.URL.RawQuery,
		},
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer res.Body.Close()
	w.WriteHeader(res.StatusCode)
	if _, err := io.Copy(w, res.Body); err != nil {
		log.Println("Error copying response to ResponseWriter:", err)
		return
	}
}

type gzipResponseWriter struct {
	io.WriteCloser
	http.ResponseWriter
}

func newGzipResponseWriter(w http.ResponseWriter) *gzipResponseWriter {
	gz := gzip.NewWriter(w)
	return &gzipResponseWriter{WriteCloser: gz, ResponseWriter: w}
}

func (w gzipResponseWriter) Write(b []byte) (int, error) {
	return w.WriteCloser.Write(b)
}

func (w gzipResponseWriter) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	h, ok := w.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, errors.New("gzipResponseWriter: ResponseWriter does not satisfy http.Hijacker interface")
	}
	return h.Hijack()
}

type server struct{}

// Any path prefixes that should resolve to index.html.
var (
	fePaths    = []string{"/q/", "/c/", "/dashboard/"}
	issueNumRE = regexp.MustCompile(`^\/\d+\/?$`)
)

func (_ *server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	log.Printf("%s %s %s %s\n", r.Proto, r.Method, r.RemoteAddr, r.URL)
	for _, prefix := range fePaths {
		if strings.HasPrefix(r.URL.Path, prefix) {
			r.URL.Path = "/"
			log.Println("Redirecting to /")
			break
		} else if match := issueNumRE.Find([]byte(r.URL.Path)); match != nil {
			r.URL.Path = "/"
			log.Println("Redirecting to /")
			break
		}
	}
	if !strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
		http.DefaultServeMux.ServeHTTP(w, r)
		return
	}
	w.Header().Set("Content-Encoding", "gzip")
	gzw := newGzipResponseWriter(w)
	defer gzw.Close()
	http.DefaultServeMux.ServeHTTP(gzw, r)
}
