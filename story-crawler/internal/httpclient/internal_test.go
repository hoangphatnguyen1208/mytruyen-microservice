package httpclient

import (
	"fmt"
	"github.com/go-resty/resty/v2"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestInternalRoutesDataAndTaskCommands(t *testing.T) {
	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, r.URL.EscapedPath()+"?"+r.URL.RawQuery)
	}))
	defer s.Close()
	c := resty.New().SetBaseURL(s.URL + "/api/v1")
	UseInternalImportEndpoints(c, s.URL+"/api/v1")
	cases := map[string]string{
		"books/id/123":                          "/internal/import/crawler/books/id/123?",
		"/authors/Nguy%E1%BB%85n?test=1":        "/internal/import/crawler/authors/Nguy%E1%BB%85n?test=1",
		"genres":                                "/internal/import/crawler/genres?",
		"tags":                                  "/internal/import/crawler/tags?",
		"book-statuses":                         "/internal/import/crawler/book-statuses?",
		"chapters/id/123":                       "/internal/import/crawler/chapters/id/123?",
		"/rabbitmq/book":                        "/internal/import/crawler/tasks/book?",
		"/rabbitmq/chapters":                    "/internal/import/crawler/tasks/chapters?",
		"/auth/login":                           "/auth/login?",
		"/internal/import/crawler/books/id/123": "/internal/import/crawler/books/id/123?",
		s.URL + "/api/v1/rabbitmq/all-books":    "/internal/import/crawler/tasks/all-books?",
	}
	for path, want := range cases {
		response, err := c.R().Get(path)
		if err != nil {
			t.Fatal(err)
		}
		if response.String() != "/api/v1"+want {
			t.Errorf("%s: got %s want %s", path, response.String(), want)
		}
	}
	if _, err := c.R().Get("https://unexpected.example/api/v1/books"); err == nil {
		t.Fatal("external origin accepted")
	}
	if _, err := c.R().Get(s.URL + "/outside/books"); err == nil {
		t.Fatal("outside base path accepted")
	}
}
