package org.localroute.android.core

/** User messages never contain exception text, URLs, credentials or response bodies. */
object ActionErrors {
    fun message(method: String, error: Exception): String = when(error) {
        is android.content.ActivityNotFoundException -> "Не найдено приложение для открытия ссылки. Установите Telegram или браузер."
        is javax.net.ssl.SSLException -> "Не удалось проверить защищённое соединение. Проверьте дату устройства и адрес подписки."
        is java.net.UnknownHostException -> "Не найден адрес сервера. Проверьте интернет и адрес подписки."
        is java.net.SocketTimeoutException -> "Сервер не ответил вовремя. Попробуйте другую сеть или повторите позже."
        is SecurityException -> "Android не разрешил действие. Проверьте разрешение VPN в системных настройках."
        is IllegalArgumentException -> when(method) {
            "importVless", "saveVless" -> "Не удалось прочитать подключение. Нужна полная VLESS-ссылка или HTTPS-подписка с поддерживаемыми VLESS-серверами (TCP, WS, gRPC, HTTPUpgrade). Другие протоколы не поддерживаются."
            "saveTelegram" -> "Укажите свободный порт от 1024 до 65535, кроме 1080 и 1081."
            else -> "Проверьте введённые настройки. Данные не удалены."
        }
        is java.io.IOException -> "Не удалось загрузить данные. Проверьте сеть и доступность источника."
        else -> when(method) {
            "importVless" -> "Источник не вернул подходящий список. Проверьте ссылку, срок подписки и наличие VLESS-серверов."
            "saveBypassApps" -> "Не удалось сохранить исключения. Предыдущий список сохранён."
            "startVless", "toggleVless", "startVerified", "autoSelect", "toggleEngine" -> "Подключение не запустилось. Проверьте разрешение Android VPN, настройки сервера и журнал в настройках."
            else -> "Действие не выполнено. Проверьте состояние подключения и повторите."
        }
    }
}
