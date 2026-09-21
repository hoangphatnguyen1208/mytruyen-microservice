# Kế hoạch chuyển `mytruyen-be` sang microservices

## 1. Kết luận kiến trúc

Không nên tách mỗi bảng hoặc mỗi nhóm endpoint thành một service. Với quy mô code hiện tại, kiến trúc đích hợp lý là 4 service bắt buộc và 1 service tùy chọn:

| Service | Công nghệ | Sở hữu dữ liệu | Ghi chú |
|---|---|---|---|
| `api-gateway` | Java 17+, Spring Cloud Gateway | Không có DB | Routing, CORS, rate limit, xác thực JWT ở biên; service bên trong vẫn tự kiểm tra quyền |
| `identity-service` | Java 17+, Spring Boot | user, role, credential, refresh session | Gộp `auth-service` và `user-service` hiện tại thành một bounded context; không truyền password hash qua HTTP |
| `catalog-service` | Java 17+, Spring Boot | book, author, genre, tag, book_status, chapter, chapter_content | CRUD và transaction cốt lõi, schema ổn định, phù hợp Java/JPA; chưa nên tách book và chapter thành hai service |
| `search-service` | Python, FastAPI | search index/projection, không sở hữu dữ liệu catalog | Meilisearch trước; semantic/audio search sau. Nhận event catalog để cập nhật index |
| `ingestion-worker` | Python | job state, crawl checkpoint nếu cần | Crawl nguồn ngoài, chuẩn hóa dữ liệu, gửi command/event; Python phù hợp scraping, NLP/AI |
| `engagement-service` (phase sau) | Java, Spring Boot | comment, review, rating, bookmark | Chỉ tách khi các tính năng này được triển khai thật và có tải riêng |

Không tạo riêng `stat-service` ở giai đoạn đầu. Các count như chapter count, view count, review count là projection được cập nhật bằng event hoặc nằm trong service sở hữu nghiệp vụ.

## 2. Vì sao phần nào chuyển sang Java

### Chuyển sang Java ngay

- Identity: authentication, authorization, user/role, refresh-token rotation. Đây là phần cần type safety, security filter chain, validation và transaction rõ ràng.
- API Gateway: routing, JWT verification, CORS, rate limiting, correlation ID.
- Catalog/chapter CRUD: nghiệp vụ transaction ổn định, quan hệ dữ liệu chặt, cần migration schema và contract lâu dài.

### Giữ Python

- Search, embedding, reranking, transcription và xử lý audio.
- Crawler/ingestion worker và tích hợp nguồn truyện ngoài.
- Các job thử nghiệm ML/AI hoặc data pipeline.

Java không tự động làm endpoint CRUD nhanh hơn đủ để bù chi phí rewrite. Lý do chuyển catalog sang Java ở đây là chuẩn hóa core domain, vận hành và contract, không phải vì FastAPI không đáp ứng hiệu năng. Nếu đội hiện tại mạnh Python hơn Java, có thể giữ `catalog-service` bằng FastAPI trong phase 1-3 rồi rewrite sau khi cutover ổn định.

## 3. Đánh giá code hiện tại

### Monolith `mytruyen-be`

- Tất cả model nằm trong một schema và có foreign key xuyên domain user/book/chapter/comment/review.
- Auth phát JWT đối xứng và lưu refresh token cùng DB nghiệp vụ.
- Book write gọi Meilisearch đồng bộ sau khi commit DB, nên có thể lệch index khi Meilisearch lỗi.
- ARQ/Redis và RabbitMQ cùng làm hàng đợi crawl, tạo hai cơ chế cho một bài toán.
- Search hybrid/audio/YouTube hiện trả 503 nhưng giữ một khối code chết lớn.
- Comment/review có model nhưng chưa có API; README mô tả nhiều chức năng chưa khớp code.
- `latest_chapter` là UUID trong khi khóa chính `Chapter.id` là integer; cần sửa trước khi chuyển dữ liệu.

### Repo `mytruyen-microservice` hiện tại

- Hướng tách Gateway + Auth + User + Book đã có nền tảng tốt và RSA JWT tốt hơn monolith.
- Không nên giữ Auth và User là hai service riêng: login đang gọi đồng bộ User service và nhận `hashed_password` qua internal API. Credential phải nằm cùng transaction và trust boundary với authentication.
- `book-service` vẫn còn `RefreshToken`, `Comment`, `Review` và code search/crawl; ranh giới chưa sạch.
- Book DB đã bỏ foreign key tới User là đúng hướng; chỉ lưu `creator_id` như external reference.
- User service dùng `hibernate.ddl-auto=update`; production phải chuyển sang Flyway và `validate`.
- Gateway thêm identity header nhưng downstream Python vẫn tự đọc JWT; cần chọn một contract nhất quán. Khuyến nghị downstream verify JWT và không tin header từ mạng ngoài; gateway phải xóa header giả mạo.
- Test ngày 2026-09-21: Auth, User và Gateway pass; Book service pass 39/73, fail 34 test. Chưa được dùng làm baseline cutover.

