# EXA Android — безопасность

Модель угроз: защищаем ключи VLESS-профилей, подписки и трафик пользователя от
других приложений, случайных утечек в логи/бэкапы и подмены нативных библиотек.
Локальный владелец устройства и root-доступ считаются доверенными.

## Что сделано

| Область | Мера |
|---|---|
| Разрешения | Только INTERNET, ACCESS_NETWORK_STATE, CAMERA, foreground service, notifications, boot. `QUERY_ALL_PACKAGES` удалён — список приложений через `<queries>` MAIN/LAUNCHER. |
| Бэкапы | `android:allowBackup="false"`, ключи и профили не попадают в cloud backup. |
| Сеть | `usesCleartextTraffic="false"` + `network_security_config` (только системные CA, без cleartext). |
| Подписки | Только `https://`; HTTP отклоняется с понятной ошибкой. До 4 редиректов, только HTTPS. Лимит 2 МБ, таймауты 15/20 с, gzip/deflate. |
| Импорт | Лимит длины ссылки/QR/интента (100 КБ), лимит строки VLESS 8 КБ, лимит base64 512 КБ. |
| Парсер VLESS | Валидация схемы, UUID-ish id, host (ASCII, без пробелов/слэшей), порта, network/security. Nested `extra` санитизируется: только плоские строки/числа/boolean, ≤20 ключей. |
| Deep link | Обрабатывается только `vless://`, с лимитом длины. Ничего не подключается автоматически. |
| Компоненты | `ExaVpnService` — `exported=false` + `BIND_VPN_SERVICE`; `ExaTileService` — `exported=true` + `BIND_QUICK_SETTINGS_TILE` (требование системы); `BootReceiver` — `exported=false`; `MainActivity` экспортирована только для launcher и `vless://`. |
| Логи | В release `VpnLog` не пишет в logcat (`BuildConfig.DEBUG`). В логах нет UUID/ключей — только события core/VPN. |
| Скриншоты | Опция «Защита от скриншотов» ставит `FLAG_SECURE` (снимки и превью в «недавних»). По умолчанию выключена. |
| QR | Камера используется только для декодирования; кадры не сохраняются и не отправляются. Декодер — офлайн ZXing, без Play Services / Firebase / ML Kit. |
| Трафик | Приложение исключено из TUN; xray подключается напрямую. Per-app allow/deny не смешиваются (нельзя случайно завернуть собственный upstream в VPN). |
| Секреты в репозитории | `keystore/`, `keystore.properties`, `local.properties`, AAR и APK в `.gitignore`. В git нет ключей и реальных конфигов. |
| Supply chain | `libv2ray.aar` и `hev-socks5-tunnel.aar` скачиваются по закреплённым версиям и проверяются по SHA-256. `geoip.dat`/`geosite.dat` также проверяются по SHA-256. |
| Сборка | Release не `debuggable`, подписан отдельным ключом; APK не содержит ML Kit/Firebase/datatransport. |

## Проверено вручную

- `aapt2 dump permissions` — нет `QUERY_ALL_PACKAGES` и сторонних опасных разрешений.
- `aapt2 dump xmltree` — exported-компоненты соответствуют таблице выше.
- `unzip -l` — отсутствуют Firebase/ML Kit/datatransport классы.
- Реальный туннель: IP до подключения `91.79.44.209` (DME), после — `185.230.190.19` (HEL), DNS/HTTP работают.
- Подписка HTTPS с base64-профилями: загрузка, группировка, обновление.
- QR: генерация и шаринг профиля, офлайн-сканер камерой.
- Песочница правил, сценарии, JSON-превью.
- Плитка Quick Settings и действие «Отключить» в уведомлении.

## Остаточные риски

- Вредоносная или скомпрометированная подписка может добавить произвольные
  VLESS-хосты. Пользователь сам выбирает источник подписки.
- `vless://` deep link может добавить профиль из другого приложения. Профиль не
  активируется и не подключается без действия пользователя.
- На устройстве с root/отладкой возможен доступ к SharedPreferences и к процессу.
- Локальный ключ подписи хранится у владельца; компрометация ключа позволяет
  выпустить обновление, которое установится поверх.
- `geoip/geosite` — сторонние базы; они проверяются по SHA-256 на этапе сборки,
  но не подписаны.

## Сообщить об уязвимости

Открой приватный security advisory в GitHub:
<https://github.com/GretskyOleg/exa-android/security/advisories/new>
