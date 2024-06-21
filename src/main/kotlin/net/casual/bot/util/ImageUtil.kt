package net.casual.bot.util

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.casual.bot.CasualBot
import net.casual.stat.FormattedStat
import net.casual.stat.ResolvedPlayerStat
import net.casual.util.Named
import net.dv8tion.jda.api.utils.FileUpload
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max

// TODO: Cache images
object ImageUtil {
    private val MINECRAFT_FONT: Font
    private val ADVANCEMENT_BANNER: BufferedImage

    private val BORDER_COLOR = Color(0x17191C)
    private val BACKGROUND_COLOR = Color(0x313338)
    private val TEXT_COLOR = Color(0x5555FF)
    private val SCORE_COLOR = Color(0xFF5555)
    private val TITLE_COLOR = Color(0xBFBFBF)

    init {
        var stream = ImageUtil::class.java.classLoader.getResourceAsStream("assets/minecraft.ttf")
        MINECRAFT_FONT = Font.createFont(Font.TRUETYPE_FONT, stream)

        stream = ImageUtil::class.java.classLoader.getResourceAsStream("assets/AdvancementBanner.png")
        ADVANCEMENT_BANNER = ImageIO.read(stream)
    }

    // TODO:
    //  Put the stats in columns when there are a large number of stats
    suspend fun playerStatsImage(username: String, type: String, stats: List<Named<FormattedStat>>): BufferedImage {
        val statCount = max(stats.size, 5)
        val scoreFontSize = 180.0F / statCount
        val titleFontSize = scoreFontSize * 1.33F

        val title = "$type Stats for $username"

        val dummy = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val dummyGraphics = dummy.createGraphics()
        dummyGraphics.font = MINECRAFT_FONT.deriveFont(titleFontSize)
        val titleFontMetrics = dummyGraphics.fontMetrics
        dummyGraphics.font = MINECRAFT_FONT.deriveFont(scoreFontSize)
        val scoreFontMetrics = dummyGraphics.fontMetrics
        dummyGraphics.dispose()

        val titleWidth = titleFontMetrics.stringWidth(title)
        val titleHeight = (titleFontMetrics.height * 0.8).toInt()
        val scoreHeight = (scoreFontMetrics.height * 0.8).toInt()

        val padding = (0.4 * titleFontSize).toInt()
        val thirdPadding = padding / 3
        val quarterPadding = padding / 4

        val scoresMaxWidth = stats.maxOf { 
            max(scoreFontMetrics.stringWidth(it.name), scoreFontMetrics.stringWidth(it.value.formatted())) 
        }
        val scoresHeight = statCount * (scoreHeight * 2) + (statCount - 1) * padding

        val response = CasualBot.httpClient.get(EmbedUtil.getPlayerBody(username, scoresHeight)) {
            headers {
                set(HttpHeaders.UserAgent, "CasualBot/1.0")
            }
        }
        var player: BufferedImage? = null
        if (response.status == HttpStatusCode.OK) {
            val body = withContext(Dispatchers.IO) {
                response.bodyAsChannel().toInputStream().use {
                    ImageIO.read(it)
                }
            }
            player = body
        }

        val totalWidth = max(scoresMaxWidth + (player?.width ?: 0) + padding, titleWidth) + padding * 2
        val totalHeight = titleHeight + scoresHeight + padding * 3

        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Border
        graphics.color = BORDER_COLOR
        graphics.fillRect(0, 0, totalWidth, totalHeight)

        // Background
        graphics.color = BACKGROUND_COLOR
        graphics.fillRect(thirdPadding, thirdPadding, totalWidth - 2 * thirdPadding, totalHeight - 2 * thirdPadding)

        var yPos = (titleHeight * 2) + padding + quarterPadding

        if (player != null) {
            val remaining = totalWidth - scoresMaxWidth - player.width
            graphics.drawImage(player, scoresMaxWidth + remaining / 2, yPos - scoreHeight, null)
        }

        // Font
        graphics.font = MINECRAFT_FONT.deriveFont(scoreFontSize)

        for ((name, stat) in stats) {
            graphics.color = TEXT_COLOR
            graphics.drawString("$name:", padding, yPos)
            yPos += scoreHeight
            graphics.color = SCORE_COLOR

            graphics.drawString(stat.formatted(), padding, yPos)
            yPos += padding + scoreHeight
        }

        // Name
        graphics.font = MINECRAFT_FONT.deriveFont(titleFontSize)
        graphics.color = TITLE_COLOR
        graphics.drawString(title, (totalWidth - titleWidth) / 2, padding + titleHeight - thirdPadding)

        graphics.dispose()

        return image
    }

