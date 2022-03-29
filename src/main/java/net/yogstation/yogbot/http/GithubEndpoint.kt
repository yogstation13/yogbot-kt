package net.yogstation.yogbot.http

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.spec.EmbedCreateSpec
import net.yogstation.yogbot.ByondConnector
import net.yogstation.yogbot.GithubManager
import net.yogstation.yogbot.config.DiscordChannelsConfig
import net.yogstation.yogbot.config.GithubConfig
import net.yogstation.yogbot.util.HttpUtil
import net.yogstation.yogbot.util.StringUtils
import net.yogstation.yogbot.util.YogResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
class GithubEndpoint (
	githubConfig: GithubConfig,
	private val githubManager: GithubManager,
	private val mapper: ObjectMapper,
	private val byondConnector: ByondConnector,
	private val client: GatewayDiscordClient,
	private val channelsConfig: DiscordChannelsConfig,
){
	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val keySpec: SecretKeySpec?
	private val mac: Mac?

	init {
		if (githubConfig.hmac == "") {
			keySpec = null
			mac = null
		} else {
			keySpec = SecretKeySpec(githubConfig.hmac.encodeToByteArray(), "HmacSHA256")
			mac = Mac.getInstance("HmacSHA256")
			mac.init(keySpec)
		}
	}

	@PostMapping("/api/github")
	fun handleWebhook(
		@RequestBody data: String,
		@RequestHeader("X-Hub-Signature-256") hash: String,
		@RequestHeader("X-Github-Event") event: String
	): Mono<HttpEntity<String>> {
		if (!verifySignature(data, hash)) return HttpUtil.badRequest("Hash Incorrect")
		if (event == "ping") return HttpUtil.ok("pong")
		val jsonData: JsonNode = mapper.readTree(data)
		if (event == "pull_request") {
			return pullRequestEvent(jsonData)
		}
		return HttpUtil.ok("Request processed")
	}

	private fun verifySignature(data: String, hash: String): Boolean {
		if (mac == null) {
			logger.warn("Verification key not set for webhook, assuming valid")
			return true
		}
		val signature = "sha256=${StringUtils.bytesToHex(mac.doFinal(data.encodeToByteArray()))}".lowercase()
		return MessageDigest.isEqual(signature.encodeToByteArray(), hash.encodeToByteArray())
	}

	private fun pullRequestEvent(
		jsonData: JsonNode
	): Mono<HttpEntity<String>> {
		var action = jsonData.get("action").asText()
		if (action != "opened" && action != "reopened" && action != "closed") return HttpUtil.ok("Action not supported")
		if (jsonData.get("pull_request").get("merged") != null) action = "merged"

		val monoCollection: MutableList<Mono<*>> = ArrayList()

		val changelogResult = githubManager.compileChangelog(jsonData.get("pull_request").get("body").asText() ?: "")
		val embed: EmbedCreateSpec = getEventPrEmbed(jsonData, changelogResult)
		val title = jsonData.get("pull_request").get("title").asText()
		val securityPr = title.lowercase().contains("[s]")

		if (title.lowercase().contains("[admin]")) {
			monoCollection.add(sendEmbedTo(channelsConfig.channelImportantAdmin, embed))
		}

		if (!securityPr) {
			if (action == "opened") {
				monoCollection.add(
					byondConnector.requestAsync(
						"?announce=$title&author=${
							jsonData.get("sender").get("login")
						}&id=${jsonData.get("pull_request").get("number")}"
					)
				)
			}

			monoCollection.add(sendEmbedTo(channelsConfig.channelPublic, embed))
			monoCollection.add(sendEmbedTo(channelsConfig.channelDevelopmentPublic, embed))
			monoCollection.add(sendEmbedTo(channelsConfig.channelGithubSpam, embed))
		} else {
			monoCollection.add(sendEmbedTo(channelsConfig.channelMaintainerChat, embed))
		}

		monoCollection.add(githubManager.setTags(jsonData, securityPr, embed, changelogResult))

		if (action == "merged" && changelogResult.value != null) {
			monoCollection.add(githubManager.uploadChangelog(changelogResult.value, jsonData))
		}
		return Mono.`when`(monoCollection).then(HttpUtil.ok("Handled Message"))
	}

	private fun sendEmbedTo(channelId: Long, embed: EmbedCreateSpec): Mono<*> {
		return client.getChannelById(Snowflake.of(channelId)).flatMap {
			if (it is TextChannel) it.createMessage(embed)
			else Mono.empty<Any>()
		}
	}

	private fun getEventPrEmbed(data: JsonNode, changelog: YogResult<GithubManager.Changelog?, String?>): EmbedCreateSpec {
		return githubManager.getPrEmbed(
			data.get("pull_request"),
			"A PR has been ${data.get("action").asText()} by ${data.get("sender").get("login").asText()}",
			changelog
		)
	}
}
