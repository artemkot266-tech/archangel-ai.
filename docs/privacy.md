# Политика конфиденциальности ARCHANGEL AI

Действует с 22 ноября 2026 г.

ARCHANGEL AI («Архангел») — голосовой помощник для Android. У приложения нет собственного сервера,
аналитики и рекламы. Всё, что вы сохраняете, хранится на вашем телефоне.

## Что хранится на телефоне
- Переписка с помощником, заметки, напоминания, факты, которые вы попросили запомнить, настройки.
- Дневник сделок, песочница (виртуальные сделки), планы регулярных покупок, ценовые оповещения.
- Ваши API-ключи (ИИ, ElevenLabs, биржи, Home Assistant) — в зашифрованном виде (Android Keystore).
- Офлайн-модель распознавания речи (Vosk), если вы её скачали.
- Удалить всё можно в приложении (очистка истории, памяти, удаление модели) или удалив приложение.

## Микрофон
- **Обычный режим:** микрофон включается, когда вы касаетесь сферы или кнопки микрофона, или нажимаете кнопку питания
  (если Архангел назначен помощником).
- **Режим ожидания (по вашему выбору):** Архангел слушает микрофон, чтобы услышать своё имя, — в том числе в фоне,
  если вы включили «Слушать в фоне». Этот звук обрабатывается **только на телефоне** офлайн-моделью Vosk,
  не записывается и никуда не отправляется. Пока режим включён, видно постоянное уведомление с кнопкой «Выключить».
- После имени ваша команда распознаётся службой распознавания речи Android (обычно Google) и в виде текста
  передаётся выбранному вами ИИ.

## Что и куда отправляется
Данные уходят только в сервисы, нужные для вашей команды, и только когда это нужно:

| Сервис | Что получает | Зачем |
|---|---|---|
| Выбранный вами ИИ (Google Gemini, Groq, OpenRouter, DeepSeek, Anthropic Claude или OpenAI) | текст запроса, недавняя история диалога, фото, если вы его приложили | ответ помощника |
| Распознавание речи Android (обычно Google) | ваш голос во время прослушивания команды | превращение речи в текст |
| ElevenLabs (если включён этот голос) | текст ответа | озвучка |
| Open-Meteo | приблизительные координаты | прогноз погоды |
| Binance (публичные данные) | название монеты | графики и цены (без ключей) |
| Binance, Bybit, OKX, WhiteBIT (если подключили ключи) | ключ API, параметры ордеров | баланс и сделки — только после вашего подтверждения |
| alternative.me | — (обычный запрос) | индекс страха и жадности |
| ForkLog, Cointelegraph | — (обычный запрос) | новости |
| alphacephei.com | — (однократная загрузка) | скачивание офлайн-модели Vosk |
| Ваш Home Assistant (если подключили) | команды для устройств | управление умным домом |

Каждый из этих сервисов обрабатывает данные по своей политике конфиденциальности.

## Разрешения
- **Микрофон** — голосовые команды и (по вашему выбору) отклик на имя в фоне.
- **Контакты** — найти номер, когда вы просите позвонить или написать. Контакты не покидают телефон.
- **Звонки** — набрать номер после вашего подтверждения.
- **Календарь** — прочитать события для утренней сводки. Не покидает телефон.
- **Приблизительное местоположение** — погода.
- **Уведомления, будильники, автозапуск** — напоминания, сводка, сигналы рынка, покупки по плану.
- **Поверх других окон** — открывать YouTube, музыку и звонилку по голосовой команде, когда Архангел в фоне.
- **Вибрация** — короткий отклик, когда Архангел услышал имя.

## Финансы
Архангел не хранит деньги и не является биржей или кошельком. Сделки выполняются на бирже по вашим ключам
и только после вашего подтверждения. Используйте ключи без права вывода средств. Информация о рынке не является
финансовым советом. В версии из Google Play торговля на реальные деньги отключена (только демо-счёт и песочница).

## Дети
Приложение не предназначено для лиц младше 18 лет.

## Связь
Вопросы и запросы на удаление: https://github.com/artemkot266-tech/archangel-ai./issues

---

# Privacy Policy (English)

ARCHANGEL AI is a voice assistant for Android. It has no server of its own, no analytics and no ads. Chats, notes,
reminders, memory, trading journal, paper-trading data and settings are stored only on your device; API keys are
encrypted with the Android Keystore.

**Microphone.** The microphone is used when you tap to talk. If you enable standby (optionally in the background),
the app listens for its wake word using the on-device Vosk model; this audio is processed only on the device, is not
recorded and is not sent anywhere, and a persistent notification with a "Turn off" button is shown. After the wake
word, your command is transcribed by the Android speech recognizer (usually Google) and sent as text to the AI
provider you selected.

**Data sent to third parties** only to fulfil your commands: your chosen AI provider (request text, recent chat
history, attached photo); the Android speech recognizer (your voice while a command is being heard); ElevenLabs (reply
text, if enabled); Open-Meteo (approximate location for weather); Binance public market data; Binance, Bybit, OKX or
WhiteBIT (API key and order parameters, only after your confirmation, if you connected keys); alternative.me (Fear &
Greed index); ForkLog/Cointelegraph (news); alphacephei.com (one-time download of the offline speech model); your Home
Assistant (device commands). Contacts and calendar are read on the device only.

The app does not hold funds and is not an exchange or wallet. Market information is not financial advice. The Google
Play version does not support real-money trading. The app is not directed at users under 18.
Contact: https://github.com/artemkot266-tech/archangel-ai./issues
