package net.yogstation.yogbot.listeners.commands

import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageEditSpec
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import net.yogstation.yogbot.util.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

@Component
class ReviewCommand(
	discordConfig: DiscordConfig,
	permissions: PermissionsManager,
	private val channelsConfig: DiscordChannelsConfig,
	private val database: DatabaseManager
) : PermissionsCommand(discordConfig, permissions) {
	override val requiredPermissions = "note"

	override fun doCommand(event: MessageCreateEvent): Mono<*> {
		val args = event.message.content.split(" ").toTypedArray()
		if (args.size < 2) return DiscordUtil.reply(
			event,
			"Usage is: `${discordConfig.commandPrefix}review <ckey> [strict]`"
		)
		val ckey = StringUtils.ckeyIze(args[1])
		val strict = event.message
			.channelId
			.asLong() == channelsConfig.channelAdmin || args.size >= 3 && args[2].equals("strict", ignoreCase = true)
		return event.message.channel.flatMap { messageChannel: MessageChannel ->
			ReviewWorker(ckey, messageChannel, strict, database).start()
			Mono.empty<Any>()
		}
	}

	override val description = "Check the ckey inputted for related ckeys."
	override val name = "review"

	internal class ReviewWorker(
		private val initialCkey: String,
		private val channel: MessageChannel,
		private val strict: Boolean,
		private val database: DatabaseManager
	) : Thread() {
		private val startTime: LocalDateTime = LocalDateTime.now()
		private val messages = ArrayList<Message>()
		private val ckeysQueue: MutableList<String> = ArrayList()
		private val ckeysChecked: MutableMap<String, String> = LinkedHashMap()
		private var updateIdx = 0

		private val logger: Logger = LoggerFactory.getLogger(javaClass)

		override fun run() {
			ckeysQueue.add(initialCkey)
			ckeysChecked[initialCkey] = "Original Ckey"
			checkBannu(initialCkey)
			sendUpdate(false)
			var limiter = if (strict) 2 else 30
			while (ckeysQueue.size > 0) {
				limiter--
				if (limiter <= 0) break
				val thisCkey = ckeysQueue.removeAt(0)
				val thisCids: MutableSet<String> = HashSet()
				val thisIps: MutableSet<String> = HashSet()
				val relatedKeys: MutableMap<String, RelatedInfo> = HashMap()
				val tableName = database.prefix("connection_log")
				try {
					getRelatedKeys(tableName, thisCkey, thisCids, thisIps, relatedKeys)
				} catch (e: SQLException) {
					logger.error("Error getting connections", e)
					channel.createMessage("An error has occurred")
					return
				}
				for (relatedKey in relatedKeys.keys) {
					processRelatedKey(relatedKeys, relatedKey, thisCkey)
				}
				sendUpdate(false)
			}
			sendUpdate(true)
		}

		private fun processRelatedKey(
			relatedKeys: MutableMap<String, RelatedInfo>,
			relatedKey: String,
			thisCkey: String
		) {
			val info = relatedKeys[relatedKey] ?: return
			val builder = StringBuilder("Related to ").append(thisCkey).append(" via ")
			if (info.cids.size > 0) {
				builder.append("cid")
				if (info.cids.size != 1) builder.append("s")
				builder.append(" ")
				builder.append(java.lang.String.join(", ", info.cids))
			}
			if (info.cids.size > 0 && info.ips.size > 0) builder.append(" ")
			if (info.ips.size > 0) {
				builder.append("ip")
				if (info.ips.size != 1) builder.append("s")
				builder.append(" ")
				builder.append(java.lang.String.join(", ", info.ips))
			}
			ckeysChecked[relatedKey] = builder.toString()
			ckeysQueue.add(relatedKey)
			checkBannu(relatedKey)
		}

		private fun getRelatedKeys(
			tableName: String,
			thisCkey: String,
			thisCids: MutableSet<String>,
			thisIps: MutableSet<String>,
			relatedKeys: MutableMap<String, RelatedInfo>
		) {
			database.byondDbConnection.use { connection ->
				connection.prepareStatement(
					"SELECT computerid, ip FROM $tableName WHERE ckey = ?;"
				).use { connectionsStmt ->
					connection.prepareStatement(
						"SELECT ckey,ip,computerid FROM `$tableName` WHERE " +
							"computerid IN (SELECT computerid FROM `$tableName` WHERE ckey = ?) OR " +
							"ip IN (SELECT ip FROM `$tableName` WHERE ckey = ?)"
					).use { relatedStmt ->
						queryRelatedKeys(connectionsStmt, thisCkey, thisCids, thisIps, relatedStmt, relatedKeys)
					}
				}
			}
		}

		@Suppress("LongParameterList")
		private fun queryRelatedKeys(
			connectionsStmt: PreparedStatement,
			thisCkey: String,
			thisCids: MutableSet<String>,
			thisIps: MutableSet<String>,
			relatedStmt: PreparedStatement,
			relatedKeys: MutableMap<String, RelatedInfo>
		) {
			connectionsStmt.setString(1, thisCkey)
			val connectionsResult: ResultSet = connectionsStmt.executeQuery()
			while (connectionsResult.next()) {
				thisCids.add(connectionsResult.getString("computerid"))
				thisIps.add(connectionsResult.getString("ip"))
			}
			connectionsResult.close()
			relatedStmt.setString(1, thisCkey)
			relatedStmt.setString(2, thisCkey)
			val relatedResult: ResultSet = relatedStmt.executeQuery()
			while (relatedResult.next()) {
				val relatedCkey: String = relatedResult.getString("ckey")
				val relatedIp: String = relatedResult.getString("ip")
				val relatedCid: String = relatedResult.getString("computerid")
				if (ckeysChecked.containsKey(relatedCkey)) continue
				if (!relatedKeys.containsKey(relatedCkey)) {
					relatedKeys[relatedCkey] = RelatedInfo()
				}
				val relatedInfo = relatedKeys[relatedCkey]!!
				if (thisIps.contains(relatedIp)) relatedInfo.ips.add(relatedIp)
				if (thisCids.contains(relatedCid)) relatedInfo.cids.add(relatedCid)
			}
		}

		private fun checkBannu(victim: String) {
			try {
				database.byondDbConnection.use { connection ->
					connection.prepareStatement(
						"SELECT 1 FROM `${database.prefix("ban")}` " +
							"WHERE ckey = ? AND " +
							"role IN ('Server') AND " +
							"unbanned_datetime IS NULL AND " +
							"(expiration_time IS NULL OR expiration_time > NOW())"
					).use { bannuStmt ->
						testBannu(bannuStmt, victim)
					}
				}
			} catch (e: SQLException) {
				logger.error("Failed to check bannu of $victim", e)
			}
		}

		private fun testBannu(bannuStmt: PreparedStatement, victim: String) {
			bannuStmt.setString(1, victim)
			val resultSet: ResultSet = bannuStmt.executeQuery()
			if (resultSet.next()) {
				ckeysChecked[victim] = "${ckeysChecked[victim]} (BANNED)"
			}
			resultSet.close()
		}

		private fun sendUpdate(complete: Boolean) {
			val checkedKeys = ckeysChecked.keys.stream().toList()
			val count = ceil(checkedKeys.size / 23.0).toInt()
			for (i in 0 until count) {
				val embedBuilder: EmbedCreateSpec.Builder = EmbedCreateSpec.builder()
				embedBuilder.author(
					"Account review${if (i != 0) " (CONTINUED)" else ""}", "",
					"https://i.imgur.com/GPZgtbe.png"
				)
				for (ckey in checkedKeys.subList(i * 23, ((i + 1) * 23).coerceAtMost(checkedKeys.size))) {
					val ckeyData: String = ckeysChecked[ckey] ?: continue
					embedBuilder.addField(ckey, ckeyData, false)
				}
				if (i < count - 1) {
					embedBuilder.addField("CONTINUED", "IN NEXT EMBED", false)
				} else if (complete) {
					embedBuilder.addField(
						"*Done!*",
						"Took ${startTime.until(LocalDateTime.now(), ChronoUnit.SECONDS)} seconds",
						false
					)
				} else {
					embedBuilder.addField("WORKING...", arrayOf("-", "\\", "|", "/")[updateIdx++ % 4], false)
				}
				if (messages.size > i) messages[i].edit(
					MessageEditSpec.builder().addEmbed(embedBuilder.build()).build()
				).block() else {
					val message: Message = channel.createMessage(embedBuilder.build()).block() ?: return
					messages.add(message)
				}
			}
		}

		internal class RelatedInfo {
			val ips: MutableSet<String> = HashSet()
			val cids: MutableSet<String> = HashSet()
		}
	}
}
