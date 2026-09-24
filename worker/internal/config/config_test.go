package config

import "testing"

func TestLoad(t *testing.T) {
	valid := map[string]string{"MYTRUYEN_BACKEND": "http://localhost:8080/api/v1", "MYTRUYEN_EMAIL": "worker@example.test", "MYTRUYEN_PASSWORD": "test", "METRUYEN_BACKEND": "https://example.test/api", "METRUYEN_EMAIL": "worker@example.test", "METRUYEN_PASSWORD": "test", "RABBITMQ_URL": "amqp://localhost", "RABBITMQ_QUEUE_CRAWL": "crawl"}
	if c, err := Load(func(k string) string { return valid[k] }); err != nil || c.Concurrency != 2 || c.BackendMode != "legacy" {
		t.Fatalf("defaults: %v", err)
	}
	for key, value := range map[string]string{"MYTRUYEN_BACKEND_MODE": "unknown", "MYTRUYEN_BACKEND": "file:///tmp", "MYTRUYEN_PASSWORD": "", "RABBITMQ_URL": "https://localhost", "RABBITMQ_QUEUE_CRAWL": "", "CRAWL_CONCURRENCY": "0", "HTTP_TIMEOUT": "-1s"} {
		t.Run(key, func(t *testing.T) {
			_, err := Load(func(k string) string {
				if k == key {
					return value
				}
				return valid[k]
			})
			if err == nil {
				t.Fatal("expected validation error")
			}
		})
	}
	c, err := Load(func(k string) string {
		if k == "CRAWL_COURUTINE_COUNT" {
			return "3"
		}
		return valid[k]
	})
	if err != nil || c.Concurrency != 3 {
		t.Fatal("legacy concurrency not supported")
	}
}

func TestImportConfigurationDoesNotRequireQueueSettings(t *testing.T) {
	values := map[string]string{
		"MYTRUYEN_BACKEND": "http://localhost:8080/api/v1", "MYTRUYEN_EMAIL": "worker@example.test", "MYTRUYEN_PASSWORD": "test",
		"METRUYEN_BACKEND": "https://example.test/api", "METRUYEN_EMAIL": "worker@example.test", "METRUYEN_PASSWORD": "test",
	}
	c, err := LoadImport(func(key string) string { return values[key] })
	if err != nil || c.RabbitURL != "" || c.Queue != "" {
		t.Fatalf("HTTP-only config: %v", err)
	}
}
