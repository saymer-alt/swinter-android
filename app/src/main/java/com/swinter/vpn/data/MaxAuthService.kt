package com.swinter.vpn.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageBufferPacker
import java.io.DataInputStream
import java.net.InetAddress
import java.io.DataOutputStream
import java.net.Socket
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * MAX SMS авторизация через бинарный TCP протокол.
 * Сервер: api.oneme.ru:443 (обычный TLS, не ГОСТ).
 * deviceType=ANDROID обязателен для SMS auth.
 *
 * Флоу: INIT(6) → AUTH_REQUEST(17, START_AUTH) → AUTH_CONFIRM(18, CHECK_CODE) → LOGIN(19)
 */
object MaxAuthService {
    private const val TAG = "MaxAuthService"
    private const val HOST = "api.oneme.ru"
    private const val PORT = 443
    private const val PROTO_VER: Byte = 10

    // SharedPreferences keys
    private const val PREFS_NAME = "max_auth"
    private const val KEY_TOKEN = "max_auth_token"
    private const val KEY_PHONE = "max_auth_phone"
    private const val KEY_2FA_PASSWORD = "max_2fa_password"

    private var socket: SSLSocket? = null
    private var output: DataOutputStream? = null
    private var input: DataInputStream? = null
    private var seq: Short = 0

    // MARK: - Public API

