# Kiểm thử HomeBox Lite

Báo cáo tách rõ **kết quả tự động** (chạy trên GitHub Actions) và **kết quả đo trên box thật** (người dùng điền).

## 1. Tự động (GitHub Actions, mỗi lần đẩy mã)

| Hạng mục | Cách kiểm | Trạng thái |
|---|---|---|
| Logic cấu hình v1: mặc định, giá trị hỏng, sai kiểu, giới hạn lề/màu, tối đa 4 yêu thích | `testDebugUnitTest` | xem log Actions |
| Thêm/bỏ/di chuyển/thay yêu thích | `testDebugUnitTest` | |
| Chống mở trùng khi giữ OK (cửa sổ 1,2 s) | `testDebugUnitTest` | |
| Khôi phục focus, về Karaoke khi ô đã mất | `testDebugUnitTest` | |
| Định dạng đích mở, loại trùng Activity | `testDebugUnitTest` | |
| Không dùng API mới hơn 19 mà thiếu kiểm tra phiên bản | `lintRelease` (NewApi = lỗi) | |
| Chữ ký v1 cho Android 4.4 | `apksigner verify --min-sdk-version 19` | |
| APK ≤ 5 MB, SHA-256 | bước kiểm tra trong workflow | |

## 2. Trên điện thoại (thử trước)

- [ ] Cài được, mở từ danh sách ứng dụng.
- [ ] Dọc và ngang đều hiển thị đủ, không tràn chữ.
- [ ] Chạm mở Karaoke/YouTube; giữ ô mở tuỳ chọn.
- [ ] Thêm 4 yêu thích, di chuyển trái/phải, bỏ một ô.
- [ ] Đổi màu nền, cỡ chữ, lề an toàn.
- [ ] Đặt làm Home bằng "Chỉ một lần", rồi quay lại launcher cũ.

## 3. Trên VNPT SmartBox 2 (bắt buộc trước khi dùng hằng ngày)

**Nhiệm vụ 1 — xác minh box** (mở Cài đặt → Thông tin box, chụp ảnh màn hình):

| Mục | Giá trị đọc được |
|---|---|
| Model / API / ABI | |
| RAM hệ thống nhận | |
| Bộ nhớ trống | |
| Độ phân giải, dpi | |
| Launcher gốc (tên gói) | |
| Ứng dụng YouTube có sẵn | |
| Mã phím Menu / Home / Back trên remote | |

**Chức năng:**

- [ ] Mọi nút tới được bằng remote; viền focus luôn thấy rõ.
- [ ] Back ở Home không thoát; Back đóng hộp thoại.
- [ ] Giữ OK trên Karaoke không mở 2 lần.
- [ ] Rút mạng: Home không treo, báo "Chưa kết nối mạng".
- [ ] Gỡ/cập nhật một ứng dụng yêu thích: Home không lỗi, ô báo "Đã gỡ".
- [ ] Các trang cài đặt bị ROM lược bỏ chuyển về Cài đặt chung, không crash.
- [ ] Cấu hình còn sau khi khởi động lại box.
- [ ] Đặt Home mặc định thành công / ghi lại nếu ROM chặn.
- [ ] Quay lại được launcher VNPT.

**Độ ổn định:**

- [ ] 5 lần khởi động box.
- [ ] 50 lần chuyển Karaoke ↔ YouTube: không phát chồng âm thanh, không mất hàng chờ karaoke.
- [ ] Một phiên karaoke/xem video ≥ 30 phút.

**Đo hiệu năng** (qua ADB):

| Chỉ tiêu | Mục tiêu | Lệnh gợi ý | Kết quả |
|---|---|---|---|
| PSS ổn định tại Home | ≤ 50 MiB | `adb shell dumpsys meminfo vn.homekara.launcher` | |
| CPU khi Home đứng yên | ≤ 1% | `adb shell top -n 12 -d 5 \| grep homekara.launcher` | |
| Mở lạnh tới Home | ≤ 2 s | `adb shell am start -W -n vn.homekara.launcher/.ui.HomeActivity` | |
| Trở lại Home | ≤ 500 ms | bấm Home, xem `adb logcat -s ActivityManager:I` dòng Displayed | |
