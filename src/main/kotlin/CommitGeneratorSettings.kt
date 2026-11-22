package com.hiper.iacommit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "com.hiper.iacommit.CommitGeneratorSettings",
    storages = [Storage("AiCommitGeneratorSettings.xml")]
)
class CommitGeneratorSettings : PersistentStateComponent<CommitGeneratorSettings> {

    var endpointUrl: String = "https://api.groq.com/openai/v1/chat/completions"
    var modelName: String = "llama-3.3-70b-versatile"
    var apiKey: String = ""

    override fun getState(): CommitGeneratorSettings = this

    override fun loadState(state: CommitGeneratorSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): CommitGeneratorSettings =
            ApplicationManager.getApplication().getService(CommitGeneratorSettings::class.java)
    }
}