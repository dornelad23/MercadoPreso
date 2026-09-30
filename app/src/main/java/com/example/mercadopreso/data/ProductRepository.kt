package com.example.mercadopreso.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.mercadopreso.data.model.Produto
import org.json.JSONArray

object ProductRepository {
    var produtos: List<Produto> by mutableStateOf(emptyList())
        private set

    val categorias
        get() = listOf("Todos") + produtos.map { it.categoria }.distinct()

    fun init(context: Context) {
        val json =
            JSONArray(context.assets.open("produtos.json").bufferedReader().use { it.readText() })
        replace(json)
    }

    fun replace(json: JSONArray) {
        produtos =
            (0 until json.length()).map { index ->
                val p = json.getJSONObject(index)
                Produto(
                    p.getInt("id"),
                    p.getString("nome"),
                    p.getString("descricao"),
                    p.getDouble("preco"),
                    p.getString("categoria"),
                    p.getDouble("avaliacao"),
                    p.getInt("vendas"),
                    p.getString("imagem"),
                    p.getInt("estoque"),
                    p.getBoolean("freteGratis"),
                    if (p.isNull("precoOriginal")) null else p.getDouble("precoOriginal"),
                    p.getDouble("pesoKg"),
                )
            }
    }

    fun find(id: Int) = produtos.firstOrNull { it.id == id }
}
