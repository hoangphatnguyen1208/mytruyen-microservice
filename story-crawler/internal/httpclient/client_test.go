package httpclient

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestRefreshConcurrentRequests(t *testing.T) {
	var logins, refreshes atomic.Int32
	s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/api/v1/auth/login":
			logins.Add(1)
			fmt.Fprint(w, `{"data":{"access_token":"old","refresh_token":"refresh"}}`)
		case "/api/v1/auth/refresh-token":
			refreshes.Add(1)
			fmt.Fprint(w, `{"data":{"access_token":"new","refresh_token":"rotated"}}`)
		default:
			if r.Header.Get("Authorization") != "Bearer new" {
				w.WriteHeader(401)
				return
			}
			fmt.Fprint(w, `{"data":[]}`)
		}
	}))
	defer s.Close()
	c := New(context.Background(), s.URL+"/api/v1", Credentials{}, false, time.Second)
	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, err := c.R().Get("/books"); err != nil {
				t.Error(err)
			}
		}()
	}
	wg.Wait()
	if logins.Load() != 1 || refreshes.Load() != 1 {
		t.Fatalf("login=%d refresh=%d", logins.Load(), refreshes.Load())
	}
}

func TestErrorsAreNotRetried(t *testing.T) {
	for _, status := range []int{400, 403, 404, 409, 422, 429, 500, 503} {
		t.Run(fmt.Sprint(status), func(t *testing.T) {
			var calls atomic.Int32
			s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.URL.Path == "/auth/login" {
					fmt.Fprint(w, `{"data":{"access_token":"token"}}`)
					return
				}
				calls.Add(1)
				w.WriteHeader(status)
				fmt.Fprint(w, "sensitive-body")
			}))
			defer s.Close()
			c := New(context.Background(), s.URL, Credentials{}, false, time.Second)
			r, err := c.R().SetBody(map[string]string{"name": "test"}).Post("/books")
			if err == nil || r.StatusCode() != status || calls.Load() != 1 {
				t.Fatalf("status=%v err=%v calls=%d", r.StatusCode(), err, calls.Load())
			}
		})
	}
}

func TestInvalidLogin(t *testing.T) {
	for _, body := range []string{`{`, `{}`, `{"data":{"access_token":""}}`} {
		s := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, body) }))
		c := New(context.Background(), s.URL, Credentials{}, false, time.Second)
		if _, err := c.R().Get("/books"); err == nil {
			t.Errorf("accepted %s", body)
		}
		s.Close()
	}
}

func TestCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	c := New(ctx, "http://127.0.0.1:1", Credentials{}, false, time.Second)
	if _, err := c.R().Get("/books"); err == nil {
		t.Fatal("expected cancellation")
	}
}
