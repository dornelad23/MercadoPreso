package com.example.mercadopreso.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.mercadopreso.data.model.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Local demo or server persistence, selected by MERCADO_API_URL. */
object AppStore {
    private lateinit var prefs: SharedPreferences
    var state by mutableStateOf(EstadoApp())
        private set

    var loadError: String? = null
        private set

    val user
        get() = state.usuarios.first { it.id == state.ativo }

    val loggedIn
        get() = user.id != "guest"

    val count
        get() = user.carrinho.values.sum()

    val subtotal
        get() =
            user.carrinho.entries.sumOf { (id, q) ->
                (ProductRepository.find(id)?.preco ?: 0.0) * q
            }

    fun init(context: Context) {
        prefs =
            context.getSharedPreferences(
                if (ApiClient.enabled)
                    "mercado_preso_online_guest_" +
                        com.example.mercadopreso.BuildConfig.API_BASE_URL.hashCode()
                else "mercado_preso_v4",
                Context.MODE_PRIVATE,
            )
        ApiClient.init(context)
        if (ApiClient.enabled) ProductRepository.replace(JSONArray())
        state = EstadoApp()
        loadError = null
        val raw = prefs.getString("state", null) ?: return
        try {
            val root = JSONObject(raw)
            val users = root.getJSONArray("usuarios").objects().map(::readUser)
            require(users.any { it.id == "guest" })
            val active =
                root.optString("ativo", "guest").takeIf { id -> users.any { it.id == id } }
                    ?: "guest"
            state =
                EstadoApp(
                    users,
                    active,
                    root.optJSONArray("reviews").objects().map {
                        Review(
                            it.getInt("produtoId"),
                            it.getString("userId"),
                            it.getString("nome"),
                            it.getInt("nota"),
                            it.getString("comentario"),
                        )
                    },
                )
            if (ApiClient.enabled)
                state = EstadoApp(listOf(state.usuarios.first { it.id == "guest" }))
        } catch (_: Exception) {
            loadError =
                "Não foi possível ler os dados locais. Os dados originais foram preservados; não será possível gravar até recuperar o arquivo."
        }
    }

    private fun save(next: EstadoApp) {
        check(loadError == null) { loadError ?: "Falha ao abrir dados" }
        val root =
            JSONObject()
                .put("version", 1)
                .put("ativo", next.ativo)
                .put("usuarios", JSONArray(next.usuarios.map(::writeUser)))
                .put(
                    "reviews",
                    JSONArray(
                        next.reviews.map {
                            JSONObject()
                                .put("produtoId", it.produtoId)
                                .put("userId", it.userId)
                                .put("nome", it.nome)
                                .put("nota", it.nota)
                                .put("comentario", it.comentario)
                        }
                    ),
                )
        check(prefs.edit().putString("state", root.toString()).commit()) {
            "Não foi possível salvar. Tente novamente."
        }
        state = next
    }

    private fun update(transform: (Usuario) -> Usuario) {
        val id = state.ativo
        save(state.copy(usuarios = state.usuarios.map { if (it.id == id) transform(it) else it }))
    }

