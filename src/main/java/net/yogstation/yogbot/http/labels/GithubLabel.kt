package net.yogstation.yogbot.http.labels

import com.fasterxml.jackson.databind.JsonNode
import net.yogstation.yogbot.GithubManager

/**
 * The superclass of all labels, should handle most logic fine
 * Either changelogTypes or matchExtensions need overridden,
 * or the default logic need replaced to prevent the label from always being true
 */
abstract class GithubLabel {
	/**
	 * The name of the label to apply
	 */
	abstract val label: String

	/**
	 * The changelog entry types that should trigger this label
	 */
	protected open val changelogTypes: List<String> = listOf()

	/**
	 * The file extensions that should trigger this label
	 */
	protected open val matchExtensions: List<String> = listOf()

	protected open fun isMatch(eventData: JsonNode): Boolean {
		return true
	}

	protected open fun isMatch(changelog: GithubManager.Changelog?): Boolean {
		return changelogTypes.isEmpty() || changelog != null && changelogTypes.any { tag ->
			changelog.entries.any {
				it.type == tag
			}
		}
	}

	protected open fun isMatch(extensions: Set<String>): Boolean {
		return matchExtensions.isEmpty() || matchExtensions.any {
			extensions.contains(it)
		}
	}

	fun isMatch(eventData: JsonNode, changelog: GithubManager.Changelog?, extensions: Set<String>): Boolean {
		return isMatch(eventData) && isMatch(changelog) && isMatch(extensions)
	}
}
