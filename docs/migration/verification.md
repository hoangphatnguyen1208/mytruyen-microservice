# Nhóm 1, 3, 6 — kiểm chứng và vận hành

## Đã triển khai

- Nhóm 1: Catalog ghi `search_outbox` cùng transaction tạo/sửa/xóa truyện; đổi tên tác giả tạo job cho các truyện liên quan. Publisher chờ RabbitMQ confirm và kiểm tra unroutable trước đánh dấu đã gửi. Consumer đọc trạng thái public hiện tại từ Catalog, upsert hoặc xóa Meilisearch, chờ task thành công rồi ACK. Không gửi nội dung chương/credential vào event.
- Nhóm 3: `GET /api/v1/stats/{books|chapters|chapter_content}/count` trả envelope cũ, `data` là số nguyên. Public chỉ đếm bản published, không soft-delete; chương/content phải có cả truyện lẫn chương public. `GET /api/v1/admin/catalog/stats/.../count` yêu cầu ADMIN, bao gồm draft nhưng vẫn loại soft-delete của cả parent và child. Chapter content đếm bản ghi content, không đếm số từ. Gateway đã route và phân quyền tương ứng.
- Nhóm 6: workflow `.github/workflows/migration.yml` tự chạy Java/Python tests. Job Compose kiểm tra PostgreSQL 15 + RabbitMQ + Meilisearch qua Gateway chỉ chạy khi bật thủ công `run_integration=true`, mặc định tắt. Smoke dùng token do Identity cấp, không JWT giả hay truy cập DB để bỏ qua phân quyền. Test tạo/sửa tác giả, truyện, chương, nội dung; publish/unpublish/delete; thống kê và index. Xóa khỏi Search được kiểm tra cả estimated total, không chỉ kết quả đã được Catalog lọc.

## Chạy lại trên Windows

Yêu cầu PowerShell 7, Docker Desktop Linux engine, `uv`, đủ RAM/disk để build cả stack:

```powershell
.\scripts\verify-migration.ps1
# Thêm kiểm tra upstream bên ngoài (không bắt buộc trong CI):
.\scripts\verify-migration.ps1 -CheckTopboxes
```

Script tạo tên Compose project, cổng và credential ngẫu nhiên; không đọc/sửa `.env` hiện có và không dùng PostgreSQL đang cài trên host. Chỉ volume/container của project kiểm thử vừa tạo bị xóa trong `finally`; credential tạm cũng được xóa. Nếu bị kill/mất điện, kiểm tra và dọn đúng project `mytruyen-smoke-<id>` được in đầu phiên, không dùng lệnh prune toàn máy. Smoke tạo dữ liệu nên **không chạy lên production**. CI dùng runner riêng và tự dọn volume sau test.

