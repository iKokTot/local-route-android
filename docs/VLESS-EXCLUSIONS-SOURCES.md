# Рекомендованные исключения VLESS — 2026-10-17

Пакеты проверены по карточкам издателей в RuStore. Это проверка идентичности
приложения, НЕ доказательство, что каждое приложение всегда несовместимо с VPN.
Назначение списка — обычные банковские, государственные и повседневные сервисы,
для которых пользователь предпочитает прямой доступ. Все пункты можно изменить.

| Приложение | Android package / источник |
|---|---|
| СберБанк | [ru.sberbankmobile](https://www.rustore.ru/catalog/app/ru.sberbankmobile) |
| Т-Банк | [com.idamob.tinkoff.android](https://www.rustore.ru/catalog/app/com.idamob.tinkoff.android) |
| ВТБ | [ru.vtb24.mobilebanking.android](https://www.rustore.ru/catalog/app/ru.vtb24.mobilebanking.android) |
| Альфа-Банк | [ru.alfabank.mobile.android](https://www.rustore.ru/catalog/app/ru.alfabank.mobile.android) |
| Райффайзен | [ru.raiffeisennews](https://www.rustore.ru/catalog/app/ru.raiffeisennews) |
| Газпромбанк | [ru.gazprombank.android.mobilebank.app](https://www.rustore.ru/catalog/app/ru.gazprombank.android.mobilebank.app) |
| Халва / Совкомбанк | [ru.sovcomcard.halva.v1](https://www.rustore.ru/catalog/app/ru.sovcomcard.halva.v1) |
| Россельхозбанк | [ru.rshb.dbo](https://www.rustore.ru/catalog/app/ru.rshb.dbo) |
| ОТП Банк | [ru.otpbank.mobile](https://www.rustore.ru/catalog/app/ru.otpbank.mobile) |
| Ozon Банк | [ru.ozon.fintech.finance](https://www.rustore.ru/catalog/app/ru.ozon.fintech.finance) |
| Mir Pay | [ru.nspk.mirpay](https://www.rustore.ru/catalog/app/ru.nspk.mirpay) |
| Ozon | [ru.ozon.app.android](https://www.rustore.ru/catalog/app/ru.ozon.app.android) |
| Wildberries | [com.wildberries.ru](https://www.rustore.ru/catalog/app/com.wildberries.ru) |
| Яндекс Маркет | [ru.beru.android](https://www.rustore.ru/catalog/app/ru.beru.android) |
| Авито | [com.avito.android](https://www.rustore.ru/catalog/app/com.avito.android) |
| Госуслуги | [ru.rostel](https://www.rustore.ru/catalog/app/ru.rostel) |
| Почта России | [com.octopod.russianpost.client.android](https://www.rustore.ru/catalog/app/com.octopod.russianpost.client.android) |
| Яндекс Go | [ru.yandex.taxi](https://www.rustore.ru/catalog/app/ru.yandex.taxi) |
| 2ГИС | [ru.dublgis.dgismobile](https://www.rustore.ru/catalog/app/ru.dublgis.dgismobile) |
| РЖД Пассажирам | [ru.rzd.pass](https://www.rustore.ru/catalog/app/ru.rzd.pass) |
| Магнит | [ru.tander.magnit](https://www.rustore.ru/catalog/app/ru.tander.magnit) |
| Додо Пицца | [ru.dodopizza.app](https://www.rustore.ru/catalog/app/ru.dodopizza.app) |

Почта России: основная карточка ограничила запрос кодом 429; идентификатор
проверен по доступной странице отзывов той же карточки:
https://www.rustore.ru/catalog/app/com.octopod.russianpost.client.android/reviews.
Неудачные кандидаты пакетов не включены. В частности, исправлены прежние
ru.ozon.app → ru.ozon.app.android и ru.vtb24.mobilebank → ru.vtb24.mobilebanking.android.

Новая установка получает 22 рекомендуемых пакета. Сохранённый вручную список,
включая пустой, не сбрасывается. Чтобы добавить новые рекомендации к нему,
в редакторе есть «Добавить рекомендованные», затем явное «Сохранить».
Отсутствующие приложения не влияют на запуск; видимые установленные версии
можно отметить вручную. QUERY_ALL_PACKAGES не добавляется.

Исключения применяются только при включённом VLESS, в том числе совместно
с DPI. При DPI без VLESS пользовательских исключений нет. В совместном режиме
исключённое приложение целиком вне TUN, без DNS через сервер. Android lockdown
может блокировать такой прямой доступ. Это исключения приложений, не сайтов
в общем браузере. Сам Local Route по-прежнему исключён технически для
предотвращения петли; это не пользовательское исключение DPI.