## 4. Data ownership và giao tiếp

### Quy tắc bắt buộc

1. Mỗi service có database/schema và migration riêng; service khác không query trực tiếp.
2. Không có foreign key xuyên service. Dùng UUID/ID tham chiếu mềm, ví dụ `creator_id`, `user_id`.
3. REST chỉ dùng cho query/command cần phản hồi tức thời. RabbitMQ dùng cho propagation và công việc nền.
4. Mọi event phải có `event_id`, `event_type`, `schema_version`, `occurred_at`, `correlation_id` và payload.
5. Producer dùng transactional outbox; consumer idempotent và lưu processed event ID.
6. Không gọi Meilisearch trong transaction/request ghi catalog. Catalog phát `BookCreated`, `BookUpdated`, `BookDeleted`; Search consume để cập nhật index.

### Event tối thiểu

- `UserCreated`, `UserUpdated`, `UserDeactivated`
- `BookCreated`, `BookUpdated`, `BookPublished`, `BookDeleted`
- `ChapterCreated`, `ChapterUpdated`, `ChapterPublished`, `ChapterDeleted`
- Sau này: `CommentCreated`, `ReviewUpserted`, `BookmarkChanged`, `ChapterViewed`

RabbitMQ là message broker duy nhất cho integration event. Redis chỉ dùng cache, rate limit hoặc job nội bộ đặc thù; bỏ ARQ crawl trùng chức năng.

## 5. API và security contract

- Giữ public path `/api/v1` trong lúc migration để frontend không phải đổi đồng thời.
- Chuẩn hóa một response envelope duy nhất hoặc bỏ envelope; không để test/client vừa mong raw list vừa nhận `{data: ...}`.
- JWT dùng RS256/ES256 với `sub`, `roles`, `iss`, `aud`, `iat`, `exp`, `jti`. Chỉ Identity giữ private key.
- Refresh token là opaque, lưu dạng hash, rotation theo token family, phát hiện reuse và có revoke/logout.
- Endpoint nội bộ không được public qua Gateway. Nếu vẫn cần service-to-service HTTP, dùng mTLS hoặc short-lived service credential; API key tĩnh chỉ là giải pháp chuyển tiếp.
- Gateway thực hiện coarse-grained policy; từng service vẫn kiểm tra role/ownership cho write endpoint.
- Thêm request/correlation ID xuyên HTTP và event.

## 6. Lộ trình migration theo Strangler Pattern

### Phase 0 - Khóa baseline (2-4 ngày)

- Chốt OpenAPI hiện tại và snapshot response thực tế của frontend đang dùng.
- Sửa hoặc loại test lỗi thời để monolith và repo đích có contract test đáng tin cậy.
- Sửa kiểu `latest_chapter`; quyết định integer ID hay UUID và dùng nhất quán.
- Lập inventory dữ liệu production: row count, dung lượng chapter content, orphan FK, duplicate slug/email.
- Thêm health/readiness, structured log, correlation ID và metrics cơ bản.

**Gate:** toàn bộ critical API có contract test; không còn quyết định ID/schema chưa chốt.

### Phase 1 - Hoàn thiện platform và Identity (1 tuần)

- Giữ Gateway Java hiện tại, bổ sung rate limit, timeout, retry có giới hạn và circuit breaker chỉ cho safe request.
- Gộp code Auth + User thành `identity-service` Java; Identity sở hữu password hash và refresh session.
- Dùng Flyway; cấm `ddl-auto=update` ở production.
- Viết migration `user` cũ sang Identity DB, map role `user/admin` sang role mới.
- Chạy dual-read/shadow verification, sau đó route `/auth/**` và `/users/**` qua Gateway sang Identity.

**Gate:** register/login/refresh/logout/user CRUD tương thích; không endpoint nào trả password hash; rollback route được bằng config.

### Phase 2 - Catalog service (1-2 tuần)

- Tạo Spring Boot `catalog-service` từ model đã làm sạch; đưa book, author, genre, tag, status, chapter và content vào cùng service.
- Dùng Flyway, optimistic locking (`version`) và index cho slug, `(book_id, chapter_index)`, published/sort fields.
- Giữ `creator_id` là external UUID, không gọi Identity cho mỗi read. Nếu cần hiển thị tên user, dùng snapshot/event hoặc BFF composition.
- Import dữ liệu bằng migration repeatable/idempotent; bảo toàn ID để URL cũ không đổi.
- Chạy shadow read và so sánh payload giữa monolith và service mới.

**Gate:** parity cho toàn bộ endpoint đang được frontend dùng; checksum/count khớp; p95 và error rate đạt mục tiêu.

### Phase 3 - Search và event backbone (1 tuần)