`scripts/smoke-migration.py` có thể chạy riêng với HTTPX, nhưng bắt buộc `ALLOW_DISPOSABLE_SMOKE=true`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`. Các biến chỉ dành cho môi trường thử. Tắt bootstrap trước triển khai thật.

## Outbox, retry và giới hạn

`search_outbox` (Flyway V4) lưu `event_id UUID PK`, `book_id BIGINT`, `occurred_at`, `published_at`; index phục vụ pending jobs. Không FK sang books để việc xóa không làm mất yêu cầu xóa index. Đây là **invalidation**, không phải snapshot/versioned domain event nên tách khỏi `catalog_outbox` của Chapter. Không cần Search SQL database hoặc bảng processed-events ở giai đoạn này: ghi Meilisearch không thể cùng transaction với bảng dedup SQL; ACK sau task và đọc lại Catalog là cơ chế replay hiện tại.

`SEARCH_SYNC_ENABLED=true` bật publisher ở Catalog (Compose đã bật; mặc định local/test tắt). Mỗi transaction relay tối đa 10 jobs; broker lỗi thì rollback, jobs vẫn pending. Crash giữa confirm và commit có thể gửi trùng. Author assignment dùng read lock, rename dùng write lock để không bỏ sót truyện vừa được gán tác giả trong lúc rename; fanout đọc IDs theo keyset từng 100, flush/detach jobs để giới hạn bộ nhớ. Tác giả có rất nhiều truyện vẫn tạo transaction dài: cần benchmark PostgreSQL trước khi tăng quy mô.

Service `search-indexer` chạy riêng với một logical writer (`x-single-active-consumer`, prefetch 1). Event lặp/đến muộn đọc lại trạng thái hiện tại, không ghi snapshot cũ. Thử tối đa 5 lần, backoff 1/2/4/8 giây; task Meilisearch mặc định có deadline 120 giây. Hết lần thử hoặc event sai schema chuyển `.dead`; chỉ ACK bản gốc sau confirm DLQ. Nếu ACK/publish/network không chắc chắn, tiến trình thoát để broker requeue; Compose restart. Đây là eventual consistency, không phải exactly-once. Khi DLQ có dữ liệu, cần người vận hành sửa lỗi và replay; không tự khẳng định index đã hội tụ.

Sau khi sửa lỗi, chạy bounded replay:

```powershell
docker compose exec search-indexer python -m app.sync.replay --max-messages 100
```

Event sai schema khiến replay dừng và vẫn còn trong DLQ để điều tra. Không purge pending/DLQ để “làm sạch” chỉ số. Import/SQL ghi trực tiếp bỏ qua service không tạo event: phải rebuild khi đã pause writers. Chapter thay đổi không phát Search job vì index hiện chỉ có `id/name/author`; nội dung/counters được hydrate từ Catalog lúc tìm kiếm.

## Theo dõi trước cutover

- DB Catalog: `SELECT count(*), min(occurred_at) FROM search_outbox WHERE published_at IS NULL;` — pending tăng liên tục hoặc quá SLO là lỗi cần xử lý.
- Rabbit management: theo dõi `mytruyen.search.sync.v1` ready/unacked/consumer và `mytruyen.search.sync.v1.dead`; DLQ > 0 phải cảnh báo. Management port chỉ bind loopback ở Compose.
- Logs: `search_outbox published/deferred`, `search_sync applied/retry/dead_letter`; không log token, URL broker có password hoặc response body. Cần nối collector/alert của môi trường triển khai; chưa có Prometheus/Grafana/alert provisioning.
- `/health` của Search chỉ là liveness. Readiness toàn pipeline phải dùng smoke/synthetic probe; chưa dùng liveness để xác nhận phụ thuộc đã sẵn sàng.
- Dùng credential broker theo vhost/quyền tối thiểu, Meili search-only key cho API, writer key cho indexer và maintenance key cho rebuild. Compose vẫn dùng master key để tiện local; **không phải cấu hình production**.
- Trước cutover vẫn phải benchmark count/sort/fanout trên PostgreSQL với kích thước dữ liệu thực, kiểm tra backup/restore/import, bảo vệ ingress bằng TLS/rate limiting, frontend contract, fault/restart recovery trên broker thật. H2/mocks không thay thế các bước này.
- Không tự dọn outbox đã publish; cần chốt retention/backup trước khi thêm cleanup job. Rabbit và Meili volumes cần backup/quy trình phục hồi; stack local không HA.

## Bằng chứng trong phiên triển khai

- Catalog: 33 tests, Gateway: 14, Identity: 16; Java `test bootJar` thành công (Identity bao gồm thay đổi cục bộ có sẵn, không nằm trong commit của nhóm này). Engagement build thành công nhưng chưa có test.
- Search: 60 tests qua, HTTPX/broker mocks. CLI smoke và PowerShell helper đã kiểm tra cú pháp; cấu hình Compose bản cuối được kiểm tra tĩnh, không chạy Docker.
- Topboxes upstream thật trả HTTP 200 JSON cho `kind=1&limit=10`; probe limit 1/100 trả 422, thông báo min 5/max 50. Catalog đã cập nhật giới hạn 5–50 và chuyển upstream validation 400/422 thành 400, không nhầm lỗi dependency 502. Đây là probe trực tiếp, chưa phải chứng minh frontend/Gateway tương thích.
- Không chạy Docker end-to-end theo yêu cầu người dùng. Docker Desktop được mở thử trước khi nhận yêu cầu dừng, sau đó đã dừng; chưa tạo container/volume kiểm thử. Có workflow/script không có nghĩa integration đã pass. Xem kết quả CI hoặc báo cáo thực thi tương lai, không suy ra từ unit tests.
