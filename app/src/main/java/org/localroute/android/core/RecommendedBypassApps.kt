package org.localroute.android.core

/** Package IDs verified against publisher listings on RuStore, 2026-10-17. */
object RecommendedBypassApps {
    val apps = linkedMapOf(
        "ru.sberbankmobile" to "СберБанк", "com.idamob.tinkoff.android" to "Т-Банк",
        "ru.vtb24.mobilebanking.android" to "ВТБ", "ru.alfabank.mobile.android" to "Альфа-Банк",
        "ru.raiffeisennews" to "Райффайзен", "ru.gazprombank.android.mobilebank.app" to "Газпромбанк",
        "ru.sovcomcard.halva.v1" to "Халва / Совкомбанк", "ru.rshb.dbo" to "Россельхозбанк",
        "ru.otpbank.mobile" to "ОТП Банк", "ru.ozon.fintech.finance" to "Ozon Банк",
        "ru.nspk.mirpay" to "Mir Pay", "ru.ozon.app.android" to "Ozon",
        "com.wildberries.ru" to "Wildberries", "ru.beru.android" to "Яндекс Маркет",
        "com.avito.android" to "Авито", "ru.rostel" to "Госуслуги",
        "com.octopod.russianpost.client.android" to "Почта России", "ru.yandex.taxi" to "Яндекс Go",
        "ru.dublgis.dgismobile" to "2ГИС", "ru.rzd.pass" to "РЖД Пассажирам",
        "ru.tander.magnit" to "Магнит", "ru.dodopizza.app" to "Додо Пицца"
    )
    val packages: Set<String> get() = apps.keys.toSet()
    fun applies(serverEnabled: Boolean): Boolean = serverEnabled
}
