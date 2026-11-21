package com.archangel.ai.core

/** Состояние «ядра» — от него зависят цвет и движение сферы. */
enum class CoreState(val label: String, val hint: String) {
    IDLE("В ожидании", "Коснитесь сферы и говорите"),
    LISTENING("Слушаю", "Говорите — коснитесь ещё раз, чтобы закончить"),
    THINKING("Думаю", "Формирую ответ"),
    SPEAKING("Отвечаю", "Коснитесь, чтобы прервать"),
}
