# HomeBox Lite

Launcher nhẹ cho **VNPT SmartBox 2** (Android 4.4.2, API 19): mở box là chọn ngay **Karaoke** hoặc **YouTube**, dùng thoải mái bằng remote. Chạy được cả trên điện thoại Android để thử trước.

- Hai ô lớn Karaoke (mở HomeKara `vn.homekara.box`) và YouTube (tự nhận SmartTube / YouTube TV / YouTube / NewPipe, hoặc tự chọn).
- Đồng hồ 24 giờ, ngày tiếng Việt, trạng thái Wi-Fi/LAN.
- Tối đa 4 ứng dụng yêu thích, thêm/bỏ/sắp xếp bằng nút bấm.
- Tất cả ứng dụng, mở nhanh cài đặt mạng, ngày giờ, âm thanh, màn hình.
- 3 màu nền tối, chữ thường/lớn, lề an toàn 0–10%.
- Trang thông tin box (model, API, ABI, RAM, bộ nhớ trống, Home mặc định) và thử mã phím remote.
- Không dùng Internet, không chạy nền, không wakelock; rời Home là nhả ảnh biểu tượng để dành RAM.

## Tải APK

Mỗi lần đẩy mã lên nhánh `main`, GitHub Actions build và đăng bản mới ở mục **Releases → latest**: tải `HomeBox-Launcher.apk` (kèm file `.sha256`).

## Cài thử trên điện thoại

1. Chép `HomeBox-Launcher.apk` vào điện thoại, mở file và cho phép "Cài ứng dụng không rõ nguồn".
2. Mở **HomeBox Lite** từ danh sách ứng dụng. Ở dạng dọc, hai ô xếp chồng; xoay ngang sẽ giống trên box.
3. Muốn thử như màn hình chính: bấm nút Home → chọn HomeBox Lite → **Chỉ một lần**.

## Cài lên box bằng USB

1. Chép APK vào USB, cắm vào box.
2. Mở trình quản lý file của box, chọn APK để cài (bật "Nguồn không xác định" nếu box hỏi).

## Cài lên box bằng ADB (qua mạng LAN)

```bash
adb connect <ip-cua-box>:5555
adb install -r HomeBox-Launcher.apk
adb shell am start -n vn.homekara.launcher/.ui.HomeActivity
```

## Đặt làm màn hình chính

1. Mở HomeBox → **Cài đặt → Đặt HomeBox làm màn hình chính**.
2. Nếu box đang dùng launcher VNPT ở chế độ "Luôn luôn", mở thông tin launcher đó và bấm **Xoá mặc định** (nút có sẵn trong hộp thoại).
3. Bấm Home trên remote → chọn **HomeBox Lite** → **Chỉ một lần**. Thử vài lần thấy ổn thì lặp lại và chọn **Luôn luôn**.

> Nếu ROM VNPT không hiện bảng chọn, hoặc nút Home trên remote luôn mở launcher VNPT, hãy ghi lại hiện tượng: đó là giới hạn của ROM, cần chốt phương án khác.

## Quay về launcher gốc VNPT

Luôn có trong **Cài đặt → Quay về launcher gốc của VNPT**:

- Cách 1: Cài đặt → Ứng dụng → HomeBox Lite → **Xoá mặc định**, rồi bấm Home và chọn launcher VNPT → **Luôn luôn**.
- Cách 2: Gỡ HomeBox Lite; box tự dùng lại launcher gốc.
- Bằng ADB: `adb uninstall vn.homekara.launcher`.

## Phím remote

| Phím | Tác dụng |
|---|---|
| Mũi tên / OK | Di chuyển, mở |
| Giữ OK hoặc Menu trên một ô | Tuỳ chọn của ô (đổi ứng dụng, di chuyển, bỏ) |
| Menu ở chỗ khác | Bật/tắt chế độ sửa yêu thích |
| Back | Đóng hộp thoại / về màn hình trước; ở Home không làm gì |
| Home | Về HomeBox (khi đã đặt mặc định) |

Giữ OK không mở trùng ứng dụng: launcher bỏ qua lần mở thứ hai trong 1,2 giây.

## Build

Yêu cầu JDK 17 và Android SDK 34.

```bash
./gradlew testDebugUnitTest lintRelease assembleRelease
```

APK ở `app/build/outputs/apk/release/app-release.apk`, ký v1 + v2 bằng `keystore/homebox.jks` (khoá cố định để bản mới cài đè được bản cũ). Muốn dùng khoá riêng, tạo `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) hoặc đặt biến môi trường `HOMEBOX_KEYSTORE*`.

Công nghệ: Kotlin 1.9.24, Android Views, minSdk 19, targetSdk 28, compileSdk 34, Gradle 8.9, AGP 8.5.2. Không có thư viện runtime ngoài Kotlin stdlib.

Xem thêm [docs/KIEM_THU.md](docs/KIEM_THU.md) cho danh sách kiểm thử trên box.
