package com.dnsfilter.data

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale

/**
 * Two hash sets: one of blocked domains, one of domains that must never be
 * blocked. A domain matches itself and every parent domain, so
 * "a.b.ads.com" hits an entry for "ads.com".
 */
class BlockList(
    private val blocked: Set<String>,
    private val allowed: Set<String>,
) {

    val blockedCount: Int get() = blocked.size

    fun isBlocked(host: String): Boolean {
        var name = host.trim().lowercase(Locale.US)
        if (name.endsWith(".")) name = name.dropLast(1)
        if (name.isEmpty()) return false
        if (matches(name, allowed)) return false
        return matches(name, blocked)
    }

    private fun matches(host: String, set: Set<String>): Boolean {
        var current = host
        while (true) {
            if (set.contains(current)) return true
            val dot = current.indexOf('.')
            if (dot < 0) return false
            current = current.substring(dot + 1)
            if (current.isEmpty()) return false
        }
    }

    companion object {

        /** hosts.txt is a hosts file (StevenBlack plus AdGuard mirrors); the
         *  parser accepts "ads.example.com", "0.0.0.0 ads.example.com" and
         *  "||ads.example.com^" alike. */
        private const val BLOCK_ASSET = "hosts.txt"
        private const val ALLOW_ASSET = "allow.txt"

        /**
         * Throws when the shipped block list is missing or empty, so the
         * service can show a real error instead of pretending a 24-entry
         * fallback is the full list.
         */
        fun load(context: Context): BlockList {
            val blocked = HashSet<String>(200_000)
            val read = readAsset(context, BLOCK_ASSET) { line ->
                parseLine(line)?.let(blocked::add)
            }
            if (!read) {
                throw IOException(
                    "The bundled block list (assets/hosts.txt) could not be read.",
                )
            }
            if (blocked.isEmpty()) {
                throw IOException(
                    "The bundled block list (assets/hosts.txt) is empty.",
                )
            }
            val allowed = HashSet<String>(256)
            readAsset(context, ALLOW_ASSET) { line ->
                parseLine(line)?.let(allowed::add)
            }
            return BlockList(blocked, allowed)
        }

        /** Returns true when the asset opened at all. */
        private fun readAsset(context: Context, name: String, sink: (String) -> Unit): Boolean {
            val stream = try {
                context.assets.open(name)
            } catch (e: IOException) {
                return false
            }
            try {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        sink(line)
                        line = reader.readLine()
                    }
                }
            } catch (e: IOException) {
                // truncated or unreadable: keep whatever was parsed so far
            } finally {
                try {
                    stream.close()
                } catch (ignored: IOException) {
                }
            }
            return true
        }

        /**
         * Accepts "ads.example.com", "0.0.0.0 ads.example.com", "::1 ads.example.com"
         * and "||ads.example.com^". Strips comments, options and the trailing dot.
         *
         * Cosmetic and scriptlet rules ("example.com##.banner", "example.com#%#//x")
         * name ordinary sites, so they are skipped — otherwise whole websites
         * would be blocked just because they carry a banner rule.
         */
        private fun parseLine(raw: String): String? {
            val start = raw.trimStart()
            if (start.isEmpty()) return null
            if (start.startsWith("!") || start.startsWith("#")) return null
            if (raw.contains("##") || raw.contains("#@#") || raw.contains("#?#") ||
                raw.contains("#%#") || raw.contains("#$#")
            ) {
                return null
            }

            var line = raw
            val hash = line.indexOf('#')
            if (hash >= 0) line = line.substring(0, hash)
            val bang = line.indexOf('!')
            if (bang >= 0) line = line.substring(0, bang)
            line = line.trim()
            if (line.isEmpty()) return null
            val parts = line.split(' ', '\t').filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            var candidate = if (parts.size > 1 && looksLikeAddress(parts[0])) {
                parts[1]
            } else {
                parts[0]
            }.trim()

            val dollar = candidate.indexOf('$')
            if (dollar >= 0) candidate = candidate.substring(0, dollar)
            if (candidate.startsWith("@@")) candidate = candidate.substring(2)
            if (candidate.startsWith("||")) {
                candidate = candidate.substring(2)
            } else if (candidate.startsWith("|")) {
                candidate = candidate.substring(1)
            }
            val slash = candidate.indexOf('/')
            if (slash >= 0) candidate = candidate.substring(0, slash)
            while (candidate.endsWith("^") || candidate.endsWith("|")) {
                candidate = candidate.dropLast(1)
            }

            var domain = candidate.trim().lowercase(Locale.US)
            if (domain.endsWith(".")) domain = domain.dropLast(1)
            if (domain.isEmpty()) return null
            if (domain == "localhost") return null
            if (domain == "0.0.0.0") return null
            if (domain == "127.0.0.1") return null
            if (domain == "::1") return null
            if (domain.contains('/')) return null
            if (domain.contains('*')) return null
            if (domain.contains(':')) return null
            if (domain.contains('_')) return null
            if (!domain.contains('.')) return null
            if (isNumeric(domain)) return null
            return domain
        }

        private fun looksLikeAddress(token: String): Boolean {
            if (token.contains(':')) return true
            if (token.isEmpty()) return false
            for (ch in token) {
                if (ch != '.' && !ch.isDigit()) return false
            }
            return true
        }

        /** True for bare IP leftovers such as "1.2.3" or "10.0.0.2". */
        private fun isNumeric(domain: String): Boolean {
            for (ch in domain) {
                if (ch != '.' && !ch.isDigit()) return false
            }
            return true
        }
    }
}
