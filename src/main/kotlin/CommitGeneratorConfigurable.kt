package com.hiper.iacommit

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class CommitGeneratorConfigurable : Configurable {

    private val settings = CommitGeneratorSettings.getInstance()
    private var mainPanel = createPanel()

    override fun getDisplayName(): String = "AI Commit Generator"

    override fun createComponent(): JComponent {
        mainPanel = createPanel()
        return mainPanel
    }

    private fun createPanel() = panel {
        group("Configuración del Proveedor de IA (OpenAI Compatible)") {
            row("Endpoint URL:") {
                textField()
                    .bindText(settings::endpointUrl)
                    .align(AlignX.FILL)
                    .comment("Ej. Groq: https://api.groq.com/openai/v1/chat/completions | GitHub: https://models.inference.ai.azure.com/chat/completions")
            }
            row("Modelo:") {
                textField()
                    .bindText(settings::modelName)
                    .align(AlignX.FILL)
                    .comment("Ej. llama-3.3-70b-versatile, gpt-4o-mini")
            }
            row("API Key:") {
                passwordField()
                    .bindText(settings::apiKey)
                    .align(AlignX.FILL)
                    .comment("Si queda vacío, el plugin funcionará en modo offline analizando los archivos modificados.")
            }
        }
    }

    override fun isModified(): Boolean = mainPanel.isModified()

    override fun apply() {
        mainPanel.apply()
    }

    override fun reset() {
        mainPanel.reset()
    }
}