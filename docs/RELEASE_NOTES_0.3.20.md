# Veilark Windows 0.3.20

Сохранён привычный интерфейс, шрифты приведены к единому виду. Добавление новой
подписки больше не меняет выбранную. Улучшена обработка длинных сообщений ядра
и исправлена нумерация будущих обновлений.

Обновление поверх 0.3.19 проверено на изолированной Windows с сохранением данных.
Ядра и настройки маршрутизации в этом выпуске не менялись.

Это preview-релиз без Authenticode. OTA-манифест подписан прежним ключом Ed25519;
размер и SHA-256 установщика проверяются клиентом. Предупреждение Windows о
неизвестном издателе всё ещё возможно. Обнаружение угрозы антивирусом не следует
игнорировать или обходить отключением защиты.

---

The familiar interface is retained with consistent typography. Adding a new
subscription no longer changes the existing selection. Processing of oversized
core messages and version numbering for future updates have been corrected.

An upgrade from 0.3.19 was tested on isolated Windows with application data
preserved. VPN engines and routing settings are unchanged in this release.

This preview has no Authenticode signature. The OTA manifest uses the existing
Ed25519 key; the client verifies installer size and SHA-256. Windows may still
show an unknown-publisher warning. Do not ignore malware detections or disable
security software to install the application.
