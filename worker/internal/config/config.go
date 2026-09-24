package config

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"time"
)

type API struct{ URL, Email, Password string }
type Config struct {
	Backend, Source  API
	RabbitURL, Queue string
	Concurrency      int
	HTTPTimeout      time.Duration
	BackendMode      string
}

// LoadImport validates only HTTP settings; the one-book import does not use RabbitMQ.
func LoadImport(get func(string) string) (Config, error) {
	c := Config{
		Backend:     API{get("MYTRUYEN_BACKEND"), get("MYTRUYEN_EMAIL"), get("MYTRUYEN_PASSWORD")},
		Source:      API{get("METRUYEN_BACKEND"), get("METRUYEN_EMAIL"), get("METRUYEN_PASSWORD")},
		HTTPTimeout: 30 * time.Second,
	}
	for _, entry := range []struct {
		name string
		api  API
	}{{"MYTRUYEN", c.Backend}, {"METRUYEN", c.Source}} {
		u, err := url.Parse(entry.api.URL)
		if err != nil || u.Host == "" || (u.Scheme != "https" && u.Scheme != "http") || u.User != nil || u.RawQuery != "" || u.Fragment != "" {
			return c, fmt.Errorf("%s_BACKEND must be an HTTP(S) base URL without credentials, query or fragment", entry.name)
		}
		if strings.TrimSpace(entry.api.Email) == "" || entry.api.Password == "" {
			return c, fmt.Errorf("%s credentials required", entry.name)
		}
	}
	if value := get("HTTP_TIMEOUT"); value != "" {
		var err error
		c.HTTPTimeout, err = time.ParseDuration(value)
		if err != nil || c.HTTPTimeout <= 0 || c.HTTPTimeout > 5*time.Minute {
			return c, fmt.Errorf("HTTP_TIMEOUT must be positive and at most 5m")
		}
	}
	return c, nil
}

func Load(get func(string) string) (Config, error) {
	c, err := LoadImport(get)
	if err != nil {
		return c, err
	}
	c.RabbitURL = get("RABBITMQ_URL")
	c.Queue = get("RABBITMQ_QUEUE_CRAWL")
	c.Concurrency = 2
	c.BackendMode = get("MYTRUYEN_BACKEND_MODE")
	if c.BackendMode == "" {
		c.BackendMode = "legacy"
	}
	if c.BackendMode != "legacy" && c.BackendMode != "compat" {
		return c, fmt.Errorf("MYTRUYEN_BACKEND_MODE must be legacy or compat")
	}
	u, err := url.Parse(c.RabbitURL)
	if err != nil || u.Host == "" || (u.Scheme != "amqp" && u.Scheme != "amqps") {
		return c, fmt.Errorf("RABBITMQ_URL must be an AMQP(S) URL")
	}
	if strings.TrimSpace(c.Queue) == "" {
		return c, fmt.Errorf("RABBITMQ_QUEUE_CRAWL required")
	}
	value := get("CRAWL_CONCURRENCY")
	if value == "" {
		value = get("CRAWL_COURUTINE_COUNT")
	}
	if value != "" {
		c.Concurrency, err = strconv.Atoi(value)
		if err != nil || c.Concurrency < 1 || c.Concurrency > 32 {
			return c, fmt.Errorf("crawl concurrency must be between 1 and 32")
		}
	}
	return c, nil
}