- Tách `search-service` Python khỏi Book; chỉ sở hữu search API và index.
- Thêm outbox publisher trong Catalog, RabbitMQ exchange + DLQ + retry policy.
- Rebuild Meilisearch từ Catalog snapshot, sau đó consume event tăng dần.
- Bật text search trước. Xóa code hybrid/audio chết hoặc đặt sau feature flag rồi mới khôi phục.

**Gate:** tạo/sửa/xóa book phản ánh lên search theo SLA; replay event không tạo sai dữ liệu; có quy trình rebuild index.

### Phase 4 - Ingestion worker (3-5 ngày)

- Tách crawler thành Python worker; một queue/command model duy nhất.
- Worker không ghi trực tiếp Catalog DB; gọi internal command API hoặc phát validated import command.
- Thêm idempotency key theo source/book/chapter, checkpoint, retry exponential và DLQ.
- Bỏ endpoint ARQ/RabbitMQ trùng nhau; admin endpoint chỉ enqueue command.

**Gate:** crawl retry an toàn, không tạo duplicate book/chapter, quan sát được trạng thái job.

### Phase 5 - Engagement khi có nhu cầu (1-2 tuần)

- Chỉ triển khai khi comment/review/bookmark/rating thực sự nằm trong product scope.
- Java service sở hữu các bảng này; tham chiếu mềm `user_id`, `book_id`, `chapter_id`.
- Cập nhật aggregate count bằng event/projection, không dùng distributed transaction.

### Phase 6 - Cutover và tháo monolith (3-5 ngày + thời gian theo dõi)

- Route theo từng prefix qua Gateway, canary 5% -> 25% -> 100% nếu metric đạt ngưỡng.
- Trong thời gian ổn định, monolith read-only cho domain đã chuyển; không duy trì dual-write kéo dài.
- Sau ít nhất một chu kỳ backup/restore đã kiểm chứng, dừng route cũ và archive code.

## 7. Chiến lược chuyển dữ liệu

1. Backup và kiểm thử restore trước mọi migration.
2. Tạo schema đích bằng Flyway/Alembic, không dùng ORM auto-create.
3. Bulk copy theo thứ tự ownership, giữ nguyên ID.
4. Validate row count, nullability, unique key, orphan reference và checksum theo batch.
5. Đồng bộ delta bằng outbox/CDC hoặc cửa sổ write freeze ngắn. Với quy mô nhỏ, write freeze ngắn đơn giản và an toàn hơn dual-write.
6. Smoke test trên DB copy, sau đó canary route.
7. Rollback bằng route Gateway; không rollback schema phá hủy dữ liệu. Migration DB luôn forward-fix.

## 8. CI/CD và vận hành tối thiểu

- CI theo path/service: unit test, integration test với Testcontainers, OpenAPI compatibility, migration test và image scan.
- Có end-to-end test qua Gateway cho login -> tạo book -> tạo chapter -> search.
- Image pin version; không dùng `latest` cho production.
- Readiness phải kiểm tra dependency thiết yếu; liveness không phụ thuộc dịch vụ ngoài.
- OpenTelemetry trace, Prometheus metrics, centralized JSON log; dashboard theo RED metrics.
- Secret nằm trong secret manager/CI secret, không trong `.env` production.
- Docker Compose dùng local/dev; production dùng nền tảng orchestrator phù hợp, chưa cần Kubernetes nếu chỉ có một VPS và tải nhỏ.

## 9. Thứ tự backlog đề xuất cho repo hiện tại

### P0 - trước khi viết thêm service

- Sửa 34 test fail của Book hoặc cập nhật test theo contract được chốt.
- Gộp Auth/User, bỏ API trả `hashed_password`.
- Thêm Flyway cho Identity; đổi Hibernate sang `validate`.
- Xóa model auth khỏi Book DB và tách comment/review ra khỏi catalog migration nếu chưa dùng.
- Chốt lại route chapter và response envelope.

### P1 - để cutover được

- Viết Catalog Java + migration dữ liệu.
- Outbox + Search consumer.
- Contract/e2e test qua Gateway.
- Timeout, retry, DLQ, health, logging và metrics.

### P2 - sau cutover

- Crawler worker độc lập.
- Semantic/audio search theo feature flag.
- Engagement service.
- Autoscaling/tuning chỉ dựa trên metric thực tế.

## 10. Definition of Done toàn chương trình

- Không service nào truy cập DB của service khác.
- Không synchronous chain bắt buộc dài hơn Gateway -> một domain service cho request phổ biến.
- Tất cả migration schema có version và chạy được trên DB rỗng lẫn DB copy production.
- Contract test, integration test và luồng e2e quan trọng đều pass.
- Search/event consumer idempotent, có DLQ và replay procedure.
- Có dashboard, alert, backup/restore drill và runbook rollback.
- Frontend dùng Gateway duy nhất và không cần biết địa chỉ service nội bộ.
- Monolith không còn nhận traffic trước khi bị archive.
