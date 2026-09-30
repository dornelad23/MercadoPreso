package com.example.mercadopreso.data

import com.example.mercadopreso.data.model.Cotacao
import com.example.mercadopreso.data.model.Endereco
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ShippingService {
    suspend fun address(cep: String): Endereco =
        withContext(Dispatchers.IO) {
            require(cep.matches(Regex("[0-9]{8}"))) { "Informe os 8 números do CEP." }
            val connection =
                URL("https://viacep.com.br/ws/$cep/json/").openConnection() as HttpsURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            try {
                check(connection.responseCode == 200) { "Não foi possível consultar o CEP." }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                require(!json.optBoolean("erro", false)) { "CEP não encontrado." }
                val city = json.optString("localidade")
                val uf = json.optString("uf")
                require(city.isNotBlank() && uf.length == 2) {
                    "O serviço retornou um endereço incompleto."
                }
                Endereco(
                    cep = cep,
                    rua = json.optString("logradouro"),
                    bairro = json.optString("bairro"),
                    cidade = city,
                    uf = uf
                )
            } finally {
                connection.disconnect()
            }
        }

    /** Demo pricing only. No carrier is connected; the UI labels every quote as an estimate. */
    fun quotes(uf: String, cart: Map<Int, Int>): List<Cotacao> {
        val products = cart.mapNotNull { (id, q) -> ProductRepository.find(id)?.let { it to q } }
        val weight = products.sumOf { (p, q) -> p.pesoKg * q }
        val free = products.isNotEmpty() && products.all { it.first.freteGratis }
        val base =
            when (uf) {
                "SP" -> 9.90
                "RJ",
                "MG",
                "ES",
                "PR" -> 16.90
                "SC",
                "RS",
                "GO",
                "DF",
                "MS",
                "MT" -> 22.90
                else -> 29.90
            }
        val value = if (free) 0.0 else kotlin.math.round((base + weight * 2.30) * 100) / 100
        return listOf(
            Cotacao("Econômico · estimativa", value, "5 a 10 dias úteis"),
            Cotacao(
                "Expresso · estimativa",
                if (free) 0.0 else kotlin.math.round(value * 1.5 * 100) / 100,
                "2 a 5 dias úteis"
            )
        )
    }
}
