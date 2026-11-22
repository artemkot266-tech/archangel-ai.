# Ключ загрузки Google Play

Google Play принимает только пакет `.aab`, подписанный вашим личным **ключом загрузки**.
Ключ создан один раз и передан владельцу файлами `archangel-upload-key.jks` и `archangel-upload-key-secrets.txt`.
**В репозитории ключа нет и не должно быть.**

Чтобы GitHub сам собирал `.aab` для Google Play, добавьте 4 секрета
(репозиторий → Settings → Secrets and variables → Actions → New repository secret):

| Name | Значение |
|---|---|
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_STORE_PASSWORD` | пароль из файла секретов |
| `UPLOAD_KEY_PASSWORD` | тот же пароль |
| `UPLOAD_KEYSTORE_B64` | длинная строка из файла секретов (целиком) |

После этого каждая сборка в Releases будет содержать `ARCHANGEL_AI-<версия>-google-play.aab`.

Отпечаток ключа (SHA-256): `C9:2F:05:A3:32:B6:5B:FA:E3:AB:AC:99:F9:7A:10:24:0B:09:B2:30:90:EE:2A:6E:E4:5A:A1:78:3D:4E:CD:71`

Если ключ потеряется — в Play Console можно запросить сброс ключа загрузки (подпись приложения хранит Google).
