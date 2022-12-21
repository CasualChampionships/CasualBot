package util

import net.dv8tion.jda.api.utils.FileUpload
import util.Util.capitalise
import util.Util.capitaliseAll
import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.DecimalFormat
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.max

object ImageUtil {
    private val STAT_CACHE = WeakHashMap<String, FileUpload>()
    private val TOTAL_STAT_CACHE = WeakHashMap<String, FileUpload>()
    private val ADVANCEMENT_CACHE = WeakHashMap<String, FileUpload>()
    private val SCOREBOARD_CACHE = WeakHashMap<String, FileUpload>()

    private val ITEM_CACHE = WeakHashMap<String, Image>()

    private val MINECRAFT_FONT: Font
    private val ADVANCEMENT_BANNER: BufferedImage
    private val HTTP_CLIENT = HttpClient.newHttpClient()
    private val DECIMAL_FORMAT = DecimalFormat("0.#")

    init {
        var stream = ImageUtil::class.java.classLoader.getResourceAsStream("assets/minecraft.ttf")
        MINECRAFT_FONT = Font.createFont(Font.TRUETYPE_FONT, stream)

        stream = ImageUtil::class.java.classLoader.getResourceAsStream("assets/AdvancementBanner.png")
        ADVANCEMENT_BANNER = ImageIO.read(stream)
    }

    fun playerStatsImage(username: String, scores: Map<String, Any>, imageName: String, total: Boolean): FileUpload {
        val cache = if (total) TOTAL_STAT_CACHE else STAT_CACHE
        cache[username]?.let { return it }

        val size = 80 * (scores.size) + 90
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Background
        graphics.color = Color(0x2C2F33)
        graphics.fillRect(0, 0, size, size)

        // Player
        val uri = URI.create(EmbedUtil.getPlayerBody(username, (size * 0.8).toInt()))
        val request = HttpRequest.newBuilder(uri).build()
        val response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() == 200) {
            val body = ImageIO.read(ByteArrayInputStream(response.body()))
            graphics.drawImage(body, (size * 0.42).toInt(), (size * 0.15).toInt(), null)
        }

        // Font
        graphics.font = MINECRAFT_FONT.deriveFont(26.0F)
        val deltaValues = 30
        val deltaStats = 50

        val xPos = 10
        var yPos = 110

        for ((id, value) in scores) {
            val name = id.split("_").joinToString(" ") { it.capitalise() }
            graphics.color = Color(0xBFBFBF)
            graphics.drawString("$name:", xPos, yPos)
            yPos += deltaValues
            graphics.color = Color(0xFF5555)

            val score = if ((value as Double).isNaN()) "None" else DECIMAL_FORMAT.format(value)
            graphics.drawString(score, xPos, yPos)
            yPos += deltaStats
        }

        // Name
        graphics.font = MINECRAFT_FONT.deriveFont(72.0F)
        graphics.color = Color(0x5555FF)
        graphics.drawString(username, 20, 60)

        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return FileUpload.fromData(output.toByteArray(), imageName).also { cache[username] = it }
    }

    fun playerAdvancementsImage(username: String, advancements: Map<String, String>, imageName: String): FileUpload {
        ADVANCEMENT_CACHE[username]?.let { return it }

        val sizeX = 384
        val sizeY = (ADVANCEMENT_BANNER.height + 10) * advancements.size + 10
        val image = BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Background
        graphics.color = Color(0x2C2F33)
        graphics.fillRect(0, 0, sizeX, sizeY)

        val delta = ADVANCEMENT_BANNER.height + 10

        val bannerX = 10
        var bannerY = 10

        graphics.color = Color(0xBFBFBF)

        for ((id, value) in advancements) {
            graphics.font = MINECRAFT_FONT.deriveFont(32.0F)
            val name = id.replace("uhc/", "").split("_").joinToString(" ") { it.capitalise() }
            graphics.drawImage(ADVANCEMENT_BANNER, bannerX, bannerY, null)

            val width = graphics.fontMetrics.stringWidth(name)
            var textShift = 50
            if (width > 240) {
                val multiplier = 240.0F / width
                graphics.font = MINECRAFT_FONT.deriveFont(32.0F * multiplier)
                textShift -= (3.5 / multiplier).toInt()
            }
            graphics.drawString(name, bannerX + 100, bannerY + textShift)

            val item = ITEM_CACHE.computeIfAbsent(value) {
                val uri = URI.create("https://mc.nerothe.com/img/1.19.2/${value}.png")
                val request = HttpRequest.newBuilder(uri).build()
                val response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray())
                if (response.statusCode() == 200) {
                    ImageIO.read(ByteArrayInputStream(response.body()))
                } else null
            }

            if (item != null) {
                graphics.drawImage(item, bannerX + 26, bannerY + 14, null)
            }

            bannerY += delta
        }

        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return FileUpload.fromData(output.toByteArray(), imageName).also { ADVANCEMENT_CACHE[username] = it }
    }

    fun scoreboardImage(stat: String, scores: List<Map<String, Any>>, imageName: String): FileUpload {
        SCOREBOARD_CACHE[stat]?.let { return it }

        val dummy = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val dummyGraphics = dummy.createGraphics()
        dummyGraphics.font = MINECRAFT_FONT.deriveFont(20.0F)
        val header = stat.capitaliseAll()
        val fontMetrics = dummyGraphics.fontMetrics

        val width = fontMetrics.stringWidth(header)
        val height = fontMetrics.height - 4

        val names = scores.map { it["name"].toString() }
        val values = scores.map { it[stat].toString() }

        val namesSizeWidth = names.maxOf { fontMetrics.stringWidth(it) }
        val valuesSizeWidth = values.maxOf { fontMetrics.stringWidth(it) }

        val spacing = 2
        val padding = 5

        val totalWidth = max(width, namesSizeWidth + valuesSizeWidth) + padding * 3
        val totalHeight = height * (scores.size + 1) + padding * 3 + spacing

        dummyGraphics.dispose()

        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        graphics.color = Color(0x2C2F33)
        graphics.fillRect(0, 0, totalWidth, totalHeight)

        graphics.font = MINECRAFT_FONT.deriveFont(20.0F)

        val namePos = padding + spacing
        var yPos = height * 2 + padding * 2
        for (i in names.indices) {
            graphics.color = Color(0xBFBFBF)
            graphics.drawString(names[i], namePos, yPos)
            graphics.color = Color(0xFF5555)
            val s = values[i]
            graphics.drawString(s, totalWidth - fontMetrics.stringWidth(s), yPos)
            yPos += height
        }

        graphics.color = Color(0x5555FF)
        graphics.drawString(header, (totalWidth - width) / 2, padding + height)

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return FileUpload.fromData(output.toByteArray(), imageName).also { SCOREBOARD_CACHE[stat] = it }
    }

    fun clearCaches() {
        STAT_CACHE.clear()
        TOTAL_STAT_CACHE.clear()
        ADVANCEMENT_CACHE.clear()
        SCOREBOARD_CACHE.clear()
    }
}