    /** Отправить SMS-код на номер телефона */
    suspend fun requestCode(phone: String): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        try {
            connect()

            // SESSION_INIT (opcode 6)
            Log.i(TAG, "Sending SESSION_INIT...")
            sendPacket(6, packMap(mapOf(
                "userAgent" to mapOf(
                    "deviceType" to "ANDROID",
                    "locale" to "ru",
                    "appVersion" to "26.11.0",
                ),
                "deviceId" to UUID.randomUUID().toString(),
            )))
            Log.i(TAG, "Waiting for INIT response...")
            val (initCmd, _) = recvPacket()
            Log.i(TAG, "INIT response: cmd=$initCmd")
            if (initCmd != 1.toByte()) return@withContext Result.failure(Exception("INIT failed"))

            // AUTH_REQUEST (opcode 17)
            sendPacket(17, packMap(mapOf(
                "phone" to phone,
                "type" to "START_AUTH",
            )))
            val (authCmd, authRaw) = recvPacket()
            if (authCmd != 1.toByte()) {
                val err = extractError(authRaw)
                return@withContext Result.failure(Exception(err))
            }

            val token = findLongToken(authRaw) ?: return@withContext Result.failure(Exception("Нет verifyToken"))
            val codeLength = findIntAfterKey(authRaw, "codeLength") ?: 6
            Log.i(TAG, "AUTH_REQUEST OK, codeLength=$codeLength")
            Result.success(Pair(token, codeLength))
        } catch (e: Exception) {
            Log.e(TAG, "requestCode error", e)
            Result.failure(e)
        }
    }

    /** Подтвердить SMS-код и залогиниться */
    suspend fun confirmAndLogin(ctx: Context, phone: String, verifyToken: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Reconnect if socket died (user may have taken time entering SMS code)
            if (socket == null || socket?.isClosed == true || socket?.isConnected != true) {
                Log.i(TAG, "Socket dead, reconnecting for confirm...")
                connect()

                // Re-init session on new connection
                sendPacket(6, packMap(mapOf(
                    "userAgent" to mapOf(
                        "deviceType" to "ANDROID",
                        "locale" to "ru",
                        "appVersion" to "26.11.0",
                    ),
                    "deviceId" to UUID.randomUUID().toString(),
                )))
                val (initCmd, _) = recvPacket()
                if (initCmd != 1.toByte()) return@withContext Result.failure(Exception("INIT failed on reconnect"))
            }

            // AUTH_CONFIRM (opcode 18)
            sendPacket(18, packMap(mapOf(
                "token" to verifyToken,
                "verifyCode" to code,
                "authTokenType" to "CHECK_CODE",
            )))
            val (confirmCmd, confirmRaw) = recvPacket()
            if (confirmCmd != 1.toByte()) {
                val err = extractError(confirmRaw)
                return@withContext Result.failure(Exception(err))
            }

            // Check for REGISTER response (account doesn't exist)
            if (confirmRaw.toString(Charsets.UTF_8).contains("REGISTER")) {
                return@withContext Result.failure(Exception("Аккаунт не найден. Нужна регистрация в MAX."))
            }

            // Check for 2FA passwordChallenge
            var authToken: String?
            val confirmStr = confirmRaw.toString(Charsets.UTF_8)
            if (confirmStr.contains("passwordChallenge")) {
                Log.i(TAG, "2FA password challenge detected")
                val trackId = findStringAfterKey(confirmRaw, "trackId") ?: ""
                // Need password — get from SharedPreferences or prompt
                val savedPassword = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_2FA_PASSWORD, null)
                if (savedPassword.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("2FA_REQUIRED"))
                }
                // Submit password (opcode 115 = PASSWORD_AUTH)
                sendPacket(115, packMap(mapOf(
                    "trackId" to trackId,
                    "password" to savedPassword,
                )))
                val (pwCmd, pwRaw) = recvPacket()
                if (pwCmd != 1.toByte()) {
                    val pwErr = extractError(pwRaw)
                    if (pwErr.contains("wrong", ignoreCase = true)) {
                        return@withContext Result.failure(Exception("Неверный пароль 2FA"))
                    }
                    return@withContext Result.failure(Exception(pwErr))
                }
                authToken = findLongToken(pwRaw)
                Log.i(TAG, "2FA password accepted")
            } else {
                authToken = findLongToken(confirmRaw)
            }

            if (authToken == null) return@withContext Result.failure(Exception("Не удалось получить токен"))

            // LOGIN (opcode 19)
            sendPacket(19, packMap(mapOf(
                "token" to authToken,
                "chatsCount" to 0,
                "interactive" to false,
            )))
            val (loginCmd, _) = recvPacket()
            if (loginCmd != 1.toByte()) {
                return@withContext Result.failure(Exception("LOGIN failed"))
            }

            Log.i(TAG, "LOGIN OK")

            // Save token
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TOKEN, authToken)
                .putString(KEY_PHONE, phone)
                .apply()

            disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "confirmAndLogin error", e)
            Result.failure(e)
        }
    }

    fun set2FAPassword(ctx: Context, password: String) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_2FA_PASSWORD, password).apply()
    }

    fun logout(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        disconnect()
    }

    fun isLoggedIn(ctx: Context): Boolean {
        return getToken(ctx) != null
    }

    fun getToken(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)
    }

    fun getPhone(ctx: Context): String? {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PHONE, null)
    }

    fun getMaskedPhone(ctx: Context): String {
        val phone = getPhone(ctx) ?: return ""
        if (phone.length <= 4) return phone
        return "${phone.take(2)}***${phone.takeLast(4)}"
    }

    // MARK: - TCP Connection

    private fun connect() {
        disconnect()
        seq = 0

        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, java.security.SecureRandom())

        // Resolve hostname — try multiple IPs in case DNS is broken through VPN
        val addresses = try {
            InetAddress.getAllByName(HOST)
        } catch (_: Exception) {
            // Fallback hardcoded IPs for api.oneme.ru
            arrayOf(
                InetAddress.getByName("155.212.204.194"),
                InetAddress.getByName("155.212.204.90"),
                InetAddress.getByName("155.212.204.150"),
            )
        }
        var lastError: Exception? = null
        var rawSocket: Socket? = null
        for (addr in addresses) {
            try {
                rawSocket = Socket()
                rawSocket.connect(java.net.InetSocketAddress(addr, PORT), 10000)
                Log.i(TAG, "Connected to ${addr.hostAddress}:$PORT")
                break
            } catch (e: Exception) {
                lastError = e
                rawSocket = null
            }
        }
        if (rawSocket == null) throw lastError ?: Exception("Failed to connect to $HOST")
        rawSocket.soTimeout = 30000
        val sslSocket = sslContext.socketFactory.createSocket(rawSocket, HOST, PORT, true) as SSLSocket
        Log.i(TAG, "Starting TLS handshake...")
        sslSocket.startHandshake()
        Log.i(TAG, "TLS handshake OK, protocol=${sslSocket.session.protocol}")

        socket = sslSocket
        output = DataOutputStream(sslSocket.outputStream)
        input = DataInputStream(sslSocket.inputStream)
    }

    private fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        output = null
        input = null
    }

    // MARK: - Binary Protocol

    private fun sendPacket(opcode: Short, payload: ByteArray) {
        val out = output ?: throw Exception("Not connected")
        val header = ByteArray(10)
        header[0] = PROTO_VER
        header[1] = 0 // cmd = request
        header[2] = (seq.toInt() shr 8 and 0xFF).toByte()
        header[3] = (seq.toInt() and 0xFF).toByte()
        header[4] = (opcode.toInt() shr 8 and 0xFF).toByte()
        header[5] = (opcode.toInt() and 0xFF).toByte()
        val len = payload.size
        header[6] = (len shr 24 and 0xFF).toByte()
        header[7] = (len shr 16 and 0xFF).toByte()
        header[8] = (len shr 8 and 0xFF).toByte()
        header[9] = (len and 0xFF).toByte()
        seq++

        out.write(header)
        out.write(payload)
        out.flush()
    }

    private fun recvPacket(): Pair<Byte, ByteArray> {
        val inp = input ?: throw Exception("Not connected")
        val header = ByteArray(10)
        inp.readFully(header)

        val cmd = header[1]
        val lengthRaw = ((header[6].toInt() and 0xFF) shl 24) or
                ((header[7].toInt() and 0xFF) shl 16) or
                ((header[8].toInt() and 0xFF) shl 8) or
                (header[9].toInt() and 0xFF)
        val flags = (lengthRaw shr 24) and 0xFF
        val payloadLen = lengthRaw and 0x00FFFFFF

        val payload = ByteArray(payloadLen)
        if (payloadLen > 0) {
            inp.readFully(payload)
        }

        // LZ4 decompression if flags > 0
        if (flags > 0) {
            val decompressed = decompressLZ4(payload)
            if (decompressed != null) return Pair(cmd, decompressed)
        }

        return Pair(cmd, payload)
    }

    // MARK: - MessagePack Encoding

    private fun packMap(map: Map<String, Any?>): ByteArray {
        val packer = MessagePack.newDefaultBufferPacker()
        packMapValue(packer, map)
        val bytes = packer.toByteArray()
        packer.close()
        return bytes
    }

    @Suppress("UNCHECKED_CAST")
    private fun packMapValue(packer: MessageBufferPacker, map: Map<String, Any?>) {
        packer.packMapHeader(map.size)
        for ((key, value) in map) {
            packer.packString(key)
            when (value) {
                is String -> packer.packString(value)
                is Int -> packer.packInt(value)
                is Long -> packer.packLong(value)
                is Boolean -> packer.packBoolean(value)
                is Map<*, *> -> packMapValue(packer, value as Map<String, Any?>)
                is List<*> -> {
                    packer.packArrayHeader(value.size)
                    for (item in value) {
                        when (item) {
                            is Long -> packer.packLong(item)
                            is Int -> packer.packInt(item)
                            is String -> packer.packString(item)
                            else -> packer.packNil()
                        }
                    }
                }
                null -> packer.packNil()
                else -> packer.packString(value.toString())
            }
        }
    }

    // MARK: - Response Parsing

    /** Find longest base64url-like string (auth/verify token) */
    private fun findLongToken(data: ByteArray): String? {
        val valid = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-+.~=".toSet()
        var best: String? = null
        val current = StringBuilder()

        for (b in data) {
            val c = b.toInt().toChar()
            if (c in valid) {
                current.append(c)
            } else {
                if (current.length > 100) {
                    val s = current.toString()
                    if (best == null || s.length > best.length) best = s
                }
                current.clear()
            }
        }
        if (current.length > 100) {
            val s = current.toString()
            if (best == null || s.length > best.length) best = s
        }
        return best
    }

    /** Find int value after a key in msgpack data */
    private fun findIntAfterKey(data: ByteArray, key: String): Int? {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val idx = findSubarray(data, keyBytes) ?: return null
        val afterKey = idx + keyBytes.size
        if (afterKey < data.size) {
            val b = data[afterKey].toInt() and 0xFF
            if (b <= 0x7F) return b // positive fixint
        }
        return null
    }

    /** Find a string value after a msgpack key */
    private fun findStringAfterKey(data: ByteArray, key: String): String? {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val idx = findSubarray(data, keyBytes) ?: return null
        val afterKey = idx + keyBytes.size
        if (afterKey >= data.size) return null
        val b = data[afterKey].toInt() and 0xFF
        var strLen = 0
        var strStart = afterKey + 1
        when {
            b and 0xE0 == 0xA0 -> { strLen = b and 0x1F }
            b == 0xD9 && strStart < data.size -> { strLen = data[strStart].toInt() and 0xFF; strStart++ }
            b == 0xDA && strStart + 1 < data.size -> { strLen = ((data[strStart].toInt() and 0xFF) shl 8) or (data[strStart + 1].toInt() and 0xFF); strStart += 2 }
        }
        if (strLen > 0 && strStart + strLen <= data.size) {
            return String(data, strStart, strLen, Charsets.UTF_8)
        }
        return null
    }

    /** Extract error message from response */
    private fun extractError(data: ByteArray): String {
        for (key in listOf("localizedMessage", "message", "error")) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val idx = findSubarray(data, keyBytes) ?: continue
            val afterKey = idx + keyBytes.size
            if (afterKey >= data.size) continue

            val b = data[afterKey].toInt() and 0xFF
            var strLen = 0
            var strStart = afterKey + 1
            when {
                b and 0xE0 == 0xA0 -> { strLen = b and 0x1F } // fixstr
                b == 0xD9 && strStart < data.size -> { strLen = data[strStart].toInt() and 0xFF; strStart++ }
                b == 0xDA && strStart + 1 < data.size -> { strLen = ((data[strStart].toInt() and 0xFF) shl 8) or (data[strStart + 1].toInt() and 0xFF); strStart += 2 }
            }
            if (strLen > 0 && strStart + strLen <= data.size) {
                val s = String(data, strStart, strLen, Charsets.UTF_8)
                if (s.isNotEmpty()) return s
            }
        }
        return "Ошибка сервера"
    }

    private fun findSubarray(haystack: ByteArray, needle: ByteArray): Int? {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return null
    }

    // MARK: - LZ4 Decompression

    private fun decompressLZ4(src: ByteArray): ByteArray? {
        return try {
            val dst = ByteArray(src.size * 10)
            val n = lz4Decode(src, dst)
            if (n > 0) dst.copyOf(n) else null
        } catch (_: Exception) { null }
    }

    private fun lz4Decode(src: ByteArray, dst: ByteArray): Int {
        var sIdx = 0; var dIdx = 0
        while (sIdx < src.size) {
            val token = src[sIdx++].toInt() and 0xFF
            var litLen = token shr 4
            if (litLen == 15) {
                while (sIdx < src.size) {
                    val b = src[sIdx++].toInt() and 0xFF
                    litLen += b
                    if (b != 255) break
                }
            }
            if (sIdx + litLen > src.size || dIdx + litLen > dst.size) {
                val copy = minOf(litLen, src.size - sIdx, dst.size - dIdx)
                System.arraycopy(src, sIdx, dst, dIdx, copy)
                return dIdx + copy
            }
            System.arraycopy(src, sIdx, dst, dIdx, litLen)
            dIdx += litLen; sIdx += litLen
            if (sIdx >= src.size) return dIdx
            if (sIdx + 2 > src.size) return dIdx
            val offset = (src[sIdx].toInt() and 0xFF) or ((src[sIdx + 1].toInt() and 0xFF) shl 8)
            sIdx += 2
            if (offset == 0) return dIdx
            var matchLen = (token and 0x0F) + 4
            if (matchLen == 19) {
                while (sIdx < src.size) {
                    val b = src[sIdx++].toInt() and 0xFF
                    matchLen += b
                    if (b != 255) break
                }
            }
            val matchStart = dIdx - offset
            if (matchStart < 0 || dIdx + matchLen > dst.size) return dIdx
            for (i in 0 until matchLen) { dst[dIdx++] = dst[matchStart + i] }
        }
        return dIdx
    }
}
