package dev.typenil.vpnclient.core.subscription

import dev.typenil.vpnclient.core.subscription.model.ProxyNode
import dev.typenil.vpnclient.core.subscription.model.isTunnelAllowed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ParserIdStabilityTest {
    private fun clash(extra: String = "", port: String = "443", tls: String = "true", type: String = "vless", name: String = "synthetic"): ProxyNode =
        ClashYamlParser().parse("""
            proxies:
              - name: $name
                type: $type
                server: server.example.invalid
                port: $port
                uuid: 00000000-0000-0000-0000-000000000000
                tls: $tls
        """.trimIndent() + "\n" + extra.trimIndent().prependIndent("    "), 1).nodes.single()

    private fun uri(query: String): ProxyNode = UriListParser().parse(
        "vless://00000000-0000-0000-0000-000000000000@server.example.invalid:443?security=tls&$query", 1,
    ).nodes.single()

    private fun cases() = linkedMapOf(
        "A-clash-header" to clash("network: ws\nws-opts:\n  headers: {Host: host.example.invalid}\n  path: /tunnel"),
        "A-clash-alias" to clash("network: ws\nws-opts: {host: host.example.invalid, path: /tunnel}"),
        "A-clash-explicit" to clash("servername: sni.example.invalid\nnetwork: ws\nws-opts: {host: host.example.invalid, path: /tunnel}"),
        "A-uri-host" to uri("type=ws&host=host.example.invalid&path=%2Ftunnel"),
        "A-uri-explicit" to uri("type=ws&host=host.example.invalid&sni=sni.example.invalid&path=%2Ftunnel"),
        "A-json-explicit" to SingBoxJsonParser().parse("""{"outbounds":[{"type":"vless","server":"server.example.invalid","server_port":443,"uuid":"00000000-0000-0000-0000-000000000000","tls":{"enabled":true},"transport":{"type":"ws","path":"/tunnel","headers":{"Host":"host.example.invalid"}}}]}""", 1).nodes.single(),
        "B-clash-h2-scalar" to clash("network: h2\nh2-opts: {host: host.example.invalid, path: /tunnel}"),
        "B-clash-h2-list" to clash("network: h2\nh2-opts: {host: [first.example.invalid, second.example.invalid], path: /tunnel}"),
        "B-clash-h2-empty" to clash("network: h2\nh2-opts: {host: \"\", path: \"\"}"),
        "B-clash-h2-mixed" to clash("network: h2\nh2-opts: {host: [\"\", host.example.invalid], path: /tunnel}"),
        "B-uri-http-empty" to uri("type=http&host=%20,%20&path=%20"),
        "B-uri-h2-mixed" to uri("type=h2&host=%20,host.example.invalid,%20&path="),
        "C-scalars" to clash("alterId: 0\nskip-cert-verify: false", type = "vmess"),
        "C-strings" to clash("alterId: \"0\"\nskip-cert-verify: \"false\"", port = "\"443\"", tls = "\"true\"", type = "vmess"),
        "C-numeric-name" to clash(name = "123"),
        "C-numeric-tls" to clash(tls = "1"),
        "C-numeric-skip" to clash("skip-cert-verify: 1"),
    )

    // Captured at fe57593; parser/helper Git objects verified identical before execution.
    @Test fun goldenIdsMatchPreWp8i() {
        val expected = listOf(
            "ddce52d94be05fef17c6ee7c4bb592ea9987b02eec6d8013bf62b3f9ae79bb09",
            "d770110726f6ec8bf777a1fa9d7a802704a4ad487e510a8e3298a15bc2119473",
            "630fbe8bf0737d934d5feb2d3ef3b5591d05a60b5c932223e196b8b6c5fab46c",
            "ddce52d94be05fef17c6ee7c4bb592ea9987b02eec6d8013bf62b3f9ae79bb09",
            "15b9b1a53b57762f4d3b9ecd60dc45aa49c049eab002e8bd65dd9615f7a0f393",
            "a8a5f5969b8021eeed3f5db112d5fa2aa00285bc76d4ed75f147eb3084e57f40",
            "43fc5a4225e11f3bdec0e78aac8fa1aa87fa7f2ab2a72435c641aa9a9da55c81",
            "28dec51db032c500405b3984b95d2cc88e6c8ebc9ff7f146c619efaba624183b",
            "0153a4c15e33adf3d47d8a0bcf6fdb91e8a29f45f2d8fa0e3d15449078667e67",
            "434d29d19281375942af0114911facd4f6c87f7d7654e974495cfbba9875385e",
            "d4849f20910b0431d480ab2b77bfd93ca833b9c88cbbd5ae62969700251e02bb",
            "4aafb9eba201f33495bf1b1af8455a0f7f11b99adfe92709ad5b84750334dcc3",
            "5fe5d57228ee043829977b83f9ca20ec068d94bec61f5e3a48022a945927818a",
            "5fe5d57228ee043829977b83f9ca20ec068d94bec61f5e3a48022a945927818a",
            "5b91a05c0450e4539e93b83ce1607ede1afd1471425bd0dca2e148067509ed8a",
            "fba7b6e8297f3e5b78899ac471eb4dbc68a7c946235193adbb9eda8d95b9a497",
            "5b91a05c0450e4539e93b83ce1607ede1afd1471425bd0dca2e148067509ed8a",
        )
        assertEquals(expected.size, cases().size)
        cases().entries.forEachIndexed { i, (label, node) -> assertEquals(label, expected[i], node.id) }
    }

    private fun outbound(node: ProxyNode) = Json.parseToJsonElement(node.outboundJson).jsonObject

    @Test fun wsHeaderAndAliasRemainDistinctFromExplicitSni() {
        val nodes = cases()
        val standard = outbound(nodes.getValue("A-clash-header"))
        assertEquals("host.example.invalid", standard["tls"]!!.jsonObject["server_name"]!!.jsonPrimitive.content)
        assertEquals("host.example.invalid", standard["transport"]!!.jsonObject["headers"]!!.jsonObject["Host"]!!.jsonPrimitive.content)
        val alias = outbound(nodes.getValue("A-clash-alias"))
        assertNull(alias["transport"]!!.jsonObject["headers"])
        assertEquals("host.example.invalid", alias["tls"]!!.jsonObject["server_name"]!!.jsonPrimitive.content)
        assertEquals("sni.example.invalid", outbound(nodes.getValue("A-clash-explicit"))["tls"]!!.jsonObject["server_name"]!!.jsonPrimitive.content)
        // Native JSON fields are preserved: transport Host does not infer tls.server_name.
        assertNull(outbound(nodes.getValue("A-json-explicit"))["tls"]!!.jsonObject["server_name"])
    }

    @Test fun clashBlankValuesArePreservedWhileUriBlanksAreFiltered() {
        val nodes = cases()
        val blank = outbound(nodes.getValue("B-clash-h2-empty"))["transport"]!!.jsonObject
        assertEquals(listOf(""), blank["host"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("", blank["path"]!!.jsonPrimitive.content)
        val mixed = outbound(nodes.getValue("B-clash-h2-mixed"))["transport"]!!.jsonObject
        assertEquals(listOf("", "host.example.invalid"), mixed["host"]!!.jsonArray.map { it.jsonPrimitive.content })
        val emptyUri = outbound(nodes.getValue("B-uri-http-empty"))["transport"]!!.jsonObject
        assertNull(emptyUri["host"]); assertNull(emptyUri["path"])
        val mixedUri = outbound(nodes.getValue("B-uri-h2-mixed"))["transport"]!!.jsonObject
        assertEquals("host.example.invalid", mixedUri["host"]!!.jsonPrimitive.content)
        assertNull(mixedUri["path"])
    }

    @Test fun scalarAndStringFormsKeepTheSameValues() {
        val nodes = cases()
        assertEquals(nodes.getValue("C-scalars").id, nodes.getValue("C-strings").id)
        val value = outbound(nodes.getValue("C-strings"))
        assertEquals("443", value["server_port"]!!.jsonPrimitive.content)
        assertEquals("0", value["alter_id"]!!.jsonPrimitive.content)
        assertEquals("true", value["tls"]!!.jsonObject["enabled"]!!.jsonPrimitive.content)
        assertEquals("123", nodes.getValue("C-numeric-name").name)
        for (skip in listOf("true", "\"true\"", "\"1\"")) {
            assertEquals("true", outbound(clash("skip-cert-verify: $skip"))["tls"]!!.jsonObject["insecure"]!!.jsonPrimitive.content)
        }
    }

    // documents current behaviour, see PARSER-HARDENING.md; change consciously if C is approved.
    @Test fun numericBooleansDocumentCurrentBehaviour() {
        val nodes = cases()
        assertNull(outbound(nodes.getValue("C-numeric-tls"))["tls"])
        assertFalse(isTunnelAllowed(nodes.getValue("C-numeric-tls"))) // D-4, remote VLESS.
        assertNull(outbound(nodes.getValue("C-numeric-skip"))["tls"]!!.jsonObject["insecure"])
        assertTrue(isTunnelAllowed(nodes.getValue("C-numeric-skip"))) // TLS still verifies.
    }
}
