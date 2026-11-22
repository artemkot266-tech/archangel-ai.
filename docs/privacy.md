# Политика конфиденциальности ARCHANGEL AI

Действует с 22 ноября 2026 г.

ARCHANGEL AI («Архангел») — голосовой помощник для Android. Мы не собираем ваши данные на своих серверах:
у приложения нет собственного сервера, аналитики и рекламы. Всё, что вы сохраняете, хранится на вашем телефоне.

## Что хранится на телефоне
- Переписка с помощником, заметки, напоминания, факты, которые вы попросили запомнить, настройки.
- Ваши API-ключи (ИИ, ElevenLabs, Binance, Home Assistant) — в зашифрованном виде (Android Keystore).
- Удалить всё можно в приложении (очистка истории и памяти) или удалив приложение.

## Что и куда отправляется
Данные уходят только в сервисы, которые вы сами подключили своими ключами, и только когда это нужно для команды:

| Сервис | Что получает | Зачем |
|---|---|---|
| Выбранный вами ИИ (Google Gemini, Groq, OpenRouter, DeepSeek, Anthropic Claude или OpenAI) | текст запроса, недавняя история диалога, фото, если вы его приложили | ответ помощника |
| Распознавание речи Android (обычно Google) | ваш голос во время прослушивания | превращение речи в текст |
| ElevenLabs (если включён этот голос) | текст ответа | озвучка |
| Open-Meteo | приблизительные координаты | прогноз погоды |
| Binance (если подключили ключи) | ключ API, параметры ордеров | цены, баланс, сделки — только после вашего подтверждения |
| Ваш Home Assistant (если подключили) | команды для устройств | управление умным домом |

Каждый из этих сервисов обрабатывает данные по своей политике конфиденциальности.

## Разрешения
- **Микрофон** — голосовые команды; в фоновом режиме, только если вы его включили (видно постоянное уведомление).
- **Контакты** — найти номер, когда вы просите позвонить или написать. Контакты никуда не отправляются.
- **Звонки** — набрать номер после вашего подтверждения.
- **Календарь** — прочитать события для сводки дня. Никуда не отправляется.
- **Приблизительное местоположение** — погода.
- **Уведомления, будильники, автозапуск** — напоминания и утренняя сводка.

## Дети
Приложение не предназначено для детей младше 13 лет.

## Связь
Вопросы и запросы на удаление: https://github.com/artemkot266-tech/archangel-ai./issues

---

# Privacy Policy (English)

ARCHANGEL AI is a voice assistant for Android. It has no server of its own, no analytics and no ads.
Chats, notes, reminders, memory and settings are stored only on your device; API keys are encrypted with the
Android Keystore. Data is sent only to services you connect with your own keys, and only to fulfil a command:
your chosen AI provider (request text, recent chat history, attached photo), the Android speech recognizer
(your voice while listening), ElevenLabs (reply text, if enabled), Open-Meteo (approximate location for weather),
Binance (API key and order parameters, trades only after your confirmation) and your Home Assistant (device commands).
Contacts and calendar are read on the device only and are never uploaded. The app is not directed at children
under 13. Contact: https://github.com/artemkot266-tech/archangel-ai./issues