    fun scoreboardImage(title: String, stats: List<ResolvedPlayerStat>): BufferedImage {
        val titleFontSize = 40.0F
        val scoreFontSize = 30.0F

        val dummy = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val dummyGraphics = dummy.createGraphics()
        dummyGraphics.font = MINECRAFT_FONT.deriveFont(titleFontSize)
        val titleFontMetrics = dummyGraphics.fontMetrics
        dummyGraphics.font = MINECRAFT_FONT.deriveFont(scoreFontSize)
        val scoreFontMetrics = dummyGraphics.fontMetrics
        dummyGraphics.dispose()

        val titleWidth = titleFontMetrics.stringWidth(title)
        val titleHeight = (titleFontMetrics.height * 0.8).toInt()
        val scoreHeight = (scoreFontMetrics.height * 0.8).toInt()

        val namesMaxWidth = stats.maxOf { scoreFontMetrics.stringWidth(it.name) }
        val valuesMaxWidth = stats.maxOf { scoreFontMetrics.stringWidth(it.stat.formatted()) }

        val padding = (0.4 * titleFontSize).toInt()
        val thirdPadding = padding / 3
        val quarterPadding = padding / 4

        val totalWidth = max(titleWidth, namesMaxWidth + valuesMaxWidth + padding) + padding * 2
        val totalHeight = titleHeight + scoreHeight * stats.size + padding * 3


        val image = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Border
        graphics.color = BORDER_COLOR
        graphics.fillRect(0, 0, totalWidth, totalHeight)

        // Background
        graphics.color = BACKGROUND_COLOR
        graphics.fillRect(thirdPadding, thirdPadding, totalWidth - 2 * thirdPadding, totalHeight - 2 * thirdPadding)

        val titlePadding = ((totalWidth - titleWidth) * 0.4).toInt()

        graphics.color = BACKGROUND_COLOR
        graphics.fillRoundRect(
            titlePadding,
            padding + titleHeight + quarterPadding,
            totalWidth - 2 * titlePadding,
            thirdPadding,
            2, 2
        )

        graphics.font = MINECRAFT_FONT.deriveFont(scoreFontSize)

        var yPos = (titleHeight * 2) + padding + quarterPadding
        for (resolved in stats) {
            graphics.color = TEXT_COLOR
            graphics.drawString(resolved.name, padding, yPos)
            graphics.color = SCORE_COLOR
            val stat = resolved.stat.formatted()
            graphics.drawString(stat, totalWidth - padding - scoreFontMetrics.stringWidth(stat), yPos)
            yPos += scoreHeight
        }

        graphics.font = MINECRAFT_FONT.deriveFont(titleFontSize)
        graphics.color = TITLE_COLOR
        graphics.drawString(title, (totalWidth - titleWidth) / 2, padding + titleHeight - thirdPadding)

        return image
    }

    fun playerAdvancementsImage(username: String, advancements: Map<String, String>, imageName: String): FileUpload {
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

        for ((title, value) in advancements) {
            graphics.font = MINECRAFT_FONT.deriveFont(32.0F)
            graphics.drawImage(ADVANCEMENT_BANNER, bannerX, bannerY, null)

            val width = graphics.fontMetrics.stringWidth(title)
            var textShift = 50
            if (width > 240) {
                val multiplier = 240.0F / width
                graphics.font = MINECRAFT_FONT.deriveFont(32.0F * multiplier)
                textShift -= (3.5 / multiplier).toInt()
            }
            graphics.drawString(title, bannerX + 100, bannerY + textShift)

            // val item = ITEM_CACHE.computeIfAbsent(value) {
            //     val uri = URI.create()
            //     val request = HttpRequest.newBuilder(uri).build()
            //     val response = HTTP_CLIENT.get("https://mc.nerothe.com/img/1.20.1/${value}.png")
            //     if (response.statusCode() == 200) {
            //         ImageIO.read(ByteArrayInputStream(response.body()))
            //     } else null
            // }
            //
            // if (item != null) {
            //     graphics.drawImage(item, bannerX + 26, bannerY + 14, null)
            // }

            bannerY += delta
        }

        graphics.dispose()

        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return FileUpload.fromData(output.toByteArray(), imageName)
    }
}
