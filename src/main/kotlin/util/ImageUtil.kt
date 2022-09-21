package util

import okhttp3.Request
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.imageio.ImageIO

object ImageUtil {
    private val MINECRAFT_FONT: Font
    private val HTTP_CLIENT = HttpClient.newHttpClient()
    private val STATS = listOf("Damage Dealt", "Damage Taken", "Kills", "Deaths")

    init {
        val url = ImageUtil::class.java.classLoader.getResource("assets/minecraft.ttf")!!
        MINECRAFT_FONT = Font.createFont(Font.TRUETYPE_FONT, File(url.toURI()))
    }

    fun playerStatsImage(username: String, scores: Map<String, Any>) {
        val sizeX = 384
        val sizeY = 384
        val image = BufferedImage(sizeX, sizeY, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        // Background
        graphics.color = Color(0x2C2F33)
        graphics.fillRect(0, 0, sizeX, sizeY)

        // Font
        graphics.font = MINECRAFT_FONT.deriveFont(26.0F)
        val height = graphics.fontMetrics.height
        val deltaValues = height + 2
        val deltaStats = height + 15

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

        ImageIO.write(image, "png", File("image.png"))
    }
}