    private fun localRegister(nome: String, email: String, senha: String) {
        val normalized = email.trim().lowercase()
        require(nome.trim().length >= 2) { "Informe seu nome." }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            "Informe um e-mail válido."
        }
        require(senha.length >= 6) { "Use uma senha com pelo menos 6 caracteres." }
        require(state.usuarios.none { it.email == normalized }) {
            "Esse e-mail já está cadastrado neste aparelho."
        }
        val salt =
            Base64.encodeToString(
                ByteArray(16).also { SecureRandom().nextBytes(it) },
                Base64.NO_WRAP,
            )
        val guest = user
        val newUser =
            Usuario(
                UUID.randomUUID().toString(),
                nome.trim(),
                normalized,
                salt,
                hash(senha, salt),
                carrinho = guest.carrinho,
                favoritos = guest.favoritos,
            )
        save(
            state.copy(
                usuarios =
                    state.usuarios.map {
                        if (it.id == "guest") it.copy(carrinho = emptyMap(), favoritos = emptySet())
                        else it
                    } + newUser,
                ativo = newUser.id,
            )
        )
    }

    private fun localLogin(email: String, senha: String) {
        val found =
            state.usuarios.firstOrNull { it.id != "guest" && it.email == email.trim().lowercase() }
        require(
            found != null &&
                MessageDigest.isEqual(
                    hash(senha, found.salt).toByteArray(),
                    found.passwordHash.toByteArray(),
                )
        ) {
            "E-mail ou senha incorretos."
        }
        val guest = state.usuarios.first { it.id == "guest" }
        val merged =
            (found.carrinho.keys + guest.carrinho.keys)
                .associateWith { id ->
                    ((found.carrinho[id] ?: 0) + (guest.carrinho[id] ?: 0)).coerceAtMost(
                        ProductRepository.find(id)?.estoque ?: 0
                    )
                }
                .filterValues { it > 0 }
        save(
            state.copy(
                ativo = found.id,
                usuarios =
                    state.usuarios.map {
                        when (it.id) {
                            found.id ->
                                it.copy(
                                    carrinho = merged,
                                    favoritos = it.favoritos + guest.favoritos,
                                )
                            "guest" -> it.copy(carrinho = emptyMap(), favoritos = emptySet())
                            else -> it
                        }
                    },
            )
        )
    }

    private fun localLogout() = save(state.copy(ativo = "guest"))

    private fun localEditProfile(nome: String) {
        require(nome.trim().length >= 2) { "Informe seu nome." }
        update { it.copy(nome = nome.trim()) }
    }

    private fun localChangePassword(current: String, next: String) {
        require(loggedIn && hash(current, user.salt) == user.passwordHash) {
            "Senha atual incorreta."
        }
        require(next.length >= 6) { "Use pelo menos 6 caracteres." }
        update { it.copy(passwordHash = hash(next, it.salt)) }
    }

    private fun localAdd(p: Produto) {
        val quantidade = user.carrinho[p.id] ?: 0
        require(quantidade < available(p)) { "Quantidade máxima disponível atingida." }
        update { it.copy(carrinho = it.carrinho + (p.id to quantidade + 1)) }
    }

    private fun localDecrease(id: Int) = update {
        val q = it.carrinho[id] ?: 0
        it.copy(carrinho = if (q <= 1) it.carrinho - id else it.carrinho + (id to q - 1))
    }

    private fun localRemove(id: Int) = update { it.copy(carrinho = it.carrinho - id) }

    private fun localFavorite(id: Int) = update {
        it.copy(favoritos = if (id in it.favoritos) it.favoritos - id else it.favoritos + id)
    }

    private fun localSettings(
        animations: Boolean = user.animacoes,
        alerts: Boolean = user.alertas,
    ) = update { it.copy(animacoes = animations, alertas = alerts) }

    private fun localSaveAddress(address: Endereco) {
        require(loggedIn) { "Entre na sua conta para salvar endereços." }
        require(address.numero.isNotBlank() && address.rua.isNotBlank()) {
            "Informe a rua e o número."
        }
        update {
            it.copy(
                enderecos = listOf(address) + it.enderecos.filterNot { old -> old.id == address.id }
            )
        }
    }

    private fun localDeleteAddress(id: String) = update {
        it.copy(enderecos = it.enderecos.filterNot { a -> a.id == id })
    }

    private fun localReview(id: Int, rating: Int, text: String) {
        require(loggedIn) { "Entre para avaliar." }
        require(text.trim().isNotEmpty() && rating in 1..5) { "Informe a nota e um comentário." }
        save(
            state.copy(
                reviews =
                    listOf(Review(id, user.id, user.nome, rating, text.trim())) +
                        state.reviews.filterNot { it.userId == user.id && it.produtoId == id }
            )
        )
    }

    fun available(p: Produto): Int {
        if (ApiClient.enabled) return p.estoque
        val sold =
            state.usuarios
                .flatMap { it.pedidos }
                .filterNot { it.status == "Cancelado" }
                .flatMap { it.itens }
                .filter { it.produtoId == p.id }
                .sumOf { it.quantidade }
        return (p.estoque - sold).coerceAtLeast(0)
    }

    private fun localCheckout(
        address: Endereco,
        quote: Cotacao,
        payment: String,
        installments: Int,
    ): String {
        require(loggedIn) { "Entre para concluir o pedido." }
        require(user.carrinho.isNotEmpty()) { "Seu carrinho está vazio." }
        require(address.numero.isNotBlank() && address.rua.isNotBlank()) {
            "Complete seu endereço."
        }
        require(payment in listOf("Pix", "Cartão de crédito", "Boleto") && installments in 1..12) {
            "Forma de pagamento inválida."
        }
        val lines =
            user.carrinho.map { (id, quantity) ->
                val p = requireNotNull(ProductRepository.find(id)) { "Produto indisponível." }
                require(quantity > 0 && quantity <= available(p)) {
                    "Estoque insuficiente para ${p.nome}."
                }
                LinhaPedido(id, p.nome, p.imagem, p.preco, quantity)
            }
        val expectedQuote =
            ShippingService.quotes(address.uf, user.carrinho).firstOrNull { it.nome == quote.nome }
        require(expectedQuote != null && expectedQuote.valor == quote.valor) {
            "O carrinho mudou. Consulte o frete novamente."
        }
        val now = System.currentTimeMillis()
        val order =
            Pedido(
                "MP-" + UUID.randomUUID().toString().take(8).uppercase(),
                now,
                lines,
                address,
                quote.valor,
                quote.nome,
                payment,
                if (payment == "Cartão de crédito") installments else 1,
                listOf(EventoPedido("Pago (teste)", now)),
            )
        update { it.copy(carrinho = emptyMap(), pedidos = listOf(order) + it.pedidos) }
        return order.id
    }

    private fun localAdvance(id: String) = update { u ->
        u.copy(
            pedidos =
                u.pedidos.map { p ->
                    if (p.id != id) p
                    else {
                        val next =
                            when (p.status) {
                                "Pago (teste)" -> "Em preparação"
                                "Em preparação" -> "Enviado (teste)"
                                "Enviado (teste)" -> "Entregue (teste)"
                                else -> null
                            }
                        if (next == null) p
                        else
                            p.copy(
                                eventos = p.eventos + EventoPedido(next, System.currentTimeMillis())
                            )
                    }
                }
        )
    }

    private fun localCancel(id: String) = update { u ->
        u.copy(
            pedidos =
                u.pedidos.map { p ->
                    if (p.id != id) p
                    else {
                        require(p.status in listOf("Pago (teste)", "Em preparação")) {
                            "Esse pedido já foi enviado."
                        }
                        p.copy(
                            eventos =
                                p.eventos + EventoPedido("Cancelado", System.currentTimeMillis())
                        )
                    }
                }
        )
    }

    suspend fun register(nome: String, email: String, senha: String) {
        if (ApiClient.enabled)
            authenticate(
                "register",
                JSONObject().put("nome", nome).put("email", email).put("senha", senha),
            )
        else localRegister(nome, email, senha)
    }

    suspend fun login(email: String, senha: String) {
        if (ApiClient.enabled)
            authenticate("login", JSONObject().put("email", email).put("senha", senha))
        else localLogin(email, senha)
    }

    suspend fun logout() {
        if (!ApiClient.enabled) {
            localLogout()
            return
        }
        if (ApiClient.hasSession) {
            try {
                ApiClient.request("/auth/logout", JSONObject())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                /* Logout local remains available without network; remote token expires. */
            }
        }
        ApiClient.clearSession()
        state = EstadoApp()
    }

    suspend fun editProfile(nome: String) {
        if (!remote("profile", JSONObject().put("nome", nome))) localEditProfile(nome)
    }

    suspend fun changePassword(current: String, next: String) {
        if (!remote("password", JSONObject().put("current", current).put("next", next)))
            localChangePassword(current, next)
    }

    suspend fun add(p: Produto) {
        if (!remote("add", JSONObject().put("id", p.id))) localAdd(p)
    }

    suspend fun decrease(id: Int) {
        if (!remote("decrease", JSONObject().put("id", id))) localDecrease(id)
    }

    suspend fun remove(id: Int) {
        if (!remote("remove", JSONObject().put("id", id))) localRemove(id)
    }

    suspend fun favorite(id: Int) {
        if (!remote("favorite", JSONObject().put("id", id))) localFavorite(id)
    }

    suspend fun settings(animations: Boolean = user.animacoes, alerts: Boolean = user.alertas) {
        if (!remote("settings", JSONObject().put("animacoes", animations).put("alertas", alerts)))
            localSettings(animations, alerts)
    }

    suspend fun saveAddress(address: Endereco) {
        val existing =
            user.enderecos.firstOrNull { it.id == address.id }
                ?: user.enderecos.firstOrNull {
                    it.cep == address.cep && it.numero == address.numero && it.rua == address.rua
                }
        val payload = writeAddress(address).put("logradouro", address.rua).put("estado", address.uf)
        existing?.let { payload.put("id", it.id) }
        if (!remote("address", payload)) localSaveAddress(address)
    }

    suspend fun deleteAddress(id: String) {
        if (!remote("delete_address", JSONObject().put("id", id))) localDeleteAddress(id)
    }

    suspend fun review(id: Int, rating: Int, text: String) {
        if (
            !remote(
                "review",
                JSONObject().put("id", id).put("nota", rating).put("comentario", text),
            )
        )
            localReview(id, rating, text)
    }

    suspend fun advance(id: String) {
        if (!remote("advance", JSONObject().put("id", id))) localAdvance(id)
    }

    suspend fun cancel(id: String) {
        if (!remote("cancel", JSONObject().put("id", id))) localCancel(id)
    }

    suspend fun checkout(
        address: Endereco,
        quote: Cotacao,
        payment: String,
        installments: Int,
    ): String {
        if (!ApiClient.enabled) return localCheckout(address, quote, payment, installments)
        check(loggedIn && ApiClient.hasSession) { "Entre na sua conta." }
        val savedAddress =
            user.enderecos.firstOrNull { it.id == address.id }
                ?: user.enderecos.firstOrNull {
                    it.cep == address.cep && it.numero == address.numero && it.rua == address.rua
                }
                ?: error("Salve seu endereço antes de continuar.")
        val body =
            JSONObject()
                .put("endereco", savedAddress.id)
                .put("modalidade", quote.nome)
                .put("frete", quote.valor)
                .put("pagamento", payment)
                .put("parcelas", installments)
        val fingerprint = user.id + body.toString() + user.carrinho.toSortedMap().toString()
        body.put("chave", ApiClient.checkoutKey(fingerprint))
        val result =
            ApiClient.request(
                "/app/mutate",
                JSONObject().put("kind", "checkout").put("payload", body),
            )
        applyRemote(result)
        ApiClient.checkoutCompleted()
        return result.getString("order_id")
    }

    suspend fun refresh() {
        if (!ApiClient.enabled) return
        if (ApiClient.hasSession) applyRemote(ApiClient.request("/app/state"))
        else ProductRepository.replace(ApiClient.request("/app/catalog").getJSONArray("products"))
    }

    internal fun applyRemote(result: JSONObject) {
        val u = readUser(result.getJSONObject("user"))
        ProductRepository.replace(result.getJSONArray("products"))
        state =
            EstadoApp(
                listOf(Usuario("guest", "Visitante", ""), u),
                u.id,
                result.getJSONArray("reviews").objects().map {
                    Review(
                        it.getInt("produtoId"),
                        it.getString("userId"),
                        it.getString("nome"),
                        it.getInt("nota"),
                        it.getString("comentario"),
                    )
                },
            )
    }

    private suspend fun authenticate(kind: String, body: JSONObject) {
        val guest = state.usuarios.first { it.id == "guest" }
        ApiClient.clearSession()
        state = EstadoApp(listOf(guest))
        val result = ApiClient.request("/auth/" + kind, body)
        ApiClient.saveSession(result.getString("access_token"))
        refresh()
        if (guest.carrinho.isNotEmpty() || guest.favoritos.isNotEmpty()) {
            val merged =
                ApiClient.request(
                    "/app/mutate",
                    JSONObject()
                        .put("kind", "merge_guest")
                        .put(
                            "payload",
                            JSONObject()
                                .put(
                                    "carrinho",
                                    JSONObject(guest.carrinho.mapKeys { it.key.toString() }),
                                )
                                .put("favoritos", JSONArray(guest.favoritos.toList())),
                        ),
                )
            applyRemote(merged)
        }
        check(prefs.edit().remove("state").commit()) {
            "Não foi possível limpar o carrinho de visitante."
        }
    }

    private suspend fun remote(kind: String, payload: JSONObject): Boolean {
        if (!ApiClient.enabled || !loggedIn) return false
        check(ApiClient.hasSession) { "Entre na sua conta novamente." }
        applyRemote(
            ApiClient.request("/app/mutate", JSONObject().put("kind", kind).put("payload", payload))
        )
        return true
    }

    private fun hash(password: String, salt: String): String {
        val spec =
            PBEKeySpec(password.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 60000, 256)
        return try {
            Base64.encodeToString(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded,
                Base64.NO_WRAP,
            )
        } finally {
            spec.clearPassword()
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun writeAddress(a: Endereco) =
        JSONObject()
            .put("id", a.id)
            .put("cep", a.cep)
            .put("rua", a.rua)
            .put("numero", a.numero)
            .put("complemento", a.complemento)
            .put("bairro", a.bairro)
            .put("cidade", a.cidade)
            .put("uf", a.uf)

    private fun readAddress(a: JSONObject) =
        Endereco(
            a.getString("id"),
            a.getString("cep"),
            a.getString("rua"),
            a.getString("numero"),
            a.getString("complemento"),
            a.getString("bairro"),
            a.getString("cidade"),
            a.getString("uf"),
        )

    private fun writeOrder(p: Pedido): JSONObject =
        JSONObject()
            .put("id", p.id)
            .put("criado", p.criado)
            .put("endereco", writeAddress(p.endereco))
            .put("frete", p.frete)
            .put("modalidade", p.modalidade)
            .put("pagamento", p.pagamento)
            .put("parcelas", p.parcelas)
            .put("teste", p.teste)
            .put(
                "itens",
                JSONArray(
                    p.itens.map {
                        JSONObject()
                            .put("produtoId", it.produtoId)
                            .put("nome", it.nome)
                            .put("imagem", it.imagem)
                            .put("preco", it.preco)
                            .put("quantidade", it.quantidade)
                    }
                ),
            )
            .put(
                "eventos",
                JSONArray(
                    p.eventos.map { JSONObject().put("status", it.status).put("data", it.data) }
                ),
            )

    private fun readOrder(p: JSONObject) =
        Pedido(
            p.getString("id"),
            p.getLong("criado"),
            p.getJSONArray("itens").objects().map {
                LinhaPedido(
                    it.getInt("produtoId"),
                    it.getString("nome"),
                    it.getString("imagem"),
                    it.getDouble("preco"),
                    it.getInt("quantidade"),
                )
            },
            readAddress(p.getJSONObject("endereco")),
            p.getDouble("frete"),
            p.getString("modalidade"),
            p.getString("pagamento"),
            p.getInt("parcelas"),
            p.getJSONArray("eventos").objects().map {
                EventoPedido(it.getString("status"), it.getLong("data"))
            },
            p.optBoolean("teste", true),
        )

    private fun writeUser(u: Usuario): JSONObject =
        JSONObject()
            .put("id", u.id)
            .put("nome", u.nome)
            .put("email", u.email)
            .put("salt", u.salt)
            .put("hash", u.passwordHash)
            .put("animacoes", u.animacoes)
            .put("alertas", u.alertas)
            .put("carrinho", JSONObject(u.carrinho.mapKeys { it.key.toString() }))
            .put("favoritos", JSONArray(u.favoritos.toList()))
            .put("enderecos", JSONArray(u.enderecos.map(::writeAddress)))
            .put("pedidos", JSONArray(u.pedidos.map(::writeOrder)))

    private fun readUser(u: JSONObject): Usuario {
        val cart = u.getJSONObject("carrinho")
        val favorites = u.getJSONArray("favoritos")
        return Usuario(
            u.getString("id"),
            u.getString("nome"),
            u.getString("email"),
            u.getString("salt"),
            u.getString("hash"),
            cart.keys().asSequence().associate { it.toInt() to cart.getInt(it) },
            (0 until favorites.length()).map { favorites.getInt(it) }.toSet(),
            u.getJSONArray("enderecos").objects().map(::readAddress),
            u.getJSONArray("pedidos").objects().map(::readOrder),
            u.optBoolean("animacoes", true),
            u.optBoolean("alertas", true),
        )
    }
}
