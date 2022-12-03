package util

import net.dv8tion.jda.api.utils.FileUpload
import util.Util.capitaliseAll
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.imageio.ImageIO
import kotlin.math.max

object ImageUtil {
    private val MINECRAFT_FONT: Font
    private val HTTP_CLIENT = HttpClient.newHttpClient()
    private val STATS = listOf("Damage Dealt", "Damage Taken", "Kills", "Deaths")

    init {
        val stream = ImageUtil::class.java.classLoader.getResourceAsStream("assets/minecraft.ttf")
        MINECRAFT_FONT = Font.createFont(Font.TRUETYPE_FONT, stream)
    }

    fun playerStatsImage(username: String, scores: Map<String, Any>, imageName: String): FileUpload {
        val sizeX = 384
        val sizeY = 384
        val image = BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        graphics.color = Color(0x2C2F33)
        graphics.fillRect(0, 0, sizeX, sizeY)

        // Font
        graphics.font = MINECRAFT_FONT.deriveFont(26.0F)
        val height = graphics.fontMetrics.height - 4
        val deltaValues = height + 5
        val deltaStats = height + 24

        val xPos = 10
        var yPos = 88
        STATS.forEach { stat ->
            graphics.color = Color(0xBFBFBF)
            graphics.drawString("$stat:", xPos, yPos)
            yPos += deltaValues
            val score = scores[stat.lowercase()].toString()
            graphics.color = Color(0xFF5555)
            graphics.drawString(score, xPos, yPos)
            yPos += deltaStats
        }

        // Player
        val imageSize = 280
        val uri = URI.create(EmbedUtil.getPlayerBody(username, imageSize))
        val request = HttpRequest.newBuilder(uri).build()
        val response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() == 200) {
            val body = ImageIO.read(ByteArrayInputStream(response.body()))
            graphics.drawImage(body, 200, 60, null)
        }

        // Name
        graphics.font = MINECRAFT_FONT.deriveFont(48.0F)
        graphics.color = Color(0x5555FF)
        graphics.drawString(username, 10, 40)

        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return FileUpload.fromData(output.toByteArray(), imageName)
    }

    fun scoreboardImage(stat: String, scores: List<Map<String, Any>>, imageName: String): FileUpload {
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
        return FileUpload.fromData(output.toByteArray(), imageName)
    }
}