package app.minisul.productphoto

/** Готовые форматы площадок. Требования проверены по справкам площадок (ноябрь 2026). */
data class Preset(
    val id: String,
    val title: String,
    val ratio: String,
    val width: Int,
    val height: Int,
    val folder: String,
)

val PRESETS = listOf(
    // Kaspi: от 500×500 до 5000×5000 px, до 25 МБ
    Preset("kaspi", "Kaspi", "1:1", 1500, 1500, "Kaspi"),
    // Ozon: вертикальный 3:4 почти для всех категорий
    Preset("ozon", "Ozon", "3:4", 1200, 1600, "Ozon"),
    // Wildberries: тоже 3:4
    Preset("wb", "Wildberries", "3:4", 1200, 1600, "Wildberries"),
    // Универсальный квадрат: OLX, соцсети, другие площадки
    Preset("square", "Квадрат", "1:1", 2000, 2000, "Square"),
)
