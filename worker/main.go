package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/joho/godotenv"
	amqp "github.com/rabbitmq/amqp091-go"
	cron "github.com/robfig/cron/v3"

	"mytruyen-worker/internal/config"
	"mytruyen-worker/internal/httpclient"
	"mytruyen-worker/task"
)

func consumer(ctx context.Context) error {
	err := godotenv.Load()
	if err != nil {
		log.Printf("Error loading .env file")
	}

	cfg, err := config.Load(os.Getenv)
	if err != nil {
		return err
	}
	conn, err := amqp.Dial(cfg.RabbitURL)
	if err != nil {
		log.Fatal("Failed to connect to RabbitMQ:", err)
	}

	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		log.Fatal("Failed to open a channel:", err)
	}
	defer ch.Close()

	q, err := ch.QueueDeclare(
		cfg.Queue,
		true,  // durable
		false, // delete when unused
		false, // exclusive
		false, // no-wait
		nil,   // arguments
	)
	if err != nil {
		log.Fatal("Failed to declare queue:", err)
	}

	couroutineCount := cfg.Concurrency
	log.Printf("Crawl couroutine count: %d", couroutineCount)
	err = ch.Qos(couroutineCount, 0, false)
	if err != nil {
		log.Fatal("Failed to set QoS:", err)
	}

	msgs, err := ch.Consume(
		q.Name,            // queue
		"mytruyen-worker", // consumer
		false,             // auto-ack
		false,             // exclusive
		false,             // no-local
		false,             // no-wait
		nil,               // args
	)
	if err != nil {
		log.Fatal("Failed to register a consumer:", err)
	}

	MeTruyencvClient := httpclient.New(ctx, cfg.Source.URL, httpclient.Credentials{
		Email: cfg.Source.Email, Password: cfg.Source.Password,
	}, true, cfg.HTTPTimeout)
	MyTruyenClient := httpclient.New(ctx, cfg.Backend.URL, httpclient.Credentials{
		Email: cfg.Backend.Email, Password: cfg.Backend.Password,
	}, false, cfg.HTTPTimeout)
	if cfg.BackendMode == "compat" {
		httpclient.UseWorkerCompatibilityEndpoints(MyTruyenClient, cfg.Backend.URL)
	}

	c := cron.New(cron.WithChain(cron.SkipIfStillRunning(cron.DefaultLogger)))
	_, err = c.AddFunc("*/1 * * * *", func() {
		log.Println("Running scheduled task: CheckNewChaptersHandler")
		success := task.CheckNewChaptersHandler(MeTruyencvClient, MyTruyenClient)
		if success {
			log.Println("Scheduled task CheckNewChaptersHandler completed successfully.")
		} else {
			log.Println("Scheduled task CheckNewChaptersHandler failed.")
		}
	})
	if err != nil {
		log.Fatalf("Failed to schedule CheckNewChaptersHandler: %v", err)
	}
	c.Start()
	defer c.Stop()

	closeChan := make(chan *amqp.Error, 1)
	conn.NotifyClose(closeChan)

	var wg sync.WaitGroup

	for i := 0; i < couroutineCount; i++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			for d := range msgs {
				var crawlRequest struct {
					Type   string `json:"type"`
					BookID int    `json:"book_id"`
				}
				err := json.Unmarshal(d.Body, &crawlRequest)
				if err != nil {
					log.Printf("Error parsing message: %v", err)
					_ = d.Nack(false, false)
					continue
				}

				log.Printf("[Worker %d] Received a crawl request: Type=%s, BookID=%d",
					workerID,
					crawlRequest.Type,
					crawlRequest.BookID,
				)

				var success bool
				switch crawlRequest.Type {
				case "crawl_genres":
					success = task.GenresHandler(MeTruyencvClient, MyTruyenClient, workerID)
					if !success {
						log.Printf("[Worker %d] Failed to crawl genres", workerID)
					} else {
						log.Printf("[Worker %d] Successfully crawled genres", workerID)
					}

				case "crawl_tags":
					success = task.TagsHandler(MeTruyencvClient, MyTruyenClient, workerID)
					if !success {
						log.Printf("[Worker %d] Failed to crawl tags", workerID)
					} else {
						log.Printf("[Worker %d] Successfully crawled tags", workerID)
					}

				case "crawl_book_statuses":
					success = task.BookStatusHandler(MeTruyencvClient, MyTruyenClient, workerID)
					if !success {
						log.Printf("[Worker %d] Failed to crawl book statuses", workerID)
					} else {
						log.Printf("[Worker %d] Successfully crawled book statuses", workerID)
					}

				case "crawl_all_books":
					success = task.AllBookHandler(MeTruyencvClient, MyTruyenClient, q.Name, workerID)
					if !success {
						log.Printf("[Worker %d] Failed to crawl all books", workerID)
					} else {
						log.Printf("[Worker %d] Successfully enqueued all books crawling", workerID)
					}

				case "crawl_book":
					success = task.BookHandler(MeTruyencvClient, MyTruyenClient, crawlRequest.BookID, workerID)
					if !success {
						log.Printf("[Worker %d] Failed to crawl book ID %d", workerID, crawlRequest.BookID)
					} else {
						log.Printf("[Worker %d] Successfully crawled book ID %d", workerID, crawlRequest.BookID)
					}

				case "crawl_chapters":
					success = task.ChaptersHandler(MeTruyencvClient, MyTruyenClient, crawlRequest.BookID, workerID)
					if !success {
						log.Printf("[Worker %d] Failed to crawl chapters", workerID)
					} else {
						log.Printf("[Worker %d] Successfully crawled chapters", workerID)
					}

				// case "check_new_chapters":
				// 	success = task.CheckNewChaptersHandler(MeTruyencvClient, MyTruyenClient, ch, q.Name)
				// 	if !success {
				// 		log.Printf("[Worker %d] Failed to check new chapters", workerID)
				// 	} else {
				// 		log.Printf("[Worker %d] Successfully checked new chapters", workerID)
				// 	}

				// case "refresh_mytruyen_token":
				// 	token, err := getMyTruyenAuthToken(MyTruyenClient)
				// 	if err != nil {
				// 		log.Printf("[Worker %d] Failed to refresh MyTruyen auth token: %v", workerID, err)
				// 		success = false
				// 	} else {
				// 		log.Printf("[Worker %d] Successfully refreshed MyTruyen auth token.", workerID)
				// 		MyTruyenClient.SetAuthToken(token)
				// 		success = true
				// 	}

				// case "add_all_books_to_meili":
				// 	success = task.MeiliHandler(MyTruyencvClient, MeiliClient)
				// 	if !success {
				// 		log.Printf("[Worker %d] Failed to add all books to Meilisearch", workerID)
				// 	} else {
				// 		log.Printf("[Worker %d] Successfully added all books to Meilisearch", workerID)
				// 	}

				default:
					log.Printf("[Worker %d] Unknown crawl request type: %s", workerID, crawlRequest.Type)
					success = false
				}

				if success {
					if err := d.Ack(false); err != nil {
						log.Printf("[Worker %d] Ack failed: %v", workerID, err)
					}
				} else {
					if err := d.Nack(false, true); err != nil {
						log.Printf("[Worker %d] Nack failed: %v", workerID, err)
					}
				}
			}
		}(i)
	}
	select {
	case errClose := <-closeChan:
		log.Printf("RabbitMQ closed: %v", errClose)
		wg.Wait()
		return fmt.Errorf("rabbitmq disconnected")

	case <-ctx.Done():
		log.Println("Shutdown requested")

		ch.Cancel("mytruyen-worker", false)

		wg.Wait()

		ch.Close()

		return nil
	}
}

func main() {
	ctx, cancel := context.WithCancel(context.Background())

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		sig := <-sigChan
		log.Printf("Received signal: %v", sig)

		cancel()
	}()

	for {
		select {
		case <-ctx.Done():
			log.Println("Exiting...")
			return
		default:
		}

		err := consumer(ctx)

		if ctx.Err() != nil {
			log.Println("Shutdown complete")
			return
		}

		log.Printf("Consumer stopped: %v", err)

		time.Sleep(5 * time.Second)
	}
}
