package httpclient

import (
	"fmt"
	"github.com/go-resty/resty/v2"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestCompatibilityChangesEndpointsOnly(t *testing.T) {
	var paths []string
	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		paths = append(paths, r.URL.EscapedPath()+"?"+r.URL.RawQuery)
		fmt.Fprint(w, `{"data":{}}`)
	}))
	defer s.Close()
	c := resty.New().SetBaseURL(s.URL + "/api/v1")
	UseWorkerCompatibilityEndpoints(c, s.URL+"/api/v1")
	for _, path := range []string{"books/id/123", "/authors/Nguy%E1%BB%85n?test=1", "/rabbitmq/book", "/auth/login", "/worker/books/id/123", s.URL + "/api/v1/chapters/id/123"} {
		if _, err := c.R().Get(path); err != nil {
			t.Fatal(err)
		}
	}
	expected := []string{"/api/v1/worker/books/id/123?", "/api/v1/worker/authors/Nguy%E1%BB%85n?test=1", "/api/v1/rabbitmq/book?", "/api/v1/auth/login?", "/api/v1/worker/books/id/123?", "/api/v1/worker/chapters/id/123?"}
	for i, want := range expected {
		if paths[i] != want {
			t.Errorf("got %s want %s", paths[i], want)
		}
	}
}
