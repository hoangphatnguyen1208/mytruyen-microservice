package httpclient

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/go-resty/resty/v2"
)

type Credentials struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type tokens struct {
	Access  string `json:"access_token"`
	Refresh string `json:"refresh_token"`
	Source  string `json:"token"`
}

// StatusError deliberately excludes response bodies, URLs and credentials.
type StatusError struct{ Code int }

func (e *StatusError) Error() string { return fmt.Sprintf("HTTP status %d", e.Code) }

// New creates a client without contacting either API. Authentication is lazy.
// Only a rejected authentication request is replayed, never a timeout or 5xx write.
func New(ctx context.Context, baseURL string, credentials Credentials, source bool, timeout time.Duration) *resty.Client {
	auth := resty.New().SetBaseURL(strings.TrimRight(baseURL, "/")).SetTimeout(timeout)
	auth.SetRedirectPolicy(resty.NoRedirectPolicy())
	var mu sync.Mutex
	var current tokens
	login := func(refresh bool) error {
		path := "/auth/login"
		var body any = credentials
		if refresh {
			path = "/auth/refresh-token"
			body = map[string]string{"refresh_token": current.Refresh}
		} else if source {
			body = map[string]any{"email": credentials.Email, "password": credentials.Password, "remember": 1, "device_name": "mytruyen-worker"}
		}
		response, err := auth.R().SetContext(ctx).SetBody(body).Post(path)
		if err != nil {
			return fmt.Errorf("authentication request failed")
		}
		if response.StatusCode() < 200 || response.StatusCode() >= 300 {
			return &StatusError{response.StatusCode()}
		}
		var envelope struct {
			Data tokens `json:"data"`
		}
		if json.Unmarshal(response.Body(), &envelope) != nil {
			return fmt.Errorf("invalid authentication response")
		}
		next := envelope.Data
		if source {
			next.Access = next.Source
		}
		if strings.TrimSpace(next.Access) == "" {
			return fmt.Errorf("authentication response missing access token")
		}
		if refresh && next.Refresh == "" {
			return fmt.Errorf("refresh response missing refresh token")
		}
		current = next
		return nil
	}
	client := resty.New().SetBaseURL(strings.TrimRight(baseURL, "/")).SetTimeout(timeout)
	client.SetRedirectPolicy(resty.NoRedirectPolicy())
	client.OnBeforeRequest(func(_ *resty.Client, r *resty.Request) error {
		r.SetContext(ctx)
		mu.Lock()
		defer mu.Unlock()
		if current.Access == "" {
			if err := login(false); err != nil {
				return err
			}
		}
		r.SetAuthToken(current.Access)
		return nil
	})
	client.OnAfterResponse(func(_ *resty.Client, r *resty.Response) error {
		if r.StatusCode() < 200 || r.StatusCode() >= 300 {
			return &StatusError{r.StatusCode()}
		}
		return nil
	})
	client.SetRetryCount(1).SetRetryWaitTime(time.Millisecond).SetRetryMaxWaitTime(time.Millisecond)
	client.AddRetryCondition(func(r *resty.Response, _ error) bool {
		if r == nil || r.StatusCode() != http.StatusUnauthorized || r.Request.Attempt > 1 {
			return false
		}
		mu.Lock()
		defer mu.Unlock()
		if r.Request.Token != current.Access {
			return true
		}
		return login(!source && current.Refresh != "") == nil
	})
	return client
}
