package net.yogstation.yogbot

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.GuildChannel
import discord4j.discordjson.json.MessageData
import discord4j.discordjson.json.MessageEditRequest
import discord4j.discordjson.possible.Possible
import discord4j.rest.http.client.ClientException
import io.netty.handler.codec.http.HttpResponseStatus
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.config.ForumsConfig
import net.yogstation.yogbot.util.ByondLinkUtil
import net.yogstation.yogbot.util.StringUtils
import org.apache.logging.log4j.util.Strings.isBlank
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.net.URI
import java.util.*
import java.util.regex.*

@Component
class ForumsManager(
	private val channelsConfig: DiscordChannelsConfig,
	client: GatewayDiscordClient,
	private val discordConfig: DiscordConfig,
	private val webClient: WebClient,
	private val databaseManager: DatabaseManager,
	private val forumsConfig: ForumsConfig,
	private val mapper: ObjectMapper
) {
	private val logger: Logger = LoggerFactory.getLogger(javaClass)

	val guild: Guild? = client.getGuildById(Snowflake.of(discordConfig.mainGuildID)).block()

	@Scheduled(fixedRate = 15000)
	fun handleForums() {
		if(isBlank(forumsConfig.xenforoKey)) {
			logger.info("Xenforo key unset, skipping forums updates")
			return;
		}
		handleChannel(
			channelsConfig.channelBanAppeals,
			forumsConfig.banAppealsForum
		)
		handleChannel(
			channelsConfig.channelPlayerComplaints,
			forumsConfig.playerComplaintsForum,
			PingType.PLAYER_COMPLAINT
		)
		handleChannel(
			channelsConfig.channelAdminComplaints,
			forumsConfig.adminComplaintsForums
		)
		handleChannel(
			channelsConfig.channelAdminAdminComplaints,
			forumsConfig.adminAdminComplaintsForum
		)
		handleChannel(
			channelsConfig.channelStaffApplications,
			forumsConfig.staffApplicationsForum
		)
		handleChannel(
			channelsConfig.channelMentorApplications,
			forumsConfig.mentorApplicationsForum,
			PingType.MENTOR_STAFF
		)
	}

	private val appealPattern: Pattern = Pattern.compile(".+ - .+ - (?<ping>.+)")
	private val complaintPattern: Pattern = Pattern.compile("(?<ping>.+) - report by ")

	private fun getPing(pingType: PingType, title: String): Mono<String> {
		val defaultPing = getDefaultPing(pingType)
		val mentionPattern = when(pingType) {
			PingType.STAFF_ONLY, PingType.MENTOR_STAFF -> null
			PingType.BAN_APPEAL -> appealPattern
			PingType.PLAYER_COMPLAINT -> complaintPattern
		}
		if(mentionPattern == null || guild == null) return Mono.just(defaultPing)
		val mentionMatcher = mentionPattern.matcher(title)
		if(!mentionMatcher.find()) return Mono.just(defaultPing)
		val ckey = StringUtils.ckeyIze(mentionMatcher.group("ping"))
		val response = ByondLinkUtil.getMemberID(ckey, databaseManager)
		if (response.value == null) return Mono.just(defaultPing)
		return guild!!.getMemberById(response.value).map {
			if(pingType == PingType.PLAYER_COMPLAINT) {
				"${it.mention} $defaultPing"
			} else if (it.roleIds.contains(Snowflake.of(discordConfig.staffRole))) {
				it.mention
			} else defaultPing
		}.doOnError {
			// Equality on the boolean because it is nullable
			if (it is ClientException &&
				it.status == HttpResponseStatus.NOT_FOUND &&
				it.message?.contains("Unknown Member") == true)
				logger.debug("Error getting member from ID", it)
			else {
				logger.error("Unexpected error in getPing: ${it.message}")
				logger.debug("Get ping exception: ", it)
			}
		}.onErrorReturn(defaultPing)
	}

	private fun getDefaultPing(pingType: PingType): String {
		return when (pingType) {
			PingType.BAN_APPEAL, PingType.STAFF_ONLY, PingType.PLAYER_COMPLAINT -> "<@&${discordConfig.staffRole}>"
			PingType.MENTOR_STAFF -> "<@&${discordConfig.mentorRole}> <@&${discordConfig.staffRole}>"
		}
	}

	private fun handleChannel(channelId: Long, forumId: Int, pingType: PingType = PingType.STAFF_ONLY) {
		guild?.getChannelById(Snowflake.of(channelId))?.flatMap { channel ->
			fetchData(forumId).flatMap forumData@ { response ->
				if(response == null) return@forumData Mono.empty<Void>()
				val threads = response.get("threads")
				channel.restChannel.getMessagesAfter(Snowflake.of(0)).collectList().flatMap { unprocessedMessages ->
					var publishResult: Mono<*> = Mono.empty<Any>()
					for (thread in threads) {
						publishResult = publishResult.and(
							processPost(
								pingType,
								unprocessedMessages,
								channel,
								thread
							)
						)
					}

					for (toDelete in unprocessedMessages) {
						publishResult = publishResult.and(
							channel.restChannel.getRestMessage(Snowflake.of(toDelete.id())).delete("Post Removed")
						)
					}

					publishResult
				}
			}
		}?.subscribe()
	}

	private fun processPost(
		pingType: PingType,
		unprocessedMessages: MutableList<MessageData>,
		channel: GuildChannel,
		thread: JsonNode
	): Mono<*> {
		val link = thread.get("view_url").asText()
		val title = thread.get("title").asText()
		for (message in unprocessedMessages) {
			if (message.content().contains(link)) {
				unprocessedMessages.remove(message)
				return getMessageContent(pingType, title, link).flatMap {
					if (message.content() != it) {
						channel.restChannel.getRestMessage(Snowflake.of(message.id())).edit(
							MessageEditRequest.builder().content(Possible.of(Optional.of(it))).build()
						)
					} else Mono.empty<Any>()
				}
			}
		}
		return getMessageContent(pingType, title, link).flatMap { channel.restChannel.createMessage(it) }
	}

	private fun getMessageContent(pingType: PingType, title: String, link: String): Mono<String> {
		return getPing(pingType, title).map { "$it `$title`\n        <$link>" }
	}

	private fun fetchData(forumId: Int): Mono<JsonNode?> {
		return webClient.get()
			.uri(URI.create("https://forums.yogstation.net/api/forums/$forumId?with_threads=true"))
			.header("XF-Api-Key", forumsConfig.xenforoKey)
			.retrieve()
			.bodyToMono(String::class.java)
			.doOnError {
				if (it is WebClientResponseException) {
					if(it.statusCode == HttpStatus.FORBIDDEN || it.statusCode == HttpStatus.UNAUTHORIZED) {
						logger.error("Cannot access forum $forumId")
					} else logger.error("Error response received", it)
				} else logger.error("Unknown error making web request", it)
			}.onErrorComplete()
			.map { mapper.readTree(it) }
	}

	enum class PingType {
		STAFF_ONLY, MENTOR_STAFF, BAN_APPEAL, PLAYER_COMPLAINT
	}
}
